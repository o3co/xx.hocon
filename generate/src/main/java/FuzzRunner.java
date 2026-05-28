import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Inc2 fuzz differential runner: the "find the unknowns" loop.
 *
 * Generates {@code -Dfuzz.count} seeded HOCON documents (reproducible from
 * {@code -Dfuzz.seed}), runs each through the same oracle + go/rs/ts comparison
 * as the seed-corpus driver ({@link DifferentialDriver#evaluate}), and for every
 * case that diverges from Lightbend, shrinks it to a minimal reproducer via
 * statement-level delta debugging. Unique minimal repros are written under
 * {@code differential/fuzz-findings/} and summarised in
 * {@code differential/report/fuzz-summary.md}.
 *
 * Each case's RNG is {@code new Random(seed + index)}, so any finding is
 * replayable from its reported seed.
 */
public final class FuzzRunner {

    public static void main(String[] args) throws IOException {
        long seed = Long.getLong("fuzz.seed", 1L);
        int count = Integer.getInteger("fuzz.count", 100);
        Path workDir = Path.of(System.getProperty("fuzz.work", "differential/fuzz-work"));
        Path findingsDir = Path.of(System.getProperty("fuzz.findings", "differential/fuzz-findings"));
        Path reportDir = Path.of(System.getProperty("report.dir", "differential/report"));

        Map<String, String[]> adapters = DifferentialDriver.parseAdapters();
        if (adapters.isEmpty()) {
            System.err.println("fuzz needs at least one -Dadapter.<go|rs|ts>; nothing to compare against the oracle.");
            return;
        }

        rmTree(workDir);
        rmTree(findingsDir);
        Files.createDirectories(workDir);
        Files.createDirectories(findingsDir);
        Path probeRoot = workDir.resolve("probe");

        List<Finding> findings = new ArrayList<>();
        Set<String> seenMinimal = new HashSet<>();
        int diverged = 0, nondet = 0;

        for (int i = 0; i < count; i++) {
            long caseSeed = seed + i;
            FuzzDoc doc = FuzzCorpusGenerator.generate(new Random(caseSeed));
            Path caseDir = workDir.resolve("case" + i);
            doc.writeTo(caseDir);
            DifferentialDriver.CaseReport r =
                DifferentialDriver.evaluate("fuzz-" + i, caseDir.resolve("main.conf"), adapters, null);

            if (r.status.equals("ORACLE_NONDET")) { nondet++; continue; }
            if (!r.status.equals("DIVERGE_FROM_LIGHTBEND")) continue;
            diverged++;

            FuzzDoc min = shrink(doc, signature(r), probeRoot, adapters);
            if (!seenMinimal.add(min.render())) continue; // dedup identical minimal repros

            Path fdir = findingsDir.resolve("finding-" + findings.size());
            min.writeTo(fdir);
            DifferentialDriver.CaseReport mr =
                DifferentialDriver.evaluate("finding-" + findings.size(), fdir.resolve("main.conf"), adapters, null);
            findings.add(new Finding(caseSeed, min, mr));
        }

        Files.createDirectories(reportDir);
        writeFuzzReport(reportDir.resolve("fuzz-summary.md"), seed, count, diverged, nondet, findings, adapters.keySet());

        System.out.println("Fuzz: seed=" + seed + " count=" + count + " — " + diverged
            + " diverged, " + findings.size() + " unique minimal repros, " + nondet + " oracle-nondet.");
        for (Finding f : findings) {
            System.out.println("  FINDING (seed " + f.caseSeed + ", off: "
                + String.join(",", f.report.divergersFromOracle) + "): " + oneLine(f.doc.render()));
        }
    }

    // ---- Shrinking (statement-level delta debugging) ------------------------

    // Statement-level delta debugging that preserves the ORIGINAL divergence
    // signature (oracle + per-engine ok/error pattern). Without the signature
    // guard the shrinker drifts to a smaller-but-different divergence — e.g. it
    // would collapse any document down to an empty file, which itself diverges
    // (all impls reject empty input, Lightbend returns {}).
    static FuzzDoc shrink(FuzzDoc doc, String sig, Path probeRoot, Map<String, String[]> adapters) throws IOException {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 500) {
            changed = false;
            for (int i = 0; i < doc.statements.size(); i++) {
                List<String> reduced = new ArrayList<>(doc.statements);
                reduced.remove(i);
                FuzzDoc cand = new FuzzDoc(reduced, pruneIncludes(reduced, doc.includes));
                if (stillDiverges(cand, sig, probeRoot, adapters)) {
                    doc = cand;
                    changed = true;
                    break;
                }
            }
        }
        return doc;
    }

    static boolean stillDiverges(FuzzDoc d, String sig, Path probeRoot, Map<String, String[]> adapters) throws IOException {
        rmTree(probeRoot);
        d.writeTo(probeRoot);
        DifferentialDriver.CaseReport r =
            DifferentialDriver.evaluate("probe", probeRoot.resolve("main.conf"), adapters, null);
        return r.status.equals("DIVERGE_FROM_LIGHTBEND") && signature(r).equals(sig);
    }

    /** A divergence "shape": oracle outcome + each engine's ok/error/broken
     *  state + the set of impls that diverge from the oracle. Pinning the
     *  diverging set (not just the ok/error pattern) stops the shrinker drifting
     *  to a different engine's divergence that merely shares the same shape. */
    static String signature(DifferentialDriver.CaseReport r) {
        StringBuilder sb = new StringBuilder(state(r.oracle));
        for (String k : DifferentialDriver.IMPLS) {
            if (r.impls.containsKey(k)) sb.append("|").append(k).append(":").append(state(r.impls.get(k)));
        }
        List<String> div = new ArrayList<>(r.divergersFromOracle);
        div.sort(null);
        sb.append("#").append(String.join(",", div));
        return sb.toString();
    }

    static String state(DifferentialDriver.EngineResult e) {
        return e.broken ? "B" : (e.ok ? "K" : "E");
    }

    static Map<String, String> pruneIncludes(List<String> statements, Map<String, String> includes) {
        Map<String, String> kept = new java.util.LinkedHashMap<>();
        String joined = String.join("\n", statements);
        for (Map.Entry<String, String> e : includes.entrySet()) {
            if (joined.contains("\"" + e.getKey() + "\"")) kept.put(e.getKey(), e.getValue());
        }
        return kept;
    }

    // ---- Report -------------------------------------------------------------

    static void writeFuzzReport(Path path, long seed, int count, int diverged, int nondet,
                                List<Finding> findings, Set<String> implKeys) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Fuzz differential report\n\n");
        sb.append("Seed: ").append(seed).append("  ·  cases: ").append(count)
          .append("  ·  diverged: ").append(diverged)
          .append("  ·  unique minimal repros: ").append(findings.size())
          .append("  ·  oracle-nondet: ").append(nondet).append("\n\n");
        sb.append("Engines: oracle(Lightbend) + ").append(String.join(", ", implKeys)).append("\n\n");
        if (findings.isEmpty()) {
            sb.append("No new divergences found.\n");
        }
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("## finding-").append(i)
              .append("  — seed ").append(f.caseSeed)
              .append(", diverging: ").append(String.join(", ", f.report.divergersFromOracle));
            if (f.report.implsDisagree) sb.append(" [impls disagree]");
            sb.append("\n\n```hocon\n").append(f.doc.render()).append("```\n\n");
            if (!f.doc.includes.isEmpty()) {
                for (Map.Entry<String, String> inc : f.doc.includes.entrySet()) {
                    sb.append("`").append(inc.getKey()).append("`:\n```hocon\n").append(inc.getValue()).append("```\n\n");
                }
            }
            sb.append("- oracle: ").append(DifferentialDriver.brief(f.report.oracle)).append("\n");
            for (String k : implKeys) {
                if (f.report.impls.containsKey(k)) {
                    sb.append("- ").append(k).append(": ").append(DifferentialDriver.brief(f.report.impls.get(k))).append("\n");
                }
            }
            sb.append("\n");
        }
        Files.writeString(path, sb.toString());
    }

    static String oneLine(String s) {
        String t = s.replace("\n", " ; ").trim();
        return t.length() <= 160 ? t : t.substring(0, 160) + "…";
    }

    static void rmTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
    }

    static final class Finding {
        final long caseSeed;
        final FuzzDoc doc;
        final DifferentialDriver.CaseReport report;

        Finding(long caseSeed, FuzzDoc doc, DifferentialDriver.CaseReport report) {
            this.caseSeed = caseSeed;
            this.doc = doc;
            this.report = report;
        }
    }

    private FuzzRunner() {}
}

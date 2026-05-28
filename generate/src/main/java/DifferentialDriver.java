import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Cross-impl differential driver: the in-house "cgordon".
 *
 * For every seed corpus case it runs the Lightbend reference implementation
 * (the oracle, in-process via typesafe-config) plus the go/rs/ts adapters
 * (subprocesses emitting canonical JSON or a {@code {"__error__":..}} record),
 * normalises each result, and classifies the case:
 *
 *   ALL_AGREE              every engine agrees (all success+equal, or all error)
 *   DIVERGE_FROM_LIGHTBEND at least one impl disagrees with the oracle
 *   ORACLE_NONDET          the oracle gave different output across two runs
 *   SUPPRESSED             a known-intentional divergence (E-item suppression)
 * Plus a non-status sub-flag IMPLS_DISAGREE (the impls disagree among
 * themselves) that is surfaced in the report but never assigned to status.
 *
 * Known-intentional divergences (E-items, documented as .divergence.md in
 * xx.hocon) are loaded from a suppression file and downgraded so only NEW
 * divergences surface as action items. The triage of a new divergence follows
 * the lightbend-as-spec-interpretation-authority rule: align the impl to
 * Lightbend (bug) unless Lightbend's behaviour is itself indefensible.
 *
 * Adapter commands are configured via system properties (space-separated, the
 * conf file is appended as the final arg):
 *   -Dadapter.go="<dir>/bin/hocon-json"
 *   -Dadapter.rs="<dir>/target/debug/examples/hocon-json"
 *   -Dadapter.ts="node <dir>/tools/hocon-json.ts"
 * Missing properties skip that engine.
 */
public final class DifferentialDriver {

    static final long ADAPTER_TIMEOUT_SECONDS = 20;
    static final String[] IMPLS = {"go", "rs", "ts"};

    // Unlike GenerateExpected.GSON (which drops nulls for the curated expected
    // files), the differential harness MUST preserve explicit nulls so the
    // oracle faithfully compares against impls that render null members — this
    // is exactly the go.hocon#131 null-preservation class.
    static final Gson GSON_COMPACT = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    static final Gson GSON_PRETTY = new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create();

    public static void main(String[] args) throws IOException {
        Path corpusDir = Path.of(System.getProperty("corpus.dir", "differential/corpus"));
        Path reportDir = Path.of(System.getProperty("report.dir", "differential/report"));
        Path suppressionFile = Path.of(System.getProperty("suppression.file", "differential/known-divergences.json"));

        CorpusGenerator.generate(corpusDir);

        Map<String, String[]> adapters = new LinkedHashMap<>();
        for (String impl : IMPLS) {
            String cmd = System.getProperty("adapter." + impl);
            if (cmd != null && !cmd.isBlank()) {
                adapters.put(impl, cmd.trim().split("\\s+"));
            }
        }
        if (adapters.isEmpty()) {
            System.err.println("WARNING: no -Dadapter.<go|rs|ts> configured; only the oracle will run.");
        }

        Map<String, Suppression> suppressions = loadSuppressions(suppressionFile);

        List<Path> cases = new ArrayList<>();
        try (var dirs = Files.list(corpusDir)) {
            dirs.filter(Files::isDirectory)
                .filter(d -> Files.exists(d.resolve("main.conf")))
                .sorted()
                .forEach(cases::add);
        }

        List<CaseReport> reports = new ArrayList<>();
        for (Path caseDir : cases) {
            String caseId = caseDir.getFileName().toString();
            Path mainConf = caseDir.resolve("main.conf");

            EngineResult oracle = runOracle(mainConf);
            Map<String, EngineResult> impls = new LinkedHashMap<>();
            for (Map.Entry<String, String[]> a : adapters.entrySet()) {
                impls.put(a.getKey(), runAdapter(a.getValue(), mainConf));
            }
            reports.add(classify(caseId, oracle, impls, suppressions.get(caseId)));
        }

        Files.createDirectories(reportDir);
        writeJsonReport(reportDir.resolve("results.json"), reports, adapters.keySet());
        writeMarkdownReport(reportDir.resolve("summary.md"), reports, adapters.keySet());
        printConsoleSummary(reports);
    }

    // ---- Oracle (Lightbend) -------------------------------------------------

    static EngineResult runOracle(Path mainConf) {
        EngineResult a = oracleOnce(mainConf);
        EngineResult b = oracleOnce(mainConf);
        if (a.ok && b.ok && !normalize(a.json).equals(normalize(b.json))) {
            a.nondeterministic = true;
        }
        if (a.ok != b.ok) {
            a.nondeterministic = true;
        }
        return a;
    }

    static EngineResult oracleOnce(Path mainConf) {
        ConfigFactory.invalidateCaches();
        try {
            Config c = ConfigFactory
                .parseFile(mainConf.toFile(), ConfigParseOptions.defaults().setAllowMissing(false))
                .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(true));
            return EngineResult.success(GSON_COMPACT.toJson(GenerateExpected.configValueToJson(c.root())));
        } catch (Exception e) {
            return EngineResult.error(e.getClass().getName(), String.valueOf(e.getMessage()));
        }
    }

    // ---- Adapter subprocess -------------------------------------------------

    static EngineResult runAdapter(String[] cmdTemplate, Path mainConf) {
        List<String> cmd = new ArrayList<>(List.of(cmdTemplate));
        cmd.add(mainConf.toAbsolutePath().toString());
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            // Inherit env (so PATH/node resolve); the corpus only references
            // guaranteed-unset GH_DIFF_UNSET_* names, so ambient env is inert.
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            byte[] err = p.getErrorStream().readAllBytes();
            if (!p.waitFor(ADAPTER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return EngineResult.broken("timeout after " + ADAPTER_TIMEOUT_SECONDS + "s");
            }
            String stdout = new String(out, StandardCharsets.UTF_8).trim();
            if (stdout.isEmpty()) {
                return EngineResult.broken("empty stdout (exit " + p.exitValue() + ", stderr: "
                    + new String(err, StandardCharsets.UTF_8).trim() + ")");
            }
            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(stdout);
            } catch (RuntimeException pe) {
                return EngineResult.broken("unparseable stdout: " + truncate(stdout, 200));
            }
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("__error__")) {
                JsonObject eo = parsed.getAsJsonObject().getAsJsonObject("__error__");
                String type = eo.has("type") ? eo.get("type").getAsString() : "error";
                String msg = eo.has("message") ? eo.get("message").getAsString() : "";
                return EngineResult.error(type, msg);
            }
            return EngineResult.success(stdout);
        } catch (IOException | InterruptedException e) {
            return EngineResult.broken(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---- Normalization ------------------------------------------------------

    /** Canonicalises an element: object keys sorted, integral numbers folded to
     *  long, non-integral to double; strings/bools/null untouched. Lets 5 and
     *  5.0 compare equal while keeping "26.05" != "26.5" distinct as strings.
     *  Integral magnitudes >= 2^53 fold to double (lossy) — acceptable for the
     *  current corpus; revisit with BigInteger if large-long fixtures land. */
    static JsonElement normalize(JsonElement e) {
        if (e == null || e.isJsonNull()) return com.google.gson.JsonNull.INSTANCE;
        if (e.isJsonObject()) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet()) {
                sorted.put(m.getKey(), normalize(m.getValue()));
            }
            JsonObject o = new JsonObject();
            sorted.forEach(o::add);
            return o;
        }
        if (e.isJsonArray()) {
            com.google.gson.JsonArray out = new com.google.gson.JsonArray();
            e.getAsJsonArray().forEach(x -> out.add(normalize(x)));
            return out;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) {
            double d = p.getAsDouble();
            if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) < 9.007199254740992e15) {
                return new JsonPrimitive((long) d);
            }
            return new JsonPrimitive(d);
        }
        return p;
    }

    static JsonElement normalize(String json) {
        return normalize(JsonParser.parseString(json));
    }

    // ---- Classification -----------------------------------------------------

    static CaseReport classify(String caseId, EngineResult oracle,
                               Map<String, EngineResult> impls, Suppression supp) {
        CaseReport r = new CaseReport();
        r.caseId = caseId;
        r.oracle = oracle;
        r.impls = impls;
        r.suppression = supp;

        if (oracle.nondeterministic) {
            r.status = "ORACLE_NONDET";
            return r;
        }

        List<String> divergers = new ArrayList<>();
        for (Map.Entry<String, EngineResult> e : impls.entrySet()) {
            if (!agree(oracle, e.getValue())) divergers.add(e.getKey());
        }
        r.divergersFromOracle = divergers;

        // Do the impls agree among themselves?
        boolean implsDisagree = false;
        List<EngineResult> implResults = new ArrayList<>(impls.values());
        for (int i = 1; i < implResults.size(); i++) {
            if (!agree(implResults.get(0), implResults.get(i))) { implsDisagree = true; break; }
        }
        r.implsDisagree = implsDisagree;

        if (divergers.isEmpty() && !implsDisagree) {
            r.status = "ALL_AGREE";
        } else if (supp != null && !implsDisagree) {
            // A suppression whitelists an intentional, agreed divergence FROM
            // Lightbend; it must never hide the impls disagreeing among
            // THEMSELVES, which is a new inter-impl bug.
            r.status = "SUPPRESSED";
        } else {
            r.status = "DIVERGE_FROM_LIGHTBEND";
        }
        return r;
    }

    /** Two results agree if both errored, or both succeeded with equal
     *  normalized JSON. A BROKEN result agrees with nothing. */
    static boolean agree(EngineResult a, EngineResult b) {
        if (a.broken || b.broken) return false;
        if (a.ok != b.ok) return false;
        // Both errored: agree per the "any error raised" convention. The error
        // type/message strings are intentionally NOT compared — each impl spells
        // them differently (go FQCN, rs enum variant, ts class name), so they
        // are report-only, never a comparison input.
        if (!a.ok) return true;
        return normalize(a.json).equals(normalize(b.json));
    }

    // ---- Suppression --------------------------------------------------------

    static Map<String, Suppression> loadSuppressions(Path file) throws IOException {
        Map<String, Suppression> out = new LinkedHashMap<>();
        if (!Files.exists(file)) return out;
        JsonElement root = JsonParser.parseString(Files.readString(file));
        if (!root.isJsonObject()) return out;
        JsonElement entries = root.getAsJsonObject().get("divergences");
        if (entries == null || !entries.isJsonArray()) return out;
        for (JsonElement el : entries.getAsJsonArray()) {
            JsonObject o = el.getAsJsonObject();
            Suppression s = new Suppression();
            s.caseId = o.get("case").getAsString();
            s.eRef = o.has("eRef") ? o.get("eRef").getAsString() : "";
            s.reason = o.has("reason") ? o.get("reason").getAsString() : "";
            out.put(s.caseId, s);
        }
        return out;
    }

    // ---- Reporting ----------------------------------------------------------

    static void writeJsonReport(Path path, List<CaseReport> reports, java.util.Set<String> implKeys) throws IOException {
        JsonObject root = new JsonObject();
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (CaseReport r : reports) {
            JsonObject o = new JsonObject();
            o.addProperty("case", r.caseId);
            o.addProperty("status", r.status);
            o.add("oracle", engineJson(r.oracle));
            JsonObject impls = new JsonObject();
            for (String k : implKeys) {
                if (r.impls.containsKey(k)) impls.add(k, engineJson(r.impls.get(k)));
            }
            o.add("impls", impls);
            if (!r.divergersFromOracle.isEmpty()) {
                com.google.gson.JsonArray d = new com.google.gson.JsonArray();
                r.divergersFromOracle.forEach(d::add);
                o.add("divergersFromOracle", d);
            }
            if (r.suppression != null) {
                JsonObject s = new JsonObject();
                s.addProperty("eRef", r.suppression.eRef);
                s.addProperty("reason", r.suppression.reason);
                o.add("suppression", s);
            }
            arr.add(o);
        }
        root.add("cases", arr);
        Files.writeString(path, GSON_PRETTY.toJson(root) + "\n");
    }

    static JsonElement engineJson(EngineResult r) {
        JsonObject o = new JsonObject();
        if (r.broken) {
            o.addProperty("outcome", "broken");
            o.addProperty("detail", r.errorMessage);
        } else if (r.ok) {
            o.addProperty("outcome", "ok");
            o.add("json", normalize(r.json));
            if (r.nondeterministic) o.addProperty("nondeterministic", true);
        } else {
            o.addProperty("outcome", "error");
            o.addProperty("type", r.errorType);
            o.addProperty("message", r.errorMessage);
        }
        return o;
    }

    static void writeMarkdownReport(Path path, List<CaseReport> reports, java.util.Set<String> implKeys) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Differential harness report\n\n");
        Map<String, Integer> counts = new TreeMap<>();
        for (CaseReport r : reports) counts.merge(r.status, 1, Integer::sum);
        sb.append("Engines: oracle(Lightbend) + ").append(String.join(", ", implKeys)).append("\n\n");
        sb.append("| status | count |\n|---|---|\n");
        counts.forEach((k, v) -> sb.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        sb.append("\n");

        appendSection(sb, "NEW DIVERGENCES (action needed)", reports, implKeys,
            r -> r.status.equals("DIVERGE_FROM_LIGHTBEND"));
        appendSection(sb, "Oracle non-deterministic (excluded)", reports, implKeys,
            r -> r.status.equals("ORACLE_NONDET"));
        appendSection(sb, "Suppressed (known E-item divergences)", reports, implKeys,
            r -> r.status.equals("SUPPRESSED"));
        appendSection(sb, "All agree", reports, implKeys,
            r -> r.status.equals("ALL_AGREE"));
        Files.writeString(path, sb.toString());
    }

    interface CasePredicate { boolean test(CaseReport r); }

    static void appendSection(StringBuilder sb, String title, List<CaseReport> reports,
                              java.util.Set<String> implKeys, CasePredicate pred) {
        List<CaseReport> sel = reports.stream().filter(pred::test).toList();
        if (sel.isEmpty()) return;
        sb.append("## ").append(title).append(" (").append(sel.size()).append(")\n\n");
        for (CaseReport r : sel) {
            sb.append("### ").append(r.caseId);
            if (!r.divergersFromOracle.isEmpty()) {
                sb.append("  — diverging: ").append(String.join(", ", r.divergersFromOracle));
            }
            if (r.implsDisagree) sb.append(" [impls disagree]");
            sb.append("\n\n");
            sb.append("- oracle: ").append(brief(r.oracle)).append("\n");
            for (String k : implKeys) {
                if (r.impls.containsKey(k)) sb.append("- ").append(k).append(": ").append(brief(r.impls.get(k))).append("\n");
            }
            if (r.suppression != null) {
                sb.append("- suppressed: ").append(r.suppression.eRef).append(" — ").append(r.suppression.reason).append("\n");
            }
            sb.append("\n");
        }
    }

    static String brief(EngineResult r) {
        if (r.broken) return "BROKEN(" + truncate(r.errorMessage, 120) + ")";
        if (!r.ok) return "ERROR " + r.errorType;
        return "`" + truncate(GSON_COMPACT.toJson(normalize(r.json)), 300) + "`";
    }

    static void printConsoleSummary(List<CaseReport> reports) {
        long diverge = reports.stream().filter(r -> r.status.equals("DIVERGE_FROM_LIGHTBEND")).count();
        long nondet = reports.stream().filter(r -> r.status.equals("ORACLE_NONDET")).count();
        long suppressed = reports.stream().filter(r -> r.status.equals("SUPPRESSED")).count();
        long agree = reports.stream().filter(r -> r.status.equals("ALL_AGREE")).count();
        System.out.println("Differential: " + reports.size() + " cases — "
            + agree + " agree, " + diverge + " NEW divergence, "
            + suppressed + " suppressed, " + nondet + " oracle-nondet.");
        for (CaseReport r : reports) {
            if (r.status.equals("DIVERGE_FROM_LIGHTBEND")) {
                System.out.println("  DIVERGE " + r.caseId + " (impls off-oracle: "
                    + String.join(",", r.divergersFromOracle) + ")");
            }
        }
    }

    static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    // ---- Data ---------------------------------------------------------------

    static final class EngineResult {
        boolean ok;
        boolean broken;
        boolean nondeterministic;
        String json;
        String errorType;
        String errorMessage;

        static EngineResult success(String json) {
            EngineResult r = new EngineResult();
            r.ok = true;
            r.json = json;
            return r;
        }
        static EngineResult error(String type, String message) {
            EngineResult r = new EngineResult();
            r.ok = false;
            r.errorType = type;
            r.errorMessage = message;
            return r;
        }
        static EngineResult broken(String detail) {
            EngineResult r = new EngineResult();
            r.broken = true;
            r.errorMessage = detail;
            return r;
        }
    }

    static final class Suppression {
        String caseId;
        String eRef;
        String reason;
    }

    static final class CaseReport {
        String caseId;
        String status;
        EngineResult oracle;
        Map<String, EngineResult> impls;
        List<String> divergersFromOracle = new ArrayList<>();
        boolean implsDisagree;
        Suppression suppression;
    }

    private DifferentialDriver() {}
}

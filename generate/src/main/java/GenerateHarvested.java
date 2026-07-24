import com.typesafe.config.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Expected-output generator for the harvested ecosystem corpus
 * (testdata/harvested/ — see its README.md).
 *
 * Unlike GenerateExpected, there are no normative SUCCESS/ERROR lists: every
 * *.conf under testdata/harvested/<source>/ is a fixture root unless listed in
 * that directory's companions.txt, and the observed Lightbend behaviour decides
 * the classification:
 *
 *   parseFile().resolve() succeeds -> expected/harvested/<source>/<name>-expected.json
 *   parseFile().resolve() throws   -> expected/harvested/<source>/<name>.error
 *
 * Resolution is always hermetic (setUseSystemEnvironment(false)) so harvested
 * fixtures referencing ${?HOME}-style env fallbacks stay machine-independent.
 */
public class GenerateHarvested {

    public static void main(String[] args) throws Exception {
        Path testdataDir = Path.of("../testdata/harvested");
        Path expectedDir = Path.of("../expected/harvested");

        System.out.println("Generating harvested-corpus expected output from typesafe-config...");
        System.out.println("testdata dir: " + testdataDir.toAbsolutePath());
        System.out.println();

        int jsonCount = 0;
        int errorCount = 0;
        int companionCount = 0;

        List<Path> sourceDirs;
        try (Stream<Path> s = Files.list(testdataDir)) {
            sourceDirs = s.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        }

        for (Path sourceDir : sourceDirs) {
            String source = sourceDir.getFileName().toString();
            Set<String> companions = loadCompanions(sourceDir.resolve("companions.txt"));

            List<Path> confs;
            try (Stream<Path> s = Files.walk(sourceDir)) {
                confs = s.filter(p -> p.getFileName().toString().endsWith(".conf"))
                         .sorted()
                         .collect(Collectors.toList());
            }

            Path outDir = expectedDir.resolve(source);
            Files.createDirectories(outDir);
            clearGeneratedOutputs(outDir);

            for (Path confPath : confs) {
                // Relative path within the source dir (slash-normalized) — source
                // trees may nest (per-case dirs, include trees), and companions.txt
                // entries use the same relative form.
                String name = sourceDir.relativize(confPath).toString().replace('\\', '/');
                if (companions.contains(name)) {
                    System.out.println("  companion (skipped): " + source + "/" + name);
                    companionCount++;
                    continue;
                }

                ConfigResolveOptions resolveOpts = ConfigResolveOptions.defaults()
                    .setUseSystemEnvironment(false);
                // Only the reference implementation's own rejection may classify a
                // fixture as an error; I/O or rendering failures must fail the run.
                Config config = null;
                ConfigException caught = null;
                try {
                    config = ConfigFactory.parseFile(confPath.toFile()).resolve(resolveOpts);
                } catch (ConfigException e) {
                    caught = e;
                }

                // Strip the ".conf" suffix (suffix-only — a plain replace()
                // would also mangle a mid-path ".conf" like "foo.conf.d/").
                String stem = name.substring(0, name.length() - ".conf".length());

                if (caught == null) {
                    String json = GenerateExpected.toSortedJson(config.root()) + "\n";
                    Path outPath = outDir.resolve(stem + "-expected.json");
                    Files.createDirectories(outPath.getParent());
                    Files.writeString(outPath, json);
                    System.out.println("  OK: " + source + "/" + name);
                    jsonCount++;
                    // Stale-doc alarm: a divergence doc usually documents a
                    // reference-side error, so success + doc means a reference
                    // upgrade may have changed behaviour. Docs whose first line
                    // contains "quirk" describe sanctioned quirk SUCCESSES
                    // (e.g. array-5.error) and are expected to coexist.
                    Path divergenceDoc = sourceDir.resolve(stem + ".divergence.md");
                    if (Files.exists(divergenceDoc)
                            && !Files.readAllLines(divergenceDoc).get(0).toLowerCase().contains("quirk")) {
                        System.err.println("  WARN: " + source + "/" + name + " classifies as success but has a "
                            + ".divergence.md sibling — check whether a reference-implementation upgrade "
                            + "changed behaviour and the divergence doc is stale.");
                    }
                } else {
                    String sidecar = "Exception class: " + caught.getClass().getName() + "\n"
                                   + "Message: " + caught.getMessage() + "\n";
                    Path outPath = outDir.resolve(stem + ".error");
                    Files.createDirectories(outPath.getParent());
                    Files.writeString(outPath, sidecar);
                    System.out.println("  OK (.error sidecar): " + source + "/" + name);
                    errorCount++;
                }
            }
        }

        System.out.println();
        System.out.printf("Done. Success fixtures: %d, error fixtures: %d, companions: %d%n",
            jsonCount, errorCount, companionCount);
    }

    /**
     * Delete previously generated outputs so removed or reclassified fixtures
     * cannot leave a stale twin behind (e.g. both <name>-expected.json and
     * <name>.error after a classification flip on a reference-impl upgrade).
     */
    static void clearGeneratedOutputs(Path outDir) throws Exception {
        try (Stream<Path> s = Files.walk(outDir)) {
            for (Path p : s.collect(Collectors.toList())) {
                String n = p.getFileName().toString();
                // isRegularFile guard: source case DIRECTORIES may themselves be
                // named *.error (upstream convention) and mirror into outDir.
                if (Files.isRegularFile(p) && (n.endsWith("-expected.json") || n.endsWith(".error"))) {
                    Files.delete(p);
                }
            }
        }
    }

    static Set<String> loadCompanions(Path companionsFile) throws Exception {
        if (!Files.exists(companionsFile)) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        for (String line : Files.readAllLines(companionsFile)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                names.add(trimmed);
            }
        }
        return names;
    }
}

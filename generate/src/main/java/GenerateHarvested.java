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
            try (Stream<Path> s = Files.list(sourceDir)) {
                confs = s.filter(p -> p.getFileName().toString().endsWith(".conf"))
                         .sorted()
                         .collect(Collectors.toList());
            }

            Path outDir = expectedDir.resolve(source);
            Files.createDirectories(outDir);

            for (Path confPath : confs) {
                String name = confPath.getFileName().toString();
                if (companions.contains(name)) {
                    System.out.println("  companion (skipped): " + source + "/" + name);
                    companionCount++;
                    continue;
                }

                ConfigResolveOptions resolveOpts = ConfigResolveOptions.defaults()
                    .setUseSystemEnvironment(false);
                try {
                    Config config = ConfigFactory.parseFile(confPath.toFile()).resolve(resolveOpts);
                    String json = GenerateExpected.toSortedJson(config.root()) + "\n";
                    Path outPath = outDir.resolve(name.replace(".conf", "-expected.json"));
                    Files.writeString(outPath, json);
                    System.out.println("  OK: " + source + "/" + name);
                    jsonCount++;
                } catch (Exception e) {
                    String sidecar = "Exception class: " + e.getClass().getName() + "\n"
                                   + "Message: " + e.getMessage() + "\n";
                    Path outPath = outDir.resolve(name.replace(".conf", ".error"));
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

import com.typesafe.config.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/**
 * Entry point for subprocess-based expected JSON generation for env-var-list fixtures.
 *
 * Invoked by GenerateExpected when a .env sidecar exists alongside a .conf fixture.
 *
 * Because Lightbend typesafe-config 1.4.3 does not natively support the ${X[]} list-expansion
 * syntax, this writer pre-processes the .conf source by expanding ${X[]} / ${?X[]} patterns
 * using env vars from the .env sidecar before handing the resulting HOCON to Lightbend.
 *
 * Args:
 *   args[0] = absolute path to the .conf file
 *   args[1] = absolute path to the .env sidecar file
 *
 * Outputs resolved JSON to stdout, exits 0 on success, 1 on error (unresolved substitution, etc.).
 */
public class SubprocessExpectedWriter {

    static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: SubprocessExpectedWriter <conf-path> <env-path>");
            System.exit(1);
        }
        try {
            Path confPath = Path.of(args[0]);
            Path envPath = Path.of(args[1]);
            Map<String, String> env = loadEnvSidecar(envPath);

            String result = generateJson(confPath, env);
            System.out.print(result);
            System.exit(0);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Parses a .env sidecar file and returns a map of key=value pairs.
     * Blank lines and lines starting with '#' are skipped.
     */
    static Map<String, String> loadEnvSidecar(Path sidecarPath) throws IOException {
        Map<String, String> env = new LinkedHashMap<>();
        if (!Files.exists(sidecarPath)) {
            return env;
        }
        List<String> lines = Files.readAllLines(sidecarPath);
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eqIdx = line.indexOf('=');
            if (eqIdx < 0) {
                env.put(line, "");
                continue;
            }
            String key = line.substring(0, eqIdx);
            String value = line.substring(eqIdx + 1);
            env.put(key, value);
        }
        return env;
    }

    /**
     * Generates expected JSON for a .conf file, resolving ${X[]} / ${?X[]} list-expansion
     * substitutions using the provided env map.
     *
     * Strategy: pre-process all .conf files in the fixture's directory, writing expanded
     * versions to a temp directory. Lightbend loads from the temp directory so that
     * include directives resolve correctly. The expansion handles include-context correctly
     * because included files are also pre-processed using the same env map.
     */
    static String generateJson(Path confPath, Map<String, String> env) throws Exception {
        Path confDir = confPath.getParent();
        Path tmpDir = Files.createTempDirectory("hocon-ev-gen-");
        try {
            // Pre-process all .conf files in the fixture directory into tmpDir
            try (var stream = Files.list(confDir)) {
                stream.filter(p -> p.toString().endsWith(".conf")).forEach(p -> {
                    try {
                        String source = Files.readString(p);
                        String processed = expandListSubstitutions(source, env);
                        Files.writeString(tmpDir.resolve(p.getFileName()), processed);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            Path tmpConfPath = tmpDir.resolve(confPath.getFileName());
            ConfigParseOptions parseOpts = ConfigParseOptions.defaults()
                .setOriginDescription(confPath.getFileName().toString())
                .setSyntax(ConfigSyntax.CONF);
            Config config = ConfigFactory.parseFile(tmpConfPath.toFile(), parseOpts).resolve();
            ConfigObject root = config.root();
            return toSortedJson(root) + "\n";
        } finally {
            // Clean up temp files
            try (var stream = Files.list(tmpDir)) {
                stream.forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
            Files.deleteIfExists(tmpDir);
        }
    }

    /**
     * Expands ${NAME[]} and ${?NAME[]} patterns in HOCON source text.
     *
     * Rules (normative per spec S13c):
     * 1. If NAME is defined as a config key in the same source text, the [] suffix is
     *    a no-op — config wins. We strip the [] and let Lightbend resolve ${NAME} normally.
     * 2. If NAME is NOT defined in config, look up env vars NAME_0, NAME_1, ... stopping
     *    at the first absent key. Empty string IS a value (not "missing").
     * 3. Required (${NAME[]}) with no env elements → replace with ${NAME} so Lightbend
     *    throws an UnresolvedSubstitutionError.
     * 4. Optional (${?NAME[]}) with no env elements → replace with a guaranteed-absent
     *    optional substitution so Lightbend drops the key.
     */
    static String expandListSubstitutions(String source, Map<String, String> env) {
        // Match ${?NAME []} or ${NAME []} — whitespace before [] is optional.
        // Group 1: '?' or '' (optional marker)
        // Group 2: NAME (path, single-segment or dotted)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\$\\{(\\??)([A-Za-z_][A-Za-z0-9_.]*) *\\[\\]\\}"
        );
        java.util.regex.Matcher m = pattern.matcher(source);
        StringBuilder sb = new StringBuilder();

        while (m.find()) {
            boolean optional = !m.group(1).isEmpty();
            String name = m.group(2);

            // Check if name is defined as a config key in this source (config wins per S13c.5)
            if (isDefinedInConfig(source, name)) {
                // Strip [] only — let Lightbend resolve the config-defined value
                String replacement = optional ? "${?" + name + "}" : "${" + name + "}";
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
                continue;
            }

            // Env-var list expansion (spec normative pseudocode)
            List<String> values = expandEnvVarList(env, name);

            if (values.isEmpty()) {
                if (optional) {
                    // No env elements, optional → undefined → drop key
                    // Use an env var that cannot possibly be set
                    m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                        "${?__S13C_UNDEFINED_PLACEHOLDER__}"));
                } else {
                    // No env elements, required → unresolved error
                    // Replace with ${NAME} (which Lightbend will fail to resolve)
                    m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                        "${" + name + "}"));
                }
            } else {
                // Expand to HOCON literal array: ["a","b"]
                StringBuilder arr = new StringBuilder("[");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) arr.append(",");
                    arr.append("\"");
                    arr.append(escapeJson(values.get(i)));
                    arr.append("\"");
                }
                arr.append("]");
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(arr.toString()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Expands env-var list: looks up baseName_0, baseName_1, ... stopping at first absent key.
     * Empty string IS a valid value — uses containsKey(), not falsy/truthy check.
     */
    static List<String> expandEnvVarList(Map<String, String> env, String baseName) {
        List<String> values = new ArrayList<>();
        for (int i = 0; ; i++) {
            String key = baseName + "_" + i;
            if (!env.containsKey(key)) {
                break;
            }
            values.add(env.get(key));
        }
        return values;
    }

    /**
     * Checks if a key name is defined as a config assignment in the source text.
     * Simple heuristic: NAME followed by = or { at start of a line or after whitespace.
     */
    static boolean isDefinedInConfig(String source, String name) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "(?m)^\\s*" + java.util.regex.Pattern.quote(name) + "\\s*[={]"
        );
        return p.matcher(source).find();
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String toSortedJson(ConfigObject root) {
        JsonElement el = configValueToJson(root);
        return GSON.toJson(el);
    }

    static JsonElement configValueToJson(ConfigValue value) {
        switch (value.valueType()) {
            case OBJECT: {
                ConfigObject obj = (ConfigObject) value;
                JsonObject json = new JsonObject();
                TreeMap<String, ConfigValue> sorted = new TreeMap<>(obj);
                for (Map.Entry<String, ConfigValue> e : sorted.entrySet()) {
                    json.add(e.getKey(), configValueToJson(e.getValue()));
                }
                return json;
            }
            case LIST: {
                ConfigList list = (ConfigList) value;
                JsonArray arr = new JsonArray();
                for (ConfigValue v : list) {
                    arr.add(configValueToJson(v));
                }
                return arr;
            }
            case NUMBER: {
                Number n = (Number) value.unwrapped();
                if (n instanceof Double || n instanceof Float) {
                    return new JsonPrimitive(n.doubleValue());
                }
                return new JsonPrimitive(n.longValue());
            }
            case BOOLEAN:
                return new JsonPrimitive((Boolean) value.unwrapped());
            case NULL:
                return JsonNull.INSTANCE;
            case STRING:
                return new JsonPrimitive((String) value.unwrapped());
            default:
                throw new RuntimeException("Unknown type: " + value.valueType());
        }
    }
}

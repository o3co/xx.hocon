import com.typesafe.config.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/**
 * In-process expander for env-var-list (S13c) fixtures.
 *
 * Lightbend typesafe-config 1.4.3 does not natively support the ${X[]} list-expansion
 * syntax. This helper pre-processes .conf source by expanding ${X[]} / ${?X[]} patterns
 * using env vars from a .env sidecar before handing the result to Lightbend.
 *
 * Called by GenerateExpected via the static helpers (loadEnvSidecar, generateJson,
 * expandListSubstitutions) — there is no command-line entry point.
 *
 * Scope/limitations (documented for fixture authors):
 *   - Pre-expansion happens textually before HOCON parsing. The regex in
 *     {@link #expandListSubstitutions} does not know about HOCON lexical context,
 *     so `${X[]}` inside a quoted string (`"${X[]}"`) is still expanded — which
 *     deviates from spec (substitutions inside quotes are literals). No current
 *     fixture exercises this case; add a fixture before relying on quoted-string
 *     behavior.
 *   - {@link #isDefinedInConfig} uses a line-start regex; it does NOT detect
 *     keys defined via nested object syntax (`parent { child = ... }`), quoted
 *     keys, or path expressions (`a.b.c = ...`). Single-segment top-level
 *     assignments only.
 *   - {@link #generateJson} pre-expands every .conf in the fixture's directory
 *     so `include "sibling"` works. Cross-directory includes are NOT
 *     pre-expanded — keep multi-file env-var-list fixtures in one directory.
 */
public class EnvVarListExpander {

    static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    /**
     * Placeholder path used to substitute ${X[]} / ${?X[]} when the env var list is empty.
     *
     * The required-empty path emits ${NEVER_DEFINED_PLACEHOLDER} so Lightbend's resolver
     * fails with UnresolvedSubstitution (the failure is the assertion of "${X[]} requires
     * at least one element"). The optional-empty path emits ${?NEVER_DEFINED_PLACEHOLDER}
     * so Lightbend drops the key.
     *
     * The name is intentionally distinctive (double underscore + ALL_CAPS + "DO_NOT_SET")
     * to avoid collision with any user config key, system property, or environment var.
     * Combined with {@code setUseSystemEnvironment(false)} on the resolve options in
     * {@link #generateJson}, this is defense-in-depth against host-environment leakage.
     */
    static final String NEVER_DEFINED_PLACEHOLDER = "__hocon_gen_NEVER_DEFINED_DO_NOT_SET__";

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
     * Pre-processes every .conf in the same directory into a temp dir, then loads the
     * target from the temp dir so `include "sibling"` directives resolve to pre-expanded
     * versions. See class-level scope/limitations note for cross-directory caveat.
     */
    static String generateJson(Path confPath, Map<String, String> env) throws Exception {
        Path confDir = confPath.getParent();
        Path tmpDir = Files.createTempDirectory("hocon-ev-gen-");
        try {
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
            // Hermeticity: env-var-list fixtures depend ONLY on the .env sidecar.
            // Disable Lightbend's system-environment fallback so accidental host env vars
            // (e.g. someone exporting S13C_EV03_MY_LIST in their shell) cannot influence
            // generator output. The .env sidecar values are baked into the source via
            // expandListSubstitutions; nothing should fall back through to System.getenv.
            ConfigResolveOptions resolveOpts = ConfigResolveOptions.defaults()
                .setUseSystemEnvironment(false);
            Config config = ConfigFactory.parseFile(tmpConfPath.toFile(), parseOpts).resolve(resolveOpts);
            ConfigObject root = config.root();
            return toSortedJson(root) + "\n";
        } finally {
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
     * 3. Required (${NAME[]}) with no env elements → replace with hermetic
     *    ${NEVER_DEFINED_PLACEHOLDER} so Lightbend throws UnresolvedSubstitution
     *    (NOT ${NAME}, which would fall back to host env / system props).
     * 4. Optional (${?NAME[]}) with no env elements → replace with
     *    ${?NEVER_DEFINED_PLACEHOLDER} so Lightbend drops the key.
     *
     * Limitation: see class-level note about quoted-string contexts.
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

            List<String> values = expandEnvVarList(env, name);

            if (values.isEmpty()) {
                // Both required and optional empty paths use the same hermetic placeholder.
                // The `?` controls whether Lightbend throws (required) or drops the key (optional);
                // the path name is deliberately a never-defined sentinel so the resolution
                // outcome cannot leak through to System.getenv / system properties / config.
                // See NEVER_DEFINED_PLACEHOLDER doc.
                String marker = optional ? "${?" : "${";
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                    marker + NEVER_DEFINED_PLACEHOLDER + "}"));
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
     * Checks if a key name is defined as a top-level config assignment in the source text.
     * Line-start regex match for `NAME =` or `NAME {`.
     *
     * Known limitations (see class-level note): does not match keys inside nested
     * object blocks, quoted keys, or path-expression assignments. Sufficient for the
     * S13c.5 "config-defined wins" fixtures which all use top-level single-segment keys.
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

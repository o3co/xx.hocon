import com.typesafe.config.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public class GenerateExpected {

    // Conf files that should parse successfully and produce expected JSON
    static final String[] SUCCESS_CONFS = {
        "test01.conf",
        "test02.conf",
        "test04.conf",
        "test05.conf",
        "test06.conf",
        "test07.conf",
        "test08.conf",
        "test09.conf",
        "test10.conf",
        "test11.conf",
        "test12.conf",
        "test13-reference-with-substitutions.conf",
        "test13-application-override-substitutions.conf",
        "bom.conf",
        "file-include.conf",
        // "include-from-list.conf",  // typesafe-config limitation: include in list can't resolve ${}
        // "env-variables.conf",      // uses ${VAR[]} syntax not supported by typesafe-config
        "subst-tokenize/st01-unquoted-simple.conf",
        "subst-tokenize/st02-quoted-single-segment.conf",
        "subst-tokenize/st03-quoted-dot-in-key.conf",
        "subst-tokenize/st04-quoted-dots-separator.conf",
        "subst-tokenize/st05-unquoted-dot-chain.conf",
        "subst-tokenize/st06-mixed-quoted-unquoted.conf",
        "subst-tokenize/st07-quoted-ws-concat.conf",
        "subst-tokenize/st08-quoted-unquoted-concat.conf",
        "subst-tokenize/st09-empty-quoted-key.conf",
        "subst-tokenize/st10-escape-newline.conf",
        "subst-tokenize/st11-escape-tab.conf",
        "subst-tokenize/st12-escape-backslash.conf",
        "subst-tokenize/st13-escape-quote.conf",
        "subst-tokenize/st14-escape-unicode-bmp.conf",
        "subst-tokenize/st15-optional-subst.conf",
        "subst-tokenize/st16-optional-quoted.conf",
        "subst-tokenize/st17-optional-mixed-concat.conf",
        "subst-tokenize/st18-deep-nested-quoted.conf",
        "subst-tokenize/st19-quoted-escape-slash.conf",
        "subst-tokenize/st20-quoted-escape-backspace-formfeed.conf",
    };

    // Conf files that should produce a parse/resolve error
    static final String[] ERROR_CONFS = {
        "cycle.conf",
        "test13-reference-bad-substitutions.conf",
        "subst-tokenize/st-err01-invalid-escape-x.conf",
        "subst-tokenize/st-err02-invalid-escape-q.conf",
        "subst-tokenize/st-err03-invalid-unicode-short.conf",
        "subst-tokenize/st-err04-invalid-unicode-nonhex.conf",
        "subst-tokenize/st-err05-unterminated-subst.conf",
        "subst-tokenize/st-err06-unterminated-string.conf",
        "subst-tokenize/st-err07-newline-in-string.conf",
        "subst-tokenize/st-err08-empty-path.conf",
        "subst-tokenize/st-err09-empty-segment-leading-dot.conf",
        "subst-tokenize/st-err10-empty-segment-trailing-dot.conf",
        "subst-tokenize/st-err11-empty-segment-double-dot.conf",
    };

    static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public static void main(String[] args) throws Exception {
        Path testdataDir = Path.of("../testdata/hocon");
        Path expectedDir = Path.of("../expected/hocon");
        Files.createDirectories(expectedDir);

        System.out.println("Generating expected JSON from typesafe-config...");
        System.out.println("testdata dir: " + testdataDir.toAbsolutePath());
        System.out.println();

        int okCount = 0;
        int errCount = 0;
        int skipCount = 0;

        for (String confName : SUCCESS_CONFS) {
            Path confPath = testdataDir.resolve(confName);
            if (!Files.exists(confPath)) {
                System.err.println("SKIP (not found): " + confName);
                skipCount++;
                continue;
            }
            try {
                ConfigParseOptions parseOpts = ConfigParseOptions.defaults()
                    .setOriginDescription(confName);
                Config config = ConfigFactory.parseFile(confPath.toFile(), parseOpts)
                    .resolve();
                ConfigObject root = config.root();
                // Filter out environment-dependent keys that differ per machine
                if (confName.equals("test01.conf")) {
                    root = filterKeys(root, Set.of("system"));
                }
                String json = toSortedJson(root);
                String outName = confName.replace(".conf", "-expected.json");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, json + "\n");
                System.out.println("  OK: " + confName + " -> " + outName);
                okCount++;
            } catch (Exception e) {
                System.err.println("  ERROR (unexpected): " + confName + ": " + e.getMessage());
                errCount++;
            }
        }

        for (String confName : ERROR_CONFS) {
            Path confPath = testdataDir.resolve(confName);
            if (!Files.exists(confPath)) {
                System.err.println("SKIP (not found): " + confName);
                skipCount++;
                continue;
            }
            try {
                ConfigFactory.parseFile(confPath.toFile()).resolve();
                System.err.println("  UNEXPECTED SUCCESS: " + confName);
                errCount++;
            } catch (Exception e) {
                JsonObject errObj = new JsonObject();
                errObj.addProperty("error", true);
                errObj.addProperty("type", e.getClass().getSimpleName());
                errObj.addProperty("message", e.getMessage());
                String outName = confName.replace(".conf", "-expected-error.json");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, GSON.toJson(errObj) + "\n");
                System.out.println("  OK (error): " + confName + " -> " + outName);
                okCount++;
            }
        }

        System.out.println();
        System.out.printf("Done. OK: %d, Errors: %d, Skipped: %d%n", okCount, errCount, skipCount);
    }

    static ConfigObject filterKeys(ConfigObject obj, Set<String> exclude) {
        Map<String, ConfigValue> filtered = new HashMap<>(obj);
        for (String key : exclude) {
            filtered.remove(key);
        }
        return ConfigValueFactory.fromMap(filtered).toConfig().root();
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

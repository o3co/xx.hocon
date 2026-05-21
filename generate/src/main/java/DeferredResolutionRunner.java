import com.typesafe.config.*;
import com.google.gson.*;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/**
 * Java generator for E12 deferred-resolution scenario fixtures.
 *
 * Reads dr*.yaml files from testdata/hocon/deferred-resolution/, runs each scenario
 * through Lightbend com.typesafe.config, and emits expected-outcome files under
 * expected/hocon/deferred-resolution/.
 *
 * Output file conventions:
 *   <name>-expected.json   — success scenario: sorted JSON of resolved Config
 *   <name>-expected.txt    — success scenario: getter assertions + isResolved record
 *   <name>-expected.error  — error scenario: Category / At / Message
 *   <name>-expected.skip   — lightbendSkip: true — written with reason, runner skips
 *   <name>-expected.UNEXPECTED — outcome mismatch (build threw but expected success, or vice versa)
 *
 * Scenario YAML schema is defined in testdata/hocon/deferred-resolution/README.md.
 *
 * Error category mapping (cross-impl canonical categories):
 *   ParseError   ← ConfigException.Parse
 *   ResolveError ← ConfigException.UnresolvedSubstitution (no cycle in message)
 *   CycleError   ← ConfigException.UnresolvedSubstitution (message contains "cycle"/"circular")
 *   NotResolved  ← ConfigException.NotResolved
 *   TypeError    ← ConfigException.WrongType
 */
public class DeferredResolutionRunner {

    static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    // ---------------------------------------------------------------------------
    // Public entry points
    // ---------------------------------------------------------------------------

    /**
     * Iterates all dr*.yaml files under testdataHoconDir/deferred-resolution/,
     * runs each, and writes outputs to expectedHoconDir/deferred-resolution/.
     * Callers pass the hocon-group roots (e.g. "../testdata/hocon" and
     * "../expected/hocon"), consistent with the rest of GenerateExpected.
     */
    public static void runAll(Path testdataHoconDir, Path expectedHoconDir) throws IOException {
        Path scenarioDir = testdataHoconDir.resolve("deferred-resolution");
        Path outDir = expectedHoconDir.resolve("deferred-resolution");
        Files.createDirectories(outDir);

        System.out.println("=== Deferred resolution scenarios ===");

        int okCount = 0;
        int errCount = 0;
        int skipCount = 0;

        // Collect and sort so output is deterministic
        List<Path> yamlFiles = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(scenarioDir, "dr*.yaml")) {
            for (Path p : ds) {
                yamlFiles.add(p);
            }
        }
        yamlFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));

        for (Path yamlFile : yamlFiles) {
            String scenarioName = yamlFile.getFileName().toString().replace(".yaml", "");
            try {
                Result result = runScenario(yamlFile);
                writeOutputs(result, outDir, scenarioName);
                if (result.skipped) {
                    System.out.println("  SKIP: " + scenarioName + " (" + result.skipReason + ")");
                    skipCount++;
                } else if (result.unexpected) {
                    System.err.println("  UNEXPECTED: " + scenarioName + " — " + result.unexpectedMessage);
                    errCount++;
                } else {
                    System.out.println("  OK: " + scenarioName);
                    okCount++;
                }
            } catch (Exception e) {
                System.err.println("  Error: " + scenarioName + ": " + e.getMessage());
                errCount++;
            }
        }

        System.out.println();
        System.out.printf("Done. Scenarios OK: %d, Errors: %d, Skipped: %d%n", okCount, errCount, skipCount);
    }

    /**
     * Runs a single scenario YAML file and returns the Outcome.
     * Throws RuntimeException (loudly) on malformed YAML schema.
     */
    public static Result runScenario(Path yamlFile) throws IOException {
        String yamlContent = Files.readString(yamlFile);
        Yaml yaml = new Yaml();
        Map<String, Object> doc = yaml.load(yamlContent);
        if (doc == null) {
            throw new RuntimeException("Empty/null YAML document in " + yamlFile);
        }

        // --- lightbendSkip check ---
        Object lightbendSkip = doc.get("lightbendSkip");
        if (Boolean.TRUE.equals(lightbendSkip)) {
            String desc = getString(doc, "description", yamlFile);
            return Result.skipped("lightbendSkip=true; description: " + desc);
        }

        // --- Parse sources ---
        Object sourcesObj = doc.get("sources");
        if (!(sourcesObj instanceof Map)) {
            throw new RuntimeException("Missing or invalid 'sources' in " + yamlFile);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> sourcesMap = (Map<String, Object>) sourcesObj;

        // --- Parse build steps ---
        Object buildObj = doc.get("build");
        if (!(buildObj instanceof List)) {
            throw new RuntimeException("Missing or invalid 'build' in " + yamlFile);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> buildSteps = (List<Map<String, Object>>) buildObj;

        // --- Parse expect ---
        Object expectObj = doc.get("expect");
        if (!(expectObj instanceof Map)) {
            throw new RuntimeException("Missing or invalid 'expect' in " + yamlFile);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> expect = (Map<String, Object>) expectObj;

        String expectedOutcome = getString(expect, "outcome", yamlFile);

        // --- Materialise sources ---
        // artifact namespace: sources + built artifacts share one map
        Map<String, Object> artifacts = new LinkedHashMap<>();

        // --- Pre-declare error tracking so source materialisation errors flow
        //     through the same caughtException path as build-step errors. ---
        Integer errorAt = (Integer) expect.get("errorAt");
        Exception caughtException = null;
        int errorStepIndex = -1;
        Object lastArtifact = null;
        String resultName = null;

        // Materialise sources. A parseString that throws (e.g. dr13's type-incompatible
        // concat) surfaces here as an error at step "source:<id>" — we report it as
        // errorStepIndex = -1 (sentinel "during source materialisation").
        for (Map.Entry<String, Object> entry : sourcesMap.entrySet()) {
            String srcId = entry.getKey();
            Object srcVal = entry.getValue();
            if (!(srcVal instanceof Map)) {
                throw new RuntimeException("Source '" + srcId + "' must be a map in " + yamlFile);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> srcDef = (Map<String, Object>) srcVal;

            try {
                Config cfg = materialiseSource(srcId, srcDef, yamlFile);
                artifacts.put(srcId, cfg);
            } catch (ConfigException e) {
                caughtException = e;
                errorStepIndex = -1;
                break;
            }
        }

        // --- Execute build steps ---
        for (int i = 0; caughtException == null && i < buildSteps.size(); i++) {
            Map<String, Object> step = buildSteps.get(i);
            String op = getString(step, "op", yamlFile);
            String as = (String) step.get("as");

            try {
                Object artifact = executeStep(op, step, artifacts, yamlFile);
                if (as != null) {
                    artifacts.put(as, artifact);
                    if ("result".equals(as)) {
                        resultName = "result";
                    }
                }
                lastArtifact = artifact;
            } catch (Exception e) {
                caughtException = e;
                errorStepIndex = i;
                break;
            }
        }

        // --- Determine result artifact ---
        Object resultArtifact = null;
        if (caughtException == null) {
            if (resultName != null) {
                resultArtifact = artifacts.get("result");
            } else {
                resultArtifact = lastArtifact;
            }
        }

        // --- Validate against expect ---
        if ("success".equals(expectedOutcome)) {
            if (caughtException != null) {
                // Build threw but expected success
                String msg = "Expected success but step " + errorStepIndex + " threw: "
                           + caughtException.getClass().getSimpleName() + ": " + firstLine(caughtException.getMessage());
                return Result.unexpected(msg);
            }
            // Build succeeded
            if (!(resultArtifact instanceof Config)) {
                throw new RuntimeException("Result artifact is not a Config in " + yamlFile);
            }
            Config resultConfig = (Config) resultArtifact;
            return buildSuccessResult(resultConfig, expect, yamlFile);

        } else if ("error".equals(expectedOutcome)) {
            if (caughtException == null) {
                // Build succeeded but expected error
                String msg = "Expected error but build succeeded";
                return Result.unexpected(msg);
            }
            // Build threw as expected
            return buildErrorResult(caughtException, errorStepIndex, expect, yamlFile);

        } else {
            throw new RuntimeException("Unknown outcome '" + expectedOutcome + "' in " + yamlFile);
        }
    }

    // ---------------------------------------------------------------------------
    // Source materialisation
    // ---------------------------------------------------------------------------

    private static Config materialiseSource(String srcId, Map<String, Object> srcDef, Path yamlFile) {
        if (srcDef.containsKey("parseString")) {
            String text = (String) srcDef.get("parseString");
            if (text == null) {
                throw new RuntimeException("Source '" + srcId + "' has null parseString in " + yamlFile);
            }

            // Build ConfigParseOptions
            ConfigParseOptions parseOpts = ConfigParseOptions.defaults()
                .setOriginDescription(srcId);

            boolean doResolve = false;
            Object parseOptionsObj = srcDef.get("parseOptions");
            if (parseOptionsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parseOptions = (Map<String, Object>) parseOptionsObj;
                parseOpts = applyParseOptions(parseOpts, parseOptions);
                Object resolveSubst = parseOptions.get("resolveSubstitutions");
                if (Boolean.TRUE.equals(resolveSubst)) {
                    doResolve = true;
                }
                // resolveSubstitutions: false (or absent) → do NOT resolve (default behaviour)
            }

            Config cfg = ConfigFactory.parseString(text, parseOpts);
            if (doResolve) {
                cfg = cfg.resolve();
            }
            return cfg;

        } else if (srcDef.containsKey("fromMap")) {
            Object fromMapObj = srcDef.get("fromMap");
            Map<?, ?> rawMap = (fromMapObj instanceof Map) ? (Map<?, ?>) fromMapObj : Collections.emptyMap();
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) rawMap;
            return ((ConfigObject) ConfigValueFactory.fromAnyRef(typedMap)).toConfig();

        } else {
            throw new RuntimeException("Source '" + srcId + "' must have 'parseString' or 'fromMap' in " + yamlFile);
        }
    }

    // ---------------------------------------------------------------------------
    // Step execution
    // ---------------------------------------------------------------------------

    private static Object executeStep(String op, Map<String, Object> step, Map<String, Object> artifacts, Path yamlFile) {
        switch (op) {
            case "take": {
                String source = getString(step, "source", yamlFile);
                Object artifact = artifacts.get(source);
                if (artifact == null) {
                    throw new RuntimeException("'take' step references unknown source/artifact '" + source + "' in " + yamlFile);
                }
                return artifact;
            }

            case "extract": {
                String thisName = getString(step, "this", yamlFile);
                String path = getString(step, "path", yamlFile);
                Object artifact = artifacts.get(thisName);
                if (!(artifact instanceof Config)) {
                    throw new RuntimeException("'extract' step: artifact '" + thisName + "' is not a Config in " + yamlFile);
                }
                return ((Config) artifact).getConfig(path);
            }

            case "withFallback": {
                String thisName = getString(step, "this", yamlFile);
                String otherName = getString(step, "other", yamlFile);
                Object thisArtifact = artifacts.get(thisName);
                Object otherArtifact = artifacts.get(otherName);
                if (!(thisArtifact instanceof Config)) {
                    throw new RuntimeException("'withFallback' step: artifact '" + thisName + "' is not a Config in " + yamlFile);
                }
                if (!(otherArtifact instanceof ConfigMergeable)) {
                    throw new RuntimeException("'withFallback' step: artifact '" + otherName + "' is not a ConfigMergeable in " + yamlFile);
                }
                return ((Config) thisArtifact).withFallback((ConfigMergeable) otherArtifact);
            }

            case "resolve": {
                String thisName = getString(step, "this", yamlFile);
                Object artifact = artifacts.get(thisName);
                if (!(artifact instanceof Config)) {
                    throw new RuntimeException("'resolve' step: artifact '" + thisName + "' is not a Config in " + yamlFile);
                }
                ConfigResolveOptions opts = buildResolveOptions(
                    (Boolean) step.get("allowUnresolved"),
                    (Boolean) step.get("useSystemEnvironment")
                );
                return ((Config) artifact).resolve(opts);
            }

            case "resolveWith": {
                String thisName = getString(step, "this", yamlFile);
                String sourceName = getString(step, "source", yamlFile);
                Object thisArtifact = artifacts.get(thisName);
                Object sourceArtifact = artifacts.get(sourceName);
                if (!(thisArtifact instanceof Config)) {
                    throw new RuntimeException("'resolveWith' step: artifact '" + thisName + "' is not a Config in " + yamlFile);
                }
                if (!(sourceArtifact instanceof Config)) {
                    throw new RuntimeException("'resolveWith' step: source artifact '" + sourceName + "' is not a Config in " + yamlFile);
                }
                ConfigResolveOptions opts = buildResolveOptions(
                    (Boolean) step.get("allowUnresolved"),
                    (Boolean) step.get("useSystemEnvironment")
                );
                return ((Config) thisArtifact).resolveWith((Config) sourceArtifact, opts);
            }

            default:
                throw new RuntimeException("Unknown op '" + op + "' in " + yamlFile);
        }
    }

    // ---------------------------------------------------------------------------
    // Result construction
    // ---------------------------------------------------------------------------

    private static Result buildSuccessResult(Config resultConfig, Map<String, Object> expect, Path yamlFile) {
        // Render JSON via Lightbend's ConfigRenderOptions
        String renderedJson = resultConfig.root().render(
            ConfigRenderOptions.concise().setJson(true).setFormatted(true)
        );
        // If the config is resolved, re-parse via Gson and pretty-print with sorted keys to
        // match existing GenerateExpected style. If unresolved (AllowUnresolved=true), Lightbend's
        // render emits non-JSON tokens for substitution placeholders (e.g. `${b}` literal), so
        // we keep Lightbend's raw output and rely on per-impl tests to validate.
        String sortedJson;
        if (resultConfig.isResolved()) {
            JsonElement parsed = JsonParser.parseString(renderedJson);
            sortedJson = GSON.toJson(sortJsonElement(parsed)) + "\n";
        } else {
            sortedJson = renderedJson.endsWith("\n") ? renderedJson : renderedJson + "\n";
        }

        // isResolved check
        Boolean expectIsResolved = (Boolean) expect.get("isResolved");
        boolean actualIsResolved = resultConfig.isResolved();

        // Warn if expected JSON mismatches Lightbend's output (but still write Lightbend truth)
        String jsonWarn = null;
        Object expectJson = expect.get("json");
        if (expectJson instanceof String) {
            String normalised = normaliseJson((String) expectJson);
            String actualNormalised = normaliseJson(sortedJson);
            if (!normalised.equals(actualNormalised)) {
                jsonWarn = "JSON mismatch: scenario expected:\n" + expectJson + "\nLightbend produced:\n" + sortedJson;
            }
        }

        // Getter assertions
        List<GetterRecord> getterRecords = new ArrayList<>();
        Object getterObj = expect.get("getter");
        if (getterObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> getterList = (List<Map<String, Object>>) getterObj;
            for (Map<String, Object> g : getterList) {
                getterRecords.add(runGetter(resultConfig, g, yamlFile));
            }
        }

        // Build txt content
        StringBuilder txt = new StringBuilder();
        txt.append("isResolved: ").append(actualIsResolved).append("\n");
        if (!getterRecords.isEmpty()) {
            txt.append("getter:\n");
            for (GetterRecord gr : getterRecords) {
                txt.append("  ").append(gr.path).append(": ").append(gr.result).append("\n");
            }
        }
        if (expectIsResolved != null && expectIsResolved != actualIsResolved) {
            txt.append("WARN: expected isResolved=").append(expectIsResolved)
               .append(" but Lightbend returned ").append(actualIsResolved).append("\n");
        }
        if (jsonWarn != null) {
            txt.append("WARN: ").append(jsonWarn).append("\n");
        }

        return Result.success(sortedJson, txt.toString());
    }

    private static Result buildErrorResult(Exception ex, int errorStepIndex,
                                           Map<String, Object> expect, Path yamlFile) {
        String category = mapExceptionToCategory(ex);
        String expectedCategory = (String) expect.get("errorCategory");

        String categoryWarn = null;
        if (expectedCategory != null && !expectedCategory.equals(category)) {
            categoryWarn = "Category mismatch: expected " + expectedCategory + ", Lightbend threw " + category
                         + " (" + ex.getClass().getSimpleName() + ")";
        }

        // errorContains check (warn only)
        String containsWarn = null;
        Object errorContains = expect.get("errorContains");
        if (errorContains instanceof String) {
            String sub = (String) errorContains;
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (!msg.contains(sub)) {
                containsWarn = "errorContains '" + sub + "' not found in: " + firstLine(msg);
            }
        }

        StringBuilder errorContent = new StringBuilder();
        errorContent.append("Category: ").append(category).append("\n");
        errorContent.append("At: ").append(errorStepIndex).append("\n");
        errorContent.append("Message: ").append(firstLine(ex.getMessage())).append("\n");
        if (categoryWarn != null) {
            errorContent.append("WARN: ").append(categoryWarn).append("\n");
        }
        if (containsWarn != null) {
            errorContent.append("WARN: ").append(containsWarn).append("\n");
        }

        return Result.error(errorContent.toString());
    }

    // ---------------------------------------------------------------------------
    // Getter runner
    // ---------------------------------------------------------------------------

    private static GetterRecord runGetter(Config cfg, Map<String, Object> g, Path yamlFile) {
        String path = getString(g, "path", yamlFile);
        String result;

        if (g.containsKey("expectInt")) {
            try {
                int val = cfg.getInt(path);
                result = String.valueOf(val);
            } catch (ConfigException e) {
                result = "ERROR: " + mapExceptionToCategory(e);
            }
        } else if (g.containsKey("expectString")) {
            try {
                String val = cfg.getString(path);
                result = val;
            } catch (ConfigException e) {
                result = "ERROR: " + mapExceptionToCategory(e);
            }
        } else if (g.containsKey("expectArray")) {
            try {
                ConfigList list = cfg.getList(path);
                JsonArray arr = new JsonArray();
                for (ConfigValue v : list) {
                    arr.add(configValueToJson(v));
                }
                result = GSON.toJson(arr);
            } catch (ConfigException e) {
                result = "ERROR: " + mapExceptionToCategory(e);
            }
        } else if (g.containsKey("expectObject")) {
            try {
                Config sub = cfg.getConfig(path);
                result = GSON.toJson(sortJsonElement(configValueToJson(sub.root())));
            } catch (ConfigException e) {
                result = "ERROR: " + mapExceptionToCategory(e);
            }
        } else if (g.containsKey("expectError")) {
            // Caller expects a specific error category — verify isResolved would throw
            String expectedErrCat = (String) g.get("expectError");
            try {
                // Try getString first — the most common probe for unresolved path
                cfg.getString(path);
                // If that didn't throw, try getAnyRef
                cfg.getAnyRef(path);
                result = "UNEXPECTED_SUCCESS (expected " + expectedErrCat + ")";
            } catch (ConfigException e) {
                String actual = mapExceptionToCategory(e);
                if (actual.equals(expectedErrCat)) {
                    result = "ERROR: " + actual;
                } else {
                    result = "ERROR: " + actual + " (expected " + expectedErrCat + ")";
                }
            }
        } else {
            result = "UNKNOWN_ASSERTION (no expectInt/expectString/expectArray/expectObject/expectError)";
        }

        return new GetterRecord(path, result);
    }

    // ---------------------------------------------------------------------------
    // Output file writing
    // ---------------------------------------------------------------------------

    private static void writeOutputs(Result result, Path outDir, String scenarioName) throws IOException {
        if (result.skipped) {
            Path skipPath = outDir.resolve(scenarioName + "-expected.skip");
            Files.writeString(skipPath, result.skipReason + "\n");
            return;
        }
        if (result.unexpected) {
            Path unexpectedPath = outDir.resolve(scenarioName + "-expected.UNEXPECTED");
            // DO NOT overwrite existing expected files — only write if absent
            if (!Files.exists(unexpectedPath)) {
                Files.writeString(unexpectedPath, result.unexpectedMessage + "\n");
            }
            return;
        }
        if (result.isSuccess) {
            Path jsonPath = outDir.resolve(scenarioName + "-expected.json");
            Files.writeString(jsonPath, result.json);
            Path txtPath = outDir.resolve(scenarioName + "-expected.txt");
            Files.writeString(txtPath, result.txt);
        } else {
            Path errorPath = outDir.resolve(scenarioName + "-expected.error");
            Files.writeString(errorPath, result.errorContent);
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    static ConfigResolveOptions buildResolveOptions(Boolean allowUnresolved, Boolean useSystemEnvironment) {
        ConfigResolveOptions opts = ConfigResolveOptions.defaults();
        if (allowUnresolved != null) {
            opts = opts.setAllowUnresolved(allowUnresolved);
        }
        if (useSystemEnvironment != null) {
            opts = opts.setUseSystemEnvironment(useSystemEnvironment);
        }
        return opts;
    }

    static ConfigParseOptions applyParseOptions(ConfigParseOptions base, Map<String, Object> parseOptions) {
        Object originDesc = parseOptions.get("originDescription");
        if (originDesc instanceof String) {
            base = base.setOriginDescription((String) originDesc);
        }
        // resolveSubstitutions is NOT a Lightbend ConfigParseOptions field — handled at call site
        return base;
    }

    static String mapExceptionToCategory(Exception ex) {
        // Order matters: ConfigException.UnresolvedSubstitution extends ConfigException.Parse,
        // and ConfigException.NotResolved may also extend Parse in some Lightbend versions.
        // Check the most-specific types first.
        if (ex instanceof ConfigException.UnresolvedSubstitution) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("cycle") || msg.contains("circular")) {
                return "CycleError";
            }
            return "ResolveError";
        } else if (ex instanceof ConfigException.NotResolved) {
            return "NotResolved";
        } else if (ex instanceof ConfigException.WrongType) {
            return "TypeError";
        } else if (ex instanceof ConfigException.Parse) {
            return "ParseError";
        } else {
            // Fallback: check message for cycle hint even on other exception types
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("cycle") || msg.contains("circular")) {
                return "CycleError";
            }
            return "ResolveError";
        }
    }

    /** Returns the first non-empty line of a (possibly multiline) exception message. */
    static String firstLine(String msg) {
        if (msg == null) return "(null)";
        for (String line : msg.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        return msg;
    }

    /** Normalise JSON for comparison: re-parse + re-render with Gson. */
    static String normaliseJson(String json) {
        try {
            return GSON.toJson(JsonParser.parseString(json.trim()));
        } catch (Exception e) {
            return json.trim();
        }
    }

    /** Sort JSON object keys recursively (match GenerateExpected style). */
    static JsonElement sortJsonElement(JsonElement el) {
        if (el instanceof JsonObject) {
            JsonObject obj = (JsonObject) el;
            JsonObject sorted = new JsonObject();
            new TreeMap<>(obj.asMap()).forEach((k, v) -> sorted.add(k, sortJsonElement(v)));
            return sorted;
        } else if (el instanceof JsonArray) {
            JsonArray arr = (JsonArray) el;
            JsonArray out = new JsonArray();
            for (JsonElement item : arr) {
                out.add(sortJsonElement(item));
            }
            return out;
        }
        return el;
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
                throw new RuntimeException("Unknown ConfigValue type: " + value.valueType());
        }
    }

    private static String getString(Map<String, Object> map, String key, Path yamlFile) {
        Object val = map.get(key);
        if (!(val instanceof String)) {
            throw new RuntimeException("Expected string for field '" + key + "' in " + yamlFile
                                       + ", got: " + val);
        }
        return (String) val;
    }

    // ---------------------------------------------------------------------------
    // Result record
    // ---------------------------------------------------------------------------

    public static class Result {
        // success path
        public final boolean isSuccess;
        public final String json;
        public final String txt;
        // error path
        public final String errorContent;
        // skip path
        public final boolean skipped;
        public final String skipReason;
        // unexpected path
        public final boolean unexpected;
        public final String unexpectedMessage;

        private Result(boolean isSuccess, String json, String txt,
                       String errorContent, boolean skipped, String skipReason,
                       boolean unexpected, String unexpectedMessage) {
            this.isSuccess = isSuccess;
            this.json = json;
            this.txt = txt;
            this.errorContent = errorContent;
            this.skipped = skipped;
            this.skipReason = skipReason;
            this.unexpected = unexpected;
            this.unexpectedMessage = unexpectedMessage;
        }

        static Result success(String json, String txt) {
            return new Result(true, json, txt, null, false, null, false, null);
        }

        static Result error(String errorContent) {
            return new Result(false, null, null, errorContent, false, null, false, null);
        }

        static Result skipped(String reason) {
            return new Result(false, null, null, null, true, reason, false, null);
        }

        static Result unexpected(String message) {
            return new Result(false, null, null, null, false, null, true, message);
        }
    }

    private static class GetterRecord {
        final String path;
        final String result;

        GetterRecord(String path, String result) {
            this.path = path;
            this.result = result;
        }
    }
}

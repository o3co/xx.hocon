import com.typesafe.config.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public class GenerateExpected {

    // Conf files that should parse successfully and produce expected JSON.
    // Fixtures with a .env sidecar are processed via EnvVarListExpander
    // (which expands ${X[]} patterns using the sidecar env vars before Lightbend parses).
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
        // "env-variables.conf",      // uses ${VAR[]} syntax not supported by typesafe-config 1.4.3
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
        "numeric-obj-array/na01-basic.conf",
        "numeric-obj-array/na02-lazy-getobject.conf",
        "numeric-obj-array/na03a-concat-left-list.conf",
        "numeric-obj-array/na03b-concat-right-list.conf",
        "numeric-obj-array/na03c-concat-two-objs.conf",
        "numeric-obj-array/na03d-concat-multi-piece.conf",
        "numeric-obj-array/na03e-multi-piece-overlap.conf",
        "numeric-obj-array/na04-empty.conf",
        "numeric-obj-array/na05-non-int-keys.conf",
        "numeric-obj-array/na06-gaps.conf",
        "numeric-obj-array/na07-sort.conf",
        "numeric-obj-array/na08-leading-zero.conf",
        "numeric-obj-array/na09-negative.conf",
        "numeric-obj-array/na10a-plus-sign.conf",
        "numeric-obj-array/na10b-minus-zero.conf",
        "numeric-obj-array/na11-overflow.conf",
        "numeric-obj-array/na12-no-eligible.conf",
        // concat-errors success fixtures (regression guards: ce09 S15 bridge, ce15 optional-omission)
        "concat-errors/ce09-numeric-obj-still-works.conf",
        "concat-errors/ce15-optional-missing-suppresses-pair.conf",
        // env-var-list fixtures (S13c): processed via EnvVarListExpander with .env sidecar
        // because typesafe-config 1.4.3 does not natively support ${X[]} syntax.
        "env-var-list/ev01-basic.conf",
        "env-var-list/ev02-stops-at-gap.conf",
        // ev03-required-no-elements.conf is in ENV_VAR_LIST_ERROR_CONFS (expected resolve error)
        "env-var-list/ev04-optional-no-elements.conf",
        "env-var-list/ev05-config-defined-wins.conf",
        "env-var-list/ev06-concat-prepend.conf",
        "env-var-list/ev07-concat-append.conf",
        "env-var-list/ev08-self-append.conf",
        "env-var-list/ev09-whitespace-before-suffix.conf",
        "env-var-list/ev10-empty-string-element.conf",
        "env-var-list/ev11-include-context.conf",
        // S8.6 unquoted-string-starts strict-spec fixtures (cluster 3c). The strict spec
        // says: leading 0-9 must trigger number-lex; leading `-` must trigger number-lex
        // with required digit (lex error if absent). Per-impl conformance asserts the
        // token stream. Lightbend-quirk fixtures EXCLUDED here:
        //   - us02 (`-foo`), us03 (`-`): Lightbend silently accepts as unquoted; strict
        //     spec errors. See E8.
        //   - us13 (`01`): Lightbend parses as number 1 (Long.parseLong drops leading
        //     zero); strict spec emits number(0) + unquoted("1") → string "01". See E8.
        // SIDECAR_ERROR_CONFS handles us15 (`1e+x`) since Lightbend errors on the
        // reserved `+` character (strict-spec lex also errors but for a different reason).
        "unquoted-starts/us01-digit-prefix-with-tail.conf",
        "unquoted-starts/us04-hyphen-with-digit.conf",
        "unquoted-starts/us05-number-then-comment.conf",
        "unquoted-starts/us06-embedded-digits.conf",
        "unquoted-starts/us07-embedded-hyphen.conf",
        "unquoted-starts/us08-numeric-key-positive.conf",
        "unquoted-starts/us09-dotted-number-key.conf",
        "unquoted-starts/us10-greedy-backtrack-exp.conf",
        "unquoted-starts/us11-greedy-backtrack-frac.conf",
        "unquoted-starts/us12-hex-prefix.conf",
        "unquoted-starts/us14-multi-dot-version.conf",
        "unquoted-starts/us16-negative-with-tail.conf",
        // S12.5 include-reservation positive fixtures (cluster 3e). Negative fixtures
        // distributed between SIDECAR_ERROR_CONFS (Lightbend throws) and Lightbend-quirk
        // exclusions (Lightbend silently accepts dotted `include.foo`). See E9 in
        // docs/extra-spec-conventions.md and "Lightbend quirks" in docs/fixture-conventions.md.
        "include-reservation/ir05-include-statement.conf",
        "include-reservation/ir06-quoted-include.conf",
        "include-reservation/ir07-include-non-initial.conf",
        "include-reservation/ir08-include-as-value.conf",
        "include-reservation/ir09-include-file-form.conf",
        "include-reservation/ir11-quoted-include-dotted.conf",
        "include-reservation/ir14-substitution-include-path.conf",
    };

    // Conf files expected to produce a parse/resolve error and have a plain-text
    // .error sidecar written to expected/hocon/<group>/<name>.error.
    // Format: "Exception class: <fqcn>\nMessage: <message>"
    // Used by cross-impl conformance tests to assert "any HoconError raised"
    // (no exact message matching required). Shared convention for clusters 3b, 3c, 3e, 3f
    // and any future cluster using the .error sidecar pattern (see docs/fixture-conventions.md).
    static final String[] SIDECAR_ERROR_CONFS = {
        "concat-errors/ce01-array-plus-object.conf",
        "concat-errors/ce02-object-plus-array.conf",
        "concat-errors/ce03-array-plus-scalar.conf",
        "concat-errors/ce04-scalar-plus-array.conf",
        // ce05-object-plus-scalar.conf: EXCLUDED — Lightbend 1.4.3 silently accepts
        // "a = { b: 1 } x" (object wins, trailing scalar discarded). Spec S10.13 says
        // this should error, but Lightbend is permissive here. Fixture file is kept on
        // disk for documentation; conformance tests for ce05 must use a Lightbend-quirk
        // annotation rather than a .error sidecar. See docs/fixture-conventions.md §Lightbend
        // quirks.
        "concat-errors/ce06-scalar-plus-object.conf",
        "concat-errors/ce07-subst-obj-plus-array.conf",
        "concat-errors/ce08-subst-array-plus-obj.conf",
        "concat-errors/ce10-empty-array-plus-object.conf",
        "concat-errors/ce11-array-plus-empty-object.conf",
        "concat-errors/ce12-string-concat-resolved-array.conf",
        "concat-errors/ce13-string-concat-resolved-object.conf",
        "concat-errors/ce14-optional-missing-mid-concat.conf",
        // S8.6 us15: Lightbend errors on reserved `+` outside quotes in the unquoted tail.
        // Strict-spec impl would also lex-error (number(1) backtrack from `e+`, then `+` as
        // reserved char). Both produce "some error" — conformance test asserts error raised.
        "unquoted-starts/us15-incomplete-exp.conf",
        // S12.5 include-reservation negative fixtures (cluster 3e). Lightbend throws on
        // ir01/ir02/ir10/ir12/ir13 via the include-statement parser (text after `include`
        // is not a valid include argument). Lightbend EXCLUDES (quirks):
        //   - ir03 `include.foo = 1`: tokenizer joins into single unquoted, PathParser
        //     splits later → silently accepted as key path
        //   - ir04 `a = { include.bar = 1 }`: same mechanism inside nested object
        // Both are documented under §Lightbend quirks in docs/fixture-conventions.md and
        // E9 in docs/extra-spec-conventions.md. Per-impl tests load ir03/ir04 directly.
        "include-reservation/ir01-include-equals.conf",
        "include-reservation/ir02-include-colon.conf",
        "include-reservation/ir10-include-plus-equals.conf",
        "include-reservation/ir12-include-newline-arg.conf",
        "include-reservation/ir13-include-object-body.conf",
    };

    // Conf files that should produce a parse/resolve error (traditional JSON error record format)
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

    // env-var-list error fixtures: these use ${X[]} syntax so require sidecar-based
    // expansion before we can determine if they error. The generator pre-processes the
    // source text to expand ${X[]} patterns; if the expanded form still has an unresolvable
    // substitution (no env vars set for a required subst), Lightbend throws.
    static final String[] ENV_VAR_LIST_ERROR_CONFS = {
        // ev03: required ${X[]} with no env elements → UnresolvedSubstitution error
        "env-var-list/ev03-required-no-elements.conf",
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

            // Check for .env sidecar — use EnvVarListExpander if present
            Path sidecarPath = testdataDir.resolve(confName.replace(".conf", ".env"));
            boolean hasSidecar = Files.exists(sidecarPath);

            try {
                String json;
                if (hasSidecar) {
                    Map<String, String> env = EnvVarListExpander.loadEnvSidecar(sidecarPath);
                    json = EnvVarListExpander.generateJson(confPath, env);
                } else {
                    ConfigParseOptions parseOpts = ConfigParseOptions.defaults()
                        .setOriginDescription(confName);
                    Config config = ConfigFactory.parseFile(confPath.toFile(), parseOpts).resolve();
                    ConfigObject root = config.root();
                    // Filter out environment-dependent keys that differ per machine
                    if (confName.equals("test01.conf")) {
                        root = filterKeys(root, Set.of("system"));
                    }
                    json = toSortedJson(root) + "\n";
                }

                String outName = confName.replace(".conf", "-expected.json");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, json);
                System.out.println("  OK" + (hasSidecar ? " (sidecar)" : "") + ": " + confName + " -> " + outName);
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

        // ENV_VAR_LIST_ERROR_CONFS: error fixtures that use ${X[]} syntax. Pre-process via
        // EnvVarListExpander.expandListSubstitutions so the expanded form can be evaluated
        // by Lightbend; expect Lightbend to throw. Emits a plain-text .error sidecar (same
        // format as SIDECAR_ERROR_CONFS) and applies the same UNEXPECTED-SUCCESS safety net.
        // setUseSystemEnvironment(false) ensures the expanded fixture cannot leak through
        // to host env / system properties — same hermeticity guarantee as EnvVarListExpander.
        for (String confName : ENV_VAR_LIST_ERROR_CONFS) {
            Path confPath = testdataDir.resolve(confName);
            if (!Files.exists(confPath)) {
                System.err.println("SKIP (not found): " + confName);
                skipCount++;
                continue;
            }
            Path sidecarPath = testdataDir.resolve(confName.replace(".conf", ".env"));
            Map<String, String> env;
            if (Files.exists(sidecarPath)) {
                env = EnvVarListExpander.loadEnvSidecar(sidecarPath);
            } else {
                // The safety net only catches "did NOT throw"; it cannot detect "threw the
                // wrong error". Surface missing-sidecar so a fixture author who forgot the
                // .env file gets a clear breadcrumb instead of a confusing vacuous error.
                System.err.println("  WARN: no .env sidecar for " + confName + " — proceeding with empty env");
                env = new LinkedHashMap<>();
            }
            String source = Files.readString(confPath);
            String processed = EnvVarListExpander.expandListSubstitutions(source, env);
            // Origin appears in Lightbend's exception message and lands in the .error sidecar.
            // We go through parseString (because the source is pre-expanded), so we set the
            // origin manually to the short conf path. This differs from SIDECAR_ERROR_CONFS
            // which uses parseFile and gets `../testdata/hocon/<group>/<name>.conf` as origin.
            // Per fixture-conventions.md §Semantics, conformance tests MUST NOT match on
            // message content, so the format difference is purely cosmetic.
            ConfigParseOptions parseOpts = ConfigParseOptions.defaults()
                .setOriginDescription(confName)
                .setSyntax(ConfigSyntax.CONF);
            ConfigResolveOptions resolveOpts = ConfigResolveOptions.defaults()
                .setUseSystemEnvironment(false);
            Exception caught = null;
            try {
                ConfigFactory.parseString(processed, parseOpts).resolve(resolveOpts);
            } catch (Exception e) {
                caught = e;
            }
            if (caught == null) {
                throw new RuntimeException(
                    "Expected error fixture " + confName + " did NOT throw — verify the .env sidecar "
                    + "(missing keys?) or move to SUCCESS_CONFS per docs/fixture-conventions.md.");
            }
            {
                Exception e = caught;
                String sidecar = "Exception class: " + e.getClass().getName() + "\n"
                               + "Message: " + e.getMessage() + "\n";
                String outName = confName.replace(".conf", ".error");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, sidecar);
                System.out.println("  OK (.error sidecar): " + confName + " -> " + outName);
                okCount++;
            }
        }

        // SIDECAR_ERROR_CONFS: fixtures expected to fail; write plain-text .error sidecars.
        // Sidecar format:
        //   Exception class: <fully-qualified class name>
        //   Message: <exception message verbatim>
        // Existence of the sidecar signals "expected to fail" to cross-impl test runners.
        for (String confName : SIDECAR_ERROR_CONFS) {
            Path confPath = testdataDir.resolve(confName);
            if (!Files.exists(confPath)) {
                System.err.println("SKIP (not found): " + confName);
                skipCount++;
                continue;
            }
            Exception caught = null;
            try {
                ConfigFactory.parseFile(confPath.toFile()).resolve();
            } catch (Exception e) {
                caught = e;
            }
            if (caught == null) {
                // FAIL the build: an entry in SIDECAR_ERROR_CONFS must throw under Lightbend.
                // If Lightbend silently accepts (e.g. ce05-style Lightbend quirk), the fixture
                // MUST be moved out of SIDECAR_ERROR_CONFS and documented under §Lightbend
                // quirks in docs/fixture-conventions.md. This safety net catches both regressions
                // (Lightbend used to throw, now doesn't) and typos (fixture content doesn't
                // actually trigger the expected error).
                throw new RuntimeException(
                    "Expected error fixture " + confName + " did NOT throw — verify the fixture input "
                    + "or move it to a Lightbend-quirk section per docs/fixture-conventions.md.");
            }
            {
                Exception e = caught;
                String sidecar = "Exception class: " + e.getClass().getName() + "\n"
                               + "Message: " + e.getMessage() + "\n";
                String outName = confName.replace(".conf", ".error");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, sidecar);
                System.out.println("  OK (.error sidecar): " + confName + " -> " + outName);
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

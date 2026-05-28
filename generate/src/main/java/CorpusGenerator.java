import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seed corpus generator for the cross-impl differential harness.
 *
 * Emits a tree of HOCON cases under {@code differential/corpus/<case-id>/}, each
 * a directory whose entry point is {@code main.conf} (plus any include children).
 * The cases are hand-seeded around the axes where go/rs/ts have historically
 * diverged from the Lightbend reference implementation (the cgordon
 * "Lightbend Parity" issue family go.hocon#128, #131-#135 and neighbours):
 *
 *   A. deferred substitution resolution across includes   (go.hocon#135)
 *   B. {@code +=} accumulation across includes             (go.hocon#134)
 *   C. optional {@code ${?ENV}} fallback preservation      (go.hocon#128)
 *   D. null preservation through merge / reference         (go.hocon#131)
 *   E. literal whitespace in value concatenation           (go.hocon#132)
 *   F. numeric source lexeme in string concatenation       (go.hocon#133)
 *   G. self-referential append + mixed neighbours
 *
 * Optional substitutions reference guaranteed-unset, namespaced env names
 * ({@code GH_DIFF_UNSET_*}) so every engine resolves them as undefined. The
 * Lightbend oracle and the go/rs/ts adapters all resolve env substitutions
 * against the ambient process environment (no injection), so Inc1 hermeticity
 * rests on those names being absent on the host, not on any env clearing.
 * Controlled per-fixture env injection is deferred to Inc2.
 */
public final class CorpusGenerator {

    public static void main(String[] args) throws IOException {
        Path corpusDir = Path.of(args.length > 0 ? args[0] : "differential/corpus");
        int n = generate(corpusDir);
        System.out.println("Generated " + n + " differential corpus cases under " + corpusDir);
    }

    /** Writes all seed cases under {@code corpusDir}. Returns the case count. */
    public static int generate(Path corpusDir) throws IOException {
        if (Files.exists(corpusDir)) {
            // Wipe stale cases so removed/renamed fixtures don't linger.
            try (var walk = Files.walk(corpusDir)) {
                walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
        Files.createDirectories(corpusDir);
        int n = 0;

        // ---- Group A: deferred substitution resolution across includes (#135) ----
        n += emit(corpusDir, "a1-deferred-include-override-after", Map.of(
            "main.conf", "include \"common.conf\"\ninclude \"override.conf\"\nvalue = ${computed}\n",
            "common.conf", "base = 11\ncomputed = ${base}\n",
            "override.conf", "base = 17\n"));
        n += emit(corpusDir, "a2-deferred-include-override-before", Map.of(
            "main.conf", "include \"override.conf\"\ninclude \"common.conf\"\nvalue = ${computed}\n",
            "common.conf", "base = 11\ncomputed = ${base}\n",
            "override.conf", "base = 17\n"));
        n += emit(corpusDir, "a3-deferred-include-no-override", Map.of(
            "main.conf", "include \"common.conf\"\nvalue = ${computed}\n",
            "common.conf", "base = 11\ncomputed = ${base}\n"));

        // ---- Group B: += accumulation across includes (#134) ----
        n += emit(corpusDir, "b1-append-across-includes", Map.of(
            "main.conf", "include \"first.conf\"\ninclude \"second.conf\"\nitems += \"main\"\n",
            "first.conf", "items += \"first\"\n",
            "second.conf", "items += \"second\"\n"));
        n += emit(corpusDir, "b2-append-with-base-then-includes", Map.of(
            "main.conf", "items = [base]\ninclude \"first.conf\"\nitems += \"main\"\n",
            "first.conf", "items += \"first\"\n"));
        n += emit(corpusDir, "b3-append-include-order-reversed", Map.of(
            "main.conf", "include \"second.conf\"\ninclude \"first.conf\"\nitems += \"main\"\n",
            "first.conf", "items += \"first\"\n",
            "second.conf", "items += \"second\"\n"));

        // ---- Group C: optional ${?ENV} fallback preservation (#128) ----
        n += emit(corpusDir, "c1-optional-env-unset-inline", Map.of(
            "main.conf", "registry {\n  instance-id = \"localhost\"\n  instance-id = ${?GH_DIFF_UNSET_C1}\n}\n"));
        n += emit(corpusDir, "c2-optional-env-unset-via-include", Map.of(
            "main.conf", "include \"child.conf\"\n",
            "child.conf", "registry {\n  instance-id = \"localhost\"\n  instance-id = ${?GH_DIFF_UNSET_C2}\n}\n"));

        // ---- Group D: null preservation through merge / reference (#131) ----
        n += emit(corpusDir, "d1-null-reference", Map.of(
            "main.conf", "variables {\n  value = null\n}\nenvironment {\n  value = ${variables.value}\n}\n"));
        n += emit(corpusDir, "d2-null-via-include", Map.of(
            "main.conf", "include \"defaults.conf\"\nactive = ${value}\n",
            "defaults.conf", "value = null\n"));

        // ---- Group E: literal whitespace in value concatenation (#132) ----
        n += emit(corpusDir, "e1-ws-around-optional", Map.of(
            "main.conf", "value = \"left\"  ${?GH_DIFF_UNSET_E1}  \"right\"\n"));
        n += emit(corpusDir, "e2-ws-between-quoted", Map.of(
            "main.conf", "value = \"a\"   \"b\"\n"));
        n += emit(corpusDir, "e3-ws-unquoted-words", Map.of(
            "main.conf", "value = foo   bar\n"));

        // ---- Group F: numeric source lexeme in string concatenation (#133) ----
        n += emit(corpusDir, "f1-numeric-lexeme-version", Map.of(
            "main.conf", "major = 26\nminor = 05\nversion = ${major}.${minor}\n"));
        n += emit(corpusDir, "f2-leading-zero-unquoted", Map.of(
            "main.conf", "name = 00_example\n"));
        n += emit(corpusDir, "f3-float-lexeme-concat", Map.of(
            "main.conf", "x = 1.0\nlabel = ${x}px\n"));

        // ---- Group G: self-referential append + mixed neighbours ----
        n += emit(corpusDir, "g1-self-ref-append", Map.of(
            "main.conf", "a = [1]\na = ${a} [2]\n"));
        n += emit(corpusDir, "g2-subst-into-string-ws", Map.of(
            "main.conf", "host = example.com\nurl = \"http://\"${host}\"/path\"\n"));

        return n;
    }

    private static int emit(Path corpusDir, String caseId, Map<String, String> files) throws IOException {
        Path dir = corpusDir.resolve(caseId);
        Files.createDirectories(dir);
        // Deterministic order is irrelevant for file writes; LinkedHashMap keeps
        // the literal map iteration stable for readability of generated trees.
        for (Map.Entry<String, String> e : new LinkedHashMap<>(files).entrySet()) {
            Files.writeString(dir.resolve(e.getKey()), e.getValue());
        }
        return 1;
    }

    private CorpusGenerator() {}
}

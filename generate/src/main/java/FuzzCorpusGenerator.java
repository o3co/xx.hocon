import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Seeded, grammar-based HOCON generator for the Inc2 fuzz differential runner.
 *
 * Produces structurally-varied, mostly-valid HOCON documents biased toward the
 * constructs where the impls have historically diverged from Lightbend:
 * includes, {@code +=} accumulation, {@code ${x}} / {@code ${?x}} substitutions,
 * value concatenation (literal whitespace), leading-zero / float numeric
 * lexemes, duplicate-key overrides, nested objects, and quoted keys.
 *
 * A document is a list of top-level statements plus include child files, which
 * makes it cheaply shrinkable (the runner removes whole statements and prunes
 * orphaned includes). Generation is fully determined by the supplied
 * {@link Random}, so a divergence is reproducible from its seed + index.
 */
public final class FuzzCorpusGenerator {

    static final String[] KEYS = {"a", "b", "c", "foo", "bar", "host", "items", "cfg", "x", "n0"};

    static FuzzDoc generate(Random rng) {
        List<String> statements = new ArrayList<>();
        Map<String, String> includes = new LinkedHashMap<>();
        List<String> defined = new ArrayList<>();
        int n = 2 + rng.nextInt(7); // 2..8 statements
        int includeCount = 0;
        for (int i = 0; i < n; i++) {
            int kind = rng.nextInt(100);
            if (kind < 18 && includeCount < 3) {
                String fname = "child" + includeCount + ".conf";
                includes.put(fname, genChild(rng, defined));
                statements.add("include \"" + fname + "\"");
                includeCount++;
            } else if (kind < 34) {
                String k = pickKey(rng);
                statements.add(k + " += " + genScalar(rng, defined));
                defined.add(k);
            } else if (kind < 50) {
                String k = pickKey(rng); // duplicate-key override
                statements.add(k + " = " + genValue(rng, defined, 0));
                statements.add(k + " = " + genValue(rng, defined, 0));
                defined.add(k);
            } else if (kind < 64) {
                String k = pickKey(rng); // nested object
                statements.add(k + " {\n" + indent(genAssignments(rng, defined, 1 + rng.nextInt(2))) + "\n}");
                defined.add(k);
            } else {
                String k = pickKey(rng);
                statements.add(k + " = " + genValue(rng, defined, 0));
                defined.add(k);
            }
        }
        return new FuzzDoc(statements, includes);
    }

    // An include child: a couple of assignments, sometimes a += to a shared key
    // (to exercise cross-include accumulation) and a substitution to a prior key.
    static String genChild(Random rng, List<String> defined) {
        StringBuilder sb = new StringBuilder();
        int lines = 1 + rng.nextInt(3);
        for (int i = 0; i < lines; i++) {
            if (rng.nextInt(3) == 0) {
                sb.append("items += ").append(genScalar(rng, defined)).append('\n');
            } else {
                String k = pickKey(rng);
                sb.append(k).append(" = ").append(genScalar(rng, defined)).append('\n');
                defined.add(k);
            }
        }
        return sb.toString();
    }

    static String genAssignments(Random rng, List<String> defined, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append('\n');
            String k = pickKey(rng);
            sb.append(k).append(" = ").append(genValue(rng, defined, 1));
            defined.add(k);
        }
        return sb.toString();
    }

    static String genValue(Random rng, List<String> defined, int depth) {
        int r = rng.nextInt(100);
        if (depth < 2 && r < 12) { // array
            int len = rng.nextInt(3);
            List<String> elems = new ArrayList<>();
            for (int i = 0; i < len; i++) elems.add(genScalar(rng, defined));
            return "[" + String.join(", ", elems) + "]";
        }
        if (depth < 2 && r < 22) { // inline object
            return "{ " + genAssignments(rng, defined, 1).replace("\n", ", ") + " }";
        }
        if (r < 40) { // concatenation with literal whitespace
            String ws = " ".repeat(1 + rng.nextInt(3));
            return genScalar(rng, defined) + ws + genScalar(rng, defined);
        }
        return genScalar(rng, defined);
    }

    static String genScalar(Random rng, List<String> defined) {
        int r = rng.nextInt(100);
        if (r < 14 && !defined.isEmpty()) {
            return "${" + defined.get(rng.nextInt(defined.size())) + "}";
        }
        if (r < 24) { // optional substitution, often undefined
            String k = rng.nextInt(2) == 0 && !defined.isEmpty()
                ? defined.get(rng.nextInt(defined.size()))
                : "GH_FUZZ_UNSET_" + rng.nextInt(1000);
            return "${?" + k + "}";
        }
        if (r < 40) return Integer.toString(rng.nextInt(1000));         // plain int
        if (r < 50) return "0" + rng.nextInt(100);                      // leading-zero int (#133/E2)
        if (r < 58) return rng.nextInt(100) + "." + rng.nextInt(100);   // float
        if (r < 64) return "0" + rng.nextInt(10) + "." + rng.nextInt(100); // leading-zero float
        if (r < 70) return Boolean.toString(rng.nextBoolean());
        if (r < 76) return "null";
        if (r < 88) return "\"" + randWord(rng) + (rng.nextInt(2) == 0 ? "  " + randWord(rng) : "") + "\""; // quoted (maybe inner ws)
        return "0" + rng.nextInt(10) + "_" + randWord(rng);             // unquoted leading-zero token (#133)
    }

    static String pickKey(Random rng) {
        int r = rng.nextInt(100);
        if (r < 70) return KEYS[rng.nextInt(KEYS.length)];
        if (r < 82) return KEYS[rng.nextInt(KEYS.length)] + "-" + KEYS[rng.nextInt(KEYS.length)]; // hyphenated
        if (r < 92) return "\"" + KEYS[rng.nextInt(KEYS.length)] + "." + KEYS[rng.nextInt(KEYS.length)] + "\""; // quoted dotted
        return KEYS[rng.nextInt(KEYS.length)] + "." + KEYS[rng.nextInt(KEYS.length)]; // path key
    }

    static String randWord(Random rng) {
        String[] w = {"alpha", "beta", "x", "localhost", "v", "00", "left", "right", "example"};
        return w[rng.nextInt(w.length)];
    }

    static String indent(String block) {
        StringBuilder sb = new StringBuilder();
        for (String line : block.split("\n", -1)) sb.append("  ").append(line).append('\n');
        return sb.toString().stripTrailing();
    }

    private FuzzCorpusGenerator() {}
}

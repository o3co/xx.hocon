import com.typesafe.config.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProbeIssue31 {

    static Map<String, String> cases() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        // Group A: substitution -> unquoted-like start
        m.put("A1 subst-dash-text",   "a = foo\nb = ${a}-bar");
        m.put("A2 subst-dash-only",   "a = foo\nb = ${a}-");
        m.put("A3 subst-double-dash", "a = foo\nb = ${a}--bar");
        m.put("A4 subst-dash-digit",  "a = foo\nb = ${a}-1");
        m.put("A5 subst-digit-text",  "a = foo\nb = ${a}1bar");
        m.put("A6 subst-dot-text",    "a = foo\nb = ${a}.bar");
        m.put("A7 subst-underscore",  "a = foo\nb = ${a}_bar");
        m.put("A8 subst-plus-text",   "a = foo\nb = ${a}+bar");
        // Group B: quoted -> unquoted-like start
        m.put("B1 quoted-dash-text",  "b = \"foo\"-bar");
        m.put("B2 quoted-dot-text",   "b = \"foo\".bar");
        m.put("B3 quoted-digit-text", "b = \"foo\"1bar");
        // Group C: unquoted only (baseline)
        m.put("C1 unquoted-dash",     "b = foo-bar");
        m.put("C2 unquoted-ws-dash",  "b = foo -bar");
        // Group D: subst x subst boundary
        m.put("D1 subst-dash-subst-self", "a = foo\nc = ${a}-${a}");
        m.put("D2 subst-dash-subst-other", "a = foo\nb = bar\nc = ${a}-${b}");
        // Group E: prefix + subst
        m.put("E1 unquoted-dash-subst", "a = foo\nb = foo-${a}");
        m.put("E2 quoted-dash-subst",   "a = foo\nb = \"foo\"-${a}");
        // Group F: value-start position (E8 territory, baseline check)
        m.put("F1 dash-text-start",   "a = -foo");
        m.put("F2 dash-only",         "a = -");
        m.put("F3 leading-zero",      "a = 01");
        m.put("F4 dot-five",          "a = .5");
        m.put("F5 plus-text-start",   "a = +foo");
        return m;
    }

    public static void main(String[] args) {
        ConfigRenderOptions opts = ConfigRenderOptions.concise().setJson(true);
        for (Map.Entry<String, String> e : cases().entrySet()) {
            String id = e.getKey();
            String src = e.getValue();
            String oneLine = src.replace("\n", " \\n ");
            try {
                Config c = ConfigFactory.parseString(src).resolve();
                String rendered = c.root().render(opts);
                System.out.println("[OK]  " + id);
                System.out.println("      src: " + oneLine);
                System.out.println("      out: " + rendered);
            } catch (ConfigException ex) {
                System.out.println("[ERR] " + id);
                System.out.println("      src: " + oneLine);
                System.out.println("      err: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            System.out.println();
        }
    }
}

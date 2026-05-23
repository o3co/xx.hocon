import com.typesafe.config.*;

public class ProbeKeyHyphenAndPathWS {
  public static void main(String[] args) {
    String[] inputs = {
      // S8.6 in key position
      "foo -bar = 1",
      "a.b -bar = 1",
      "\"foo\" -bar = 1",
      "foo bar.-baz = 1",
      "-foo bar = 1",
      "foo -1bar = 1",
      "foo - = 1",
      // Path-expression whitespace (space + tab)
      "a b. c = 1",
      "a . b = 1",
      "a .b = 1",
      "a b.c d = 1",
      "a b . c = 1",
      "a b.\tc = 1",
      "a\tb.c d = 1",
      // Combined / edge cases
      "a b.-c = 1",
      "foo.-bar = 1",
      ".foo = 1",
      "foo. = 1",
      "a b. = 1",
    };
    for (String s : inputs) {
      System.out.print(repr(s) + "  ->  ");
      try {
        Config c = ConfigFactory.parseString(s);
        System.out.println("OK  " + c.root().render(ConfigRenderOptions.concise()));
      } catch (ConfigException e) {
        System.out.println("ERR " + e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }
  }
  private static String repr(String s) {
    return "[" + s.replace("\n", "\\n") + "]";
  }
}

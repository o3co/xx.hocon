import com.typesafe.config.*;
public class ProbeS13_9 {
  public static void main(String[] args) {
    // Case A: HOME = null in config, optional substitution
    String s1 = "HOME = null\nresult = ${?HOME}";
    Config c1 = ConfigFactory.parseString(s1).resolve();
    System.out.println("=== A: HOME=null, ${?HOME} ===");
    System.out.println("hasPath('result'): " + c1.hasPath("result"));
    System.out.println("entrySet: " + c1.entrySet());
    System.out.println("rendered: " + c1.root().render(ConfigRenderOptions.concise()));

    // Case B: HOME = null in config, required substitution
    String s2 = "HOME = null\nresult = ${HOME}";
    Config c2 = ConfigFactory.parseString(s2).resolve();
    System.out.println("\n=== B: HOME=null, ${HOME} ===");
    System.out.println("hasPath('result'): " + c2.hasPath("result"));
    System.out.println("entrySet: " + c2.entrySet());
    System.out.println("rendered: " + c2.root().render(ConfigRenderOptions.concise()));
  }
}

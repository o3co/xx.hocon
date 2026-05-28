import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A generated HOCON document for the fuzz differential runner: a list of
 * top-level statements (each may itself be multi-line, e.g. a nested object)
 * plus include child files keyed by filename. Statement-level granularity makes
 * the document cheaply shrinkable by {@link FuzzRunner}.
 */
public final class FuzzDoc {
    final List<String> statements;
    final Map<String, String> includes; // filename -> content

    FuzzDoc(List<String> statements, Map<String, String> includes) {
        this.statements = statements;
        this.includes = includes;
    }

    String render() {
        return String.join("\n", statements) + "\n";
    }

    void writeTo(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("main.conf"), render());
        for (Map.Entry<String, String> e : includes.entrySet()) {
            Files.writeString(dir.resolve(e.getKey()), e.getValue());
        }
    }
}

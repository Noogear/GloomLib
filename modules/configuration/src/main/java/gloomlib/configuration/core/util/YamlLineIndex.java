package gloomlib.configuration.core.util;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses a YAML string with SnakeYAML to extract the 1-based start line of each key path.
 *
 * <p>Bukkit's {@code YamlConfiguration} discards SnakeYAML {@code Mark} (position) data
 * during parsing, so there is no built-in API for line numbers.
 * This utility calls {@code Yaml.compose()} on the same content string that
 * {@code YamlConfiguration.loadFromString()} already uses, avoiding a second file read.
 *
 * <p>All failures are silently ignored — callers fall back to path-only location.
 */
public final class YamlLineIndex {

    private YamlLineIndex() {}

    /**
     * Builds a {@code dotpath → 1-based line} index from a pre-read YAML string.
     * Prefer this over {@link #build(File)} when the file content is already in memory.
     *
     * @param content YAML file content as a string
     * @return path-to-line map, never null
     */
    public static Map<String, Integer> buildFromString(String content) {
        Map<String, Integer> index = new HashMap<>();
        try {
            Node root = new Yaml().compose(new StringReader(content));
            if (root instanceof MappingNode mn) {
                walk(mn, "", index);
            }
        } catch (Exception ignored) {}
        return index;
    }

    /**
     * Convenience overload that reads the file and delegates to {@link #buildFromString}.
     * Use {@link #buildFromString} directly when the content is already available.
     *
     * @param file the YAML file to index
     * @return path-to-line map, never null
     */
    public static Map<String, Integer> build(File file) {
        try {
            return buildFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static void walk(MappingNode node, String prefix, Map<String, Integer> index) {
        for (NodeTuple tuple : node.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) {
                continue;
            }
            String key = keyNode.getValue();
            String dotPath = prefix.isEmpty() ? key : prefix + "." + key;
            int line = getLine(keyNode);
            if (line > 0) {
                index.put(dotPath, line);
            }
            if (tuple.getValueNode() instanceof MappingNode sub) {
                walk(sub, dotPath, index);
            }
        }
    }

    /**
     * Extracts the 1-based line number from a SnakeYAML node.
     * Handles both SnakeYAML 1.x ({@code Mark}) and 2.x ({@code Optional<Mark>}).
     */
    private static int getLine(ScalarNode node) {
        try {
            Object mark = node.getStartMark(); // Mark or Optional<Mark>
            if (mark == null) return 0;

            // SnakeYAML 2.x: Optional<Mark>
            if (mark instanceof java.util.Optional<?> opt) {
                return opt.map(m -> {
                    try {
                        return ((org.yaml.snakeyaml.error.Mark) m).getLine() + 1;
                    } catch (Exception e) {
                        return 0;
                    }
                }).orElse(0);
            }

            // SnakeYAML 1.x: Mark directly
            return ((org.yaml.snakeyaml.error.Mark) mark).getLine() + 1;
        } catch (Exception e) {
            return 0;
        }
    }
}

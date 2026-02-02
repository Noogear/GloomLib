package gloomlib.translation.loader;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Parsers for translation file formats.
 */
public final class TranslationParsers {

    private static final ThreadLocal<Yaml> YAML = ThreadLocal.withInitial(Yaml::new);

    private TranslationParsers() {
    }

    /**
     * Parses a YAML file.
     *
     * @param path    the file path
     * @param charset the encoding
     * @return flattened key-value map
     * @throws IOException if reading fails
     */
    public static @NotNull Map<String, String> parseYaml(@NotNull Path path, @NotNull Charset charset) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> data = YAML.get().load(in);
            if (data == null) {
                return Collections.emptyMap();
            }
            Map<String, String> result = new LinkedHashMap<>();
            flatten(new StringBuilder(64), data, result);
            return result;
        }
    }

    /**
     * Parses YAML content string.
     *
     * @param content the YAML content
     * @return flattened key-value map
     */
    public static @NotNull Map<String, String> parseYamlContent(@NotNull String content) {
        Map<String, Object> data = YAML.get().load(content);
        if (data == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        flatten(new StringBuilder(64), data, result);
        return result;
    }

    /**
     * Parses a properties file.
     *
     * @param path    the file path
     * @param charset the encoding
     * @return key-value map
     * @throws IOException if reading fails
     */
    public static @NotNull Map<String, String> parseProperties(@NotNull Path path, @NotNull Charset charset) throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path, charset)) {
            props.load(reader);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            result.put(key, props.getProperty(key));
        }
        return result;
    }

    /**
     * Parses properties content string.
     *
     * @param content the properties content
     * @return key-value map
     */
    public static @NotNull Map<String, String> parsePropertiesContent(@NotNull String content) {
        Properties props = new Properties();
        try {
            props.load(new java.io.StringReader(content));
        } catch (IOException ignored) {
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            result.put(key, props.getProperty(key));
        }
        return result;
    }

    /**
     * Parses a translation file by extension.
     *
     * @param path    the file path
     * @param charset the encoding
     * @return key-value map
     * @throws IOException if reading fails
     */
    public static @NotNull Map<String, String> parse(@NotNull Path path, @NotNull Charset charset) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            return parseYaml(path, charset);
        } else if (name.endsWith(".properties")) {
            return parseProperties(path, charset);
        }
        throw new IllegalArgumentException("Unsupported format: " + name);
    }

    /**
     * Parses a translation file with UTF-8.
     *
     * @param path the file path
     * @return key-value map
     * @throws IOException if reading fails
     */
    public static @NotNull Map<String, String> parse(@NotNull Path path) throws IOException {
        return parse(path, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static void flatten(StringBuilder prefix, Map<String, Object> map, Map<String, String> target) {
        int baseLen = prefix.length();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (baseLen > 0) {
                prefix.append('.');
            }
            prefix.append(entry.getKey());

            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(prefix, (Map<String, Object>) value, target);
            } else if (value != null) {
                target.put(prefix.toString(), value.toString());
            }

            prefix.setLength(baseLen);
        }
    }
}

package gloomlib.translation.source;

import gloomlib.translation.api.TranslationSource;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * YAML file-based translation source.
 */
public final class YamlTranslationSource implements TranslationSource {

    private static final ThreadLocal<Yaml> YAML_INSTANCE = ThreadLocal.withInitial(Yaml::new);

    private final Path path;
    private final Locale locale;
    private final Map<String, String> rawValues = new HashMap<>(64);
    private Set<String> keysView;

    /**
     * Creates new YAML translation source.
     *
     * @param path the YAML file path
     * @param locale the target locale
     */
    public YamlTranslationSource(Path path, Locale locale) {
        this.path = path;
        this.locale = locale;
    }

    @Override
    public void load() throws Exception {
        rawValues.clear();
        keysView = null;

        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> data = YAML_INSTANCE.get().load(in);
            if (data != null) {
                flatten(new StringBuilder(64), data, rawValues);
            }
        }
        keysView = Collections.unmodifiableSet(rawValues.keySet());
    }

    @SuppressWarnings("unchecked")
    private void flatten(StringBuilder prefix, Map<String, Object> map, Map<String, String> target) {
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

    @Override
    public @NotNull Locale getLocale() {
        return locale;
    }

    @Override
    public @NotNull Set<String> getKeys() {
        return keysView != null ? keysView : Collections.emptySet();
    }

    @Override
    public @NotNull String getRaw(@NotNull String key) {
        return rawValues.getOrDefault(key, key);
    }

    @Override
    public @NotNull Map<String, MessageFormat> getTranslations() {
        return Collections.emptyMap();
    }
}

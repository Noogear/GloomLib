package gloomlib.translation.source;

import gloomlib.translation.api.TranslationSource;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Properties file-based translation source.
 */
public final class PropertiesTranslationSource implements TranslationSource {

    private final Path path;
    private final Locale locale;
    private final Map<String, String> rawValues = new HashMap<>(64);
    private Set<String> keysView;

    /**
     * Creates new properties translation source.
     *
     * @param path the properties file path
     * @param locale the target locale
     */
    public PropertiesTranslationSource(Path path, Locale locale) {
        this.path = path;
        this.locale = locale;
    }

    @Override
    public void load() throws Exception {
        rawValues.clear();
        keysView = null;

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        for (String key : properties.stringPropertyNames()) {
            rawValues.put(key, properties.getProperty(key));
        }
        keysView = Collections.unmodifiableSet(rawValues.keySet());
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

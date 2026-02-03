package gloomlib.translation.impl;

import gloomlib.translation.api.TranslationSource;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Map-based translation source implementation.
 */
public final class MapTranslationSource implements TranslationSource {

    private final Locale locale;
    private final Map<String, String> translations;
    private final Set<String> keys;

    /**
     * Creates new map translation source.
     *
     * @param locale       the target locale
     * @param translations the translation map
     */
    public MapTranslationSource(@NotNull Locale locale, @NotNull Map<String, String> translations) {
        this.locale = locale;
        this.translations = translations;
        this.keys = Collections.unmodifiableSet(translations.keySet());
    }

    @Override
    public void load() {
    }

    @Override
    public @NotNull Locale getLocale() {
        return locale;
    }

    @Override
    public @NotNull Set<String> getKeys() {
        return keys;
    }

    @Override
    public @NotNull String getRaw(@NotNull String key) {
        return translations.getOrDefault(key, key);
    }

    @Override
    public @NotNull Map<String, MessageFormat> getTranslations() {
        return Collections.emptyMap();
    }
}

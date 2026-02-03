package gloomlib.translation.api;

import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Source of translations for a specific locale from files or other sources.
 *
 * @since 1.0.0
 */
public interface TranslationSource {

    /**
     * Loads the translation data from the source.
     *
     * @throws Exception if the data cannot be loaded
     */
    void load() throws Exception;

    /**
     * Gets the locale this source provides translations for.
     *
     * @return the locale
     */
    @NotNull
    Locale getLocale();

    /**
     * Gets all translation keys available in this source.
     *
     * @return the set of translation keys
     */
    @NotNull
    Set<String> getKeys();

    /**
     * Gets the raw translation string for a key.
     *
     * @param key the translation key
     * @return the raw translation string
     */
    @NotNull
    String getRaw(@NotNull String key);

    /**
     * Gets all translations as MessageFormat instances.
     *
     * @return map of translation keys to MessageFormat objects
     */
    @NotNull
    Map<String, MessageFormat> getTranslations();
}

package gloomlib.translation.api;

import gloomlib.translation.config.FileSourceOptions;
import gloomlib.translation.impl.TranslationManagerImpl;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Manages translation loading, caching, and retrieval for MiniMessage-based translations.
 *
 * <p>Supports YAML and properties file formats with automatic file watching and migration.</p>
 *
 * @since 1.0.0
 */
public interface TranslationManager {

    /**
     * Gets the singleton instance of the translation manager.
     *
     * @return the instance, or null if not initialized
     */
    static @Nullable TranslationManager instance() {
        return TranslationManagerImpl.getInstance();
    }

    /**
     * Creates a new translation manager.
     *
     * @param registryKey   the unique key for the translation registry
     * @param dataFolder    the folder containing translation files
     * @param defaultLocale the default locale for fallback
     * @return a new translation manager instance
     */
    static @NotNull TranslationManager create(
            @NotNull Key registryKey,
            @NotNull Path dataFolder,
            @NotNull Locale defaultLocale) {
        return new TranslationManagerImpl(registryKey, dataFolder, defaultLocale);
    }

    /**
     * Parses a locale string into a {@link Locale} object.
     *
     * @param locale the locale string (e.g., "en_US", "zh-CN")
     * @return the parsed locale, or null if invalid
     */
    static @Nullable Locale parseLocale(@Nullable String locale) {
        return locale == null || locale.isEmpty()
                ? null
                : Translator.parseLocale(locale);
    }

    /**
     * Formats a locale to a string representation.
     *
     * @param locale the locale
     * @return the formatted string (e.g., "en_us")
     */
    static @NotNull String formatLocale(@NotNull Locale locale) {
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry().toLowerCase(Locale.ROOT);
        if (country.isEmpty()) {
            return language;
        } else {
            return language + "_" + country;
        }
    }

    /**
     * Loads translation files for the specified language codes.
     *
     * @param languageCodes the language codes to load (e.g., "en_US", "zh_CN")
     */
    void load(@NotNull List<String> languageCodes);

    /**
     * Loads translation files with custom options.
     *
     * @param languageCodes the language codes to load
     * @param options       the file source options
     */
    void load(@NotNull List<String> languageCodes, @NotNull FileSourceOptions options);

    /**
     * Reloads all currently loaded translations.
     */
    void reload();

    /**
     * Closes and cleans up the translation manager.
     */
    void close();

    /**
     * Gets the raw MiniMessage translation string for the default locale.
     *
     * @param key the translation key
     * @return the raw translation string, or null if not found
     */
    @Nullable
    String getRawTranslation(@NotNull String key);

    /**
     * Gets the raw MiniMessage translation string for a specific locale.
     *
     * @param key    the translation key
     * @param locale the locale
     * @return the raw translation string, or null if not found
     */
    @Nullable
    String getRawTranslation(@NotNull String key, @NotNull Locale locale);

    /**
     * Translates a key using the default locale.
     *
     * @param key the translation key
     * @return the translated component, or the key as text if not found
     */
    @NotNull
    Component translate(@NotNull String key);

    /**
     * Translates a key using the default locale with tag resolvers.
     *
     * @param key  the translation key
     * @param tags the tag resolvers for placeholders
     * @return the translated component
     */
    @NotNull
    Component translate(@NotNull String key, @NotNull TagResolver... tags);

    /**
     * Translates a key using a specific locale with tag resolvers.
     *
     * @param key    the translation key
     * @param locale the locale to use
     * @param tags   the tag resolvers for placeholders
     * @return the translated component
     */
    @NotNull
    Component translate(@NotNull String key, @NotNull Locale locale, @NotNull TagResolver... tags);

    /**
     * Renders a component, translating any {@link net.kyori.adventure.text.TranslatableComponent}s.
     *
     * @param component the component to render
     * @return the rendered component using the default locale
     */
    @NotNull
    Component render(@NotNull Component component);

    /**
     * Renders a component with a specific locale.
     *
     * @param component the component to render
     * @param locale    the locale to use for translation
     * @return the rendered component
     */
    @NotNull
    Component render(@NotNull Component component, @NotNull Locale locale);

    /**
     * Gets the raw translation string (legacy method).
     *
     * @param key    the translation key
     * @param locale the locale
     * @return the raw translation, or null if not found
     * @deprecated Use {@link #getRawTranslation(String, Locale)} instead
     */
    @Deprecated
    @Nullable
    default String getRaw(@NotNull String key, @NotNull Locale locale) {
        return getRawTranslation(key, locale);
    }

    /**
     * Gets the default locale.
     *
     * @return the default locale
     */
    @NotNull
    Locale getDefaultLocale();

    /**
     * Gets the MiniMessage translation registry.
     *
     * @return the registry
     */
    @NotNull
    MiniMessageTranslationRegistry getRegistry();

    /**
     * Registers this manager's translations with the global translator.
     *
     * <p>After registration, {@link Component#translatable(String)} will automatically
     * use translations from this manager.</p>
     */
    void registerTranslations();

    /**
     * Unregisters this manager's translations from the global translator.
     */
    void unregisterTranslations();

    /**
     * Checks if this manager is registered with the global translator.
     *
     * @return true if registered
     */
    boolean isRegistered();

    /**
     * Gets a translated component, never returning null.
     *
     * <p>If the translation is not found, returns an error component
     * showing the key in red text.</p>
     *
     * @param key the translation key
     * @return the translated component, or error component if not found
     */
    default @NotNull Component component(@NotNull String key) {
        return component(key, getDefaultLocale());
    }

    /**
     * Gets a translated component with tag resolvers, never returning null.
     *
     * @param key  the translation key
     * @param tags the tag resolvers
     * @return the translated component, or error component if not found
     */
    default @NotNull Component component(@NotNull String key, @NotNull TagResolver... tags) {
        return component(key, getDefaultLocale(), tags);
    }

    /**
     * Gets a translated component for a specific locale, never returning null.
     *
     * @param key    the translation key
     * @param locale the locale
     * @param tags   the tag resolvers
     * @return the translated component, or error component if not found
     */
    default @NotNull Component component(@NotNull String key, @NotNull Locale locale, @NotNull TagResolver... tags) {
        String raw = getRawTranslation(key, locale);
        if (raw == null) {
            return Component.text(key);
        }
        return translate(key, locale, tags);
    }
}

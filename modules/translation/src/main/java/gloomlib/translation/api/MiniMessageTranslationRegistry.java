package gloomlib.translation.api;

import gloomlib.translation.impl.MiniMessageTranslationRegistryImpl;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Translation registry storing MiniMessage format strings.
 *
 * <p>Integrates with Adventure's translation system while supporting full MiniMessage syntax.</p>
 *
 * @since 1.0.0
 */
public interface MiniMessageTranslationRegistry extends Translator {

    /**
     * Creates a new translation registry.
     *
     * @param name        the registry name key
     * @param miniMessage the MiniMessage instance to use for deserialization
     * @return a new registry instance
     */
    static @NotNull MiniMessageTranslationRegistry create(
            final @NotNull Key name,
            final @NotNull MiniMessage miniMessage) {
        return new MiniMessageTranslationRegistryImpl(
                requireNonNull(name, "name"),
                requireNonNull(miniMessage, "miniMessage")
        );
    }

    /**
     * Registers a translation.
     *
     * @param key    the translation key
     * @param locale the locale
     * @param format the MiniMessage format string
     * @throws IllegalArgumentException if a translation already exists for this key and locale
     */
    void register(@NotNull String key, @NotNull Locale locale, @NotNull String format);

    /**
     * Unregisters all translations for a key.
     *
     * @param key the translation key
     */
    void unregister(@NotNull String key);

    /**
     * Checks if a translation exists for a key.
     *
     * @param key the translation key
     * @return true if a translation exists
     */
    boolean contains(@NotNull String key);

    /**
     * Gets the raw MiniMessage translation string for a key and locale.
     *
     * @param key    the translation key
     * @param locale the locale
     * @return the MiniMessage string, or null if not found
     */
    @Nullable String miniMessageTranslation(@NotNull String key, @NotNull Locale locale);

    /**
     * Sets the default locale for fallback.
     *
     * @param defaultLocale the default locale
     */
    void defaultLocale(@NotNull Locale defaultLocale);

    /**
     * Registers all translations from a bundle.
     *
     * @param locale the locale
     * @param bundle a map of translation keys to MiniMessage format strings
     * @throws IllegalArgumentException if any translation already exists
     */
    default void registerAll(final @NotNull Locale locale, final @NotNull Map<String, String> bundle) {
        IllegalArgumentException firstError = null;
        int errorCount = 0;

        for (final Map.Entry<String, String> entry : bundle.entrySet()) {
            try {
                this.register(entry.getKey(), locale, entry.getValue());
            } catch (final IllegalArgumentException e) {
                if (firstError == null) {
                    firstError = e;
                }
                errorCount++;
            }
        }

        if (firstError != null) {
            if (errorCount == 1) {
                throw firstError;
            } else {
                throw new IllegalArgumentException(
                        String.format("Invalid or duplicated translation key (and %d more).", errorCount - 1),
                        firstError
                );
            }
        }
    }
}

package gloomlib.translation.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Locale fallback strategy for CLDR-style parent locale inheritance.
 */
public interface LocaleFallback {

    /**
     * Gets the fallback chain for the specified locale.
     *
     * @param locale target locale
     * @return immutable fallback chain with at least one element
     */
    @NotNull List<Locale> getFallbackChain(@NotNull Locale locale);

    /**
     * Gets the parent locale for the specified locale.
     *
     * @param locale target locale
     * @return parent locale, or null if already at root
     */
    @Nullable Locale getParent(@NotNull Locale locale);

    /**
     * Sets the root fallback locale.
     *
     * @param locale root locale
     */
    void setRootLocale(@NotNull Locale locale);

    /**
     * Gets the current root fallback locale.
     *
     * @return root locale
     */
    @NotNull Locale getRootLocale();
}

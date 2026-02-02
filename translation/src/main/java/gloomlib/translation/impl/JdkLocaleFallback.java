package gloomlib.translation.impl;

import gloomlib.translation.api.LocaleFallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDK CLDR-based locale fallback implementation with caching.
 */
public final class JdkLocaleFallback implements LocaleFallback {

    /**
     * Singleton instance.
     */
    public static final JdkLocaleFallback INSTANCE = new JdkLocaleFallback();
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);
    private final Map<Locale, List<Locale>> chainCache = new ConcurrentHashMap<>();
    private final Map<Locale, Optional<Locale>> parentCache = new ConcurrentHashMap<>();
    private volatile Locale rootLocale = Locale.US;

    private JdkLocaleFallback() {
    }

    @Override
    public @NotNull List<Locale> getFallbackChain(@NotNull Locale locale) {
        Objects.requireNonNull(locale, "locale");

        List<Locale> baseChain = chainCache.computeIfAbsent(locale, this::computeBaseChain);

        Locale currentRoot = this.rootLocale;
        if (baseChain.contains(currentRoot)) {
            return baseChain;
        }

        List<Locale> fullChain = new ArrayList<>(baseChain.size() + 1);
        fullChain.addAll(baseChain);
        fullChain.add(currentRoot);
        return Collections.unmodifiableList(fullChain);
    }

    private List<Locale> computeBaseChain(Locale locale) {
        List<Locale> candidates = CONTROL.getCandidateLocales("", locale);

        List<Locale> chain = new ArrayList<>(candidates.size());
        for (Locale candidate : candidates) {
            if (!candidate.getLanguage().isEmpty()) {
                chain.add(candidate);
            }
        }

        return Collections.unmodifiableList(chain);
    }

    @Override
    public @Nullable Locale getParent(@NotNull Locale locale) {
        Objects.requireNonNull(locale, "locale");

        return parentCache.computeIfAbsent(locale, this::computeParent).orElse(null);
    }

    private Optional<Locale> computeParent(Locale locale) {
        List<Locale> candidates = CONTROL.getCandidateLocales("", locale);

        for (int i = 0; i < candidates.size() - 1; i++) {
            if (candidates.get(i).equals(locale)) {
                Locale parent = candidates.get(i + 1);
                if (parent.getLanguage().isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(parent);
            }
        }

        return Optional.empty();
    }

    @Override
    public @NotNull Locale getRootLocale() {
        return rootLocale;
    }

    @Override
    public void setRootLocale(@NotNull Locale locale) {
        this.rootLocale = Objects.requireNonNull(locale, "locale");
    }

    /**
     * Clears all cached fallback chains.
     */
    public void clearCache() {
        chainCache.clear();
        parentCache.clear();
    }

    /**
     * Gets the current cache size.
     *
     * @return number of cached entries
     */
    public int getCacheSize() {
        return chainCache.size();
    }
}

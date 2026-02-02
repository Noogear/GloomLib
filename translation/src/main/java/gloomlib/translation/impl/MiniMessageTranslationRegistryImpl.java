package gloomlib.translation.impl;

import gloomlib.translation.api.LocaleFallback;
import gloomlib.translation.api.MiniMessageTranslationRegistry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.util.TriState;
import net.kyori.examination.Examinable;
import net.kyori.examination.ExaminableProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link MiniMessageTranslationRegistry}.
 */
public final class MiniMessageTranslationRegistryImpl implements Examinable, MiniMessageTranslationRegistry {

    private final Key name;
    private final Map<String, Translation> translations = new ConcurrentHashMap<>();
    private final Map<String, Component> componentCache = new ConcurrentHashMap<>();
    private volatile Locale defaultLocale = Locale.US;
    private final MiniMessage miniMessage;
    private final LocaleFallback fallback;

    /**
     * Creates new MiniMessage translation registry with default fallback.
     *
     * @param name the registry key
     * @param miniMessage the MiniMessage instance
     */
    public MiniMessageTranslationRegistryImpl(final Key name, final MiniMessage miniMessage) {
        this(name, miniMessage, JdkLocaleFallback.INSTANCE);
    }

    /**
     * Creates new MiniMessage translation registry with custom fallback.
     *
     * @param name the registry key
     * @param miniMessage the MiniMessage instance
     * @param fallback the locale fallback strategy
     */
    public MiniMessageTranslationRegistryImpl(
            final Key name,
            final MiniMessage miniMessage,
            final LocaleFallback fallback) {
        this.name = name;
        this.miniMessage = miniMessage;
        this.fallback = requireNonNull(fallback, "fallback");
    }

    @Override
    public void register(final @NotNull String key, final @NotNull Locale locale, final @NotNull String format) {
        this.translations.computeIfAbsent(key, Translation::new).register(locale, format);
    }

    @Override
    public void unregister(final @NotNull String key) {
        this.translations.remove(key);
        this.componentCache.keySet().removeIf(k -> k.startsWith(key + "\0"));
    }

    @Override
    public boolean contains(final @NotNull String key) {
        return this.translations.containsKey(key);
    }

    @Override
    public @NotNull Key name() {
        return name;
    }

    @Override
    public @Nullable MessageFormat translate(@NotNull String key, @NotNull Locale locale) {
        return null;
    }

    @Override
    public @Nullable Component translate(@NotNull TranslatableComponent component, @NotNull Locale locale) {
        String key = component.key();
        Translation translation = translations.get(key);
        if (translation == null) {
            return null;
        }

        String miniMessageString = translation.translate(locale);
        if (miniMessageString == null) {
            return null;
        }
        if (miniMessageString.isEmpty()) {
            return Component.empty();
        }

        List<? extends ComponentLike> arguments = component.arguments();
        final Component resultingComponent;

        if (arguments.isEmpty()) {
            String cacheKey = key + "\0" + locale.toLanguageTag();
            resultingComponent = componentCache.computeIfAbsent(cacheKey,
                    k -> this.miniMessage.deserialize(miniMessageString));
        } else {
            int size = arguments.size();
            Component[] argArray = new Component[size];
            for (int i = 0; i < size; i++) {
                argArray[i] = arguments.get(i).asComponent();
            }
            resultingComponent = this.miniMessage.deserialize(
                    miniMessageString,
                    new gloomlib.translation.tag.IndexedArgumentTag(List.of(argArray))
            );
        }

        if (component.children().isEmpty()) {
            return resultingComponent;
        }
        return resultingComponent.children(component.children());
    }

    @Override
    public @Nullable String miniMessageTranslation(@NotNull String key, @NotNull Locale locale) {
        Translation translation = translations.get(key);
        if (translation == null) {
            return null;
        }
        return translation.translate(locale);
    }

    @Override
    public @NotNull TriState hasAnyTranslations() {
        if (!this.translations.isEmpty()) {
            return TriState.TRUE;
        }
        return TriState.FALSE;
    }

    @Override
    public void defaultLocale(final @NotNull Locale defaultLocale) {
        this.defaultLocale = requireNonNull(defaultLocale, "defaultLocale");
    }

    @Override
    public @NotNull Stream<? extends ExaminableProperty> examinableProperties() {
        return Stream.of(ExaminableProperty.of("translations", this.translations));
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) return true;
        if (!(other instanceof MiniMessageTranslationRegistryImpl that)) return false;
        return this.name.equals(that.name)
                && this.translations.equals(that.translations)
                && this.defaultLocale.equals(that.defaultLocale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.translations, this.defaultLocale);
    }

    /**
     * A single translation entry with multiple locale variants.
     */
    final class Translation implements Examinable {
        private final String key;
        private final Map<Locale, String> formats;
        private final Map<Locale, String> resolvedCache;

        Translation(final @NotNull String key) {
            this.key = requireNonNull(key, "translation key");
            this.formats = new ConcurrentHashMap<>();
            this.resolvedCache = new ConcurrentHashMap<>();
        }

        void register(final @NotNull Locale locale, final @NotNull String format) {
            if (this.formats.putIfAbsent(
                    requireNonNull(locale, "locale"),
                    requireNonNull(format, "message format")) != null) {
                throw new IllegalArgumentException(
                        String.format("Translation already exists: %s for %s", this.key, locale)
                );
            }
            resolvedCache.clear();
        }

        /**
         * Translates using CLDR fallback chain with caching.
         *
         * @param locale target locale
         * @return translation string, or null if not found
         */
        @Nullable String translate(final @NotNull Locale locale) {
            requireNonNull(locale, "locale");

            String cached = resolvedCache.get(locale);
            if (cached != null) {
                return cached.isEmpty() ? null : cached;
            }

            String result = resolveTranslation(locale);
            resolvedCache.put(locale, result != null ? result : "");
            return result;
        }

        private @Nullable String resolveTranslation(Locale locale) {
            for (Locale candidate : fallback.getFallbackChain(locale)) {
                String format = this.formats.get(candidate);
                if (format != null) {
                    return format;
                }
            }
            return this.formats.get(MiniMessageTranslationRegistryImpl.this.defaultLocale);
        }

        @Override
        public @NotNull Stream<? extends ExaminableProperty> examinableProperties() {
            return Stream.of(
                    ExaminableProperty.of("key", this.key),
                    ExaminableProperty.of("formats", this.formats)
            );
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) return true;
            if (!(other instanceof Translation that)) return false;
            return this.key.equals(that.key) && this.formats.equals(that.formats);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.key, this.formats);
        }
    }
}

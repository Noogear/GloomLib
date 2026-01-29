package gloomlib.translation.impl;

import gloomlib.translation.api.MiniMessageTranslationRegistry;
import gloomlib.translation.api.MiniMessageTranslator;
import gloomlib.translation.api.TranslationManager;
import gloomlib.translation.api.TranslationSource;
import gloomlib.translation.config.FileSourceOptions;
import gloomlib.translation.factory.FileSourceFactory;
import gloomlib.translation.util.MiniMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.translation.GlobalTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core implementation of translation manager.
 */
public final class TranslationManagerImpl implements TranslationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationManagerImpl.class);
    private static final TagResolver EMPTY_TAGS = TagResolver.empty();

    /** Singleton instance. */
    public static volatile TranslationManagerImpl instance;

    private final MiniMessageTranslationRegistry registry;
    private final Path dataFolder;
    private final Locale defaultLocale;

    private final List<TranslationSource> sources = new ArrayList<>();
    private final Set<String> activeLanguageCodes = ConcurrentHashMap.newKeySet();
    private final Map<Path, CachedFile> fileCache = new ConcurrentHashMap<>();
    private FileSourceOptions currentOptions = FileSourceOptions.defaults();
    private volatile boolean registered = false;

    /**
     * Creates new translation manager.
     *
     * @param registryKey the registry key
     * @param dataFolder the data folder for translation files
     * @param defaultLocale the default locale
     */
    public TranslationManagerImpl(@NotNull net.kyori.adventure.key.Key registryKey,
                           @NotNull Path dataFolder,
                           @NotNull Locale defaultLocale) {
        this.registry = MiniMessageTranslationRegistry.create(registryKey, MiniMessages.get());
        this.registry.defaultLocale(defaultLocale);
        this.dataFolder = dataFolder;
        this.defaultLocale = defaultLocale;

        instance = this;
        MiniMessageTranslator.translator().setSource(this.registry);
    }

    @Override
    public void load(@NotNull List<String> languageCodes) {
        load(languageCodes, FileSourceOptions.defaults());
    }

    @Override
    public void load(@NotNull List<String> languageCodes, @NotNull FileSourceOptions options) {
        this.sources.clear();
        for (String code : activeLanguageCodes) {
            this.registry.unregister(code);
        }
        this.activeLanguageCodes.clear();
        this.activeLanguageCodes.addAll(languageCodes);
        this.currentOptions = options;

        if (!Files.exists(dataFolder)) {
            try {
                Files.createDirectories(dataFolder);
            } catch (IOException e) {
                LOGGER.error("Failed to create translations directory: {}", dataFolder, e);
                return;
            }
        }

        for (String code : languageCodes) {
            Locale locale = TranslationManager.parseLocale(code);
            if (locale == null) {
                LOGGER.warn("Invalid locale code: {}", code);
                continue;
            }

            String fileName = code + ".yml";
            Path ymlPath = dataFolder.resolve(fileName);
            Path propPath = dataFolder.resolve(code + ".properties");

            if (!Files.exists(ymlPath) && Files.exists(propPath)) {
                fileName = code + ".properties";
            }

            Path filePath = dataFolder.resolve(fileName);

            try {
                CachedFile cached = fileCache.get(filePath);
                BasicFileAttributes attrs = Files.exists(filePath)
                        ? Files.readAttributes(filePath, BasicFileAttributes.class)
                        : null;

                Map<String, String> translations;

                if (cached != null && attrs != null
                        && cached.lastModified() == attrs.lastModifiedTime().toMillis()
                        && cached.size() == attrs.size()) {
                    translations = cached.translations();
                    if (options.verbose()) {
                        LOGGER.info("Using cached locale: {} ({} keys)", locale, translations.size());
                    }
                } else {
                    TranslationSource source = FileSourceFactory.create(dataFolder, fileName, locale, options);
                    source.load();

                    translations = new HashMap<>();
                    for (String key : source.getKeys()) {
                        translations.put(key, source.getRaw(key));
                    }
                    sources.add(source);

                    if (attrs != null) {
                        fileCache.put(filePath, new CachedFile(
                                translations,
                                attrs.lastModifiedTime().toMillis(),
                                attrs.size()
                        ));
                    }

                    if (options.verbose()) {
                        LOGGER.info("Loaded locale: {} ({} keys)", locale, translations.size());
                    }
                }

                registry.registerAll(locale, translations);

                if (!locale.getCountry().isEmpty()) {
                    Locale languageOnly = Locale.of(locale.getLanguage());
                    try {
                        registry.registerAll(languageOnly, translations);
                    } catch (IllegalArgumentException ignored) {
                    }
                }

            } catch (Exception e) {
                LOGGER.error("Failed to load locale file for: {}", code, e);
            }
        }
    }

    @Override
    public void reload() {
        fileCache.clear();
        if (!activeLanguageCodes.isEmpty()) {
            load(new ArrayList<>(activeLanguageCodes), currentOptions);
        }
    }

    @Override
    public void close() {
        unregisterTranslations();
        sources.clear();
        activeLanguageCodes.clear();
        fileCache.clear();
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public @Nullable String getRawTranslation(@NotNull String key) {
        return getRawTranslation(key, defaultLocale);
    }

    @Override
    public @Nullable String getRawTranslation(@NotNull String key, @NotNull Locale locale) {
        return registry.miniMessageTranslation(key, locale);
    }

    @Override
    public @NotNull Component translate(@NotNull String key) {
        return translate(key, defaultLocale, EMPTY_TAGS);
    }

    @Override
    public @NotNull Component translate(@NotNull String key, @NotNull TagResolver... tags) {
        return translate(key, defaultLocale, tags);
    }

    @Override
    public @NotNull Component translate(@NotNull String key, @NotNull Locale locale, @NotNull TagResolver... tags) {
        String pattern = registry.miniMessageTranslation(key, locale);

        if (pattern == null && !locale.equals(defaultLocale)) {
            pattern = registry.miniMessageTranslation(key, defaultLocale);
        }

        if (pattern == null) {
            return Component.text(key);
        }

        return MiniMessages.get().deserialize(pattern, tags);
    }

    @Override
    public @NotNull Component render(@NotNull Component component) {
        return render(component, defaultLocale);
    }

    @Override
    public @NotNull Component render(@NotNull Component component, @NotNull Locale locale) {
        return MiniMessageTranslator.render(component, locale);
    }

    @Override
    public @NotNull Locale getDefaultLocale() {
        return defaultLocale;
    }

    @Override
    public @NotNull MiniMessageTranslationRegistry getRegistry() {
        return registry;
    }

    @Override
    public void registerTranslations() {
        if (!registered) {
            GlobalTranslator.translator().addSource(registry);
            registered = true;
            LOGGER.debug("Registered translations with GlobalTranslator");
        }
    }

    @Override
    public void unregisterTranslations() {
        if (registered) {
            GlobalTranslator.translator().removeSource(registry);
            registered = false;
            LOGGER.debug("Unregistered translations from GlobalTranslator");
        }
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Cached translation file data for avoiding unnecessary reloads.
     */
    private record CachedFile(Map<String, String> translations, long lastModified, long size) {
    }
}

package gloomlib.configuration.core.service;

import gloomlib.configuration.api.ConfigurationPart;
import gloomlib.configuration.api.ResourceProvider;
import gloomlib.configuration.api.annotation.DefaultResources;
import gloomlib.configuration.api.annotation.PostLoad;
import gloomlib.configuration.api.annotation.Template;
import gloomlib.configuration.api.util.ConfigurationLogger;
import gloomlib.configuration.api.util.FileCache;
import gloomlib.configuration.core.util.ReflectionUtils;
import gloomlib.diagnostic.LoadContext;
import gloomlib.diagnostic.YamlLineIndex;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads all YAML files from a directory into a merged {@code Map<String, V>}.
 * Pipeline: copy defaults (if empty) → scan .yml/.yaml → per-key deserialize → last-write-wins merge → @Template → @PostLoad.
 *
 * <p>Supports an optional {@link FileCache} to skip re-parsing unchanged files.</p>
 */
public final class DirectoryLoader {

    private DirectoryLoader() {}

    /**
     * Full reload — always re-reads and re-parses every file (no caching).
     */
    @NotNull
    public static <V> Map<String, V> load(
            @NotNull Class<V> valueType,
            @NotNull File directory,
            @Nullable ResourceProvider resourceProvider,
            @Nullable Object context,
            @NotNull ConfigurationSynchronizer synchronizer,
            @NotNull DeserializationService deserializationService
    ) throws Exception {
        return load(valueType, directory, resourceProvider, context, synchronizer, deserializationService, null, null);
    }

    /**
     * Smart reload — uses {@code fileCache} to skip files whose content hasn't changed,
     * and merges unchanged entries from {@code previousEntries} directly.
     *
     * @param fileCache       file-level cache for change detection (may be {@code null} for full reload)
     * @param previousEntries the previous map from the last load (may be {@code null})
     */
    @NotNull
    public static <V> Map<String, V> load(
            @NotNull Class<V> valueType,
            @NotNull File directory,
            @Nullable ResourceProvider resourceProvider,
            @Nullable Object context,
            @NotNull ConfigurationSynchronizer synchronizer,
            @NotNull DeserializationService deserializationService,
            @Nullable FileCache fileCache,
            @Nullable Map<String, V> previousEntries
    ) throws Exception {
        ensureDirectory(directory);

        File[] files = listYamlFiles(directory);
        if (files.length == 0 && resourceProvider != null) {
            copyDefaults(valueType, directory, resourceProvider);
            files = listYamlFiles(directory);
        }

        // Build a set of previous files' keys per file for smart skip.
        // We track which keys came from which file so we can carry forward unchanged files.
        Map<String, V> merged = new LinkedHashMap<>();

        for (File file : files) {
            try {
                if (fileCache != null && previousEntries != null && fileCache.isFresh(file)) {
                    // File hasn't changed — carry forward all keys that were loaded from it.
                    // We re-use cached content to identify keys without re-parsing.
                    String cachedContent = fileCache.getCachedContent(file);
                    if (cachedContent != null) {
                        YamlConfiguration yaml = new YamlConfiguration();
                        yaml.loadFromString(cachedContent);
                        for (String key : yaml.getKeys(false)) {
                            V prev = previousEntries.get(key);
                            if (prev != null) merged.put(key, prev);
                        }
                        continue;
                    }
                }
                loadFile(file, valueType, merged, context, synchronizer, deserializationService, fileCache);
            } catch (Exception e) {
                ConfigurationLogger.error("[DirectoryLoader] Failed to load " + file.getName() + ": " + e.getMessage(), e);
            }
        }

        // Purge stale cache entries for deleted files
        if (fileCache != null) fileCache.purgeStale(directory);

        applyTemplate(valueType, merged);
        return Collections.unmodifiableMap(merged);
    }

    private static <V> void loadFile(
            File file, Class<V> valueType, Map<String, V> target, @Nullable Object context,
            ConfigurationSynchronizer synchronizer, DeserializationService deserializationService,
            @Nullable FileCache fileCache
    ) throws Exception {
        String content;
        if (fileCache != null) {
            String changed = fileCache.readIfChanged(file);
            content = changed != null ? changed : fileCache.getCachedContent(file);
            if (content == null) content = fileCache.read(file);
        } else {
            content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);

        LoadContext.set(file.getName(), YamlLineIndex.buildFromString(content));
        try {
            for (String key : yaml.getKeys(false)) {
                try {
                    V entry = deserializeEntry(yaml, key, valueType, context, synchronizer, deserializationService);
                    if (entry != null) target.put(key, entry);
                } catch (Exception e) {
                    ConfigurationLogger.warn("[DirectoryLoader] Key '" + key + "' in " + file.getName() + ": " + e.getMessage());
                }
            }
        } finally {
            LoadContext.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> V deserializeEntry(
            YamlConfiguration yaml, String key, Class<V> valueType, @Nullable Object context,
            ConfigurationSynchronizer synchronizer, DeserializationService deserializationService
    ) throws Exception {
        if (ConfigurationPart.class.isAssignableFrom(valueType)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) return null;

            ConfigurationPart instance = ReflectionUtils.createInstance(
                    (Class<? extends ConfigurationPart>) valueType);
            synchronizer.syncSection(section, instance, new AtomicBoolean());
            ReflectionUtils.runHooks(instance, PostLoad.class, context);
            return (V) instance;
        }

        V result = (V) deserializationService.deserialize(yaml.get(key), valueType, valueType);
        if (result != null) ReflectionUtils.runHooks(result, PostLoad.class, context);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <V> void applyTemplate(Class<V> valueType, Map<String, V> map) throws Exception {
        Template template = valueType.getAnnotation(Template.class);
        if (template == null) return;

        boolean shouldAdd = switch (template.value()) {
            case FORCE -> !map.containsKey(template.name());
            case SMART -> map.isEmpty();
            case STRICT -> false;
        };

        if (shouldAdd && ConfigurationPart.class.isAssignableFrom(valueType)) {
            map.put(template.name(), (V) ReflectionUtils.createInstance(
                    (Class<? extends ConfigurationPart>) valueType));
        }
    }

    private static void copyDefaults(Class<?> valueType, File directory, ResourceProvider provider) {
        DefaultResources annotation = valueType.getAnnotation(DefaultResources.class);
        if (annotation == null) return;

        for (String resourcePath : annotation.value()) {
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            File dest = new File(directory, fileName);
            if (dest.exists()) continue;

            try (InputStream in = provider.getResource(resourcePath)) {
                if (in != null) Files.copy(in, dest.toPath());
                else ConfigurationLogger.warn("[DirectoryLoader] Default resource not found: " + resourcePath);
            } catch (IOException e) {
                ConfigurationLogger.warn("[DirectoryLoader] Could not copy '" + resourcePath + "': " + e.getMessage());
            }
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
    }

    @NotNull
    private static File[] listYamlFiles(File directory) {
        File[] files = directory.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) return new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }
}

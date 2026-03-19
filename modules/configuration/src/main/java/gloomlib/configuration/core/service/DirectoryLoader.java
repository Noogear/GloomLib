package gloomlib.configuration.core.service;

import gloomlib.configuration.api.ConfigurationPart;
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
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads all YAML files from a directory into a merged {@code Map<String, V>}.
 *
 * <p>Pipeline: copy defaults (if empty) → scan YAML files → per-key deserialize
 * → last-write-wins merge → {@code @Template} → return unmodifiable map.</p>
 *
 * <p>All options are carried in a single {@link DirectoryLoadSpec}, which eliminates
 * the previous 8-argument method signature. Supports both reflection mode
 * ({@link ConfigurationPart} subclasses) and factory mode ({@link gloomlib.configuration.api.EntryFactory}).</p>
 *
 * <p>Smart reload: when {@code previousEntries} is non-null, cached-fresh files are skipped
 * and their entries carried forward without re-parsing.</p>
 */
public final class DirectoryLoader {

    private DirectoryLoader() {}

    /**
     * Loads all entries from a directory as specified by {@code spec}.
     *
     * @param spec                   load options (directory, mode, rootKey, recursive, etc.)
     * @param synchronizer           for reflection-mode section sync
     * @param deserializationService for reflection-mode primitive/collection deserialization
     * @param fileCache              cache for change detection and content reuse
     * @param previousEntries        entries from the previous load for smart reload;
     *                               {@code null} triggers a full load
     */
    @NotNull
    public static <V> Map<String, V> load(
            @NotNull DirectoryLoadSpec<V> spec,
            @NotNull ConfigurationSynchronizer synchronizer,
            @NotNull DeserializationService deserializationService,
            @NotNull FileCache fileCache,
            @Nullable Map<String, V> previousEntries
    ) throws Exception {
        ensureDirectory(spec.directory());

        File[] files = listYamlFiles(spec.directory(), spec.recursive());
        if (files.length == 0 && spec.resourceProvider() != null) {
            copyDefaults(spec);
            files = listYamlFiles(spec.directory(), spec.recursive());
        }

        Map<String, V> merged = new LinkedHashMap<>();

        for (File file : files) {
            try {
                if (previousEntries != null && fileCache.isFresh(file)) {
                    // File unchanged — carry forward entries from previous load
                    String cachedContent = fileCache.getCachedContent(file);
                    if (cachedContent != null) {
                        YamlConfiguration yaml = new YamlConfiguration();
                        yaml.loadFromString(cachedContent);
                        ConfigurationSection scope = resolveScope(yaml, spec.rootKey());
                        if (scope != null) {
                            for (String key : scope.getKeys(false)) {
                                V prev = previousEntries.get(key);
                                if (prev != null) merged.put(key, prev);
                            }
                        }
                        continue;
                    }
                }
                loadFile(file, spec, merged, synchronizer, deserializationService, fileCache);
            } catch (Exception e) {
                ConfigurationLogger.error("[DirectoryLoader] Failed to load " + file.getName() + ": " + e.getMessage(), e);
            }
        }

        fileCache.purgeStale(spec.directory(), spec.recursive());
        applyTemplate(spec, merged);
        return Collections.unmodifiableMap(merged);
    }

    // ── File loading ──────────────────────────────────────────────────────────

    private static <V> void loadFile(
            File file,
            DirectoryLoadSpec<V> spec,
            Map<String, V> target,
            ConfigurationSynchronizer synchronizer,
            DeserializationService deserializationService,
            FileCache fileCache
    ) throws Exception {
        String content = fileCache.readIfChanged(file);
        if (content == null) content = fileCache.getCachedContent(file);
        if (content == null) content = fileCache.read(file);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);

        ConfigurationSection scope = resolveScope(yaml, spec.rootKey());
        if (scope == null) return; // file does not contain the required root key — skip silently

        LoadContext.set(file.getName(), YamlLineIndex.buildFromString(content));
        try {
            for (String key : scope.getKeys(false)) {
                try {
                    V entry = deserializeEntry(scope, key, spec, synchronizer, deserializationService);
                    if (entry != null) target.put(key, entry);
                } catch (Exception e) {
                    ConfigurationLogger.warn("[DirectoryLoader] Key '" + key + "' in " + file.getName() + ": " + e.getMessage());
                }
            }
        } finally {
            LoadContext.clear();
        }
    }

    // ── Entry deserialization — factory vs reflection ──────────────────────────

    @SuppressWarnings("unchecked")
    private static <V> V deserializeEntry(
            ConfigurationSection scope,
            String key,
            DirectoryLoadSpec<V> spec,
            ConfigurationSynchronizer synchronizer,
            DeserializationService deserializationService
    ) throws Exception {
        if (spec.isFactoryMode()) {
            // Factory mode: delegate entirely to user-supplied parser
            ConfigurationSection section = scope.getConfigurationSection(key);
            if (section == null) return null;
            return spec.factory().create(key, section);
        }

        // Reflection mode
        Class<V> type = spec.valueType();
        if (ConfigurationPart.class.isAssignableFrom(type)) {
            ConfigurationSection section = scope.getConfigurationSection(key);
            if (section == null) return null;
            ConfigurationPart instance = ReflectionUtils.createInstance((Class<? extends ConfigurationPart>) type);
            synchronizer.syncSection(section, instance, new AtomicBoolean());
            ReflectionUtils.runHooks(instance, PostLoad.class, spec.context());
            return (V) instance;
        }

        V result = (V) deserializationService.deserialize(scope.get(key), type, type);
        if (result != null) ReflectionUtils.runHooks(result, PostLoad.class, spec.context());
        return result;
    }

    // ── Scope resolution (rootKey) ─────────────────────────────────────────────

    @Nullable
    private static ConfigurationSection resolveScope(YamlConfiguration yaml, @Nullable String rootKey) {
        if (rootKey == null) return yaml;
        return yaml.getConfigurationSection(rootKey); // null = file has no matching root key
    }

    // ── @Template ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <V> void applyTemplate(DirectoryLoadSpec<V> spec, Map<String, V> map) throws Exception {
        if (spec.isFactoryMode() || spec.valueType() == null) return;
        Template template = spec.valueType().getAnnotation(Template.class);
        if (template == null) return;

        boolean shouldAdd = switch (template.value()) {
            case FORCE -> !map.containsKey(template.name());
            case SMART -> map.isEmpty();
            case STRICT -> false;
        };

        if (shouldAdd && ConfigurationPart.class.isAssignableFrom(spec.valueType())) {
            map.put(template.name(), (V) ReflectionUtils.createInstance(
                    (Class<? extends ConfigurationPart>) spec.valueType()));
        }
    }

    // ── Default resources ──────────────────────────────────────────────────────

    private static <V> void copyDefaults(DirectoryLoadSpec<V> spec) {
        // Builder-supplied paths take precedence; fall back to @DefaultResources annotation
        String[] paths = spec.defaultResourcePaths();
        if (paths == null && spec.valueType() != null) {
            DefaultResources ann = spec.valueType().getAnnotation(DefaultResources.class);
            if (ann != null) paths = ann.value();
        }
        if (paths == null) return;

        for (String resourcePath : paths) {
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            File dest = new File(spec.directory(), fileName);
            if (dest.exists()) continue;
            try (InputStream in = spec.resourceProvider().getResource(resourcePath)) {
                if (in != null) Files.copy(in, dest.toPath());
                else ConfigurationLogger.warn("[DirectoryLoader] Default resource not found: " + resourcePath);
            } catch (IOException e) {
                ConfigurationLogger.warn("[DirectoryLoader] Could not copy '" + resourcePath + "': " + e.getMessage());
            }
        }
    }

    // ── File scanning ──────────────────────────────────────────────────────────

    @NotNull
    private static File[] listYamlFiles(@NotNull File directory, boolean recursive) {
        List<File> result = new ArrayList<>();
        collectYaml(directory, result, recursive);
        result.sort(Comparator.comparing(File::getPath));
        return result.toArray(new File[0]);
    }

    private static void collectYaml(@NotNull File dir, @NotNull List<File> out, boolean recursive) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory() && recursive) collectYaml(f, out, true);
            else if (f.isFile() && (f.getName().endsWith(".yml") || f.getName().endsWith(".yaml")))
                out.add(f);
        }
    }

    // ── Directory creation ─────────────────────────────────────────────────────

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
    }
}

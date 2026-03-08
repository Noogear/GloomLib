package gloomlib.configuration.api;

import gloomlib.configuration.api.util.FileCache;
import gloomlib.configuration.core.service.ConfigurationSynchronizer;
import gloomlib.configuration.core.service.DeserializationService;
import gloomlib.configuration.core.service.DirectoryLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Directory-based configuration that merges all {@code .yml}/{@code .yaml} files
 * into a single {@code Map<String, V>}. Last-write-wins on duplicate keys (alphabetical file order).
 *
 * <p>Supports smart reload via {@link FileCache}: only files that have actually changed
 * on disk will be re-parsed. Unchanged files carry forward their previous entries.</p>
 *
 * @param <V> value type for each top-level key
 * @see ConfigurationManager#loadDirectory(Class, File)
 */
public final class DirectoryConfiguration<V> {

    private final Class<V> valueType;
    private final File directory;
    private final ResourceProvider resourceProvider;
    private final ConfigurationSynchronizer synchronizer;
    private final DeserializationService deserializationService;
    private final FileCache fileCache = new FileCache();
    private Object context;

    private volatile Map<String, V> entries = Collections.emptyMap();

    DirectoryConfiguration(Class<V> valueType, File directory, @Nullable ResourceProvider resourceProvider,
                           ConfigurationSynchronizer synchronizer, DeserializationService deserializationService) {
        this.valueType = valueType;
        this.directory = directory;
        this.resourceProvider = resourceProvider;
        this.synchronizer = synchronizer;
        this.deserializationService = deserializationService;
    }

    /**
     * Sets a context object passed to {@code @PostLoad} hook methods accepting a single parameter.
     */
    @NotNull
    public DirectoryConfiguration<V> withContext(@Nullable Object context) {
        this.context = context;
        return this;
    }

    /**
     * Full load — clears cache and re-reads every file from disk.
     * <p>Use this for the initial load or when you want to force a complete refresh.</p>
     */
    public void load() throws Exception {
        fileCache.clear();
        this.entries = DirectoryLoader.load(valueType, directory, resourceProvider, context,
                synchronizer, deserializationService, fileCache, null);
    }

    /**
     * Smart reload — only re-parses files that have changed on disk since the last load.
     * Unchanged files carry forward their previously deserialized entries.
     *
     * @return {@code true} if any file was re-parsed (i.e., something actually changed),
     *         {@code false} if the directory was completely fresh and no work was done
     */
    public boolean reload() throws Exception {
        if (fileCache.isDirectoryFresh(directory)) {
            return false;
        }
        this.entries = DirectoryLoader.load(valueType, directory, resourceProvider, context,
                synchronizer, deserializationService, fileCache, entries);
        return true;
    }

    /**
     * Returns {@code true} if no file in the directory has been modified since the last load.
     */
    public boolean isFresh() {
        return fileCache.isDirectoryFresh(directory);
    }

    /**
     * Returns the underlying {@link FileCache} used for change detection.
     * <p>Callers can use this to inspect per-file freshness or integrate
     * with their own caching logic.</p>
     */
    @NotNull
    public FileCache fileCache() {
        return fileCache;
    }

    /** Returns the unmodifiable merged map of all entries. */
    @NotNull
    public Map<String, V> all() {
        return entries;
    }

    /** Retrieves an entry by key, or {@code null} if not found. */
    @Nullable
    public V get(@Nullable String key) {
        return key != null ? entries.get(key) : null;
    }

    /** Retrieves an entry by key, falling back to {@code "default"} if missing. */
    @Nullable
    public V getOrDefault(@Nullable String key) {
        return getOrDefault(key, "default");
    }

    /** Retrieves an entry by key, falling back to the specified fallback key. */
    @Nullable
    public V getOrDefault(@Nullable String key, @NotNull String fallbackKey) {
        V result = (key != null) ? entries.get(key) : null;
        return result != null ? result : entries.get(fallbackKey);
    }

    /** Returns the target directory. */
    @NotNull
    public File directory() {
        return directory;
    }
}

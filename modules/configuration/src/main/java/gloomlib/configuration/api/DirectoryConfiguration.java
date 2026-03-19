package gloomlib.configuration.api;

import gloomlib.configuration.api.util.FileCache;
import gloomlib.configuration.core.service.ConfigurationSynchronizer;
import gloomlib.configuration.core.service.DeserializationService;
import gloomlib.configuration.core.service.DirectoryLoadSpec;
import gloomlib.configuration.core.service.DirectoryLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Directory-based configuration that merges all {@code .yml}/{@code .yaml} files
 * into a single {@code Map<String, V>}. Last-write-wins on duplicate keys (path-ordered).
 *
 * <p>Obtain via {@link ConfigurationManager#directory(Class, File)} (reflection mode) or
 * {@link ConfigurationManager#directory(File, EntryFactory)} (factory mode), configure the
 * returned {@link Builder}, then call {@link Builder#load()} to get a loaded instance.</p>
 *
 * @param <V> value type for each entry
 */
public final class DirectoryConfiguration<V> {

    private final DirectoryLoadSpec<V> spec;
    private final ConfigurationSynchronizer synchronizer;
    private final DeserializationService deserializationService;
    private final FileCache fileCache = new FileCache();

    private volatile Map<String, V> entries = Collections.emptyMap();

    private DirectoryConfiguration(DirectoryLoadSpec<V> spec,
                                   ConfigurationSynchronizer synchronizer,
                                   DeserializationService deserializationService) {
        this.spec = spec;
        this.synchronizer = synchronizer;
        this.deserializationService = deserializationService;
    }

    // ── Results ──────────────────────────────────────────────────────────────

    /** Returns all entries as an unmodifiable map. */
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
        V result = key != null ? entries.get(key) : null;
        return result != null ? result : entries.get(fallbackKey);
    }

    // ── Freshness ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if no file in the directory has been added, removed, or
     * modified since the last load (recursively, if configured with {@link Builder#recursive()}).
     */
    public boolean isFresh() {
        return fileCache.isDirectoryFresh(spec.directory(), spec.recursive());
    }

    /** Returns the underlying {@link FileCache} for advanced use cases. */
    @NotNull
    public FileCache fileCache() {
        return fileCache;
    }

    // ── Reload ───────────────────────────────────────────────────────────────

    /**
     * Smart reload — only re-parses files that have changed since the last load.
     * Unchanged files carry forward their previously deserialized entries.
     *
     * @return {@code true} if any file was re-parsed
     */
    public boolean reload() throws Exception {
        if (isFresh()) return false;
        this.entries = DirectoryLoader.load(spec, synchronizer, deserializationService, fileCache, entries);
        return true;
    }

    // ── Package-private Builder factories ────────────────────────────────────

    /** Called by {@link ConfigurationManager#directory(Class, File)}. */
    static <V extends ConfigurationPart> Builder<V> reflection(
            @NotNull Class<V> type, @NotNull File directory,
            @NotNull ConfigurationSynchronizer synchronizer,
            @NotNull DeserializationService deserializationService) {
        return new Builder<>(type, null, directory, synchronizer, deserializationService);
    }

    /** Called by {@link ConfigurationManager#directory(File, EntryFactory)}. */
    static <V> Builder<V> factory(
            @NotNull EntryFactory<V> factory, @NotNull File directory,
            @NotNull ConfigurationSynchronizer synchronizer,
            @NotNull DeserializationService deserializationService) {
        return new Builder<>(null, factory, directory, synchronizer, deserializationService);
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link DirectoryConfiguration}.
     * Obtain via {@link ConfigurationManager#directory}.
     */
    public static final class Builder<V> {

        private final Class<V> valueType;
        private final EntryFactory<V> entryFactory;
        private final File directory;
        private final ConfigurationSynchronizer synchronizer;
        private final DeserializationService deserializationService;

        private boolean recursive = false;
        private @Nullable String rootKey = null;
        private @Nullable ResourceProvider resourceProvider = null;
        private @Nullable String[] defaultResourcePaths = null;
        private @Nullable Object context = null;

        private Builder(@Nullable Class<V> valueType, @Nullable EntryFactory<V> entryFactory,
                        @NotNull File directory, @NotNull ConfigurationSynchronizer synchronizer,
                        @NotNull DeserializationService deserializationService) {
            this.valueType = valueType;
            this.entryFactory = entryFactory;
            this.directory = directory;
            this.synchronizer = synchronizer;
            this.deserializationService = deserializationService;
        }

        /** Recursively scans sub-directories for YAML files. */
        @NotNull
        public Builder<V> recursive() {
            this.recursive = true;
            return this;
        }

        /**
         * Filters files by a top-level discriminator key (e.g. {@code "animation"}).
         * Files that do not contain this key are silently skipped, and entries are read
         * from the section under this key rather than the file's top level.
         */
        @NotNull
        public Builder<V> rootKey(@NotNull String key) {
            this.rootKey = key;
            return this;
        }

        /**
         * Copies default resources from the JAR into the directory when it is empty.
         * Preferred over {@link #resources(ResourceProvider)} when both the provider
         * and file paths are known at build time.
         */
        @NotNull
        public Builder<V> defaults(@NotNull ResourceProvider provider, @NotNull String... paths) {
            this.resourceProvider = provider;
            this.defaultResourcePaths = paths;
            return this;
        }

        /**
         * Sets the resource provider only; default paths are taken from the
         * {@code @DefaultResources} annotation on the value type (reflection mode only).
         */
        @NotNull
        public Builder<V> resources(@NotNull ResourceProvider provider) {
            this.resourceProvider = provider;
            return this;
        }

        /** Sets a context object forwarded to {@code @PostLoad} hook methods. */
        @NotNull
        public Builder<V> context(@Nullable Object ctx) {
            this.context = ctx;
            return this;
        }

        /**
         * Performs a full load and returns the populated {@link DirectoryConfiguration}.
         */
        @NotNull
        public DirectoryConfiguration<V> load() throws Exception {
            DirectoryLoadSpec<V> spec = new DirectoryLoadSpec<>(
                    directory, valueType, entryFactory, recursive, rootKey,
                    resourceProvider, defaultResourcePaths, context);
            DirectoryConfiguration<V> cfg = new DirectoryConfiguration<>(spec, synchronizer, deserializationService);
            cfg.entries = DirectoryLoader.load(spec, synchronizer, deserializationService, cfg.fileCache, null);
            return cfg;
        }
    }
}

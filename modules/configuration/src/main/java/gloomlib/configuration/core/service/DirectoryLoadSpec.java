package gloomlib.configuration.core.service;

import gloomlib.configuration.api.EntryFactory;
import gloomlib.configuration.api.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Immutable snapshot of all options for a directory load operation.
 *
 * <p>Built by {@link gloomlib.configuration.api.DirectoryConfiguration.Builder} and consumed
 * by {@link DirectoryLoader}. Using a record eliminates the 8-argument method signature that
 * existed previously, and makes it easy to pass the full context through the call chain.</p>
 *
 * <p>Exactly one of {@link #valueType} (reflection mode) or {@link #factory} (factory mode)
 * must be non-null.</p>
 */
public record DirectoryLoadSpec<V>(
        /** Directory to scan for YAML files. */
        @NotNull File directory,

        /** Value type for reflection mode; {@code null} in factory mode. */
        @Nullable Class<V> valueType,

        /** Entry factory for factory mode; {@code null} in reflection mode. */
        @Nullable EntryFactory<V> factory,

        /** Whether to recurse into sub-directories. */
        boolean recursive,

        /**
         * Top-level discriminator key. When non-null, only entries under this YAML key are
         * loaded; files that do not contain it are silently skipped.
         */
        @Nullable String rootKey,

        /** Provider for copying default resources from the JAR when the directory is empty. */
        @Nullable ResourceProvider resourceProvider,

        /**
         * Resource paths to copy on first run. Takes precedence over
         * {@code @DefaultResources} annotation. Only used when {@link #resourceProvider} is set.
         */
        @Nullable String[] defaultResourcePaths,

        /** Context object forwarded to {@code @PostLoad} hook methods. */
        @Nullable Object context
) {
    public DirectoryLoadSpec {
        if (valueType == null && factory == null) {
            throw new IllegalArgumentException("DirectoryLoadSpec: either valueType or factory must be provided");
        }
    }

    /** Returns {@code true} when a custom {@link EntryFactory} drives deserialization. */
    public boolean isFactoryMode() {
        return factory != null;
    }
}

package gloomlib.translation.config;

import gloomlib.translation.api.TranslationMigrator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Options controlling translation file loading, creation, and migration behavior.
 *
 * @since 1.0.0
 */
public interface FileSourceOptions {

    /**
     * Creates default options.
     *
     * @return the default options
     */
    static @NotNull FileSourceOptions defaults() {
        return builder().build();
    }

    /**
     * Creates options with verbose logging enabled.
     *
     * @return verbose options
     */
    static @NotNull FileSourceOptions verboseMode() {
        return builder().verbose(true).build();
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder instance
     */
    static @NotNull Builder builder() {
        return new FileSourceOptionsImpl.BuilderImpl();
    }

    /**
     * Whether to create missing translation files from internal resources.
     *
     * @return true if missing files should be created
     */
    boolean createIfMissing();

    /**
     * Whether to migrate user files with new keys from internal resources.
     *
     * @return true if migration is enabled
     */
    boolean enableMigration();

    /**
     * Whether to create a backup before migration.
     *
     * @return true if backups should be created
     */
    boolean backupBeforeMigration();

    /**
     * Whether to log verbose information.
     *
     * @return true if verbose logging is enabled
     */
    boolean verbose();

    /**
     * The path prefix for internal resources.
     *
     * @return the internal path prefix
     */
    @NotNull
    String internalPathPrefix();

    /**
     * The character encoding for translation files.
     *
     * @return the charset
     */
    @NotNull
    Charset charset();

    /**
     * The expected lang-version for translation files.
     * If set, files with a different version will be updated.
     *
     * @return the expected version, or null if version checking is disabled
     */
    @Nullable
    String langVersion();

    /**
     * The scope for synchronizing user translation files with internal resources.
     *
     * @return the scope strategy
     */
    @NotNull
    Scope scope();

    /**
     * The migrator for transforming translation entries during updates.
     *
     * @return the migrator, or null if no migration is configured
     */
    @Nullable
    TranslationMigrator migrator();

    /**
     * Defines strategies for synchronizing user translation files with internal resources.
     */
    enum Scope {
        /**
         * Remove extra keys not in internal resources AND add missing keys from internal resources.
         * This ensures user files exactly match the structure of internal resources.
         */
        FILTER_AND_FILL,

        /**
         * Only remove keys that are not present in internal resources.
         * User customizations for removed keys will be lost.
         */
        FILTER,

        /**
         * Only add missing keys from internal resources.
         * Extra keys in user files are preserved.
         */
        FILL,

        /**
         * No synchronization. User files are loaded as-is without modification.
         */
        NONE
    }

    /**
     * Builder for {@link FileSourceOptions}.
     */
    interface Builder {
        /**
         * Sets whether to create missing files.
         *
         * @param createIfMissing true to create missing files
         * @return this builder
         */
        @NotNull Builder createIfMissing(boolean createIfMissing);

        /**
         * Sets whether to enable migration.
         *
         * @param enableMigration true to enable migration
         * @return this builder
         */
        @NotNull Builder enableMigration(boolean enableMigration);

        /**
         * Sets whether to backup before migration.
         *
         * @param backupBeforeMigration true to backup
         * @return this builder
         */
        @NotNull Builder backupBeforeMigration(boolean backupBeforeMigration);

        /**
         * Sets whether to enable verbose logging.
         *
         * @param verbose true for verbose logging
         * @return this builder
         */
        @NotNull Builder verbose(boolean verbose);

        /**
         * Sets the internal resource path prefix.
         *
         * @param prefix the prefix
         * @return this builder
         */
        @NotNull Builder internalPathPrefix(@NotNull String prefix);

        /**
         * Sets the character encoding.
         *
         * @param charset the charset
         * @return this builder
         */
        @NotNull Builder charset(@NotNull Charset charset);

        /**
         * Sets the expected lang-version.
         *
         * @param version the version string, or null to disable version checking
         * @return this builder
         */
        @NotNull Builder langVersion(@Nullable String version);

        /**
         * Sets the scope for synchronizing user files with internal resources.
         *
         * @param scope the scope strategy
         * @return this builder
         */
        @NotNull Builder scope(@NotNull Scope scope);

        /**
         * Sets the migrator for transforming translation entries.
         *
         * @param migrator the migrator, or null to disable custom migration
         * @return this builder
         */
        @NotNull Builder migrator(@Nullable TranslationMigrator migrator);

        /**
         * Builds the options.
         *
         * @return the built options
         */
        @NotNull FileSourceOptions build();
    }
}

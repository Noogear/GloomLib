package gloomlib.translation.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Interface for transforming translation entries during file migration.
 *
 * <p>Supports key renaming, value modification, and entry removal.</p>
 *
 * @since 1.0.0
 */
public interface TranslationMigrator {

    /**
     * Migrates a single translation entry.
     *
     * <p>Called for each key-value pair in the user's translation file during migration.
     * Return {@code null} to keep the entry unchanged.</p>
     *
     * @param locale the locale of the translation file
     * @param key    the translation key
     * @param value  the translation value (MiniMessage format)
     * @return the migration result, or null to keep unchanged
     */
    @Nullable
    Migration migrate(@NotNull Locale locale, @NotNull String key, @NotNull String value);

    /**
     * Determines if migration should be performed on a resource.
     *
     * <p>Use this to check version numbers or other metadata before migrating.</p>
     *
     * @param resource   the resource file name (e.g., "en_US.yml")
     * @param properties the parsed properties from the file
     * @return true if migration should be performed
     */
    default boolean shouldMigrate(@NotNull String resource, @NotNull Map<String, String> properties) {
        return true;
    }

    /**
     * Represents the result of migrating a translation entry.
     *
     * @param key   the new key, or null to remove the entry
     * @param value the new value, or null to remove the entry
     */
    record Migration(@Nullable String key, @Nullable String value) {

        /**
         * Sentinel value indicating the entry should be dropped (removed).
         */
        public static final Migration DROP = new Migration(null, null);

        /**
         * Creates a migration that renames the key.
         *
         * @param newKey the new key name
         * @param value  the value (unchanged or modified)
         * @return the migration result
         */
        public static @NotNull Migration rename(@NotNull String newKey, @NotNull String value) {
            return new Migration(newKey, value);
        }

        /**
         * Creates a migration that updates the value.
         *
         * @param key      the key (unchanged)
         * @param newValue the new value
         * @return the migration result
         */
        public static @NotNull Migration update(@NotNull String key, @NotNull String newValue) {
            return new Migration(key, newValue);
        }

        /**
         * Creates a migration that renames the key and updates the value.
         *
         * @param newKey   the new key name
         * @param newValue the new value
         * @return the migration result
         */
        public static @NotNull Migration transform(@NotNull String newKey, @NotNull String newValue) {
            return new Migration(newKey, newValue);
        }

        /**
         * Checks if this migration drops (removes) the entry.
         *
         * @return true if the entry should be removed
         */
        public boolean drop() {
            return key == null && value == null;
        }

        /**
         * Checks if this migration renames the key.
         *
         * @return true if the key is changed
         */
        public boolean renames() {
            return key != null;
        }
    }
}

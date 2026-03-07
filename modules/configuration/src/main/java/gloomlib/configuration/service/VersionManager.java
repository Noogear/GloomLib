package gloomlib.configuration.service;

import gloomlib.configuration.ConfigurationFile;
import gloomlib.configuration.model.FieldMeta;
import com.google.common.base.CaseFormat;
import gloomlib.configuration.util.ConfigBackup;
import gloomlib.configuration.util.ConfigurationCache;
import gloomlib.configuration.util.ConfigurationLogger;
import gloomlib.configuration.util.ReflectionUtils;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Field;
import java.util.function.Supplier;

/**
 * Manages configuration version checking and automatic upgrades.
 * <p>
 * This service handles version field detection, upgrade triggers, backup creation,
 * and data migration between configuration versions.
 * </p>
 */
public final class VersionManager {

    private final DeserializationService deserializationService;
    private final Supplier<ConfigurationLoader> loaderSupplier;

    /**
     * Creates a new version manager.
     *
     * @param deserializationService the deserialization service for data migration
     * @param loaderSupplier         lazy reference to the loader (breaks circular dependency)
     */
    public VersionManager(DeserializationService deserializationService, Supplier<ConfigurationLoader> loaderSupplier) {
        this.deserializationService = deserializationService;
        this.loaderSupplier = loaderSupplier;
    }

    /**
     * Checks if a configuration file needs a version upgrade.
     *
     * @param clazz the configuration class
     * @param file  the configuration file
     * @return the version check result
     * @throws Exception if version checking fails
     */
    public VersionCheckResult checkVersion(Class<? extends ConfigurationFile> clazz, File file) throws Exception {
        // Find @Version field
        Field versionField = null;
        int expectedVersion = -1;
        boolean autoBackup = true;
        boolean migrate = true;

        for (Field field : clazz.getFields()) {
            if (field.isAnnotationPresent(gloomlib.configuration.annotations.Version.class)) {
                versionField = field;
                var annotation = field.getAnnotation(gloomlib.configuration.annotations.Version.class);
                autoBackup = annotation.autoBackup();
                migrate = annotation.migrate();

                // Get expected version from annotation or field default
                if (annotation.value() != -1) {
                    expectedVersion = annotation.value();
                } else {
                    Object instance = ReflectionUtils.createInstance(clazz);
                    field.setAccessible(true);
                    Object val = field.get(instance);
                    if (val instanceof Integer intVal) {
                        expectedVersion = intVal;
                    }
                }
                break;
            }
        }

        // No version field found
        if (versionField == null) {
            return new VersionCheckResult(null, -1, -1, false, false);
        }

        // Read actual version from file
        YamlConfiguration yaml = loaderSupplier.get().loadYaml(file);
        String versionKey = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, versionField.getName());
        int actualVersion = yaml.getInt(versionKey, -1);

        return new VersionCheckResult(versionField, expectedVersion, actualVersion, autoBackup, migrate);
    }

    /**
     * Handles configuration version upgrade.
     *
     * @param clazz        the configuration class
     * @param file         the configuration file
     * @param versionCheck the version check result
     * @param <T>          the configuration type
     * @return the upgraded configuration instance
     * @throws Exception if upgrade fails
     */
    public <T extends ConfigurationFile> T handleVersionUpgrade(Class<T> clazz, File file, VersionCheckResult versionCheck) throws Exception {
        ConfigurationLogger.info("Configuration version upgrade detected: " + versionCheck.actualVersion + " → " + versionCheck.expectedVersion);

        // Backup old configuration
        if (versionCheck.autoBackup) {
            File backup = ConfigBackup.backup(file, "v" + versionCheck.actualVersion);
            if (backup != null) {
                ConfigurationLogger.info("Old configuration backed up to: " + backup.getName());
            }
        }

        // Attempt data migration if enabled
        YamlConfiguration oldYaml = null;
        if (versionCheck.migrate) {
            oldYaml = loaderSupplier.get().loadYaml(file);
        }

        // Delete old file and create new one
        if (!file.delete()) {
            ConfigurationLogger.warn("Failed to delete old configuration file");
        }

        // Create new configuration with defaults (skip version check)
        loaderSupplier.get().createIfNotExist(file);
        T newInstance = loaderSupplier.get().loadWithoutVersionCheck(clazz, file);

        // Migrate data if enabled
        if (versionCheck.migrate && oldYaml != null) {
            migrateConfigData(oldYaml, newInstance);
            loaderSupplier.get().save(newInstance, file);
            ConfigurationLogger.info("Configuration data migrated from version " + versionCheck.actualVersion);
        }

        return newInstance;
    }

    /**
     * Migrates configuration data from old YAML to new instance.
     *
     * @param oldYaml     the old YAML configuration
     * @param newInstance the new configuration instance
     * @throws Exception if migration fails
     */
    private void migrateConfigData(YamlConfiguration oldYaml, ConfigurationFile newInstance) throws Exception {
        for (FieldMeta meta : ConfigurationCache.getCachedMeta(newInstance.getClass())) {
            String key = meta.key();

            // Skip version field itself
            if (meta.isAnnotationPresent(gloomlib.configuration.annotations.Version.class)) {
                continue;
            }

            // Try to migrate value if it exists in old config
            if (oldYaml.contains(key)) {
                try {
                    Object value = oldYaml.get(key);
                    Object deserializedValue = deserializationService.deserialize(value, meta.getType(), meta.getGenericType());
                    meta.set(newInstance, deserializedValue);
                } catch (Exception e) {
                    ConfigurationLogger.warn("Failed to migrate field '" + key + "': " + e.getMessage());
                }
            }
        }
    }

    /**
     * Result of version checking.
     *
     * @param versionField    the version field (null if not found)
     * @param expectedVersion the expected version
     * @param actualVersion   the actual version in the file
     * @param autoBackup      whether to auto-backup before upgrade
     * @param migrate         whether to migrate data during upgrade
     */
    public record VersionCheckResult(Field versionField, int expectedVersion, int actualVersion, boolean autoBackup,
                                     boolean migrate) {
        /**
         * Checks if an upgrade is needed.
         *
         * @return true if upgrade is needed
         */
        public boolean needsUpgrade() {
            return versionField != null && actualVersion != expectedVersion && expectedVersion != -1;
        }
    }
}

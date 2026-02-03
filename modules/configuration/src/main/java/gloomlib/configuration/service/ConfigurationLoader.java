package gloomlib.configuration.service;

import gloomlib.configuration.ConfigurationFile;
import gloomlib.configuration.annotations.Header;
import gloomlib.configuration.annotations.PostLoad;
import gloomlib.configuration.annotations.PreLoad;
import gloomlib.configuration.annotations.Template;
import gloomlib.configuration.model.FieldMeta;
import gloomlib.configuration.util.ConfigurationCache;
import gloomlib.configuration.util.ConfigurationLogger;
import gloomlib.configuration.util.ReflectionUtils;
import gloomlib.configuration.util.TypeInference;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for loading, saving, and reloading configuration files.
 * <p>
 * This service handles file I/O, caching, template processing, and lifecycle hooks.
 * </p>
 */
public final class ConfigurationLoader {

    private static final int YAML_MAX_WIDTH = 250;
    private static final Map<String, FileCacheEntry> FILE_CACHE = new ConcurrentHashMap<>();

    private final ConfigurationSynchronizer synchronizer;
    private final VersionManager versionManager;

    /**
     * Creates a new configuration loader.
     *
     * @param synchronizer   the configuration synchronizer
     * @param versionManager the version manager
     */
    public ConfigurationLoader(ConfigurationSynchronizer synchronizer, VersionManager versionManager) {
        this.synchronizer = synchronizer;
        this.versionManager = versionManager;
    }

    /**
     * Loads a configuration file into a Java object.
     * <p>
     * If the file does not exist, it will be created with default values based on the class structure.
     * </p>
     *
     * @param clazz the class of the configuration object
     * @param file  the file to load from
     * @param <T>   the type of the configuration object
     * @return the loaded configuration object
     * @throws Exception if an error occurs during loading
     */
    public <T extends ConfigurationFile> T load(Class<T> clazz, File file) throws Exception {
        if (!file.exists()) {
            return saveDefault(clazz, file);
        }

        // Check for version field and handle upgrades
        VersionManager.VersionCheckResult versionCheck = versionManager.checkVersion(clazz, file);
        if (versionCheck.needsUpgrade()) {
            return versionManager.handleVersionUpgrade(clazz, file, versionCheck);
        }

        YamlConfiguration yaml = loadYaml(file);
        T instance = ReflectionUtils.createInstance(clazz);
        instance.setYaml(yaml);
        instance.setFile(file);

        populateInstance(instance, yaml, file);
        return instance;
    }

    /**
     * Loads configuration without version checking (used during upgrades).
     * <p>
     * Package-private method for VersionManager to load without version check.
     * </p>
     *
     * @param clazz the class of the configuration object
     * @param file  the file to load from
     * @param <T>   the type of the configuration object
     * @return the loaded configuration object
     * @throws Exception if an error occurs during loading
     */
    <T extends ConfigurationFile> T loadWithoutVersionCheck(Class<T> clazz, File file) throws Exception {
        YamlConfiguration yaml = loadYaml(file);
        T instance = ReflectionUtils.createInstance(clazz);
        instance.setYaml(yaml);
        instance.setFile(file);

        populateInstance(instance, yaml, file);
        return instance;
    }

    /**
     * Creates the file and delegates to load mechanism to populate and save defaults.
     * <p>
     * This avoids running hooks multiple times.
     * </p>
     *
     * @param clazz the class of the configuration object
     * @param file  the file to create and save to
     * @param <T>   the type of the configuration object
     * @return the loaded configuration object with default values
     * @throws Exception if an error occurs during saving or loading
     */
    public <T extends ConfigurationFile> T saveDefault(Class<T> clazz, File file) throws Exception {
        createIfNotExist(file);
        return load(clazz, file);
    }

    /**
     * Reloads the configuration from the file system.
     *
     * @param instance the configuration instance to reload
     * @throws Exception if an error occurs during reloading
     */
    public void reload(ConfigurationFile instance) throws Exception {
        File file = instance.getFile();
        if (file == null || !file.exists()) {
            throw new IllegalStateException("Config file does not exist: " + file);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            ConfigurationLogger.error("Reload failed: " + e.getMessage(), e);
            throw e;
        }

        FILE_CACHE.put(file.getAbsolutePath(), new FileCacheEntry(file.lastModified(), file.length(), yaml));
        instance.setYaml(yaml);
        populateInstance(instance, yaml, file);
    }

    /**
     * Saves the configuration instance to the file system.
     *
     * @param instance the configuration instance to save
     * @param file     the file to save to
     * @throws Exception if an error occurs during saving
     */
    public void save(ConfigurationFile instance, File file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        if (instance.getClass().isAnnotationPresent(Header.class)) {
            yaml.options().setHeader(List.of(instance.getClass().getAnnotation(Header.class).value()));
        }

        ReflectionUtils.runHooks(instance, PreLoad.class);
        synchronizer.writeSection(yaml, instance);

        yaml.options().width(YAML_MAX_WIDTH);
        yaml.save(file);
        FILE_CACHE.put(file.getAbsolutePath(), new FileCacheEntry(file.lastModified(), file.length(), yaml));
    }

    /**
     * Loads a YAML file with caching.
     *
     * @param file the file to load
     * @return the loaded YAML configuration
     * @throws Exception if loading fails
     */
    public YamlConfiguration loadYaml(File file) throws Exception {
        String path = file.getAbsolutePath();
        FileCacheEntry cached = FILE_CACHE.get(path);
        if (cached != null && cached.isFresh(file)) {
            return cached.yaml;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            ConfigurationLogger.error("YAML Syntax Error in '" + file.getName() + "': " + e.getMessage(), e);
            throw e;
        }
        FILE_CACHE.put(path, new FileCacheEntry(file.lastModified(), file.length(), yaml));
        return yaml;
    }

    /**
     * Populates a configuration instance from YAML.
     *
     * @param instance the configuration instance
     * @param yaml     the YAML configuration
     * @param file     the configuration file
     * @throws Exception if population fails
     */
    private void populateInstance(ConfigurationFile instance, YamlConfiguration yaml, File file) throws Exception {
        processTemplates(instance);
        ReflectionUtils.runHooks(instance, PreLoad.class);

        AtomicBoolean isDirty = new AtomicBoolean(false);
        try {
            synchronizer.syncSection(yaml, instance, isDirty);
        } catch (Exception e) {
            ConfigurationLogger.error("Structure parse failed for '" + file.getName() + "': " + e.getMessage(), e);
            throw e;
        }

        if (isDirty.get()) {
            save(instance, file);
        }

        ReflectionUtils.runHooks(instance, PostLoad.class);
    }

    /**
     * Processes @Template annotations for map fields.
     *
     * @param instance the configuration instance
     * @throws Exception if template processing fails
     */
    @SuppressWarnings("unchecked")
    private void processTemplates(Object instance) throws Exception {
        for (FieldMeta meta : ConfigurationCache.getCachedMeta(instance.getClass())) {
            Field field = meta.field();
            if (Map.class.isAssignableFrom(field.getType())) {
                Type genericType = field.getGenericType();
                Class<?> valueType = TypeInference.extractGenericParameter(genericType, 1);

                if (valueType.isAnnotationPresent(Template.class)) {
                    Template template = valueType.getAnnotation(Template.class);
                    String defaultKey = template.name();

                    Map<String, Object> map = (Map<String, Object>) meta.get(instance);
                    if (map == null) {
                        map = new HashMap<>();
                        meta.set(instance, map);
                    }

                    boolean shouldAddDefault = false;
                    switch (template.value()) {
                        case FORCE -> shouldAddDefault = !map.containsKey(defaultKey);
                        case SMART -> shouldAddDefault = map.isEmpty();
                        case STRICT -> shouldAddDefault = false;
                    }
                    if (shouldAddDefault) {
                        try {
                            map.put(defaultKey, ReflectionUtils.createInstance(valueType));
                        } catch (Exception e) {
                            ConfigurationLogger.warn("Failed to create template for " + valueType.getSimpleName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a file if it does not exist.
     *
     * @param file the file to create
     * @throws Exception if file creation fails
     */
    public void createIfNotExist(File file) throws Exception {
        if (!file.exists()) {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                if (!file.getParentFile().mkdirs()) {
                    throw new IOException("Failed to create directory: " + file.getParentFile().getAbsolutePath());
                }
            }
            if (!file.createNewFile()) {
                throw new IOException("Failed to create file: " + file.getAbsolutePath());
            }
        }
    }

    /**
     * Cache entry for YAML files.
     *
     * @param lastModified the last modified timestamp
     * @param size         the file size
     * @param yaml         the loaded YAML configuration
     */
    private record FileCacheEntry(long lastModified, long size, YamlConfiguration yaml) {
        /**
         * Checks if the cache entry is still fresh.
         *
         * @param file the file to check against
         * @return true if the cache is fresh
         */
        boolean isFresh(File file) {
            return file.lastModified() == lastModified && file.length() == size;
        }
    }
}

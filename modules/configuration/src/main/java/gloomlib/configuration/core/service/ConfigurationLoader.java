package gloomlib.configuration.core.service;

import gloomlib.configuration.api.ConfigurationFile;
import gloomlib.configuration.api.annotation.Header;
import gloomlib.configuration.api.annotation.PostLoad;
import gloomlib.configuration.api.annotation.PreLoad;
import gloomlib.configuration.api.util.ConfigurationLogger;
import gloomlib.configuration.api.util.FileCache;
import gloomlib.configuration.core.util.ReflectionUtils;
import gloomlib.diagnostic.LoadContext;
import gloomlib.diagnostic.YamlLineIndex;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for loading, saving, and reloading configuration files.
 * <p>
 * Uses {@link FileCache} for file-level freshness tracking and content caching.
 * Each load operation parses a fresh {@link YamlConfiguration} from cached file content.
 * </p>
 */
public final class ConfigurationLoader {

    private static final int YAML_MAX_WIDTH = 250;
    private static final FileCache FILE_CACHE = new FileCache();

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

        String content;
        try {
            content = FILE_CACHE.read(file);
        } catch (IOException e) {
            ConfigurationLogger.error("Reload failed: " + e.getMessage(), e);
            throw e;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(content);
        } catch (Exception e) {
            ConfigurationLogger.error("Reload failed: " + e.getMessage(), e);
            throw e;
        }

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

        synchronizer.writeSection(yaml, instance);

        yaml.options().width(YAML_MAX_WIDTH);
        yaml.save(file);
        // Update file cache after save
        String savedContent = yaml.saveToString();
        FILE_CACHE.put(file,
                new FileCache.Entry(file.lastModified(), file.length(), savedContent));
    }

    /**
     * Loads a YAML file with caching.
     *
     * @param file the file to load
     * @return the loaded YAML configuration
     * @throws Exception if loading fails
     */
    YamlConfiguration loadYaml(File file) throws Exception {
        String content;
        try {
            // Use cached content if file hasn't changed, otherwise re-read from disk
            String cached = FILE_CACHE.isFresh(file) ? FILE_CACHE.getCachedContent(file) : null;
            content = cached != null ? cached : FILE_CACHE.read(file);
        } catch (IOException e) {
            throw new IOException("Failed to read config file: " + file.getName(), e);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(content);
        } catch (InvalidConfigurationException e) {
            ConfigurationLogger.error("YAML Syntax Error in '" + file.getName() + "': " + e.getMessage(), e);
            throw e;
        }
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
        ReflectionUtils.runHooks(instance, PreLoad.class);

        AtomicBoolean isDirty = new AtomicBoolean(false);
        String cachedContent = FILE_CACHE.getCachedContent(file);
        LoadContext.set(file.getName(), YamlLineIndex.buildFromString(
                cachedContent != null ? cachedContent : ""
        ));
        try {
            synchronizer.syncSection(yaml, instance, isDirty);
        } catch (Exception e) {
            ConfigurationLogger.error("Structure parse failed for '" + file.getName() + "': " + e.getMessage(), e);
            throw e;
        } finally {
            LoadContext.clear();
        }

        if (synchronizer.processTemplates(instance)) {
            isDirty.set(true);
        }

        if (isDirty.get()) {
            save(instance, file);
        }

        ReflectionUtils.runHooks(instance, PostLoad.class);
    }

    /**
     * Creates a file if it does not exist.
     *
     * @param file the file to create
     * @throws Exception if file creation fails
     */
    void createIfNotExist(File file) throws Exception {
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
}

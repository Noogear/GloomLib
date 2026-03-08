package gloomlib.configuration.api;

import com.google.gson.reflect.TypeToken;
import gloomlib.configuration.api.exception.SerializationException;
import gloomlib.configuration.api.util.ConfigurationLogger;
import gloomlib.configuration.core.registry.AdapterRegistry;
import gloomlib.configuration.core.service.*;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.io.File;
import java.lang.reflect.Type;
import java.util.List;

/**
 * High-performance configuration manager for loading, saving, and synchronizing
 * configuration files between Java objects and YAML files.
 */
public class ConfigurationManager {

    private static final AdapterRegistry adapterRegistry = new AdapterRegistry();
    private static final SerializationService serializationService = new SerializationService(adapterRegistry);

    /**
     * Enables logging for the configuration manager.
     *
     * @param componentLogger the plugin's ComponentLogger
     */
    public static void enableLogging(ComponentLogger componentLogger) {
        ConfigurationLogger.setLogger(componentLogger);
    }    private static final DeserializationService deserializationService = new DeserializationService(adapterRegistry, () -> ConfigurationManager.synchronizer);

    /**
     * Registers a custom type adapter.
     *
     * @param type    the class type to adapt
     * @param adapter the adapter implementation
     * @param <T>     the type
     */
    public static <T> void registerAdapter(Class<T> type, TypeAdapter<T> adapter) {
        adapterRegistry.registerAdapter(type, adapter);
    }    private static final VersionManager versionManager = new VersionManager(deserializationService, () -> ConfigurationManager.loader);

    /**
     * Registers a TypeSerializer for a specific TypeToken.
     *
     * @param typeToken  the type token representing the generic type
     * @param serializer the type serializer implementation
     * @param <T>        the type
     */
    public static <T> void registerTypeSerializer(TypeToken<T> typeToken, TypeSerializer<T> serializer) {
        adapterRegistry.registerTypeSerializer(typeToken, serializer);
    }    private static final ConfigurationSynchronizer synchronizer = new ConfigurationSynchronizer(deserializationService, serializationService);

    /**
     * Deserializes a value using a TypeToken for precise generic type resolution.
     *
     * @param raw       the raw value from YAML
     * @param typeToken the type token representing the target type
     * @param <T>       the target type
     * @return the deserialized value
     * @throws SerializationException if deserialization fails
     */
    public static <T> T deserialize(Object raw, TypeToken<T> typeToken) throws SerializationException {
        return deserialize(raw, typeToken, List.of());
    }    private static final ConfigurationLoader loader = new ConfigurationLoader(synchronizer, versionManager);

    /**
     * Deserializes a value using a TypeToken with path context for error reporting.
     *
     * @param raw       the raw value from YAML
     * @param typeToken the type token representing the target type
     * @param nodePath  the path to the current node (for error context)
     * @param <T>       the target type
     * @return the deserialized value
     * @throws SerializationException if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Object raw, TypeToken<T> typeToken, List<String> nodePath) throws SerializationException {
        if (raw == null) {
            return null;
        }

        try {
            if (adapterRegistry.hasTypeSerializer(typeToken)) {
                TypeSerializer<T> serializer = adapterRegistry.getTypeSerializer(typeToken);
                return serializer.deserialize(raw, typeToken.getType());
            }

            Class<T> rawType = (Class<T>) typeToken.getRawType();
            Type genericType = typeToken.getType();

            return (T) deserializationService.deserialize(raw, rawType, genericType);
        } catch (Exception e) {
            if (e instanceof SerializationException se) throw se;
            throw SerializationException.wrap(nodePath, typeToken.getRawType(), raw, e);
        }
    }

    /**
     * Loads a configuration file into a Java object.
     *
     * @param clazz the class of the configuration object
     * @param file  the file to load from
     * @param <T>   the type of the configuration object
     * @return the loaded configuration object
     * @throws Exception if an error occurs during loading
     */
    public static <T extends ConfigurationFile> T load(Class<T> clazz, File file) throws Exception {
        return loader.load(clazz, file);
    }

    /**
     * Creates the file and delegates to load mechanism to populate and save defaults.
     *
     * @param clazz the class of the configuration object
     * @param file  the file to create and save to
     * @param <T>   the type of the configuration object
     * @return the loaded configuration object with default values
     * @throws Exception if an error occurs during saving or loading
     */
    public static <T extends ConfigurationFile> T saveDefault(Class<T> clazz, File file) throws Exception {
        return loader.saveDefault(clazz, file);
    }

    /**
     * Reloads the configuration from the file system.
     *
     * @param instance the configuration instance to reload
     * @throws Exception if an error occurs during reloading
     */
    public static void reload(ConfigurationFile instance) throws Exception {
        loader.reload(instance);
    }

    /**
     * Saves the configuration instance to the file system.
     *
     * @param instance the configuration instance to save
     * @param file     the file to save to
     * @throws Exception if an error occurs during saving
     */
    public static void save(ConfigurationFile instance, File file) throws Exception {
        loader.save(instance, file);
    }

    // ── Directory-based configuration loading ────────────────────────────────

    /**
     * Creates a {@link DirectoryConfiguration} that loads all YAML files from a directory
     * and merges their top-level keys into a single {@code Map<String, V>}.
     *
     * <p>Call {@link DirectoryConfiguration#load()} after creation to trigger loading.</p>
     *
     * @param valueType the class of each top-level entry value
     * @param directory the directory containing YAML files
     * @param <V>       the value type
     * @return a new directory configuration (not yet loaded)
     */
    public static <V> DirectoryConfiguration<V> loadDirectory(Class<V> valueType, File directory) {
        return new DirectoryConfiguration<>(valueType, directory, null, synchronizer, deserializationService);
    }

    /**
     * Creates a {@link DirectoryConfiguration} with a {@link ResourceProvider} for
     * automatic default resource copying when the directory is empty.
     *
     * <p>Call {@link DirectoryConfiguration#load()} after creation to trigger loading.</p>
     *
     * @param valueType the class of each top-level entry value
     * @param directory the directory containing YAML files
     * @param resources provider for copying default resources from the JAR
     * @param <V>       the value type
     * @return a new directory configuration (not yet loaded)
     */
    public static <V> DirectoryConfiguration<V> loadDirectory(
            Class<V> valueType, File directory, ResourceProvider resources) {
        return new DirectoryConfiguration<>(valueType, directory, resources, synchronizer, deserializationService);
    }
}


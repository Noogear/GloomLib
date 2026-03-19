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
     * Returns a {@link DirectoryConfiguration.Builder} for loading YAML files into instances
     * of {@code valueType} via reflection (reflection mode).
     *
     * <p>Chain builder methods for additional options, then call
     * {@link DirectoryConfiguration.Builder#load()} to trigger loading.</p>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * var indicators = ConfigurationManager
     *     .directory(IndicatorEntry.class, dir)
     *     .defaults(plugin::getResource, "indicator/damage.yml")
     *     .load();
     * }</pre>
     *
     * @param valueType the {@link ConfigurationPart} subclass for each entry
     * @param directory the directory containing YAML files
     * @param <V>       the entry value type
     */
    public static <V extends ConfigurationPart> DirectoryConfiguration.Builder<V> directory(
            Class<V> valueType, File directory) {
        return DirectoryConfiguration.reflection(valueType, directory, synchronizer, deserializationService);
    }

    /**
     * Returns a {@link DirectoryConfiguration.Builder} for loading YAML files using a custom
     * {@link EntryFactory} (factory mode), bypassing reflection entirely.
     *
     * <p>Suitable when entries require custom parsing logic, multi-type dispatch, or
     * cross-entry references (e.g. multi-pass loading). Combine with
     * {@link DirectoryConfiguration.Builder#rootKey(String)} and
     * {@link DirectoryConfiguration.Builder#recursive()} as needed.</p>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * var animations = ConfigurationManager
     *     .directory(dir, (name, sec) -> AnimationParser.parse(name, sec, registry))
     *     .rootKey("animation")
     *     .recursive()
     *     .load();
     * }</pre>
     *
     * @param directory the directory containing YAML files
     * @param factory   factory used to create each entry from its YAML section
     * @param <V>       the entry value type
     */
    public static <V> DirectoryConfiguration.Builder<V> directory(
            File directory, EntryFactory<V> factory) {
        return DirectoryConfiguration.factory(factory, directory, synchronizer, deserializationService);
    }
}


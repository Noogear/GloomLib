package gloomlib.configuration.core.registry;

import com.google.gson.reflect.TypeToken;
import gloomlib.configuration.api.TypeAdapter;
import gloomlib.configuration.api.TypeSerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for custom type adapters and type serializers.
 * <p>
 * This class manages the registration and retrieval of custom type adapters
 * and TypeToken-based type serializers used for configuration serialization.
 * </p>
 */
public final class AdapterRegistry {

    private final Map<Class<?>, TypeAdapter<?>> adapters = new ConcurrentHashMap<>();
    private final Map<TypeToken<?>, TypeSerializer<?>> typeSerializers = new ConcurrentHashMap<>();

    /**
     * Registers a custom type adapter.
     *
     * @param type    the class type to adapt
     * @param adapter the adapter implementation
     * @param <T>     the type
     */
    public <T> void registerAdapter(Class<T> type, TypeAdapter<T> adapter) {
        adapters.put(type, adapter);
    }

    /**
     * Registers a TypeSerializer for a specific TypeToken.
     *
     * @param typeToken  the type token representing the generic type
     * @param serializer the type serializer implementation
     * @param <T>        the type
     */
    public <T> void registerTypeSerializer(TypeToken<T> typeToken, TypeSerializer<T> serializer) {
        typeSerializers.put(typeToken, serializer);
    }

    /**
     * Checks if an adapter is registered for the given type.
     *
     * @param type the class type
     * @return true if an adapter exists
     */
    public boolean hasAdapter(Class<?> type) {
        return adapters.containsKey(type);
    }

    /**
     * Retrieves the registered adapter for the given type.
     *
     * @param type the class type
     * @param <T>  the type
     * @return the adapter, or null if not registered
     */
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> getAdapter(Class<T> type) {
        return (TypeAdapter<T>) adapters.get(type);
    }

    /**
     * Checks if a type serializer is registered for the given TypeToken.
     *
     * @param typeToken the type token
     * @return true if a type serializer exists
     */
    public boolean hasTypeSerializer(TypeToken<?> typeToken) {
        return typeSerializers.containsKey(typeToken);
    }

    /**
     * Retrieves the registered type serializer for the given TypeToken.
     *
     * @param typeToken the type token
     * @param <T>       the type
     * @return the type serializer, or null if not registered
     */
    @SuppressWarnings("unchecked")
    public <T> TypeSerializer<T> getTypeSerializer(TypeToken<T> typeToken) {
        return (TypeSerializer<T>) typeSerializers.get(typeToken);
    }
}

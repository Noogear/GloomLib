package gloomlib.configuration.api;

import gloomlib.configuration.api.exception.SerializationException;

import java.lang.reflect.Type;

/**
 * Interface for TypeToken-based type serialization.
 * <p>
 * Unlike {@link TypeAdapter}, this interface receives the full generic Type information,
 * allowing for precise handling of complex generic types like {@code Map<UUID, List<ItemStack>>}.
 * </p>
 *
 * @param <T> the target type
 */
public interface TypeSerializer<T> {

    /**
     * Serializes the value into a YAML-compatible object.
     *
     * @param value       the value to serialize
     * @param genericType the full generic type information
     * @return the serialized object
     * @throws SerializationException if serialization fails
     */
    Object serialize(T value, Type genericType) throws SerializationException;

    /**
     * Deserializes the YAML value into the target type.
     *
     * @param yamlValue   the YAML value to deserialize
     * @param genericType the full generic type information
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    T deserialize(Object yamlValue, Type genericType) throws SerializationException;
}

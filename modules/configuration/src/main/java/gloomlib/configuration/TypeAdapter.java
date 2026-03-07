package gloomlib.configuration;

/**
 * Interface for custom type serialization logic.
 *
 * @param <T> the target type
 */
public interface TypeAdapter<T> {

    /**
     * Serializes the value into a YAML-compatible object.
     *
     * @param value the value to serialize
     * @return the serialized object
     */
    Object serialize(T value);

    /**
     * Deserializes the YAML value into the target type.
     *
     * @param yamlValue the YAML value to deserialize
     * @return the deserialized object
     */
    T deserialize(Object yamlValue);
}

package gloomlib.configuration.core.service;

import com.google.common.base.CaseFormat;
import com.google.gson.reflect.TypeToken;
import gloomlib.configuration.api.ConfigurationPart;
import gloomlib.configuration.api.TypeAdapter;
import gloomlib.configuration.api.TypeSerializer;
import gloomlib.configuration.core.model.FieldMeta;
import gloomlib.configuration.core.registry.AdapterRegistry;
import gloomlib.configuration.core.util.ConfigurationCache;
import gloomlib.configuration.core.util.TypeInference;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Serializes Java objects into YAML-compatible formats.
 */
public final class SerializationService {

    private final AdapterRegistry adapterRegistry;

    /**
     * Creates service with adapter registry.
     *
     * @param adapterRegistry adapter registry instance
     */
    public SerializationService(AdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * Serializes value into YAML-compatible object.
     *
     * @param val value to serialize
     * @return serialized object
     * @throws Exception if serialization fails
     */
    public Object serialize(Object val) throws Exception {
        return serialize(val, null);
    }

    /**
     * Serializes value with full generic type info, checking TypeSerializers first.
     * This is the primary dispatch method — generic type flows through all recursive paths.
     *
     * @param val         value to serialize
     * @param genericType the full generic type information (null if unknown)
     * @return serialized object
     * @throws Exception if serialization fails
     */
    @SuppressWarnings("unchecked")
    public Object serialize(Object val, Type genericType) throws Exception {
        if (val == null) {
            return null;
        }

        // Check TypeSerializer with full generic info
        if (genericType != null) {
            TypeToken<?> token = TypeToken.get(genericType);
            if (adapterRegistry.hasTypeSerializer(token)) {
                TypeSerializer<Object> serializer = (TypeSerializer<Object>) adapterRegistry.getTypeSerializer(token);
                return serializer.serialize(val, genericType);
            }
        }

        Class<?> type = val.getClass();

        if (adapterRegistry.hasAdapter(type)) {
            TypeAdapter<Object> adapter = (TypeAdapter<Object>) adapterRegistry.getAdapter(type);
            return adapter.serialize(val);
        }

        if (type.isRecord()) {
            return serializeRecord(val, type);
        }

        if (val instanceof ConfigurationPart part) {
            return serializeConfigurationPart(part);
        }

        if (val instanceof Map) {
            return serializeMap((Map<?, ?>) val, genericType);
        }

        if (val instanceof Collection) {
            return serializeCollection((Collection<?>) val, genericType);
        }

        if (val instanceof Enum<?> e) {
            return e.name();
        }

        if (val instanceof UUID uuid) {
            return uuid.toString();
        }

        if (val instanceof ConfigurationSerializable serializable) {
            return serializable;
        }

        // Primitive types and types with custom toString()
        if (val instanceof Number || val instanceof Boolean || val instanceof String || val instanceof Character) {
            return val;
        }
        return ConfigurationCache.hasToString(type) ? val.toString() : val;
    }


    /**
     * Serializes a Java record into a map.
     *
     * @param val  the record instance
     * @param type the record class
     * @return a map representing the record
     * @throws Exception if serialization fails
     */
    private Object serializeRecord(Object val, Class<?> type) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (RecordComponent rc : type.getRecordComponents()) {
            map.put(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, rc.getName()),
                    serialize(rc.getAccessor().invoke(val), rc.getGenericType()));
        }
        return map;
    }


    /**
     * Serializes a ConfigurationPart into a map.
     *
     * @param part the configuration part
     * @return a map representing the configuration part
     * @throws Exception if serialization fails
     */
    private Object serializeConfigurationPart(ConfigurationPart part) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (FieldMeta meta : ConfigurationCache.getCachedMeta(part.getClass())) {
            map.put(meta.key(), serialize(meta.get(part), meta.getGenericType()));
        }
        return map;
    }


    /**
     * Serializes a map with key-value pairs.
     *
     * @param map         the map to serialize
     * @param genericType the generic type of the map field (may be null)
     * @return a serialized map
     * @throws Exception if serialization fails
     */
    private Object serializeMap(Map<?, ?> map, Type genericType) throws Exception {
        Map<String, Object> newMap = new LinkedHashMap<>();
        Type valueGenericType = TypeInference.extractGenericType(genericType, 1);
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object key = e.getKey();
            String keyStr = (key instanceof Enum<?> en) ? en.name() : key.toString();
            newMap.put(keyStr, serialize(e.getValue(), valueGenericType));
        }
        return newMap;
    }


    /**
     * Serializes a collection into a list.
     *
     * @param col         the collection to serialize
     * @param genericType the generic type of the collection field (may be null)
     * @return a serialized list
     * @throws Exception if serialization fails
     */
    private Object serializeCollection(Collection<?> col, Type genericType) throws Exception {
        List<Object> list = new ArrayList<>();
        Type elementGenericType = TypeInference.extractGenericType(genericType, 0);
        for (Object o : col) {
            list.add(serialize(o, elementGenericType));
        }
        return list;
    }
}

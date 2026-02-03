package gloomlib.configuration.service;

import gloomlib.configuration.ConfigurationManager;
import gloomlib.configuration.ConfigurationPart;
import gloomlib.configuration.model.FieldMeta;
import gloomlib.configuration.registry.AdapterRegistry;
import gloomlib.configuration.util.ConfigurationCache;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * Service for serializing Java objects into YAML-compatible representations.
 * <p>
 * This service handles conversion of various Java types (primitives, records,
 * ConfigurationPart, collections, maps) into formats suitable for YAML persistence.
 * </p>
 */
public final class SerializationService {

    private final AdapterRegistry adapterRegistry;

    /**
     * Creates a new serialization service with the given adapter registry.
     *
     * @param adapterRegistry the adapter registry for custom type serialization
     */
    public SerializationService(AdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * Converts camelCase to kebab-case.
     *
     * @param s the camelCase string
     * @return the kebab-case string
     */
    private static String camelToKebab(String s) {
        return s.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
    }

    /**
     * Serializes a value into a YAML-compatible object.
     *
     * @param val the value to serialize
     * @return the serialized object
     * @throws Exception if serialization fails
     */
    @SuppressWarnings("unchecked")
    public Object serialize(Object val) throws Exception {
        if (val == null) {
            return null;
        }
        Class<?> type = val.getClass();

        if (adapterRegistry.hasAdapter(type)) {
            @SuppressWarnings("unchecked")
            ConfigurationManager.TypeAdapter<Object> adapter = (ConfigurationManager.TypeAdapter<Object>) adapterRegistry.getAdapter(type);
            return adapter.serialize(val);
        }

        if (type.isRecord()) {
            return serializeRecord(val, type);
        }

        if (val instanceof ConfigurationPart part) {
            return serializeConfigurationPart(part);
        }

        if (val instanceof Map) {
            return serializeMap((Map<?, ?>) val);
        }

        if (val instanceof Collection) {
            return serializeCollection((Collection<?>) val);
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
            map.put(camelToKebab(rc.getName()), serialize(rc.getAccessor().invoke(val)));
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
            map.put(meta.key(), serialize(meta.get(part)));
        }
        return map;
    }

    /**
     * Serializes a map with key-value pairs.
     *
     * @param map the map to serialize
     * @return a serialized map
     * @throws Exception if serialization fails
     */
    private Object serializeMap(Map<?, ?> map) throws Exception {
        Map<String, Object> newMap = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object key = e.getKey();
            String keyStr = (key instanceof Enum<?> en) ? en.name() : key.toString();
            newMap.put(keyStr, serialize(e.getValue()));
        }
        return newMap;
    }

    /**
     * Serializes a collection into a list.
     *
     * @param col the collection to serialize
     * @return a serialized list
     * @throws Exception if serialization fails
     */
    private Object serializeCollection(Collection<?> col) throws Exception {
        List<Object> list = new ArrayList<>();
        for (Object o : col) {
            list.add(serialize(o));
        }
        return list;
    }
}

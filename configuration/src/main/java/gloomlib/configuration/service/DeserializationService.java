package gloomlib.configuration.service;

import gloomlib.configuration.ConfigurationPart;
import gloomlib.configuration.exception.SerializationException;
import gloomlib.configuration.registry.AdapterRegistry;
import gloomlib.configuration.util.ReflectionUtils;
import gloomlib.configuration.util.TypeConverter;
import gloomlib.configuration.util.TypeInference;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for deserializing YAML values into Java objects.
 * <p>
 * This service handles conversion of YAML-compatible values into various Java types
 * (primitives, records, ConfigurationPart, collections, maps).
 * </p>
 */
public final class DeserializationService {

    private final AdapterRegistry adapterRegistry;
    private ConfigurationSynchronizer synchronizer; // Circular dependency - set via setter

    /**
     * Creates a new deserialization service with the given adapter registry.
     *
     * @param adapterRegistry the adapter registry for custom type deserialization
     */
    public DeserializationService(AdapterRegistry adapterRegistry) {
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
     * Sets the configuration synchronizer (required to break circular dependency).
     *
     * @param synchronizer the configuration synchronizer
     */
    public void setSynchronizer(ConfigurationSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    /**
     * Deserializes a value into the target type.
     *
     * @param raw         the raw value from YAML
     * @param type        the target class type
     * @param genericType the generic type information
     * @return the deserialized value
     * @throws Exception if deserialization fails
     */
    public Object deserialize(Object raw, Class<?> type, Type genericType) throws Exception {
        return deserializeWithPath(raw, type, genericType, new ArrayList<>());
    }

    /**
     * Deserializes a value with path context for error reporting.
     *
     * @param raw         the raw value from YAML
     * @param type        the target class type
     * @param genericType the generic type information
     * @param nodePath    the path to the current node (for error context)
     * @return the deserialized value
     * @throws Exception if deserialization fails
     */
    public Object deserializeWithPath(Object raw, Class<?> type, Type genericType, List<String> nodePath) throws Exception {
        if (raw == null) {
            return null;
        }

        try {
            if (adapterRegistry.hasAdapter(type)) {
                return adapterRegistry.getAdapter(type).deserialize(raw);
            }

            if (type.isRecord()) {
                return deserializeRecord(raw, type, nodePath);
            }

            if (ConfigurationPart.class.isAssignableFrom(type)) {
                return deserializeConfigurationPart(raw, type);
            }

            if (Map.class.isAssignableFrom(type)) {
                return deserializeMap(raw, genericType, nodePath);
            }

            if (List.class.isAssignableFrom(type)) {
                return deserializeList(raw, genericType, nodePath);
            }

            if (ConfigurationSerializable.class.isAssignableFrom(type)) {
                return deserializeConfigurationSerializable(raw, type);
            }

            return TypeConverter.convertPrimitive(raw, type);
        } catch (Exception e) {
            if (e instanceof SerializationException) {
                throw e;
            }
            throw SerializationException.builder()
                    .message("Failed to deserialize value")
                    .path(nodePath)
                    .expectedType(type)
                    .actualValue(raw)
                    .cause(e)
                    .build();
        }
    }

    /**
     * Deserializes a record from a map or ConfigurationSection.
     *
     * @param raw      the raw value
     * @param type     the record class type
     * @param nodePath the current node path
     * @return the deserialized record instance
     * @throws Exception if deserialization fails
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object deserializeRecord(Object raw, Class<?> type, List<String> nodePath) throws Exception {
        Map<String, Object> map = (raw instanceof ConfigurationSection cs) ? cs.getValues(false) : (Map) raw;
        RecordComponent[] rcs = type.getRecordComponents();
        Object[] args = new Object[rcs.length];
        Class<?>[] types = new Class<?>[rcs.length];

        for (int i = 0; i < rcs.length; i++) {
            types[i] = rcs[i].getType();
            String fieldKey = camelToKebab(rcs[i].getName());
            List<String> fieldPath = new ArrayList<>(nodePath);
            fieldPath.add(fieldKey);

            Object val = deserializeWithPath(map.get(fieldKey), rcs[i].getType(), rcs[i].getGenericType(), fieldPath);
            args[i] = (val == null && types[i].isPrimitive()) ? TypeConverter.getPrimitiveDefault(types[i]) : val;
        }

        Constructor<?> c = type.getDeclaredConstructor(types);
        c.setAccessible(true);
        return c.newInstance(args);
    }

    /**
     * Deserializes a ConfigurationPart from a map or ConfigurationSection.
     *
     * @param raw  the raw value
     * @param type the ConfigurationPart class type
     * @return the deserialized ConfigurationPart instance
     * @throws Exception if deserialization fails
     */
    @SuppressWarnings("unchecked")
    private Object deserializeConfigurationPart(Object raw, Class<?> type) throws Exception {
        ConfigurationPart inst = ReflectionUtils.createInstance((Class<? extends ConfigurationPart>) type);
        ConfigurationSection tmp = new MemoryConfiguration();

        if (raw instanceof ConfigurationSection cs) {
            tmp = cs;
        } else if (raw instanceof Map map) {
            for (Object k : map.keySet()) {
                tmp.set(k.toString(), map.get(k));
            }
        }

        synchronizer.syncSection(tmp, inst, new AtomicBoolean());
        return inst;
    }

    /**
     * Deserializes a map from a ConfigurationSection or Map.
     *
     * @param raw         the raw value
     * @param genericType the generic type information for the map
     * @param nodePath    the current node path
     * @return the deserialized map
     * @throws Exception if deserialization fails
     */
    private Object deserializeMap(Object raw, Type genericType, List<String> nodePath) throws Exception {
        Map<Object, Object> map = new LinkedHashMap<>();
        Class<?> kType = TypeInference.extractGenericParameter(genericType, 0);
        Class<?> vType = TypeInference.extractGenericParameter(genericType, 1);

        if (raw instanceof ConfigurationSection cs) {
            for (String k : cs.getKeys(false)) {
                Object val = cs.get(k);
                Object keyVal = TypeConverter.convertPrimitive(k, kType);

                List<String> valuePath = new ArrayList<>(nodePath);
                valuePath.add(k);
                map.put(keyVal, deserializeWithPath(val, vType, vType, valuePath));
            }
        } else if (raw instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String k = entry.getKey().toString();
                Object val = entry.getValue();
                Object keyVal = TypeConverter.convertPrimitive(k, kType);

                List<String> valuePath = new ArrayList<>(nodePath);
                valuePath.add(k);
                map.put(keyVal, deserializeWithPath(val, vType, vType, valuePath));
            }
        } else {
            return raw;
        }
        return map;
    }

    /**
     * Deserializes a list.
     *
     * @param raw         the raw list
     * @param genericType the generic type information for the list
     * @param nodePath    the current node path
     * @return the deserialized list
     * @throws Exception if deserialization fails
     */
    private Object deserializeList(Object raw, Type genericType, List<String> nodePath) throws Exception {
        if (!(raw instanceof List<?> list)) {
            return raw;
        }

        List<Object> newList = new ArrayList<>();
        Class<?> iType = TypeInference.extractGenericParameter(genericType, 0);
        int index = 0;

        for (Object o : list) {
            List<String> indexPath = new ArrayList<>(nodePath);
            indexPath.add("[" + index + "]");
            newList.add(deserializeWithPath(o, iType, iType, indexPath));
            index++;
        }
        return newList;
    }

    /**
     * Deserializes a ConfigurationSerializable object.
     *
     * @param raw  the raw value
     * @param type the ConfigurationSerializable class type
     * @return the deserialized ConfigurationSerializable instance
     * @throws Exception if deserialization fails
     */
    @SuppressWarnings("unchecked")
    private Object deserializeConfigurationSerializable(Object raw, Class<?> type) throws Exception {
        if (type.isInstance(raw)) {
            return raw;
        }
        if (raw instanceof Map map) {
            try {
                return ConfigurationSerialization.deserializeObject(map, (Class<? extends ConfigurationSerializable>) type);
            } catch (Exception e) {
                return ConfigurationSerialization.deserializeObject(map);
            }
        }
        return raw;
    }
}

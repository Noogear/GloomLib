package gloomlib.configuration.core.service;

import com.google.common.base.CaseFormat;
import gloomlib.configuration.api.ConfigurationPart;
import gloomlib.configuration.api.exception.SerializationException;
import gloomlib.configuration.core.registry.AdapterRegistry;
import gloomlib.configuration.core.util.ReflectionUtils;
import gloomlib.configuration.core.util.TypeConverter;
import gloomlib.configuration.core.util.TypeInference;
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
import java.util.function.Supplier;

/**
 * Service for deserializing YAML values into Java objects.
 * <p>
 * This service handles conversion of YAML-compatible values into various Java types
 * (primitives, records, ConfigurationPart, collections, maps).
 * </p>
 */
public final class DeserializationService {

    private final AdapterRegistry adapterRegistry;
    private final Supplier<ConfigurationSynchronizer> synchronizerSupplier;

    /**
     * Creates a new deserialization service.
     *
     * @param adapterRegistry      the adapter registry for custom type deserialization
     * @param synchronizerSupplier lazy reference to the synchronizer (breaks circular dependency)
     */
    public DeserializationService(AdapterRegistry adapterRegistry, Supplier<ConfigurationSynchronizer> synchronizerSupplier) {
        this.adapterRegistry = adapterRegistry;
        this.synchronizerSupplier = synchronizerSupplier;
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
    private Object deserializeWithPath(Object raw, Class<?> type, Type genericType, List<String> nodePath) throws Exception {
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
            throw SerializationException.wrap(nodePath, type, raw, e);
        }
    }

    /**
     * Deserializes a record from map or ConfigurationSection.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object deserializeRecord(Object raw, Class<?> type, List<String> nodePath) throws Exception {
        Map<String, Object> map = (raw instanceof ConfigurationSection cs) ? cs.getValues(false) : (Map) raw;
        RecordComponent[] rcs = type.getRecordComponents();
        Object[] args = new Object[rcs.length];
        Class<?>[] types = new Class<?>[rcs.length];

        for (int i = 0; i < rcs.length; i++) {
            processRecordComponent(rcs[i], i, map, nodePath, args, types);
        }

        Constructor<?> c = type.getDeclaredConstructor(types);
        c.setAccessible(true);
        return c.newInstance(args);
    }

    /**
     * Processes single record component.
     */
    private void processRecordComponent(
            RecordComponent component,
            int index,
            Map<String, Object> map,
            List<String> nodePath,
            Object[] args,
            Class<?>[] types
    ) throws Exception {
        types[index] = component.getType();
        String fieldKey = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, component.getName());
        List<String> fieldPath = new ArrayList<>(nodePath);
        fieldPath.add(fieldKey);

        Object val = deserializeWithPath(
                map.get(fieldKey),
                component.getType(),
                component.getGenericType(),
                fieldPath
        );
        args[index] = (val == null && types[index].isPrimitive())
                ? TypeConverter.getPrimitiveDefault(types[index])
                : val;
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
                Object val = map.get(k);
                String key = k.toString();
                if (val instanceof Map<?, ?> nested) {
                    tmp.createSection(key, nested);
                } else {
                    tmp.set(key, val);
                }
            }
        }

        synchronizerSupplier.get().syncSection(tmp, inst, new AtomicBoolean());
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
        Type vGenericType = TypeInference.extractGenericType(genericType, 1);

        if (raw instanceof ConfigurationSection cs) {
            for (String k : cs.getKeys(false)) {
                Object val = cs.get(k);
                Object keyVal = TypeConverter.convertPrimitive(k, kType);

                List<String> valuePath = new ArrayList<>(nodePath);
                valuePath.add(k);
                map.put(keyVal, deserializeWithPath(val, vType, vGenericType, valuePath));
            }
        } else if (raw instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String k = entry.getKey().toString();
                Object val = entry.getValue();
                Object keyVal = TypeConverter.convertPrimitive(k, kType);

                List<String> valuePath = new ArrayList<>(nodePath);
                valuePath.add(k);
                map.put(keyVal, deserializeWithPath(val, vType, vGenericType, valuePath));
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
        Type iGenericType = TypeInference.extractGenericType(genericType, 0);
        int index = 0;

        for (Object o : list) {
            List<String> indexPath = new ArrayList<>(nodePath);
            indexPath.add("[" + index + "]");
            newList.add(deserializeWithPath(o, iType, iGenericType, indexPath));
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

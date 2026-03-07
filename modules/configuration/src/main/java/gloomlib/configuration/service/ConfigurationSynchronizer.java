package gloomlib.configuration.service;

import gloomlib.configuration.ConfigurationPart;
import gloomlib.configuration.annotations.Check;
import gloomlib.configuration.annotations.Comment;
import gloomlib.configuration.annotations.Inline;
import gloomlib.configuration.annotations.Template;
import gloomlib.configuration.model.FieldMeta;
import gloomlib.configuration.util.ConfigurationCache;
import gloomlib.configuration.util.ConfigurationLogger;
import gloomlib.configuration.util.ReflectionUtils;
import gloomlib.configuration.util.TypeInference;
import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for synchronizing configuration data between Java objects and YAML sections.
 * <p>
 * This service handles bidirectional field synchronization, validation, and comment management.
 * </p>
 */
public final class ConfigurationSynchronizer {

    private final DeserializationService deserializationService;
    private final SerializationService serializationService;

    /**
     * Creates a new configuration synchronizer with the given services.
     *
     * @param deserializationService the deserialization service
     * @param serializationService   the serialization service
     */
    public ConfigurationSynchronizer(DeserializationService deserializationService,
                                     SerializationService serializationService) {
        this.deserializationService = deserializationService;
        this.serializationService = serializationService;
    }

    /**
     * Synchronizes a ConfigurationSection with a ConfigurationPart object.
     * <p>
     * This method loads all fields from the YAML section, validates them,
     * and removes unused keys from the YAML.
     * </p>
     *
     * @param section the YAML section
     * @param obj     the configuration object
     * @param isDirty flag to track if YAML was modified
     * @throws Exception if synchronization fails
     */
    public void syncSection(ConfigurationSection section, ConfigurationPart obj, AtomicBoolean isDirty) throws Exception {
        List<FieldMeta> metas = ConfigurationCache.getCachedMeta(obj.getClass());
        Set<String> validKeys = new HashSet<>();

        // Load and update all fields from YAML
        for (FieldMeta meta : metas) {
            String key = meta.key();
            validKeys.add(key);

            try {
                loadFieldFromSection(section, obj, meta, key, isDirty);
            } catch (Exception e) {
                ConfigurationLogger.warn("Failed to load '" + key + "': " + e.getMessage());
            }
        }

        // Clean up unused YAML keys
        removeUnusedYamlKeys(section, validKeys, isDirty);
    }

    /**
     * Loads a single field from a ConfigurationSection into the object.
     *
     * @param section the YAML section
     * @param obj     the configuration object
     * @param meta    the field metadata
     * @param key     the YAML key
     * @param isDirty flag to track if YAML was modified
     * @throws Exception if loading fails
     */
    @SuppressWarnings("unchecked")
    private void loadFieldFromSection(ConfigurationSection section, ConfigurationPart obj, FieldMeta meta, String key, AtomicBoolean isDirty) throws Exception {
        Object defaultVal = meta.get(obj);

        if (!section.contains(key)) {
            writeField(section, key, defaultVal, meta);
            isDirty.set(true);
            return;
        }

        if (meta.hasComment()) {
            section.setComments(key, List.of(meta.getAnnotation(Comment.class).value()));
        }
        if (meta.hasInline()) {
            section.setInlineComments(key, List.of(meta.getAnnotation(Inline.class).value()));
        }

        if (ConfigurationPart.class.isAssignableFrom(meta.getType())) {
            ConfigurationPart part = (ConfigurationPart) defaultVal;
            if (part == null) {
                part = ReflectionUtils.createInstance((Class<? extends ConfigurationPart>) meta.getType());
            }

            ConfigurationSection sub = section.getConfigurationSection(key);
            if (sub == null) {
                sub = section.createSection(key);
            }

            syncSection(sub, part, isDirty);
            meta.set(obj, part);
            return;
        }

        Object loadedVal = deserializationService.deserialize(section.get(key), meta.getType(), meta.getGenericType());

        if (meta.hasCheck()) {
            loadedVal = runCheck(meta, loadedVal);
        }

        meta.set(obj, loadedVal);
    }

    /**
     * Removes YAML keys that are not present in the valid keys set.
     *
     * @param section   the YAML section
     * @param validKeys the set of valid keys
     * @param isDirty   flag to track if YAML was modified
     */
    private void removeUnusedYamlKeys(ConfigurationSection section, Set<String> validKeys, AtomicBoolean isDirty) {
        for (String yamlKey : section.getKeys(false)) {
            if (!validKeys.contains(yamlKey)) {
                section.set(yamlKey, null);
                isDirty.set(true);
            }
        }
    }

    /**
     * Writes all fields of a ConfigurationPart to a ConfigurationSection.
     *
     * @param section the YAML section
     * @param obj     the configuration object
     * @throws Exception if writing fails
     */
    public void writeSection(ConfigurationSection section, ConfigurationPart obj) throws Exception {
        for (FieldMeta meta : ConfigurationCache.getCachedMeta(obj.getClass())) {
            writeField(section, meta.key(), meta.get(obj), meta);
        }
    }

    /**
     * Writes a single field to a ConfigurationSection.
     *
     * @param section the YAML section
     * @param key     the YAML key
     * @param val     the field value
     * @param meta    the field metadata
     * @throws Exception if writing fails
     */
    private void writeField(ConfigurationSection section, String key, Object val, FieldMeta meta) throws Exception {
        if (val == null) {
            return;
        }

        if (val instanceof ConfigurationPart part) {
            ConfigurationSection sub = section.createSection(key);
            writeSection(sub, part);
        } else if (val instanceof Map<?, ?> map) {
            ConfigurationSection sub = section.createSection(key);
            writeMap(sub, map);
        } else {
            section.set(key, serializationService.serialize(val));
        }

        if (meta.hasComment()) {
            section.setComments(key, List.of(meta.getAnnotation(Comment.class).value()));
        }
        if (meta.hasInline()) {
            section.setInlineComments(key, List.of(meta.getAnnotation(Inline.class).value()));
        }
    }

    /**
     * Writes a map to a ConfigurationSection.
     *
     * @param section the YAML section
     * @param map     the map to write
     * @throws Exception if writing fails
     */
    private void writeMap(ConfigurationSection section, Map<?, ?> map) throws Exception {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object keyObj = entry.getKey();
            String k = (keyObj instanceof Enum<?> e) ? e.name() : keyObj.toString();

            Object serializedVal = serializationService.serialize(entry.getValue());
            if (serializedVal instanceof Map<?, ?> subMap) {
                ConfigurationSection sub = section.createSection(k);
                writeMap(sub, subMap);
            } else {
                section.set(k, serializedVal);
            }
        }
    }

    /**
     * Runs validation checks on field value.
     */
    private Object runCheck(FieldMeta meta, Object val) {
        Check annotation = meta.getAnnotation(Check.class);
        try {
            if (annotation.cls() != void.class && !annotation.method().isEmpty()) {
                return runCustomMethodCheck(annotation, val);
            }
            if (annotation.value() != Check.NoOpValidator.class) {
                return runValidatorCheck(annotation, val);
            }
        } catch (Exception e) {
            ConfigurationLogger.error("Validation failed: " + e.getMessage(), e);
        }
        return val;
    }

    /**
     * Runs custom method validation.
     */
    private Object runCustomMethodCheck(Check annotation, Object val) throws Exception {
        String key = annotation.cls().getName() + "#" + annotation.method();
        Method m = ConfigurationCache.getCachedMethod(key, () -> findValidationMethod(annotation));
        return Modifier.isStatic(m.getModifiers())
                ? m.invoke(null, val)
                : m.invoke(ReflectionUtils.createInstance(annotation.cls()), val);
    }

    /**
     * Finds validation method from annotation.
     */
    private Method findValidationMethod(Check annotation) {
        try {
            for (Method me : annotation.cls().getDeclaredMethods()) {
                if (me.getName().equals(annotation.method()) && me.getParameterCount() == 1) {
                    me.setAccessible(true);
                    return me;
                }
            }
            throw new RuntimeException("Method not found: " + annotation.method());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Runs validator-based validation.
     */
    @SuppressWarnings("unchecked")
    private Object runValidatorCheck(Check annotation, Object val) throws Exception {
        Check.Validator<Object> v = ConfigurationCache.getCachedValidator(annotation.value());
        return v.validate(val);
    }

    // === Template Processing ===

    /**
     * Processes @Template annotations for map fields.
     *
     * @param instance the configuration instance
     * @throws Exception if template processing fails
     */
    void processTemplates(Object instance) throws Exception {
        for (FieldMeta meta : ConfigurationCache.getCachedMeta(instance.getClass())) {
            Field field = meta.field();
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }

            Type genericType = field.getGenericType();
            Class<?> valueType = TypeInference.extractGenericParameter(genericType, 1);

            if (!valueType.isAnnotationPresent(Template.class)) {
                continue;
            }

            processTemplateField(meta, instance, valueType);
        }
    }

    @SuppressWarnings("unchecked")
    private void processTemplateField(FieldMeta meta, Object instance, Class<?> valueType) throws Exception {
        Template template = valueType.getAnnotation(Template.class);
        String defaultKey = template.name();

        Map<String, Object> map = (Map<String, Object>) meta.get(instance);
        if (map == null) {
            map = new HashMap<>();
            meta.set(instance, map);
        }

        if (!shouldAddTemplateDefault(template, map, defaultKey)) {
            return;
        }

        try {
            map.put(defaultKey, ReflectionUtils.createInstance(valueType));
        } catch (Exception e) {
            ConfigurationLogger.warn("Failed to create template for " + valueType.getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean shouldAddTemplateDefault(Template template, Map<String, Object> map, String defaultKey) {
        return switch (template.value()) {
            case FORCE -> !map.containsKey(defaultKey);
            case SMART -> map.isEmpty();
            case STRICT -> false;
        };
    }
}

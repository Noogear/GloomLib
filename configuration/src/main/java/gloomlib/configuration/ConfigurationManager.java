package gloomlib.configuration;

import gloomlib.configuration.annotations.*;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * High-Performance Configuration Manager.
 * <p>
 * This class handles the lifecycle of configuration files, including loading, saving,
 * serialization, deserialization, and synchronization between Java objects and YAML files.
 * <p>
 * <b>Key Features:</b>
 * <ul>
 * <li><b>Smart Caching:</b> Minimizes I/O operations by checking file modification times.</li>
 * <li><b>Auto-Trim:</b> Automatically removes unused keys from the YAML file to keep it clean.</li>
 * <li><b>Type Safety:</b> Supports Java Records, Enums, UUIDs, and Bukkit Serialization.</li>
 * <li><b>Robustness:</b> Provides detailed error logging and auto-recovery for invalid values.</li>
 * </ul>
 */
public class ConfigurationManager {

    private static final Pattern CAMEL_PATTERN = Pattern.compile("([a-z])([A-Z]+)");
    /**
     * Cache for reflection metadata to avoid repetitive lookups.
     */
    private static final Map<Class<?>, List<FieldMeta>> META_CACHE = new ConcurrentHashMap<>();
    /**
     * Cache for file metadata to skip unnecessary I/O reads.
     */
    private static final Map<String, FileCacheEntry> FILE_CACHE = new ConcurrentHashMap<>();
    /**
     * Cache for validator instances.
     */
    private static final Map<Class<?>, Check.Validator<?>> VALIDATOR_CACHE = new ConcurrentHashMap<>();
    /**
     * Cache for reflected validation methods.
     */
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    /**
     * Registry for custom type adapters.
     */
    private static final Map<Class<?>, TypeAdapter<?>> ADAPTERS = new ConcurrentHashMap<>();
    /**
     * Cache for checking if a class overrides toString().
     */
    private static final Map<Class<?>, Boolean> TO_STRING_CACHE = new ConcurrentHashMap<>();
    private static ComponentLogger LOGGER;

    /**
     * Enables logging for the configuration manager.
     * <p>
     * It is highly recommended to call this in your plugin's onEnable logic.
     *
     * @param logger the plugin's ComponentLogger
     */
    public static void enableLogging(ComponentLogger logger) {
        LOGGER = logger;
    }

    /**
     * Registers a custom type adapter.
     *
     * @param type    the class type to adapt
     * @param adapter the adapter implementation
     * @param <T>     the type
     */
    public static <T> void registerAdapter(Class<T> type, TypeAdapter<T> adapter) {
        ADAPTERS.put(type, adapter);
    }

    /**
     * Loads a configuration file into a Java object.
     * <p>
     * If the file does not exist, it will be created with default values.
     *
     * @param clazz the configuration class (must extend ConfigurationFile)
     * @param file  the target file on disk
     * @param <T>   the type of the configuration class
     * @return the loaded configuration instance
     * @throws Exception if loading fails (IO error, Syntax error, etc.)
     */
    @SuppressWarnings("unchecked")
    public static <T extends ConfigurationFile> T load(Class<T> clazz, File file) throws Exception {
        if (!file.exists()) return (T) saveDefault(clazz, file);

        YamlConfiguration yaml = loadYaml(file);
        T instance = createInstance(clazz);
        instance.setYaml(yaml);
        instance.setFile(file);

        populateInstance(instance, yaml, file);
        return instance;
    }

    /**
     * Reloads an existing configuration instance from disk.
     * <p>
     * This forces a read from the disk, bypassing the cache check.
     *
     * @param instance the configuration instance to reload
     * @throws Exception if reloading fails
     */
    public static void reload(ConfigurationFile instance) throws Exception {
        File file = instance.getFile();
        if (file == null || !file.exists()) throw new IllegalStateException("Config file does not exist: " + file);

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            logError("Reload failed: " + e.getMessage(), e);
            throw e;
        }

        FILE_CACHE.put(file.getAbsolutePath(), new FileCacheEntry(file.lastModified(), file.length(), yaml));
        instance.setYaml(yaml);
        populateInstance(instance, yaml, file);
    }

    /**
     * Saves the current state of a configuration instance to disk.
     *
     * @param instance the configuration instance
     * @param file     the target file
     * @throws Exception if saving fails
     */
    public static void save(ConfigurationFile instance, File file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        if (instance.getClass().isAnnotationPresent(Header.class))
            yaml.options().setHeader(List.of(instance.getClass().getAnnotation(Header.class).value()));

        runHooks(instance, PreLoad.class);
        writeSection(yaml, instance);

        yaml.options().width(250);
        yaml.save(file);
        FILE_CACHE.put(file.getAbsolutePath(), new FileCacheEntry(file.lastModified(), file.length(), yaml));
    }

    /**
     * Creates and saves a default configuration file based on the Java class structure.
     *
     * @param clazz the configuration class
     * @param file  the target file
     * @return the newly created configuration instance
     * @throws Exception if creation fails
     */
    public static ConfigurationFile saveDefault(Class<? extends ConfigurationFile> clazz, File file) throws Exception {
        createIfNotExist(file);
        ConfigurationFile instance = createInstance(clazz);
        instance.setFile(file);

        processTemplates(instance);
        runHooks(instance, PreLoad.class);

        save(instance, file);
        return load(clazz, file);
    }

    private static YamlConfiguration loadYaml(File file) throws Exception {
        String path = file.getAbsolutePath();
        FileCacheEntry cached = FILE_CACHE.get(path);
        if (cached != null && cached.isFresh(file)) return cached.yaml;

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            logError("YAML Syntax Error in '" + file.getName() + "': " + e.getMessage(), e);
            throw e;
        }
        FILE_CACHE.put(path, new FileCacheEntry(file.lastModified(), file.length(), yaml));
        return yaml;
    }

    private static void populateInstance(ConfigurationFile instance, YamlConfiguration yaml, File file) throws Exception {
        processTemplates(instance);
        runHooks(instance, PreLoad.class);

        AtomicBoolean isDirty = new AtomicBoolean(false);
        try {
            syncSection(yaml, instance, isDirty);
        } catch (Exception e) {
            logError("Structure parse failed for '" + file.getName() + "': " + e.getMessage(), e);
            throw e;
        }

        if (isDirty.get()) {
            save(instance, file);
        }

        runHooks(instance, PostLoad.class);
    }

    /**
     * Processes @Template annotations to auto-populate Maps with default values.
     */
    @SuppressWarnings("unchecked")
    private static void processTemplates(Object instance) throws Exception {
        for (FieldMeta meta : getCachedMeta(instance.getClass())) {
            Field field = meta.field();
            if (Map.class.isAssignableFrom(field.getType())) {
                Type genericType = field.getGenericType();
                Class<?> valueType = getGenericType(genericType, 1);
                if (valueType.isAnnotationPresent(Template.class)) {
                    Template template = valueType.getAnnotation(Template.class);
                    String defaultKey = template.name();

                    Map<String, Object> map = (Map<String, Object>) meta.get(instance);
                    if (map == null) {
                        map = new HashMap<>();
                        meta.set(instance, map);
                    }

                    boolean shouldAddDefault = false;
                    switch (template.value()) {
                        case FORCE -> shouldAddDefault = !map.containsKey(defaultKey);
                        case SMART -> shouldAddDefault = map.isEmpty();
                        case STRICT -> shouldAddDefault = false;
                    }
                    if (shouldAddDefault) {
                        try {
                            map.put(defaultKey, createInstance(valueType));
                        } catch (Exception e) {
                            logWarn("Failed to create template for " + valueType.getSimpleName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Synchronizes the YAML section with the Java object.
     * Handles reading values, writing defaults, and recursive parsing.
     */
    @SuppressWarnings("unchecked")
    private static void syncSection(ConfigurationSection section, ConfigurationPart obj, AtomicBoolean isDirty) throws Exception {
        List<FieldMeta> metas = getCachedMeta(obj.getClass());
        Set<String> validKeys = new HashSet<>();

        for (FieldMeta meta : metas) {
            String key = meta.key();
            validKeys.add(key);
            Object defaultVal = meta.get(obj);

            if (!section.contains(key)) {
                writeField(section, key, defaultVal, meta);
                isDirty.set(true);
                continue;
            }

            if (meta.hasComment()) section.setComments(key, List.of(meta.getAnnotation(Comment.class).value()));
            if (meta.hasInline()) section.setInlineComments(key, List.of(meta.getAnnotation(Inline.class).value()));

            try {
                if (ConfigurationPart.class.isAssignableFrom(meta.field.getType())) {
                    ConfigurationPart part = (ConfigurationPart) defaultVal;
                    if (part == null) part = createInstance((Class<? extends ConfigurationPart>) meta.field.getType());

                    ConfigurationSection sub = section.getConfigurationSection(key);
                    if (sub == null) sub = section.createSection(key);

                    syncSection(sub, part, isDirty);
                    meta.set(obj, part);
                    continue;
                }

                Object loadedVal = deserialize(section.get(key), meta.field.getType(), meta.field.getGenericType());

                if (meta.hasCheck()) loadedVal = runCheck(meta, loadedVal);

                meta.set(obj, loadedVal);
            } catch (Exception e) {
                logWarn("Failed to load '" + key + "': " + e.getMessage());
            }
        }

        for (String yamlKey : section.getKeys(false)) {
            if (!validKeys.contains(yamlKey)) {
                section.set(yamlKey, null);
                isDirty.set(true);
            }
        }
    }

    private static void writeSection(ConfigurationSection section, ConfigurationPart obj) throws Exception {
        for (FieldMeta meta : getCachedMeta(obj.getClass())) writeField(section, meta.key(), meta.get(obj), meta);
    }

    private static void writeField(ConfigurationSection section, String key, Object val, FieldMeta meta) throws Exception {
        if (val == null) return;

        if (val instanceof ConfigurationPart part) {
            ConfigurationSection sub = section.createSection(key);
            writeSection(sub, part);
        }
        else if (val instanceof Map<?, ?> map) {
            ConfigurationSection sub = section.createSection(key);
            writeMap(sub, map);
        }
        else {
            section.set(key, serialize(val));
        }

        if (meta.hasComment()) section.setComments(key, List.of(meta.getAnnotation(Comment.class).value()));
        if (meta.hasInline()) section.setInlineComments(key, List.of(meta.getAnnotation(Inline.class).value()));
    }

    private static void writeMap(ConfigurationSection section, Map<?, ?> map) throws Exception {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object keyObj = serialize(entry.getKey());

            if (keyObj instanceof String k) {
                Object serializedVal = serialize(entry.getValue());
                if (serializedVal instanceof Map<?, ?> subMap) {
                    ConfigurationSection sub = section.createSection(k);
                    writeMap(sub, subMap);
                } else {
                    section.set(k, serializedVal);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object serialize(Object val) throws Exception {
        if (val == null) return null;
        Class<?> type = val.getClass();

        if (ADAPTERS.containsKey(type)) return ((TypeAdapter<Object>) ADAPTERS.get(type)).serialize(val);

        if (type.isRecord()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (RecordComponent rc : type.getRecordComponents())
                map.put(camelToKebab(rc.getName()), serialize(rc.getAccessor().invoke(val)));
            return map;
        }

        return switch (val) {
            case ConfigurationPart part -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (FieldMeta meta : getCachedMeta(part.getClass())) map.put(meta.key(), serialize(meta.get(part)));
                yield map;
            }
            case Map<?, ?> map -> {
                Map<String, Object> newMap = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet())
                    if (e.getKey() instanceof String k) newMap.put(k, serialize(e.getValue()));
                yield newMap;
            }
            case Collection<?> col -> {
                List<Object> list = new ArrayList<>();
                for (Object o : col) list.add(serialize(o));
                yield list;
            }
            case Enum<?> e -> e.name();
            case UUID uuid -> uuid.toString();
            case ConfigurationSerializable serializable -> serializable;
            default -> {
                if (val instanceof Number || val instanceof Boolean || val instanceof String || val instanceof Character) {
                    yield val;
                }
                if (hasToString(type)) {
                    yield val.toString();
                }
                yield val;
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object deserialize(Object raw, Class<?> type, Type genericType) throws Exception {
        if (raw == null) return null;
        if (ADAPTERS.containsKey(type)) return ADAPTERS.get(type).deserialize(raw);

        if (type.isRecord()) {
            Map<String, Object> map = (raw instanceof ConfigurationSection cs) ? cs.getValues(false) : (Map) raw;
            RecordComponent[] rcs = type.getRecordComponents();
            Object[] args = new Object[rcs.length];
            Class<?>[] types = new Class<?>[rcs.length];
            for (int i = 0; i < rcs.length; i++) {
                types[i] = rcs[i].getType();
                Object val = deserialize(map.get(camelToKebab(rcs[i].getName())), rcs[i].getType(), rcs[i].getGenericType());
                args[i] = (val == null && types[i].isPrimitive()) ? getPrimitiveDefault(types[i]) : val;
            }
            Constructor<?> c = type.getDeclaredConstructor(types);
            c.setAccessible(true);
            return c.newInstance(args);
        }

        if (ConfigurationPart.class.isAssignableFrom(type)) {
            ConfigurationPart inst = createInstance((Class<? extends ConfigurationPart>) type);
            ConfigurationSection tmp = new MemoryConfiguration();
            if (raw instanceof ConfigurationSection cs) tmp = cs;
            else if (raw instanceof Map map) for (Object k : map.keySet()) tmp.set(k.toString(), map.get(k));

            syncSection(tmp, inst, new AtomicBoolean());
            return inst;
        }

        if (Map.class.isAssignableFrom(type)) {
            Map<Object, Object> map = new LinkedHashMap<>();

            Class<?> kType = getGenericType(genericType, 0);
            Class<?> vType = getGenericType(genericType, 1);

            if (raw instanceof ConfigurationSection cs) {
                for (String k : cs.getKeys(false)) {
                    Object val = cs.get(k);
                    map.put(convertPrimitive(k, kType), deserialize(val, vType, vType));
                }
            } else if (raw instanceof Map<?, ?> rawMap) {
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    String k = entry.getKey().toString();
                    Object val = entry.getValue();
                    map.put(convertPrimitive(k, kType), deserialize(val, vType, vType));
                }
            } else {
                return raw;
            }
            return map;
        }

        if (List.class.isAssignableFrom(type) && raw instanceof List<?> list) {
            List<Object> newList = new ArrayList<>();
            Class<?> iType = getGenericType(genericType, 0);
            for (Object o : list) newList.add(deserialize(o, iType, iType));
            return newList;
        }

        if (ConfigurationSerializable.class.isAssignableFrom(type)) {
            if (type.isInstance(raw)) return raw;
            if (raw instanceof Map map) {
                try {
                    return ConfigurationSerialization.deserializeObject(map, (Class<? extends ConfigurationSerializable>) type);
                } catch (Exception e) {
                    return ConfigurationSerialization.deserializeObject(map);
                }
            }
        }
        return convertPrimitive(raw, type);
    }

    @SuppressWarnings("unchecked")
    private static Object runCheck(FieldMeta meta, Object val) {
        Check annotation = meta.getAnnotation(Check.class);
        try {
            if (annotation.cls() != void.class && !annotation.method().isEmpty()) {
                String key = annotation.cls().getName() + "#" + annotation.method();
                Method m = METHOD_CACHE.computeIfAbsent(key, k -> {
                    try {
                        for (Method me : annotation.cls().getDeclaredMethods())
                            if (me.getName().equals(annotation.method()) && me.getParameterCount() == 1) {
                                me.setAccessible(true);
                                return me;
                            }
                        throw new RuntimeException("Method not found");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                return Modifier.isStatic(m.getModifiers()) ? m.invoke(null, val) : m.invoke(createInstance(annotation.cls()), val);
            }
            if (annotation.value() != Check.Validator.class) {
                Check.Validator<Object> v = (Check.Validator<Object>) VALIDATOR_CACHE.computeIfAbsent(annotation.value(), k -> {
                    try {
                        Constructor<?> c = k.getDeclaredConstructor();
                        c.setAccessible(true);
                        return (Check.Validator<?>) c.newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                return v.validate(val);
            }
        } catch (Exception e) {
            logError("Validation failed: " + e.getMessage(), e);
        }
        return val;
    }

    private static List<FieldMeta> getCachedMeta(Class<?> clazz) {
        return META_CACHE.computeIfAbsent(clazz, k -> {
            List<FieldMeta> list = new ArrayList<>();
            for (Field f : k.getFields()) {
                if (f.isAnnotationPresent(Ignore.class) || Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()) || Modifier.isFinal(f.getModifiers()))
                    continue;
                f.setAccessible(true);
                list.add(new FieldMeta(f, camelToKebab(f.getName()), f.isAnnotationPresent(Check.class), f.isAnnotationPresent(Comment.class), f.isAnnotationPresent(Inline.class)));
            }
            return list;
        });
    }

    private static void runHooks(Object inst, Class<? extends Annotation> anno) throws Exception {
        List<Method> ms = new ArrayList<>();
        for (Method m : inst.getClass().getMethods()) if (m.isAnnotationPresent(anno)) ms.add(m);
        ms.sort(Comparator.comparingInt(m -> {
            try {
                return (int) anno.getMethod("priority").invoke(m.getAnnotation(anno));
            } catch (Exception e) {
                return 0;
            }
        }));
        for (Method m : ms) m.invoke(inst);
    }

    private static <T> T createInstance(Class<T> c) throws Exception {
        try {
            return c.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Missing no-args constructor: " + c.getName());
        }
    }

    private static void createIfNotExist(File f) throws Exception {
        if (!f.exists()) {
            if (f.getParentFile() != null && !f.getParentFile().exists()) {
                if (!f.getParentFile().mkdirs()) {
                    throw new IOException("Failed to create directory: " + f.getParentFile().getAbsolutePath());
                }
            }
            if (!f.createNewFile()) {
                throw new IOException("Failed to create file: " + f.getAbsolutePath());
            }
        }
    }

    private static boolean hasToString(Class<?> type) {
        return TO_STRING_CACHE.computeIfAbsent(type, k -> {
            try {
                return k.getMethod("toString").getDeclaringClass() != Object.class;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static String camelToKebab(String s) {
        return CAMEL_PATTERN.matcher(s).replaceAll("$1-$2").toLowerCase();
    }

    private static Class<?> getGenericType(Type t, int i) {
        if (!(t instanceof ParameterizedType pt)) return Object.class;
        Type arg = pt.getActualTypeArguments()[i];
        if (arg instanceof Class<?> c) return c;
        if (arg instanceof ParameterizedType ipt) return (Class<?>) ipt.getRawType();
        return Object.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertPrimitive(Object raw, Class<?> type) {
        if (type.isEnum() && raw instanceof String s) {
            try {
                return Enum.valueOf((Class<Enum>) type, s);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid Enum: " + s);
            }
        }
        if (type == UUID.class && raw instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid UUID: " + s);
            }
        }
        if (Number.class.isAssignableFrom(primitiveToWrapper(type)) || type.isPrimitive()) {
            String s = raw.toString();
            try {
                if (type == int.class || type == Integer.class) return (int) Double.parseDouble(s);
                if (type == double.class || type == Double.class) return Double.parseDouble(s);
                if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(s);
                if (type == long.class || type == Long.class) return (long) Double.parseDouble(s);
                if (type == float.class || type == Float.class) return Float.parseFloat(s);
            } catch (Exception ignored) {
            }
        }
        return raw;
    }

    private static Class<?> primitiveToWrapper(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        return type;
    }

    private static Object getPrimitiveDefault(Class<?> t) {
        if (t == int.class) return 0;
        if (t == boolean.class) return false;
        if (t == double.class) return 0.0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0.0f;
        return null;
    }

    private static void logError(String m, Throwable e) {
        if (LOGGER != null) LOGGER.error(m, e);
        else {
            System.err.println("[Config] [ERROR] " + m);
            if (e != null) e.printStackTrace();
        }
    }

    private static void logWarn(String m) {
        if (LOGGER != null) LOGGER.warn(m);
        else System.out.println("[Config] [WARN] " + m);
    }

    /**
     * Interface for custom type serialization logic.
     *
     * @param <T> the target type
     */
    public interface TypeAdapter<T> {
        Object serialize(T value);

        T deserialize(Object yamlValue);
    }

    private record FieldMeta(Field field, String key, boolean hasCheck, boolean hasComment, boolean hasInline) {
        Object get(Object instance) throws Exception {
            return field.get(instance);
        }

        void set(Object instance, Object val) throws Exception {
            field.set(instance, val);
        }

        <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
            return field.getAnnotation(annotationClass);
        }
    }

    private record FileCacheEntry(long lastModified, long size, YamlConfiguration yaml) {
        boolean isFresh(File file) {
            return file.lastModified() == lastModified && file.length() == size;
        }
    }
}

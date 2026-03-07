package gloomlib.configuration.core.util;

import com.google.common.base.CaseFormat;
import gloomlib.configuration.api.annotation.Check;
import gloomlib.configuration.api.annotation.Comment;
import gloomlib.configuration.api.annotation.Ignore;
import gloomlib.configuration.api.annotation.Inline;
import gloomlib.configuration.core.model.FieldMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache manager for configuration metadata and reflection results.
 */
public final class ConfigurationCache {

    private static final Map<Class<?>, List<FieldMeta>> META_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Check.Validator<?>> VALIDATOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> TO_STRING_CACHE = new ConcurrentHashMap<>();

    private ConfigurationCache() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gets cached field metadata for a class.
     *
     * @param clazz the class
     * @return list of field metadata
     */
    public static List<FieldMeta> getCachedMeta(Class<?> clazz) {
        return META_CACHE.computeIfAbsent(clazz, k -> {
            List<FieldMeta> list = new ArrayList<>();
            for (Field f : k.getFields()) {
                if (f.isAnnotationPresent(Ignore.class)
                        || Modifier.isStatic(f.getModifiers())
                        || Modifier.isTransient(f.getModifiers())
                        || Modifier.isFinal(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                list.add(new FieldMeta(
                        f,
                        CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, f.getName()),
                        f.isAnnotationPresent(Check.class),
                        f.isAnnotationPresent(Comment.class),
                        f.isAnnotationPresent(Inline.class)
                ));
            }
            return list;
        });
    }

    /**
     * Checks if a class has a custom toString() method.
     *
     * @param type the class type
     * @return true if custom toString exists
     */
    public static boolean hasToString(Class<?> type) {
        return TO_STRING_CACHE.computeIfAbsent(type, k -> {
            try {
                return k.getMethod("toString").getDeclaringClass() != Object.class;
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * Gets or creates a cached validator instance.
     *
     * @param validatorClass the validator class
     * @return the validator instance
     */
    @SuppressWarnings("rawtypes")
    public static Check.Validator getCachedValidator(Class<? extends Check.Validator> validatorClass) {
        return VALIDATOR_CACHE.computeIfAbsent(validatorClass, k -> {
            try {
                var constructor = k.getDeclaredConstructor();
                constructor.setAccessible(true);
                return (Check.Validator<?>) constructor.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create validator: " + k.getName(), e);
            }
        });
    }

    /**
     * Gets or caches a method by key.
     *
     * @param key     the cache key
     * @param factory the supplier to create the method if not cached
     * @return the method
     */
    public static Method getCachedMethod(String key, Supplier<Method> factory) {
        return METHOD_CACHE.computeIfAbsent(key, k -> factory.get());
    }
}



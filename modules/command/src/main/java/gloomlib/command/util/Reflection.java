package gloomlib.command.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal reflection utility with caching.
 *
 * <p>Caches Field/Method lookups to avoid repeated reflection overhead.</p>
 */
public final class Reflection {

    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private Reflection() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gets a field value from an object.
     *
     * @param obj       Object instance
     * @param fieldName Field name
     * @param <T>       Expected field type
     * @return Field value
     */
    @SuppressWarnings("unchecked")
    public static <T> T getField(Object obj, String fieldName) {
        try {
            Field field = getCachedField(obj.getClass(), fieldName);
            return (T) field.get(obj);
        } catch (Exception e) {
            throw new ReflectionException("Failed to get field: " + fieldName, e);
        }
    }

    /**
     * Sets a field value on an object.
     *
     * @param obj       Object instance
     * @param fieldName Field name
     * @param value     New value
     */
    public static void setField(Object obj, String fieldName, Object value) {
        try {
            Field field = getCachedField(obj.getClass(), fieldName);
            field.set(obj, value);
        } catch (Exception e) {
            throw new ReflectionException("Failed to set field: " + fieldName, e);
        }
    }

    /**
     * Invokes a method on an object.
     *
     * @param obj            Object instance
     * @param methodName     Method name
     * @param parameterTypes Parameter types
     * @param args           Method arguments
     * @param <T>            Expected return type
     * @return Method return value
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = getCachedMethod(obj.getClass(), methodName, parameterTypes);
            return (T) method.invoke(obj, args);
        } catch (Exception e) {
            throw new ReflectionException("Failed to invoke method: " + methodName, e);
        }
    }

    private static Field getCachedField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        String key = buildKey(clazz, fieldName);
        return FIELD_CACHE.computeIfAbsent(key, k -> {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Method getCachedMethod(Class<?> clazz, String methodName, Class<?>... paramTypes)
            throws NoSuchMethodException {
        String key = buildKey(clazz, methodName);
        return METHOD_CACHE.computeIfAbsent(key, k -> {
            try {
                Method method = clazz.getMethod(methodName, paramTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static String buildKey(Class<?> clazz, String name) {
        return clazz.getName().concat("#").concat(name);
    }

    /**
     * Reflection operation failed.
     */
    public static class ReflectionException extends RuntimeException {
        public ReflectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

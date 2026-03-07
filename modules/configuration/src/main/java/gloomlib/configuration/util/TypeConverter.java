package gloomlib.configuration.util;

import com.google.common.base.Defaults;
import com.google.common.primitives.Primitives;

import java.util.UUID;

/**
 * Utility class for type conversion operations.
 */
public final class TypeConverter {

    private TypeConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a raw value to a primitive or simple type.
     *
     * @param raw  the raw value
     * @param type the target type
     * @return the converted value
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object convertPrimitive(Object raw, Class<?> type) {
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
        if (Number.class.isAssignableFrom(Primitives.wrap(type)) || type.isPrimitive()) {
            String s = raw.toString();
            try {
                if (type == int.class || type == Integer.class) {
                    return (int) Double.parseDouble(s);
                }
                if (type == double.class || type == Double.class) {
                    return Double.parseDouble(s);
                }
                if (type == boolean.class || type == Boolean.class) {
                    return Boolean.parseBoolean(s);
                }
                if (type == long.class || type == Long.class) {
                    return (long) Double.parseDouble(s);
                }
                if (type == float.class || type == Float.class) {
                    return Float.parseFloat(s);
                }
            } catch (Exception ignored) {
                // Ignore parsing errors for numbers
            }
        }
        return raw;
    }

    /**
     * Gets the default value for a primitive type.
     * Delegates to Guava's {@link Defaults#defaultValue(Class)}.
     *
     * @param type the primitive type
     * @return the default value (0, false, etc.), or null for non-primitive types
     */
    public static Object getPrimitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        return Defaults.defaultValue(type);
    }
}
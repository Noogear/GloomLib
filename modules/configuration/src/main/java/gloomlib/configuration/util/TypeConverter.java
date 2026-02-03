package gloomlib.configuration.util;

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
        if (Number.class.isAssignableFrom(primitiveToWrapper(type)) || type.isPrimitive()) {
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
     * Converts a primitive type to its wrapper class.
     *
     * @param type the primitive type
     * @return the wrapper class
     */
    public static Class<?> primitiveToWrapper(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        return type;
    }

    /**
     * Gets the default value for a primitive type.
     *
     * @param type the primitive type
     * @return the default value (0, false, etc.)
     */
    public static Object getPrimitiveDefault(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        return null;
    }
}



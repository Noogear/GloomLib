package gloomlib.command.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Type conversion utility class.
 *
 * <p>
 * Provides utility methods for default value conversion, primitive type
 * conversion, etc.
 * </p>
 */
public final class TypeConverterUtils {

    /**
     * "self" placeholder, representing the command sender themselves
     */
    public static final String SELF_PLACEHOLDER = "self";

    private TypeConverterUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a string default value to the target type.
     *
     * @param defaultValue Default value string
     * @param type         Target type
     * @param sender       Command sender (for "self" placeholder)
     * @return Converted value, or empty if conversion fails
     */
    public static Optional<Object> convertDefault(String defaultValue, Class<?> type, CommandSender sender) {
        // Handle "self" placeholder
        if (SELF_PLACEHOLDER.equals(defaultValue) && Player.class.isAssignableFrom(type)) {
            return Optional.ofNullable(sender instanceof Player ? sender : null);
        }

        // String type returns directly
        if (type == String.class) {
            return Optional.of(defaultValue);
        }

        // Numeric type conversion
        try {
            if (type == Integer.class || type == int.class) {
                return Optional.of(Integer.parseInt(defaultValue));
            }
            if (type == Double.class || type == double.class) {
                return Optional.of(Double.parseDouble(defaultValue));
            }
            if (type == Float.class || type == float.class) {
                return Optional.of(Float.parseFloat(defaultValue));
            }
            if (type == Long.class || type == long.class) {
                return Optional.of(Long.parseLong(defaultValue));
            }
            if (type == Boolean.class || type == boolean.class) {
                return Optional.of(Boolean.parseBoolean(defaultValue));
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    /**
     * Checks if the type supports primitive conversion.
     *
     * @param type Type to check
     * @return true if supported
     */
    public static boolean supportsPrimitiveConversion(Class<?> type) {
        return type == String.class
                || type == Integer.class || type == int.class
                || type == Double.class || type == double.class
                || type == Float.class || type == float.class
                || type == Long.class || type == long.class
                || type == Boolean.class || type == boolean.class;
    }
}

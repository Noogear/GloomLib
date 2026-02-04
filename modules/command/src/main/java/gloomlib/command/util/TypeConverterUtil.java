package gloomlib.command.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * 类型转换工具类。
 *
 * <p>
 * 提供默认值转换、基本类型转换等工具方法。
 * </p>
 */
public final class TypeConverterUtil {

    /** "self" 占位符，用于表示命令发送者自己 */
    public static final String SELF_PLACEHOLDER = "self";

    private TypeConverterUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 将字符串默认值转换为目标类型。
     *
     * @param defaultValue 默认值字符串
     * @param type         目标类型
     * @param sender       命令发送者（用于 "self" 占位符）
     * @return 转换后的值，如果无法转换则返回 empty
     */
    public static Optional<Object> convertDefault(String defaultValue, Class<?> type, CommandSender sender) {
        // 处理 "self" 占位符
        if (SELF_PLACEHOLDER.equals(defaultValue) && Player.class.isAssignableFrom(type)) {
            return Optional.ofNullable(sender instanceof Player ? sender : null);
        }

        // String 类型直接返回
        if (type == String.class) {
            return Optional.of(defaultValue);
        }

        // 数值类型转换
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
     * 检查类型是否支持基本类型转换。
     *
     * @param type 类型
     * @return true 如果支持
     */
    public static boolean supportsPrimitiveConversion(Class<?> type) {
        return type == String.class ||
               type == Integer.class || type == int.class ||
               type == Double.class || type == double.class ||
               type == Float.class || type == float.class ||
               type == Long.class || type == long.class ||
               type == Boolean.class || type == boolean.class;
    }
}

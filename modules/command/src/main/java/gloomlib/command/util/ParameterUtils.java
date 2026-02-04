package gloomlib.command.util;

import gloomlib.command.annotation.Arg;
import gloomlib.command.context.AsyncContext;
import gloomlib.command.context.GloomCommandContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Parameter;

/**
 * 参数处理工具类。
 *
 * <p>
 * 提供参数名称解析、参数索引计算等工具方法。
 * </p>
 */
public final class ParameterUtils {

    private ParameterUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 获取参数名称。
     * 
     * <p>
     * 优先使用 @Arg 注解指定的名称，否则使用参数的实际名称。
     * </p>
     *
     * @param param 方法参数
     * @return 参数名称
     */
    public static String getParameterName(Parameter param) {
        Arg arg = param.getAnnotation(Arg.class);
        if (arg != null && !arg.value().isEmpty()) {
            return arg.value();
        }
        return param.getName();
    }

    /**
     * 获取命令参数的起始索引。
     * 
     * <p>
     * 跳过特殊参数（CommandSender, Player, GloomCommandContext, AsyncContext），
     * 这些参数会被自动注入，不需要从命令行解析。
     * </p>
     *
     * @param parameters 方法参数数组
     * @return 第一个需要解析的参数的索引（0-based）
     */
    public static int getStartParameterIndex(Parameter[] parameters) {
        if (parameters.length == 0) {
            return 0;
        }
        
        Class<?> firstType = parameters[0].getType();
        if (isSpecialParameter(firstType)) {
            return 1;
        }
        
        return 0;
    }

    /**
     * 检查参数类型是否为特殊参数（会被自动注入）。
     *
     * @param type 参数类型
     * @return true 如果是特殊参数
     */
    public static boolean isSpecialParameter(Class<?> type) {
        return CommandSender.class.isAssignableFrom(type) ||
               Player.class.isAssignableFrom(type) ||
               GloomCommandContext.class.isAssignableFrom(type) ||
               AsyncContext.class.isAssignableFrom(type);
    }
}

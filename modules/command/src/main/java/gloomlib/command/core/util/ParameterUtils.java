package gloomlib.command.core.util;

import gloomlib.command.api.annotation.Arg;
import gloomlib.command.api.context.AsyncContext;
import gloomlib.command.api.context.GloomCommandContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Parameter;

/**
 * Parameter utility class.
 *
 * <p>
 * Provides utility methods for parameter name resolution, index calculation,
 * etc.
 * </p>
 */
public final class ParameterUtils {

    private ParameterUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gets parameter name.
     *
     * <p>
     * Prioritizes name specified by @Arg annotation, otherwise uses actual
     * parameter name.
     * </p>
     *
     * @param param Method parameter
     * @return Parameter name
     */
    public static String getParameterName(Parameter param) {
        Arg arg = param.getAnnotation(Arg.class);
        if (arg != null && !arg.value().isEmpty()) {
            return arg.value();
        }
        return param.getName();
    }

    /**
     * Gets the starting index of command parameters.
     *
     * <p>
     * Skips special parameters (CommandSender, Player, GloomCommandContext,
     * AsyncContext),
     * which are automatically injected and do not need to be parsed from command
     * line.
     * </p>
     *
     * @param parameters Method parameter array
     * @return Index of the first parameter to parse (0-based)
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
     * Checks if parameter type is a special parameter (automatically injected).
     *
     * @param type Parameter type
     * @return true if it is a special parameter
     */
    public static boolean isSpecialParameter(Class<?> type) {
        return CommandSender.class.isAssignableFrom(type) ||
                Player.class.isAssignableFrom(type) ||
                GloomCommandContext.class.isAssignableFrom(type) ||
                AsyncContext.class.isAssignableFrom(type);
    }
}

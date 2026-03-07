package gloomlib.command.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts the command to be usable only by the console.
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * &#64;SubCommand("maintenance")
 * @ConsoleOnly
 * public void maintenance(CommandSender sender) {
 *     // Maintenance command only executable by console
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConsoleOnly {

    /**
     * Error message shown when executed by a player.
     * Supports MiniMessage format.
     *
     * @return error message
     */
    String message() default "<red><translate:commands.help.failed>";
}

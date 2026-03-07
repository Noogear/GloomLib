package gloomlib.command.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts the command to be usable only by players.
 *
 * <p>
 * If console or other non-player entity tries to execute, an error message will
 * be shown.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * &#64;Usage
 * @PlayerOnly(message = "<red>This command is for players only!</red>")
 * public void execute(Player player) {
 *     // The first parameter type is Player, implicitly meaning only players can
 *     // execute
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlayerOnly {

    /**
     * Error message shown when executed by non-players.
     * Supports MiniMessage format.
     *
     * @return error message
     */
    String message() default "<red><translate:permissions.requires.player>";
}

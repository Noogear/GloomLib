package gloomlib.command.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the required permission for a command or subcommand.
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * &#64;Command("gamemode")
 * &#64;Permission("server.gamemode")
 * public class GameModeCommand {
 *     // All subcommands inherit this permission check
 * }
 *
 * &#64;SubCommand("set")
 * @Permission(value = "server.gamemode.set", mode = PermissionMode.REQUIRE)
 * public void setMode(Player player, @Arg GameMode mode) {
 *     // Additional permission required
 * }
 * }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {

    /**
     * Permission node name.
     *
     * @return permission node
     */
    String value();

    /**
     * Permission check mode.
     *
     * @return check mode
     */
    PermissionMode mode() default PermissionMode.REQUIRE;

    /**
     * Permission check mode enumeration.
     */
    enum PermissionMode {
        /**
         * Must have the specified permission
         */
        REQUIRE,
        /**
         * Must be an OP
         */
        OP,
        /**
         * Anyone can use
         */
        ANY
    }
}

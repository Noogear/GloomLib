package gloomlib.command.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a command class, or defines an independent root command
 * alias on a method.
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>{@code
 * @Command("gamemode")
 * @Permission("server.gamemode")
 * public class GameModeCommand {
 *     // ...
 * }
 * }</pre>
 *
 * <p>
 * Can also be used on methods to create independent root command aliases:
 * </p>
 *
 * <pre>{@code
 * @Command("gmc")
 * public void creative(Player player) {
 *     // This creates /gmc as an independent command
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {

    /**
     * The command name.
     *
     * @return command name (without / prefix)
     */
    String value();

    /**
     * List of command aliases.
     *
     * @return alias array
     */
    String[] aliases() default {};
}

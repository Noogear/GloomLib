package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the main execution entry point of a command (no
 * subcommand).
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>{@code
 * @Usage
 * public void execute(Player player, @Arg GameMode mode) {
 *     // /gamemode <mode>
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Usage {
    // Marker annotation, no attributes required
}

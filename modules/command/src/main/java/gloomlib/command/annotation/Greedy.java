package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an argument as greedy, consuming all remaining input.
 *
 * <p>
 * Greedy arguments must be the last argument of the method.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>{@code
 * @SubCommand("broadcast")
 * public void broadcast(CommandSender sender, @Arg @Greedy String message) {
 *     // /broadcast Hello world, this is a long message
 *     // message = "Hello world, this is a long message"
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Greedy {
    // Marker annotation, no attributes required
}

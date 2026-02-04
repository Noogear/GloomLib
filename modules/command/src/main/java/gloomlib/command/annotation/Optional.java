package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an argument as optional.
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>{@code
 * public void execute(Player sender, @Arg @Optional Player target) {
 *     // target argument is optional
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Optional {
    // Marker annotation, no attributes required
}

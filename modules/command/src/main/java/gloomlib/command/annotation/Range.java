package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines range constraints for numeric arguments.
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("give")
 * public void give(CommandSender sender,
 *         &#64;Arg Player target,
 *         @Arg @Range(min = 1, max = 64) int amount) {
 *     // amount must be between 1 and 64
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Range {

    /**
     * Minimum value (inclusive).
     *
     * @return minimum value
     */
    double min() default Double.MIN_VALUE;

    /**
     * Maximum value (inclusive).
     *
     * @return maximum value
     */
    double max() default Double.MAX_VALUE;
}

package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a command argument.
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>{@code
 * public void execute(Player sender, @Arg("mode") GameMode mode) {
 *     // Argument name is "mode"
 * }
 *
 * public void execute(Player sender, @Arg Player target) {
 *     // Argument name uses parameter name "target"
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Arg {

    /**
     * The argument name.
     * If empty, the method parameter variable name will be used.
     *
     * @return argument name
     */
    String value() default "";
}

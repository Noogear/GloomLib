package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the default value for an optional argument.
 *
 * <p>
 * Supported special values:
 * </p>
 * <ul>
 * <li>{@code "self"} - The current executor (Player arguments only)</li>
 * <li>{@code "console"} - The console</li>
 * <li>Literal values - such as {@code "0"}, {@code "true"}</li>
 * <li>Configuration expressions - such as
 * {@code "${config.default.value}"}</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>{@code
 * public void execute(Player sender,
 *         @Arg @Optional @Default("self") Player target) {
 *     // If target is not specified, defaults to the executor themselves
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Default {

    /**
     * Default value expression.
     *
     * @return default value
     */
    String value();
}

package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a command switch parameter (--switch).
 *
 * <p>
 * Switch parameters are boolean types, providing the name implicitly means
 * true, omission means false.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("delete")
 * public void delete(CommandSender sender,
 *         @Arg String name,
 *         &#64;Switch("confirm") boolean confirm) {
 *     if (!confirm) {
 *         sender.sendMessage("Please add --confirm to confirm deletion");
 *         return;
 *     }
 *     // Execute deletion
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Switch {

    /**
     * Switch name (format: --name).
     *
     * @return switch name
     */
    String value();

    /**
     * Short switch name (format: -n).
     * If empty, only long format can be used.
     *
     * @return short switch name
     */
    String shorthand() default "";
}

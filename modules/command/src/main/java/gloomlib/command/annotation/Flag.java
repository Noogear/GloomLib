package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a command flag argument (--flag value).
 *
 * <p>
 * Flag arguments can appear anywhere in the command arguments, in the format
 * {@code --name value} or {@code -n value}.
 * </p>
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
 *         &#64;Arg int amount,
 *         @Flag(value = "reason", shorthand = "r") String reason) {
 *     // /give <target> <amount> --reason "Some reason"
 *     // /give <target> <amount> -r "Some reason"
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Flag {

    /**
     * Flag name (long format: --name).
     *
     * @return flag name
     */
    String value();

    /**
     * Short flag name (short format: -n).
     * If empty, only long format can be used.
     *
     * @return short flag name
     */
    String shorthand() default "";
}

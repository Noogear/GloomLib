package gloomlib.command.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Defines command cooldown time.
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * &#64;SubCommand("teleport")
 * @Cooldown(value = 30, unit = TimeUnit.SECONDS, bypassPermission = "server.tp.bypass")
 * public void teleport(Player player, @Arg Player target) {
 *     // 30 seconds cooldown, bypassable with permission server.tp.bypass
 * }
 * }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cooldown {

    /**
     * Cooldown duration.
     *
     * @return cooldown duration
     */
    long value();

    /**
     * Time unit.
     *
     * @return time unit, default is SECONDS
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Permission required to bypass cooldown.
     * If empty, cannot be bypassed.
     *
     * @return bypass permission node
     */
    String bypassPermission() default "";

    /**
     * Message shown when in cooldown.
     * Supports placeholder: {remaining} - remaining time
     *
     * @return cooldown message
     */
    String message() default "";
}

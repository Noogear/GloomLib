package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines execution conditions for a command.
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>{@code
 * @SubCommand("reward")
 * @Condition("dailyReward")
 * public void claimReward(Player player) {
 *     // Executed only when dailyReward condition is met
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Condition {

    /**
     * Condition name (registered in CommandConditionRegistry).
     *
     * @return condition name
     */
    String value();
}

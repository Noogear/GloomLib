package gloomlib.command.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds one or more named conditions to a command handler method.
 *
 * <p>
 * All specified conditions must pass before the method is invoked.
 * Conditions are evaluated in declaration order; the first failure stops evaluation
 * and sends the failure message to the command sender.
 * </p>
 *
 * <p>
 * Conditions must be registered via {@code GloomCommand.registerCondition()} before
 * the plugin enables commands.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @SubCommand("buy")
 * @Condition({"has-money", "shop-open"})
 * public void buy(Player player, int amount) { ... }
 * }</pre>
 *
 * @see gloomlib.command.api.condition.CommandCondition
 * @see gloomlib.command.api.condition.CommandConditionRegistry
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Condition {

    /**
     * Names of conditions that must all pass.
     * Evaluated in order; first failure halts the chain.
     */
    String[] value();
}

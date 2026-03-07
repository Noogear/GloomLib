package gloomlib.command.api.condition;

import gloomlib.command.api.context.GloomCommandContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Command pre-execution condition interface.
 *
 * <p>
 * Conditions allow flexible, reusable pre-execution checks beyond simple permission nodes.
 * Unlike {@code @Permission} which only checks Bukkit permissions, conditions can inspect
 * any runtime state — economy balance, active events, player statistics, etc. — and
 * return a rich Component failure message.
 * </p>
 *
 * <h2>Registration</h2>
 * <pre>{@code
 * gloom.registerCondition("has-money", ctx ->
 *     economy.getBalance(ctx.getPlayer()) >= 100
 *         ? ConditionResult.pass()
 *         : ConditionResult.fail("<red>You need at least $100 to run this command.")
 * );
 * }</pre>
 *
 * <h2>Usage on command methods</h2>
 * <pre>{@code
 * @SubCommand("buy")
 * @Condition("has-money")
 * public void buy(Player player) { ... }
 * }</pre>
 *
 * <p>Multiple conditions are evaluated in declaration order; the first failure stops execution.</p>
 */
@FunctionalInterface
public interface CommandCondition {

    /**
     * Tests whether the command may proceed.
     *
     * @param context Command context
     * @return {@link ConditionResult#pass()} to allow execution,
     *         {@link ConditionResult#fail(Component)} to block it
     */
    @NotNull
    ConditionResult test(@NotNull GloomCommandContext context);

    /**
     * The result of a condition check.
     *
     * @param passed         Whether the condition passed
     * @param failureMessage Message sent to the player on failure (null when passed)
     */
    record ConditionResult(boolean passed, @Nullable Component failureMessage) {

        /**
         * Creates a passing result (execution is allowed).
         *
         * @return passing result
         */
        public static ConditionResult pass() {
            return new ConditionResult(true, null);
        }

        /**
         * Creates a failing result with an Adventure Component message.
         *
         * @param message Failure message sent to the player
         * @return failing result
         */
        public static ConditionResult fail(@NotNull Component message) {
            return new ConditionResult(false, message);
        }

        /**
         * Creates a failing result with a MiniMessage-formatted string.
         *
         * @param miniMessage MiniMessage format string (e.g. {@code "<red>Not enough money"})
         * @return failing result
         */
        public static ConditionResult fail(@NotNull String miniMessage) {
            return new ConditionResult(false, MiniMessage.miniMessage().deserialize(miniMessage));
        }
    }
}

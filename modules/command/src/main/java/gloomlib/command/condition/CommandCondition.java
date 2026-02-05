package gloomlib.command.condition;

import gloomlib.command.context.GloomCommandContext;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Command Condition Interface.
 *
 * <p>
 * Defines prerequisites for command execution.
 * </p>
 */
@FunctionalInterface
public interface CommandCondition {

    /**
     * Checks if the condition is met.
     *
     * @param context Command context
     * @return Condition check result
     */
    ConditionResult check(GloomCommandContext context);

    /**
     * Condition check result.
     */
    record ConditionResult(boolean passed, @Nullable Component failureMessage) {

        /**
         * Condition passed
         */
        public static final ConditionResult PASS = new ConditionResult(true, null);

        /**
         * Creates a passed result.
         */
        public static ConditionResult pass() {
            return PASS;
        }

        /**
         * Creates a failed result.
         *
         * @param message Failure message
         */
        public static ConditionResult fail(Component message) {
            return new ConditionResult(false, message);
        }

        /**
         * Creates a failed result (plain text).
         *
         * @param message Failure message
         */
        public static ConditionResult fail(String message) {
            return new ConditionResult(false, Component.text(message));
        }
    }
}

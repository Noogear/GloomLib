package gloomlib.command.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a method-level exception handler.
 *
 * <p>
 * This method will be invoked when a specified type of exception is thrown
 * during command execution.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * {
 *     &#64;code
 *     &#64;Command("rank")
 *     public class RankCommand {
 *
 *         &#64;OnError(RankNotFoundException.class)
 *         public void handleRankNotFound(CommandContext context, RankNotFoundException e) {
 *             context.getSender().sendMessage(Component.text("Rank " + e.getRankName() + " does not exist!"));
 *         }
 *
 *         @OnError({ IllegalArgumentException.class, NumberFormatException.class })
 *         public void handleInvalidArgument(CommandContext context, Exception e) {
 *             context.getSender().sendMessage(Component.text("Invalid argument: " + e.getMessage()));
 *         }
 *     }
 * }}
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnError {

    /**
     * Exception types to handle.
     *
     * @return exception type array
     */
    Class<? extends Throwable>[] value();
}

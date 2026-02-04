package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a command method to be executed asynchronously.
 *
 * <p>
 * Useful for commands that require IO operations like database queries or
 * network requests.
 * </p>
 *
 * <p>
 * <b>Note:</b>
 * </p>
 * <ul>
 * <li>Most Bukkit APIs cannot be called directly in async methods (not
 * thread-safe)</li>
 * <li>Message sending will automatically switch back to the main thread</li>
 * <li>Executed using Paper AsyncScheduler</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("stats")
 * @Async
 * public void showStats(Player player) {
 *     // Execute database query in async thread
 *     Map<String, Object> stats = database.queryPlayerStats(player);
 *
 *     // Sending message will automatically switch back to main thread
 *     player.sendMessage(Component.text("Query complete!"));
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Async {
    // Marker annotation, no attributes required
}

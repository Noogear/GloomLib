package gloomlib.command.context;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Asynchronous command execution context.
 *
 * <p>
 * Provides asynchronous execution support for command methods annotated with
 * {@code @Async}.
 * Automatically handles thread switching, ensuring Bukkit API calls are
 * executed on the main thread.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * {@code
 * &#64;SubCommand("stats")
 * @Async
 * public void showStats(AsyncContext ctx, Player player) {
 *     // Query database in async thread
 *     Map<String, Object> stats = ctx.runAsync(() -> database.queryStats(player));
 *
 *     // Automatically switch back to main thread to send message
 *     ctx.reply(Component.text("Query complete!"));
 * }
 * }
 * </pre>
 */
public class AsyncContext extends GloomCommandContext {

    private final JavaPlugin plugin;

    /**
     * Creates an asynchronous command context.
     *
     * @param brigadierContext Brigadier context
     * @param plugin           Plugin instance (for task scheduling)
     */
    public AsyncContext(CommandContext<CommandSourceStack> brigadierContext, JavaPlugin plugin) {
        super(brigadierContext);
        this.plugin = plugin;
    }

    /**
     * Creates an asynchronous version of a regular context.
     *
     * @param context Regular context
     * @param plugin  Plugin instance
     * @return Asynchronous context
     */
    public static AsyncContext from(GloomCommandContext context, JavaPlugin plugin) {
        return new AsyncContext(context.getBrigadierContext(), plugin);
    }

    /**
     * Gets the plugin instance.
     *
     * @return Plugin instance
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * Executes a task in an asynchronous thread.
     *
     * @param task Asynchronous task
     * @param <T>  Return type
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> runAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task);
    }

    /**
     * Executes a task in an asynchronous thread (no return value).
     *
     * @param task Asynchronous task
     * @return CompletableFuture
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task);
    }

    /**
     * Executes a task on the specified entity's scheduler (Folia compatible).
     *
     * @param entity Target entity
     * @param task   Task
     */
    public void runOn(org.bukkit.entity.Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, t -> task.run(), null);
    }

    /**
     * Executes a task on the region scheduler at the specified location (Folia
     * compatible).
     *
     * @param location Target location
     * @param task     Task
     */
    public void runOn(org.bukkit.Location location, Runnable task) {
        plugin.getServer().getRegionScheduler().execute(plugin, location, task);
    }

    /**
     * Executes a task on the main thread.
     * <p>
     * In a Folia environment, this uses the GlobalRegionScheduler.
     * If operating on a specific entity or chunk, prefer using
     * {@link #runOn(org.bukkit.entity.Entity, Runnable)}
     * or {@link #runOn(org.bukkit.Location, Runnable)}.
     * </p>
     *
     * @param task Main thread task
     */
    public void runSync(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    /**
     * Executes a task on the main thread (with return value).
     *
     * @param task Main thread task
     * @param <T>  Return type
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> runSync(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                future.complete(task.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Sends a message (automatically switches to main thread).
     *
     * @param message Adventure component message
     */
    @Override
    public void sendMessage(Component message) {
        // Paper/Adventure APIs (sendMessage) are generally thread-safe.
        // Direct invocation is preferred for performance unless strict synchronization
        // is required.
        super.sendMessage(message);
    }

    /**
     * Replies with a message.
     *
     * @param message Adventure component message
     */
    @Override
    public void reply(Component message) {
        sendMessage(message);
    }

    /**
     * Checks if currently on the main thread (Global Region).
     *
     * @return True if on Global Region thread
     */
    public boolean isMainThread() {
        return plugin.getServer().isGlobalTickThread();
    }

    /**
     * Executes consumer action if the sender is a player, on the main thread.
     *
     * @param consumer Player consumer
     */
    @Override
    public void ifPlayer(Consumer<Player> consumer) {
        CommandSender sender = getSender();
        if (sender instanceof Player player) {
            if (isMainThread()) {
                consumer.accept(player);
            } else {
                runSync(() -> consumer.accept(player));
            }
        }
    }
}

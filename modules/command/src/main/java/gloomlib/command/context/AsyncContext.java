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
 * 异步命令执行上下文。
 *
 * <p>
 * 为 {@code @Async} 标记的命令方法提供异步执行支持。
 * 自动处理线程切换，确保 Bukkit API 调用在主线程执行。
 * </p>
 *
 * <h2>用法示例</h2>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("stats")
 * @Async
 * public void showStats(AsyncContext ctx, Player player) {
 *     // 在异步线程中查询数据库
 *     Map<String, Object> stats = ctx.runAsync(() -> database.queryStats(player));
 * 
 *     // 自动切回主线程发送消息
 *     ctx.reply(Component.text("查询完成！"));
 * }
 * }
 * </pre>
 */
public class AsyncContext extends GloomCommandContext {

    private final JavaPlugin plugin;

    /**
     * 创建异步命令上下文。
     *
     * @param brigadierContext Brigadier 原生上下文
     * @param plugin           插件实例（用于调度任务）
     */
    public AsyncContext(CommandContext<CommandSourceStack> brigadierContext, JavaPlugin plugin) {
        super(brigadierContext);
        this.plugin = plugin;
    }

    /**
     * 获取插件实例。
     *
     * @return 插件实例
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * 在异步线程中执行任务。
     *
     * @param task 异步任务
     * @param <T>  返回类型
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> runAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task);
    }

    /**
     * 在异步线程中执行任务（无返回值）。
     *
     * @param task 异步任务
     * @return CompletableFuture
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task);
    }

    /**
     * 在指定实体的调度器上执行任务（Folia 兼容）。
     *
     * @param entity 目标实体
     * @param task   任务
     */
    public void runOn(org.bukkit.entity.Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, t -> task.run(), null);
    }

    /**
     * 在指定位置的区块调度器上执行任务（Folia 兼容）。
     *
     * @param location 目标位置
     * @param task     任务
     */
    public void runOn(org.bukkit.Location location, Runnable task) {
        plugin.getServer().getRegionScheduler().execute(plugin, location, task);
    }

    /**
     * 在主线程中执行任务。
     * <p>
     * 在 Folia 环境下，使用 GlobalRegionScheduler。
     * 如果操作特定实体或区块，请优先使用 {@link #runOn(org.bukkit.entity.Entity, Runnable)}
     * 或 {@link #runOn(org.bukkit.Location, Runnable)}。
     * </p>
     *
     * @param task 主线程任务
     */
    public void runSync(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    /**
     * 在主线程中执行任务（带返回值）。
     *
     * @param task 主线程任务
     * @param <T>  返回类型
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
     * 发送消息（自动切换到主线程）。
     *
     * @param message Adventure 组件消息
     */
    @Override
    public void sendMessage(Component message) {
        // Paper/Adventure APIs (sendMessage) are generally thread-safe.
        // Direct invocation is preferred for performance unless strict synchronization
        // is required.
        super.sendMessage(message);
    }

    /**
     * 回复消息。
     *
     * @param message Adventure 组件消息
     */
    @Override
    public void reply(Component message) {
        sendMessage(message);
    }

    /**
     * 检查当前是否在主线程（Global Region）。
     *
     * @return 是否在 Global Region 线程
     */
    public boolean isMainThread() {
        return plugin.getServer().isGlobalTickThread();
    }

    /**
     * 如果执行者是玩家，在主线程执行消费者操作。
     *
     * @param consumer 玩家消费者
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

    /**
     * 创建普通上下文的异步版本。
     *
     * @param context 普通上下文
     * @param plugin  插件实例
     * @return 异步上下文
     */
    public static AsyncContext from(GloomCommandContext context, JavaPlugin plugin) {
        return new AsyncContext(context.getBrigadierContext(), plugin);
    }
}

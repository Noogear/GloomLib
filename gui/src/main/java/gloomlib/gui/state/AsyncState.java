package gloomlib.gui.state;

import gloomlib.gui.GloomGuiManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * 异步响应式状态容器
 * <p>
 * 支持异步加载数据并在完成后自动更新 GUI。
 * 使用 Java 21 虚拟线程优化异步任务执行。
 * <p>
 * 虚拟线程相比传统线程池有以下优势：
 * <ul>
 *   <li>更低的内存占用</li>
 *   <li>更好的可扩展性（可以创建数百万个虚拟线程）</li>
 *   <li>简化的代码结构</li>
 * </ul>
 * 
 * @param <T> 状态值类型
 * @author GloomLib
 * @since 2.0
 */
public class AsyncState<T> extends ReactiveState<T> {

    private final T loadingValue;
    private final T errorValue;
    private volatile boolean isLoading = true;
    private final Player player;

    private AsyncState(T initialValue, T loadingValue, T errorValue, Player player) {
        super(loadingValue);
        this.loadingValue = loadingValue;
        this.errorValue = errorValue;
        this.player = player;
    }

    /**
     * 创建一个异步状态，使用 CompletableFuture 加载数据
     * 
     * @param loader       数据加载器
     * @param loadingValue 加载中显示的值
     * @param errorValue   错误时显示的值
     * @param player       关联的玩家（用于调度回主线程）
     * @param <T>          数据类型
     * @return 异步状态实例
     */
    public static <T> AsyncState<T> ofFuture(Supplier<CompletableFuture<T>> loader, 
                                             T loadingValue, 
                                             T errorValue, 
                                             Player player) {
        AsyncState<T> state = new AsyncState<>(null, loadingValue, errorValue, player);
        state.reload(loader);
        return state;
    }

    /**
     * 创建一个异步状态，使用同步方法加载数据（会在虚拟线程中执行）
     * 
     * @param loader       同步数据加载器
     * @param loadingValue 加载中显示的值
     * @param errorValue   错误时显示的值
     * @param player       关联的玩家
     * @param <T>          数据类型
     * @return 异步状态实例
     */
    public static <T> AsyncState<T> of(Supplier<T> loader, 
                                       T loadingValue, 
                                       T errorValue, 
                                       Player player) {
        return ofFuture(() -> CompletableFuture.supplyAsync(loader), 
                       loadingValue, errorValue, player);
    }

    /**
     * 重新加载数据
     * <p>
     * 使用 Java 21 虚拟线程执行异步任务，然后调度回主线程更新状态
     * 
     * @param loader 数据加载器
     */
    public void reload(Supplier<CompletableFuture<T>> loader) {
        setLoading(true);
        
        // 使用虚拟线程执行异步任务
        try {
            Thread.startVirtualThread(() -> {
                try {
                    CompletableFuture<T> future = loader.get();
                    future.whenComplete((result, ex) -> {
                        // 调度回主线程更新 GUI
                        scheduleMainThreadUpdate(result, ex);
                    });
                } catch (Exception e) {
                    scheduleMainThreadUpdate(null, e);
                }
            });
        } catch (UnsupportedOperationException e) {
            // 如果虚拟线程不可用（Java < 21），回退到传统方式
            GloomGuiManager.getPlugin().getLogger().log(
                Level.WARNING, 
                "虚拟线程不可用，使用传统异步方式。建议升级到 Java 21+",
                e
            );
            fallbackAsyncLoad(loader);
        }
    }

    /**
     * 调度更新到主线程
     * 
     * @param result 加载结果
     * @param ex     异常（如果有）
     */
    private void scheduleMainThreadUpdate(T result, Throwable ex) {
        if (player != null && player.isOnline()) {
            try {
                // 使用 Paper 的 EntityScheduler（Folia 兼容）
                player.getScheduler().run(
                    GloomGuiManager.getPlugin(),
                    task -> updateState(result, ex),
                    null
                );
            } catch (Exception e) {
                // 回退到传统调度器
                Bukkit.getScheduler().runTask(
                    GloomGuiManager.getPlugin(),
                    () -> updateState(result, ex)
                );
            }
        } else {
            // 没有玩家关联，直接在主线程执行
            Bukkit.getScheduler().runTask(
                GloomGuiManager.getPlugin(),
                () -> updateState(result, ex)
            );
        }
    }

    /**
     * 更新状态（必须在主线程执行）
     */
    private void updateState(T result, Throwable ex) {
        if (ex != null) {
            GloomGuiManager.getPlugin().getLogger().log(
                Level.WARNING,
                "异步加载数据时发生错误",
                ex
            );
            super.set(errorValue);
        } else {
            super.set(result);
        }
        setLoading(false);
    }

    /**
     * 回退到传统异步加载方式
     */
    private void fallbackAsyncLoad(Supplier<CompletableFuture<T>> loader) {
        loader.get().whenComplete((result, ex) -> {
            Bukkit.getScheduler().runTask(GloomGuiManager.getPlugin(), () -> {
                updateState(result, ex);
            });
        });
    }

    /**
     * 检查是否正在加载
     * 
     * @return 如果正在加载返回 true
     */
    public boolean isLoading() {
        return isLoading;
    }

    /**
     * 设置加载状态
     */
    private void setLoading(boolean loading) {
        this.isLoading = loading;
        if (loading) {
            super.set(loadingValue);
        }
    }

    /**
     * 获取加载中显示的值
     * 
     * @return 加载中的值
     */
    public T getLoadingValue() {
        return loadingValue;
    }

    /**
     * 获取错误时显示的值
     * 
     * @return 错误值
     */
    public T getErrorValue() {
        return errorValue;
    }
}
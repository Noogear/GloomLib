package gloomlib.gui.state;

import gloomlib.gui.GloomGuiManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

public class AsyncState<T> extends ReactiveState<T> {

    private final T loadingValue;
    private final T errorValue;
    private final Player player;
    private volatile boolean isLoading = true;

    private AsyncState(T initialValue, T loadingValue, T errorValue, Player player) {
        super(loadingValue);
        this.loadingValue = loadingValue;
        this.errorValue = errorValue;
        this.player = player;
    }

    public static <T> AsyncState<T> ofFuture(Supplier<CompletableFuture<T>> loader,
                                             T loadingValue,
                                             T errorValue,
                                             Player player) {
        AsyncState<T> state = new AsyncState<>(null, loadingValue, errorValue, player);
        state.reload(loader);
        return state;
    }

    public static <T> AsyncState<T> of(Supplier<T> loader,
                                       T loadingValue,
                                       T errorValue,
                                       Player player) {
        return ofFuture(() -> CompletableFuture.supplyAsync(loader),
                loadingValue, errorValue, player);
    }

    public void reload(Supplier<CompletableFuture<T>> loader) {
        setLoading(true);

        try {
            Thread.startVirtualThread(() -> {
                try {
                    CompletableFuture<T> future = loader.get();
                    future.whenComplete(this::scheduleMainThreadUpdate);
                } catch (Exception e) {
                    scheduleMainThreadUpdate(null, e);
                }
            });
        } catch (UnsupportedOperationException e) {
            GloomGuiManager.getPlugin().getLogger().log(
                    Level.WARNING,
                    "虚拟线程不可用，使用传统异步方式。建议升级到 Java 21+",
                    e
            );
            fallbackAsyncLoad(loader);
        }
    }

    private void scheduleMainThreadUpdate(T result, Throwable ex) {
        if (player != null && player.isOnline()) {
            try {
                player.getScheduler().run(
                        GloomGuiManager.getPlugin(),
                        task -> updateState(result, ex),
                        null
                );
            } catch (Exception e) {
                Bukkit.getServer().getGlobalRegionScheduler().run(
                        GloomGuiManager.getPlugin(),
                        task -> updateState(result, ex)
                );
            }
        } else {
            Bukkit.getServer().getGlobalRegionScheduler().run(
                    GloomGuiManager.getPlugin(),
                    task -> updateState(result, ex)
            );
        }
    }

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

    private void fallbackAsyncLoad(Supplier<CompletableFuture<T>> loader) {
        loader.get().whenComplete((result, ex) -> {
            Bukkit.getServer().getGlobalRegionScheduler().run(
                    GloomGuiManager.getPlugin(),
                    task -> updateState(result, ex)
            );
        });
    }

    public boolean isLoading() {
        return isLoading;
    }

    private void setLoading(boolean loading) {
        this.isLoading = loading;
        if (loading) {
            super.set(loadingValue);
        }
    }

    public T getLoadingValue() {
        return loadingValue;
    }

    public T getErrorValue() {
        return errorValue;
    }
}
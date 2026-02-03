package gloomlib.gui.state;

import gloomlib.gui.GloomGuiManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Reactive state with async data loading support using virtual threads.
 *
 * @param <T> the state value type
 */
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

    /**
     * Creates an async state from a CompletableFuture supplier.
     *
     * @param loader       the future supplier
     * @param loadingValue the loading value
     * @param errorValue   the error value
     * @param player       the player
     * @param <T>          the state type
     * @return a new async state
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
     * Creates an async state from a synchronous loader.
     *
     * @param loader       the data loader
     * @param loadingValue the loading value
     * @param errorValue   the error value
     * @param player       the player
     * @param <T>          the state type
     * @return a new async state
     */
    public static <T> AsyncState<T> of(Supplier<T> loader,
                                       T loadingValue,
                                       T errorValue,
                                       Player player) {
        return ofFuture(() -> CompletableFuture.supplyAsync(loader),
                loadingValue, errorValue, player);
    }

    /**
     * Reloads the state by executing the loader again.
     *
     * @param loader the future supplier
     */
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
                    "Virtual threads unavailable, using traditional async approach. Consider upgrading to Java 21+",
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
                    "Error occurred while loading data asynchronously",
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

    /**
     * Checks if the state is currently loading.
     *
     * @return true if loading
     */
    public boolean isLoading() {
        return isLoading;
    }

    private void setLoading(boolean loading) {
        this.isLoading = loading;
        if (loading) {
            super.set(loadingValue);
        }
    }

    /**
     * Gets the loading placeholder value.
     *
     * @return the loading value
     */
    public T getLoadingValue() {
        return loadingValue;
    }

    /**
     * Gets the error fallback value.
     *
     * @return the error value
     */
    public T getErrorValue() {
        return errorValue;
    }
}

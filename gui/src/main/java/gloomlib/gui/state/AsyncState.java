package gloomlib.gui.state;

import gloomlib.gui.GloomGuiManager;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class AsyncState<T> extends ReactiveState<T> {

    private final T loadingValue;
    private final T errorValue;
    private boolean isLoading = true;

    private AsyncState(T initialValue, T loadingValue, T errorValue) {
        super(loadingValue);
        this.loadingValue = loadingValue;
        this.errorValue = errorValue;
    }

    public static <T> AsyncState<T> ofFuture(Supplier<CompletableFuture<T>> loader, T loadingValue, T errorValue) {
        AsyncState<T> state = new AsyncState<>(null, loadingValue, errorValue);
        state.reload(loader);
        return state;
    }

    public void reload(Supplier<CompletableFuture<T>> loader) {
        setLoading(true);
        loader.get().whenComplete((result, ex) -> {
            Bukkit.getScheduler().runTask(GloomGuiManager.getPlugin(), () -> {
                if (ex != null) {
                    ex.printStackTrace();
                    super.set(errorValue);
                } else {
                    super.set(result);
                }
                setLoading(false);
            });
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
}
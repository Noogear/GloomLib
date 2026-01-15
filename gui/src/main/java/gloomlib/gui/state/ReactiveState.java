package gloomlib.gui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 響應式狀態容器。
 * 當值變化時，自動通知所有綁定的組件進行刷新。
 *
 * @param <T> 狀態類型
 */
public class ReactiveState<T> implements Supplier<T> {

    private T value;
    private final List<Consumer<T>> listeners = new ArrayList<>();

    public ReactiveState(T initialValue) {
        this.value = initialValue;
    }

    @Override
    public T get() {
        return value;
    }

    public void set(T newValue) {
        if (Objects.equals(this.value, newValue)) {
            return;
        }
        this.value = newValue;
        notifyListeners();
    }

    public void subscribe(Consumer<T> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<T> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Consumer<T> listener : new ArrayList<>(listeners)) {
            listener.accept(value);
        }
    }

    public static <T> ReactiveState<T> of(T value) {
        return new ReactiveState<>(value);
    }
}
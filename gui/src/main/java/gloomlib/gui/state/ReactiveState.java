package gloomlib.gui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ReactiveState<T> implements Supplier<T> {

    private final List<Consumer<T>> listeners = new ArrayList<>();
    private T value;

    public ReactiveState(T initialValue) {
        this.value = initialValue;
    }

    public static <T> ReactiveState<T> of(T value) {
        return new ReactiveState<>(value);
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
        List<Consumer<T>> snapShot = new ArrayList<>(listeners);
        for (Consumer<T> listener : snapShot) {
            listener.accept(value);
        }
    }

    public <R> ReactiveState<R> map(Function<T, R> mapper) {
        ReactiveState<R> mappedState = new ReactiveState<>(mapper.apply(this.value));
        this.subscribe(newVal -> mappedState.set(mapper.apply(newVal)));
        return mappedState;
    }

    public void observe(Consumer<T> observer) {
        this.subscribe(observer);
    }
}
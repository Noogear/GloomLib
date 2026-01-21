package gloomlib.gui.state;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ReactiveState<T> implements Supplier<T> {

    private final List<WeakReference<Consumer<T>>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

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
        if (listener != null) {
            cleanupDeadListeners();
            listeners.add(new WeakReference<>(listener));
        }
    }

    public void unsubscribe(Consumer<T> listener) {
        if (listener == null) {
            return;
        }
        listeners.removeIf(ref -> {
            Consumer<T> l = ref.get();
            return l == null || l == listener;
        });
    }

    private void cleanupDeadListeners() {
        listeners.removeIf(ref -> ref.get() == null);
    }

    private void notifyListeners() {
        List<WeakReference<Consumer<T>>> toRemove = new ArrayList<>();

        for (WeakReference<Consumer<T>> ref : listeners) {
            Consumer<T> listener = ref.get();
            if (listener == null) {
                toRemove.add(ref);
            } else {
                try {
                    listener.accept(value);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        listeners.removeAll(toRemove);
    }

    public <R> ReactiveState<R> map(Function<T, R> mapper) {
        ReactiveState<R> mappedState = new ReactiveState<>(mapper.apply(this.value));
        this.subscribe(newVal -> mappedState.set(mapper.apply(newVal)));
        return mappedState;
    }

    public void observe(Consumer<T> observer) {
        this.subscribe(observer);
    }

    public int getListenerCount() {
        cleanupDeadListeners();
        return listeners.size();
    }

    public void clearListeners() {
        listeners.clear();
    }
}
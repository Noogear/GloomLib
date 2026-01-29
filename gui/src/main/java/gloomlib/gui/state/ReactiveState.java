package gloomlib.gui.state;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Implementation of reactive state that notifies subscribers upon value changes.
 *
 * @param <T> the type of the state value
 */
public class ReactiveState<T> implements Supplier<T> {

    private final List<WeakReference<Consumer<T>>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    /**
     * Constructs a reactive state with an initial value.
     *
     * @param initialValue the initial value
     */
    public ReactiveState(T initialValue) {
        this.value = initialValue;
    }

    /**
     * Creates a reactive state from a value.
     *
     * @param value the value
     * @param <T> the type
     * @return the reactive state
     */
    public static <T> ReactiveState<T> of(T value) {
        return new ReactiveState<>(value);
    }

    @Override
    public T get() {
        return value;
    }

    /**
     * Sets a new value and notifies subscribers if changed.
     *
     * @param newValue the new value
     */
    public void set(T newValue) {
        if (Objects.equals(this.value, newValue)) {
            return;
        }
        this.value = newValue;
        notifyListeners();
    }

    /**
     * Subscribes a listener to value changes.
     *
     * @param listener the listener
     */
    public void subscribe(Consumer<T> listener) {
        if (listener != null) {
            cleanupDeadListeners();
            listeners.add(new WeakReference<>(listener));
        }
    }

    /**
     * Unsubscribes a listener.
     *
     * @param listener the listener
     */
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

    /**
     * Maps this state to another reactive state.
     *
     * @param mapper the mapper function
     * @param <R> the result type
     * @return the mapped state
     */
    public <R> ReactiveState<R> map(Function<T, R> mapper) {
        ReactiveState<R> mappedState = new ReactiveState<>(mapper.apply(this.value));
        this.subscribe(newVal -> mappedState.set(mapper.apply(newVal)));
        return mappedState;
    }

    /**
     * Observes value changes.
     *
     * @param observer the observer
     */
    public void observe(Consumer<T> observer) {
        this.subscribe(observer);
    }

    /**
     * Gets the number of active listeners.
     *
     * @return the listener count
     */
    public int getListenerCount() {
        cleanupDeadListeners();
        return listeners.size();
    }

    /**
     * Clears all listeners.
     */
    public void clearListeners() {
        listeners.clear();
    }
}

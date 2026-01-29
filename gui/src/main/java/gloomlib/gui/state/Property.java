package gloomlib.gui.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Interface representing an observable property.
 *
 * @param <T> the property type
 */
public interface Property<T> extends Supplier<T> {

    @Nullable
    @Override
    T get();

    /**
     * Observes the property with a weak reference.
     *
     * @param consumer the consumer
     */
    void observeWeak(@NotNull Consumer<T> consumer);

    /**
     * Maps this property to another value.
     *
     * @param mapper the mapper function
     * @param <R> the result type
     * @return the mapped property
     */
    @NotNull
    default <R> Property<R> map(@NotNull Function<T, R> mapper) {
        return new MappedProperty<>(this, mapper);
    }

    /**
     * Flat maps this property to another property.
     *
     * @param mapper the mapper function
     * @param <R> the result type
     * @return the flat mapped property
     */
    @NotNull
    default <R> Property<R> flatMap(@NotNull Function<T, Property<R>> mapper) {
        return new FlatMappedProperty<>(this, mapper);
    }

    class MappedProperty<T, R> implements Property<R> {
        private final Property<T> source;
        private final Function<T, R> mapper;
        private final List<WeakReference<Consumer<R>>> observers = new CopyOnWriteArrayList<>();
        private volatile R cachedValue;

        public MappedProperty(@NotNull Property<T> source, @NotNull Function<T, R> mapper) {
            this.source = source;
            this.mapper = mapper;
            this.cachedValue = mapper.apply(source.get());

            source.observeWeak(newValue -> {
                R newMappedValue = mapper.apply(newValue);
                if (!Objects.equals(cachedValue, newMappedValue)) {
                    cachedValue = newMappedValue;
                    notifyObservers();
                }
            });
        }

        @Override
        public R get() {
            return cachedValue;
        }

        @Override
        public void observeWeak(@NotNull Consumer<R> consumer) {
            cleanupDeadObservers();
            observers.add(new WeakReference<>(consumer));
        }

        private void notifyObservers() {
            R value = cachedValue;
            observers.forEach(ref -> {
                Consumer<R> consumer = ref.get();
                if (consumer != null) {
                    try {
                        consumer.accept(value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        private void cleanupDeadObservers() {
            observers.removeIf(ref -> ref.get() == null);
        }
    }

    class FlatMappedProperty<T, R> implements Property<R> {
        private final Property<T> source;
        private final Function<T, Property<R>> mapper;
        private final List<WeakReference<Consumer<R>>> observers = new CopyOnWriteArrayList<>();
        private volatile Property<R> currentInner;
        private volatile R cachedValue;

        public FlatMappedProperty(@NotNull Property<T> source, @NotNull Function<T, Property<R>> mapper) {
            this.source = source;
            this.mapper = mapper;
            this.currentInner = mapper.apply(source.get());
            this.cachedValue = currentInner != null ? currentInner.get() : null;

            source.observeWeak(newValue -> {
                Property<R> newInner = mapper.apply(newValue);
                if (newInner != currentInner) {
                    currentInner = newInner;
                    if (newInner != null) {
                        newInner.observeWeak(this::updateValue);
                    }
                    updateValue(newInner != null ? newInner.get() : null);
                }
            });

            if (currentInner != null) {
                currentInner.observeWeak(this::updateValue);
            }
        }

        private void updateValue(R newValue) {
            if (!Objects.equals(cachedValue, newValue)) {
                cachedValue = newValue;
                notifyObservers();
            }
        }

        @Override
        public R get() {
            return cachedValue;
        }

        @Override
        public void observeWeak(@NotNull Consumer<R> consumer) {
            cleanupDeadObservers();
            observers.add(new WeakReference<>(consumer));
        }

        private void notifyObservers() {
            R value = cachedValue;
            observers.forEach(ref -> {
                Consumer<R> consumer = ref.get();
                if (consumer != null) {
                    try {
                        consumer.accept(value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        private void cleanupDeadObservers() {
            observers.removeIf(ref -> ref.get() == null);
        }
    }
}

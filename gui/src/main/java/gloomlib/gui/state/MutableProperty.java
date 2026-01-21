package gloomlib.gui.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public interface MutableProperty<T> extends Property<T> {

    @NotNull
    static <T> MutableProperty<T> of(@Nullable T initialValue) {
        return new MutablePropertyImpl<>(initialValue);
    }

    void set(@Nullable T value);

    class MutablePropertyImpl<T> implements MutableProperty<T> {
        private final List<WeakReference<Consumer<T>>> observers = new CopyOnWriteArrayList<>();
        private volatile T value;

        public MutablePropertyImpl(@Nullable T initialValue) {
            this.value = initialValue;
        }

        @Override
        public @Nullable T get() {
            return value;
        }

        @Override
        public void set(@Nullable T newValue) {
            if (Objects.equals(this.value, newValue)) {
                return;
            }
            this.value = newValue;
            notifyObservers();
        }

        @Override
        public void observeWeak(@NotNull Consumer<T> consumer) {
            cleanupDeadObservers();
            observers.add(new WeakReference<>(consumer));
        }

        private void notifyObservers() {
            T currentValue = value;
            observers.forEach(ref -> {
                Consumer<T> consumer = ref.get();
                if (consumer != null) {
                    try {
                        consumer.accept(currentValue);
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

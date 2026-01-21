package gloomlib.gui.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 可变属性接口 - 可读写的响应式状态容器
 * <p>
 * MutableProperty 扩展了 {@link Property}，添加了修改值的能力。
 * 它同时提供只读和读写接口，可以根据需要选择性暴露。
 * <p>
 * 设计参考：InvUI 2.x 的 MutableProperty 接口
 * {@link <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/state/MutableProperty.java">InvUI MutableProperty.java</a>}
 * 
 * <h3>典型用法</h3>
 * <pre>{@code
 * MutableProperty<Integer> counter = MutableProperty.of(0);
 * 
 * // 订阅变化
 * counter.observeWeak(value -> System.out.println("Counter: " + value));
 * 
 * // 修改值
 * counter.set(1);  // 输出: Counter: 1
 * counter.set(2);  // 输出: Counter: 2
 * }</pre>
 * 
 * @param <T> 属性值类型
 * @author GloomLib
 * @since 3.0
 * @see Property
 */
public interface MutableProperty<T> extends Property<T> {

    /**
     * 设置新值并通知所有观察者
     * <p>
     * 如果新值与当前值相等（使用 {@link Objects#equals(Object, Object)} 比较），
     * 则不会触发通知，避免不必要的更新。
     * 
     * @param value 新值
     */
    void set(@Nullable T value);

    /**
     * 创建一个新的可变属性
     * 
     * @param initialValue 初始值
     * @param <T>          值类型
     * @return 可变属性实例
     */
    @NotNull
    static <T> MutableProperty<T> of(@Nullable T initialValue) {
        return new MutablePropertyImpl<>(initialValue);
    }

    /**
     * 可变属性的默认实现
     */
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

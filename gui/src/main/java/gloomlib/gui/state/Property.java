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
 * 不可变属性接口 - 只读的响应式状态容器
 * <p>
 * Property 提供了类型安全的只读视图，外部代码无法修改其值。
 * 这有助于封装内部状态，防止意外修改。
 * <p>
 * 设计参考：InvUI 2.x 的 Property 接口
 * {@link <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/state/Property.java">InvUI Property.java</a>}
 * 
 * <h3>典型用法</h3>
 * <pre>{@code
 * public class GloomGui {
 *     private final MutableProperty<Boolean> frozen = MutableProperty.of(false);
 *     
 *     // 只暴露只读视图
 *     public Property<Boolean> getFrozen() {
 *         return frozen;
 *     }
 *     
 *     // 内部可以修改
 *     public void setFrozen(boolean value) {
 *         frozen.set(value);
 *     }
 * }
 * }</pre>
 * 
 * @param <T> 属性值类型
 * @author GloomLib
 * @since 3.0
 * @see MutableProperty
 */
public interface Property<T> extends Supplier<T> {

    /**
     * 获取当前值
     * 
     * @return 当前值，可能为 null
     */
    @Nullable
    @Override
    T get();

    /**
     * 使用弱引用订阅值变化
     * <p>
     * 当属性值变化时，消费者会被调用并传入新值。
     * 使用弱引用可防止内存泄漏，当消费者对象被垃圾回收时，订阅会自动取消。
     * 
     * @param consumer 值变化时的回调函数
     */
    void observeWeak(@NotNull Consumer<T> consumer);

    /**
     * 映射转换为另一个 Property
     * <p>
     * 创建一个新的派生 Property，其值通过 mapper 函数从当前 Property 转换而来。
     * 当原始 Property 变化时，派生 Property 也会自动更新。
     * 
     * @param mapper 转换函数
     * @param <R>    目标类型
     * @return 派生的 Property
     */
    @NotNull
    default <R> Property<R> map(@NotNull Function<T, R> mapper) {
        return new MappedProperty<>(this, mapper);
    }

    /**
     * 扁平化映射转换
     * <p>
     * 类似于 {@link #map(Function)}，但 mapper 函数返回另一个 Property。
     * 结果 Property 会跟踪两层变化。
     * 
     * @param mapper 返回 Property 的转换函数
     * @param <R>    目标类型
     * @return 派生的 Property
     */
    @NotNull
    default <R> Property<R> flatMap(@NotNull Function<T, Property<R>> mapper) {
        return new FlatMappedProperty<>(this, mapper);
    }

    /**
     * 映射后的 Property 实现
     */
    class MappedProperty<T, R> implements Property<R> {
        private final Property<T> source;
        private final Function<T, R> mapper;
        private final List<WeakReference<Consumer<R>>> observers = new CopyOnWriteArrayList<>();
        private volatile R cachedValue;

        public MappedProperty(@NotNull Property<T> source, @NotNull Function<T, R> mapper) {
            this.source = source;
            this.mapper = mapper;
            this.cachedValue = mapper.apply(source.get());

            // 观察源 Property 的变化
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

    /**
     * 扁平化映射后的 Property 实现
     */
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

            // 观察源 Property 的变化
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

            // 观察内部 Property 的变化
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

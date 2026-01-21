package gloomlib.gui.state;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 响应式状态容器，支持观察者模式和自动内存管理
 * <p>
 * 使用弱引用存储监听器，防止内存泄漏。当监听器对象被垃圾回收时，
 * 会自动从监听器列表中移除。
 * 
 * @param <T> 状态值类型
 * @author GloomLib
 * @since 2.0
 */
public class ReactiveState<T> implements Supplier<T> {

    // 使用 CopyOnWriteArrayList 确保线程安全的迭代
    private final List<WeakReference<Consumer<T>>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    public ReactiveState(T initialValue) {
        this.value = initialValue;
    }

    /**
     * 创建一个新的响应式状态
     * 
     * @param value 初始值
     * @param <T>   值类型
     * @return 响应式状态实例
     */
    public static <T> ReactiveState<T> of(T value) {
        return new ReactiveState<>(value);
    }

    @Override
    public T get() {
        return value;
    }

    /**
     * 设置新值并通知所有监听器
     * 如果新值与当前值相等，则不会触发通知
     * 
     * @param newValue 新值
     */
    public void set(T newValue) {
        if (Objects.equals(this.value, newValue)) {
            return;
        }
        this.value = newValue;
        notifyListeners();
    }

    /**
     * 订阅状态变化
     * 使用弱引用存储监听器，防止内存泄漏
     * 
     * @param listener 监听器回调
     */
    public void subscribe(Consumer<T> listener) {
        if (listener != null) {
            // 清理已经被垃圾回收的监听器
            cleanupDeadListeners();
            listeners.add(new WeakReference<>(listener));
        }
    }

    /**
     * 取消订阅
     * 
     * @param listener 要移除的监听器
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

    /**
     * 清理所有已被垃圾回收的监听器
     */
    private void cleanupDeadListeners() {
        listeners.removeIf(ref -> ref.get() == null);
    }

    /**
     * 通知所有监听器状态已变化
     */
    private void notifyListeners() {
        // 清理已失效的弱引用
        List<WeakReference<Consumer<T>>> toRemove = new ArrayList<>();
        
        for (WeakReference<Consumer<T>> ref : listeners) {
            Consumer<T> listener = ref.get();
            if (listener == null) {
                toRemove.add(ref);
            } else {
                try {
                    listener.accept(value);
                } catch (Exception e) {
                    // 记录错误但继续通知其他监听器
                    e.printStackTrace();
                }
            }
        }
        
        // 移除失效的引用
        listeners.removeAll(toRemove);
    }

    /**
     * 映射到新的响应式状态
     * 
     * @param mapper 映射函数
     * @param <R>    目标类型
     * @return 新的响应式状态
     */
    public <R> ReactiveState<R> map(Function<T, R> mapper) {
        ReactiveState<R> mappedState = new ReactiveState<>(mapper.apply(this.value));
        this.subscribe(newVal -> mappedState.set(mapper.apply(newVal)));
        return mappedState;
    }

    /**
     * 观察状态变化（subscribe 的别名）
     * 
     * @param observer 观察者回调
     */
    public void observe(Consumer<T> observer) {
        this.subscribe(observer);
    }

    /**
     * 获取当前活跃的监听器数量（未被垃圾回收的）
     * 
     * @return 活跃监听器数量
     */
    public int getListenerCount() {
        cleanupDeadListeners();
        return listeners.size();
    }

    /**
     * 清除所有监听器
     */
    public void clearListeners() {
        listeners.clear();
    }
}
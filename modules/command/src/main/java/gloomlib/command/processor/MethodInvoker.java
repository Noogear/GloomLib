package gloomlib.command.processor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令方法调用器。
 *
 * <p>
 * 使用 {@link MethodHandle} 代替反射，提供更高的执行性能。
 * 相比普通反射调用，MethodHandle 可以获得接近直接调用的性能。
 * </p>
 *
 * <h2>性能优化说明</h2>
 * <ul>
 * <li>MethodHandle 在 JIT 编译后可以接近直接方法调用的性能</li>
 * <li>内置缓存机制，避免重复创建 MethodHandle</li>
 * <li>线程安全的缓存实现</li>
 * </ul>
 */
public class MethodInvoker {

    /** MethodHandle 缓存 */
    private static final Map<Method, MethodHandle> HANDLE_CACHE = new ConcurrentHashMap<>();

    /** MethodHandles.Lookup 实例 */
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final MethodHandle handle;
    private final Method method;

    /**
     * 创建方法调用器。
     *
     * @param method 目标方法
     * @throws IllegalAccessException 如果无法访问方法
     */
    public MethodInvoker(Method method) throws IllegalAccessException {
        this.method = method;
        this.handle = getOrCreateHandle(method);
    }

    /**
     * 获取或创建 MethodHandle。
     *
     * @param method 目标方法
     * @return MethodHandle
     * @throws IllegalAccessException 如果无法访问方法
     */
    private static MethodHandle getOrCreateHandle(Method method) throws IllegalAccessException {
        MethodHandle cached = HANDLE_CACHE.get(method);
        if (cached != null) {
            return cached;
        }

        // 确保方法可访问
        method.setAccessible(true);

        MethodHandle handle = LOOKUP.unreflect(method);
        HANDLE_CACHE.put(method, handle);
        return handle;
    }

    /**
     * 调用方法。
     *
     * @param instance 实例对象
     * @param args     方法参数
     * @return 方法返回值
     * @throws Throwable 调用过程中的异常
     */
    public Object invoke(Object instance, Object... args) throws Throwable {
        // 构建完整的参数列表（实例 + 参数）
        Object[] fullArgs = new Object[args.length + 1];
        fullArgs[0] = instance;
        System.arraycopy(args, 0, fullArgs, 1, args.length);

        return handle.invokeWithArguments(fullArgs);
    }

    /**
     * 无实例调用（静态方法）。
     *
     * @param args 方法参数
     * @return 方法返回值
     * @throws Throwable 调用过程中的异常
     */
    public Object invokeStatic(Object... args) throws Throwable {
        return handle.invokeWithArguments(args);
    }

    /**
     * 获取原始方法。
     *
     * @return 原始 Method 对象
     */
    public Method getMethod() {
        return method;
    }

    /**
     * 获取 MethodHandle。
     *
     * @return MethodHandle
     */
    public MethodHandle getHandle() {
        return handle;
    }

    /**
     * 清除缓存。
     */
    public static void clearCache() {
        HANDLE_CACHE.clear();
    }

    /**
     * 获取缓存大小。
     *
     * @return 缓存的 MethodHandle 数量
     */
    public static int getCacheSize() {
        return HANDLE_CACHE.size();
    }

    /**
     * 工厂方法：创建方法调用器。
     *
     * @param method 目标方法
     * @return 方法调用器
     */
    public static MethodInvoker of(Method method) {
        try {
            return new MethodInvoker(method);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("无法创建方法调用器: " + method.getName(), e);
        }
    }
}

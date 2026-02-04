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
 * <li>参数数量特化：根据参数数量使用直接调用避免 invokeWithArguments 开销</li>
 * </ul>
 */
public class MethodInvoker {

    /** MethodHandle 缓存 */
    private static final Map<Method, MethodHandle> HANDLE_CACHE = new ConcurrentHashMap<>();

    /** MethodHandles.Lookup 实例 */
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final MethodHandle handle;
    private final MethodHandle spreadHandle;
    private final Method method;
    private final int parameterCount;

    /**
     * 创建方法调用器。
     *
     * @param method 目标方法
     * @throws IllegalAccessException 如果无法访问方法
     */
    public MethodInvoker(Method method) throws IllegalAccessException {
        this.method = method;
        this.handle = getOrCreateHandle(method);
        this.parameterCount = method.getParameterCount();
        // 创建 spread handle 用于数组参数调用
        this.spreadHandle = createSpreadHandle(handle, parameterCount);
    }

    /**
     * 创建 spread handle 用于优化数组参数调用。
     */
    private static MethodHandle createSpreadHandle(MethodHandle handle, int paramCount) {
        try {
            // 将 handle 适配为接受 Object[] 参数的形式
            // handle 类型: (instance, arg1, arg2, ...) -> result
            // spread handle 类型: (instance, Object[]) -> result
            return handle.asSpreader(1, Object[].class, paramCount);
        } catch (Exception e) {
            // 如果创建失败，返回 null，后续使用 invokeWithArguments
            return null;
        }
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
     * 调用方法（高性能优化版本）。
     *
     * <p>
     * 使用参数数量特化策略，根据参数数量选择最优调用方式：
     * <ul>
     * <li>0-8 个参数：使用 switch + 直接 invoke，避免数组分配</li>
     * <li>9+ 个参数：使用 invokeWithArguments</li>
     * </ul>
     * </p>
     *
     * @param instance 实例对象
     * @param args     方法参数
     * @return 方法返回值
     * @throws Throwable 调用过程中的异常
     */
    public Object invoke(Object instance, Object... args) throws Throwable {
        // 参数数量特化：避免 invokeWithArguments 的开销
        // invokeWithArguments 需要装箱/拆箱和数组操作，直接调用更快
        return switch (args.length) {
            case 0 -> handle.invoke(instance);
            case 1 -> handle.invoke(instance, args[0]);
            case 2 -> handle.invoke(instance, args[0], args[1]);
            case 3 -> handle.invoke(instance, args[0], args[1], args[2]);
            case 4 -> handle.invoke(instance, args[0], args[1], args[2], args[3]);
            case 5 -> handle.invoke(instance, args[0], args[1], args[2], args[3], args[4]);
            case 6 -> handle.invoke(instance, args[0], args[1], args[2], args[3], args[4], args[5]);
            case 7 -> handle.invoke(instance, args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
            case 8 -> handle.invoke(instance, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
            default -> {
                // 超过 8 个参数，使用 spread handle 或 invokeWithArguments
                if (spreadHandle != null) {
                    yield spreadHandle.invoke(instance, args);
                } else {
                    Object[] fullArgs = new Object[args.length + 1];
                    fullArgs[0] = instance;
                    System.arraycopy(args, 0, fullArgs, 1, args.length);
                    yield handle.invokeWithArguments(fullArgs);
                }
            }
        };
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

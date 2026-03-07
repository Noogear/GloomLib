package gloomlib.command.core.processor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance method invocation via MethodHandle.
 *
 * <br>
 * <b>Implementation Note:</b> Performance: Cold start ~50-100x slower, Warm
 * ~3-5x faster than reflection, Hot ~1.1-1.5x overhead.
 */
public class MethodInvoker {

    private static final Map<Method, MethodHandle> HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final MethodHandle handle;
    private final MethodHandle spreadHandle;
    private final Method method;
    private final int parameterCount;

    public MethodInvoker(Method method) throws IllegalAccessException {
        this.method = method;
        this.handle = getOrCreateHandle(method);
        this.parameterCount = method.getParameterCount();
        this.spreadHandle = createSpreadHandle(handle, parameterCount);
    }

    private static MethodHandle createSpreadHandle(MethodHandle handle, int paramCount) {
        try {
            return handle.asSpreader(1, Object[].class, paramCount);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets or creates MethodHandle.
     *
     * @param method Target method
     * @return MethodHandle
     * @throws IllegalAccessException If method cannot be accessed
     */
    private static MethodHandle getOrCreateHandle(Method method) throws IllegalAccessException {
        MethodHandle cached = HANDLE_CACHE.get(method);
        if (cached != null) {
            return cached;
        }

        // Ensure method is accessible
        method.setAccessible(true);

        MethodHandle handle = LOOKUP.unreflect(method);
        HANDLE_CACHE.put(method, handle);
        return handle;
    }

    /**
     * Clears the cache.
     */
    public static void clearCache() {
        HANDLE_CACHE.clear();
    }

    /**
     * Gets cache size.
     *
     * @return Number of cached MethodHandles
     */
    public static int getCacheSize() {
        return HANDLE_CACHE.size();
    }

    /**
     * Factory method: Creates a method invoker.
     *
     * @param method Target method
     * @return Method invoker
     */
    public static MethodInvoker of(Method method) {
        try {
            return new MethodInvoker(method);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not create method invoker: " + method.getName(), e);
        }
    }

    /**
     * Invokes the method (High performance optimized version).
     *
     * <p>
     * Uses argument count specialization strategy, choosing optimal call method
     * based on argument count:
     * <ul>
     * <li>0-8 arguments: use switch + direct invoke to avoid array allocation</li>
     * <li>9+ arguments: use invokeWithArguments</li>
     * </ul>
     * </p>
     *
     * @param instance Instance object
     * @param args     Method arguments
     * @return Method return value
     * @throws Throwable Exception during invocation
     */
    public Object invoke(Object instance, Object... args) throws Throwable {
        // Argument count specialization: avoid overhead of invokeWithArguments
        // invokeWithArguments requires boxing/unboxing and array operations, direct
        // call is faster
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
                // More than 8 arguments, use spread handle or invokeWithArguments
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
     * Static invocation (no instance).
     *
     * @param args Method arguments
     * @return Method return value
     * @throws Throwable Exception during invocation
     */
    public Object invokeStatic(Object... args) throws Throwable {
        return handle.invokeWithArguments(args);
    }

    /**
     * Gets the original method.
     *
     * @return Original Method object
     */
    public Method getMethod() {
        return method;
    }

    /**
     * Gets the MethodHandle.
     *
     * @return MethodHandle
     */
    public MethodHandle getHandle() {
        return handle;
    }
}

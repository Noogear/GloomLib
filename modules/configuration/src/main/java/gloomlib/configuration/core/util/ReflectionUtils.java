package gloomlib.configuration.core.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class for reflection operations.
 */
public final class ReflectionUtils {

    private ReflectionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a new instance of the given class using the no-arg constructor.
     *
     * @param clazz the class to instantiate
     * @param <T>   the type
     * @return the new instance
     * @throws Exception if instantiation fails
     */
    public static <T> T createInstance(Class<T> clazz) throws Exception {
        try {
            return clazz.getConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            Constructor<T> con = clazz.getDeclaredConstructor();
            con.setAccessible(true);
            return con.newInstance();
        }
    }

    /**
     * Runs annotated hook methods on an instance (no context).
     *
     * @param instance       the instance to invoke methods on
     * @param annotationType the annotation type to scan for
     * @throws Exception if method invocation fails
     */
    public static void runHooks(Object instance, Class<? extends Annotation> annotationType) throws Exception {
        runHooks(instance, annotationType, null);
    }

    /**
     * Runs annotated hook methods on an instance, optionally passing a context
     * object to methods that accept a single parameter.
     * <p>
     * Hook methods are discovered by scanning for the given annotation. They are
     * sorted by the annotation's {@code priority()} attribute (lower = first).
     * </p>
     * <ul>
     *   <li>0-parameter methods → invoked directly</li>
     *   <li>1-parameter methods → invoked with {@code context} (skipped if context is null)</li>
     * </ul>
     *
     * @param instance       the instance to invoke methods on
     * @param annotationType the annotation type to scan for
     * @param context        optional context object for 1-parameter hooks (may be null)
     * @throws Exception if method invocation fails
     */
    public static void runHooks(Object instance, Class<? extends Annotation> annotationType,
                                Object context) throws Exception {
        List<Method> methods = new ArrayList<>();
        for (Method m : instance.getClass().getMethods()) {
            if (m.isAnnotationPresent(annotationType)) {
                methods.add(m);
            }
        }
        methods.sort(Comparator.comparingInt(m -> {
            try {
                return (int) annotationType.getMethod("priority").invoke(m.getAnnotation(annotationType));
            } catch (Exception e) {
                return 0;
            }
        }));
        for (Method m : methods) {
            if (m.getParameterCount() == 0) {
                m.invoke(instance);
            } else if (m.getParameterCount() == 1 && context != null
                    && m.getParameterTypes()[0].isAssignableFrom(context.getClass())) {
                m.invoke(instance, context);
            }
        }
    }

    /**
     * Wraps reflection errors into IllegalAccessException.
     *
     * @param e         throwable to wrap
     * @param operation operation context
     * @return IllegalAccessException instance
     */
    public static IllegalAccessException wrapReflectionException(Throwable e, String operation) {
        if (e instanceof IllegalAccessException iae) {
            return iae;
        }
        return new IllegalAccessException(operation + ": " + e.getMessage());
    }
}



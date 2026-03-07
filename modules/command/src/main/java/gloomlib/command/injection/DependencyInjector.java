package gloomlib.command.injection;

import gloomlib.command.annotation.Inject;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dependency Injector.
 *
 * <p>
 * Manages the registration and injection of service instances.
 * </p>
 */
public class DependencyInjector {

    /**
     * Singleton services stored by type
     */
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    /**
     * Services stored by qualifier (for multiple instances of the same type)
     */
    private final Map<String, Object> qualifiedBeans = new ConcurrentHashMap<>();

    /**
     * Registers a singleton service.
     *
     * @param type     Service type
     * @param instance Service instance
     * @param <T>      Type
     */
    public <T> void registerSingleton(Class<T> type, T instance) {
        singletons.put(type, instance);
    }

    /**
     * Registers a service with a qualifier.
     *
     * @param qualifier Qualifier
     * @param instance  Service instance
     * @param <T>       Type
     */
    public <T> void registerBean(String qualifier, T instance) {
        qualifiedBeans.put(qualifier, instance);
    }

    /**
     * Gets a service instance.
     *
     * @param type Service type
     * @param <T>  Type
     * @return Service instance, or null
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getService(Class<T> type) {
        return (T) singletons.get(type);
    }

    /**
     * Gets a service instance with a qualifier.
     *
     * @param qualifier Qualifier
     * @param <T>       Type
     * @return Service instance, or null
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getService(String qualifier) {
        return (T) qualifiedBeans.get(qualifier);
    }

    /**
     * Injects dependencies into the target object.
     *
     * @param target Target object
     */
    public void injectDependencies(Object target) {
        Class<?> clazz = target.getClass();

        // Iterate through all fields (including superclasses)
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    injectField(target, field);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Injects a single field.
     */
    private void injectField(Object target, Field field) {
        Inject inject = field.getAnnotation(Inject.class);
        String qualifier = inject.value();

        Object dependency;

        if (!qualifier.isEmpty()) {
            // Find by qualifier
            dependency = qualifiedBeans.get(qualifier);
        } else {
            // Find by type
            dependency = findByType(field.getType());
        }

        if (dependency == null) {
            throw new RuntimeException(String.format(
                    "Cannot inject dependency: %s.%s (Type: %s, Qualifier: %s)",
                    field.getDeclaringClass().getName(), field.getName(),
                    field.getType().getName(), qualifier));
        }

        try {
            field.setAccessible(true);
            field.set(target, dependency);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Dependency injection failed", e);
        }
    }

    /**
     * Finds service by type (supports inheritance matching).
     */
    private @Nullable Object findByType(Class<?> type) {
        // Exact match
        Object result = singletons.get(type);
        if (result != null) {
            return result;
        }

        // Inheritance match
        for (Map.Entry<Class<?>, Object> entry : singletons.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Clears all registered services.
     */
    public void clear() {
        singletons.clear();
        qualifiedBeans.clear();
    }
}

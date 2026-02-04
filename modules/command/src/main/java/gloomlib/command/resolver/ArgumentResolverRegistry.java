package gloomlib.command.resolver;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Argument Resolver Registry.
 *
 * <p>
 * Manages resolvers for all argument types, supporting automatic type matching.
 * </p>
 */
public class ArgumentResolverRegistry {

    private final Map<Class<?>, ArgumentResolver<?>> resolvers = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = Map.of(
            int.class, Integer.class,
            long.class, Long.class,
            double.class, Double.class,
            float.class, Float.class,
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            char.class, Character.class);

    /**
     * Registers an argument resolver.
     *
     * @param type     Argument type
     * @param resolver Resolver
     * @param <T>      Type
     */
    public <T> void register(Class<T> type, ArgumentResolver<T> resolver) {
        resolvers.put(type, resolver);
    }

    private final Map<Class<?>, ArgumentResolver<?>> resolverCache = new ConcurrentHashMap<>();

    /**
     * Gets or finds a resolver for the specified type.
     *
     * @param type Argument type
     * @param <T>  Type
     * @return Resolver, or null
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable ArgumentResolver<T> getResolver(Class<T> type) {
        // Try getting from cache
        ArgumentResolver<?> cached = resolverCache.get(type);
        if (cached != null) {
            return (ArgumentResolver<T>) cached;
        }

        ArgumentResolver<?> resolver = findResolver(type);
        if (resolver != null) {
            resolverCache.put(type, resolver);
        }
        return (ArgumentResolver<T>) resolver;
    }

    /**
     * Finds a resolver (no cache).
     */
    @SuppressWarnings("unchecked")
    private <T> ArgumentResolver<T> findResolver(Class<T> type) {
        // 1. Exact match
        ArgumentResolver<?> resolver = resolvers.get(type);
        if (resolver != null) {
            return (ArgumentResolver<T>) resolver;
        }

        // 2. Handle primitive wrapper types
        Class<?> primitiveWrapper = getPrimitiveWrapper(type);
        if (primitiveWrapper != null) {
            resolver = resolvers.get(primitiveWrapper);
            if (resolver != null) {
                return (ArgumentResolver<T>) resolver;
            }
        }

        // 3. Inheritance match (find parent class/interface resolver)
        for (Map.Entry<Class<?>, ArgumentResolver<?>> entry : resolvers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (ArgumentResolver<T>) entry.getValue();
            }
        }

        // 4. Generic Enum handling
        if (type.isEnum()) {
            return (ArgumentResolver<T>) createEnumResolver(type);
        }

        return null;
    }

    /**
     * Clears the cache.
     */
    public void clearCache() {
        resolverCache.clear();
    }

    /**
     * Checks if the type is supported.
     *
     * @param type Argument type
     * @return true if supported
     */
    public boolean hasResolver(Class<?> type) {
        return getResolver(type) != null;
    }

    /**
     * Gets all registered resolvers.
     *
     * @return Resolver map
     */
    public Map<Class<?>, ArgumentResolver<?>> getAllResolvers() {
        return Map.copyOf(resolvers);
    }

    /**
     * Gets the wrapper class for a primitive type.
     */
    private @Nullable Class<?> getPrimitiveWrapper(Class<?> type) {
        return PRIMITIVE_WRAPPERS.get(type);
    }

    /**
     * Creates a generic resolver for Enum types.
     */
    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> ArgumentResolver<?> createEnumResolver(Class<?> enumType) {
        return new EnumArgumentResolver<>((Class<E>) enumType);
    }
}

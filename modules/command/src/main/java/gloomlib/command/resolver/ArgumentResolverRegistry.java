package gloomlib.command.resolver;

import gloomlib.command.resolver.registry.BrigadierResolver;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Argument Resolver Registry with 3-level cache strategy.
 *
 * <h2>Cache Strategy</h2>
 * <pre>
 * Level 1: Direct type lookup → resolvers.get(type)
 *    ↓ (miss)
 * Level 2: Primitive wrapper lookup → PRIMITIVE_WRAPPERS.get(type)
 *    ↓ (miss)
 * Level 3: Assignable type scan → entry.getKey().isAssignableFrom(type)
 *    ↓ (miss)
 * Fallback: Enum resolver creation (if type.isEnum())
 * </pre>
 *
 * @implNote Performance:
 * <ul>
 * <li><b>Cache hit</b>: O(1) lookup via resolverCache</li>
 * <li><b>Cache miss</b>: O(n) scan of registered resolvers, then cached</li>
 * <li><b>Thread safety</b>: ConcurrentHashMap for lock-free reads</li>
 * </ul>
 */
public class ArgumentResolverRegistry {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(ArgumentResolverRegistry.class);
    private static final String MSG_CACHE_CLEAR_ERROR = "Failed to clear cache for resolver: {}";
    private static final String MSG_ENUM_RESOLVER_CREATED = "Created enum resolver: {}";

    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = Map.of(
            int.class, Integer.class,
            long.class, Long.class,
            double.class, Double.class,
            float.class, Float.class,
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            char.class, Character.class);
    private final Map<Class<?>, ArgumentResolver<?>> resolvers = new ConcurrentHashMap<>();
    private final Map<Class<?>, ArgumentResolver<?>> resolverCache = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, ArgumentResolver<T> resolver) {
        resolvers.put(type, resolver);
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable ArgumentResolver<T> getResolver(Class<T> type) {
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

    @SuppressWarnings("unchecked")
    private <T> ArgumentResolver<T> findResolver(Class<T> type) {
        ArgumentResolver<?> resolver = resolvers.get(type);
        if (resolver != null) {
            return (ArgumentResolver<T>) resolver;
        }

        Class<?> primitiveWrapper = getPrimitiveWrapper(type);
        if (primitiveWrapper != null) {
            resolver = resolvers.get(primitiveWrapper);
            if (resolver != null) {
                return (ArgumentResolver<T>) resolver;
            }
        }

        for (Map.Entry<Class<?>, ArgumentResolver<?>> entry : resolvers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (ArgumentResolver<T>) entry.getValue();
            }
        }

        if (type.isEnum()) {
            return (ArgumentResolver<T>) createEnumResolver(type);
        }

        return null;
    }

    public void clearCache() {
        resolverCache.clear();
    }

    public void clearAllResolverCaches() {
        for (ArgumentResolver<?> resolver : resolvers.values()) {
            try {
                resolver.clearCache();
            } catch (Exception e) {
                LOGGER.debug(MSG_CACHE_CLEAR_ERROR, resolver.getClass().getSimpleName(), e);
            }
        }
    }

    public boolean hasResolver(Class<?> type) {
        return getResolver(type) != null;
    }

    public Map<Class<?>, ArgumentResolver<?>> getAllResolvers() {
        return Map.copyOf(resolvers);
    }

    private @Nullable Class<?> getPrimitiveWrapper(Class<?> type) {
        return PRIMITIVE_WRAPPERS.get(type);
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> ArgumentResolver<?> createEnumResolver(Class<?> enumType) {
        Class<E> enumClass = (Class<E>) enumType;

        ArgumentResolver<?> resolver = BrigadierResolver.of(
                enumClass,
                param -> com.mojang.brigadier.arguments.StringArgumentType.word()
        );

        LOGGER.debug(MSG_ENUM_RESOLVER_CREATED, enumType.getSimpleName());
        return resolver;
    }
}

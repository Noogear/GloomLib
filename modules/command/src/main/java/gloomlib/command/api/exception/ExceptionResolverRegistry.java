package gloomlib.command.api.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Global registry for {@link ExceptionResolver} fallback handlers.
 *
 * <p>
 * When a command method throws an exception and no matching {@code @OnError} handler
 * is found in the command class, the executor falls back to resolvers registered here.
 * This allows plugin-wide exception handling in one place without repeating error logic
 * in every command class.
 * </p>
 *
 * <h2>Registration</h2>
 * <pre>{@code
 * gloom.registerExceptionHandler(InsufficientFundsException.class, (ctx, ex) ->
 *     ctx.sendMessage("<red>You don't have enough funds: " + ex.required())
 * );
 * }</pre>
 *
 * <p>
 * Resolution order for a thrown exception of type {@code T}:
 * <ol>
 *   <li>Exact type match ({@code handlers.get(T.class)})</li>
 *   <li>First registered type that is a supertype of {@code T}</li>
 *   <li>{@code null} — executor falls through to built-in default handling</li>
 * </ol>
 * </p>
 */
public class ExceptionResolverRegistry {

    private final Map<Class<? extends Throwable>, ExceptionResolver<?>> handlers = new ConcurrentHashMap<>();

    /**
     * Registers a resolver for the given exception class.
     * Replaces any previously registered resolver for this exact type.
     *
     * @param <T>      Exception type
     * @param type     Exception class to handle
     * @param resolver Resolver to invoke when an exception of {@code type} is thrown
     */
    public <T extends Throwable> void register(
            @NotNull Class<T> type,
            @NotNull ExceptionResolver<T> resolver) {
        handlers.put(type, resolver);
    }

    /**
     * Resolves the best-matching resolver for the given exception type.
     *
     * <p>First tries an exact match, then scans for an assignable (supertype) match.</p>
     *
     * @param <T>  Exception type
     * @param type Runtime class of the thrown exception
     * @return Matching resolver, or {@code null} if none registered
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends Throwable> ExceptionResolver<T> getResolver(@NotNull Class<T> type) {
        // 1. Exact match
        ExceptionResolver<?> resolver = handlers.get(type);
        if (resolver != null) return (ExceptionResolver<T>) resolver;

        // 2. Supertype match (first assignable wins)
        for (Map.Entry<Class<? extends Throwable>, ExceptionResolver<?>> entry : handlers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (ExceptionResolver<T>) entry.getValue();
            }
        }
        return null;
    }

    /**
     * Removes the resolver for the given exception type.
     *
     * @param type Exception class to deregister
     */
    public void unregister(@NotNull Class<? extends Throwable> type) {
        handlers.remove(type);
    }

    /** Removes all registered resolvers. */
    public void clear() {
        handlers.clear();
    }
}

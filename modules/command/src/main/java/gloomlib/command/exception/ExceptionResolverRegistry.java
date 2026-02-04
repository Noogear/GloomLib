package gloomlib.command.exception;

import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.message.CommandMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exception Resolver Registry.
 *
 * <p>
 * Manages global exception handlers.
 * </p>
 */
public class ExceptionResolverRegistry {

    private final Map<Class<? extends Throwable>, ExceptionResolver<?>> resolvers = new ConcurrentHashMap<>();

    /**
     * Registers an exception resolver.
     *
     * @param exceptionType Exception type
     * @param resolver      Resolver
     * @param <T>           Exception type
     */
    public <T extends Throwable> void register(Class<T> exceptionType, ExceptionResolver<T> resolver) {
        resolvers.put(exceptionType, resolver);
    }

    /**
     * Resolves an exception.
     *
     * @param context   Command context
     * @param exception Exception
     * @return True if handled
     */
    @SuppressWarnings("unchecked")
    public boolean resolve(GloomCommandContext context, Throwable exception) {
        Class<? extends Throwable> exceptionClass = exception.getClass();

        // Exact match
        ExceptionResolver<?> resolver = resolvers.get(exceptionClass);

        if (resolver == null) {
            // Inheritance match
            for (Map.Entry<Class<? extends Throwable>, ExceptionResolver<?>> entry : resolvers.entrySet()) {
                if (entry.getKey().isAssignableFrom(exceptionClass)) {
                    resolver = entry.getValue();
                    break;
                }
            }
        }

        if (resolver != null) {
            ((ExceptionResolver<Throwable>) resolver).resolve(context, exception);
            return true;
        }

        return false;
    }

    /**
     * Gets an exception resolver.
     *
     * @param exceptionType Exception type
     * @param <T>           Exception type
     * @return Resolver, or null
     */
    @SuppressWarnings("unchecked")
    public <T extends Throwable> @Nullable ExceptionResolver<T> getResolver(Class<T> exceptionType) {
        return (ExceptionResolver<T>) resolvers.get(exceptionType);
    }

    /**
     * Registers default resolvers.
     */
    public void registerDefaults() {
        // CommandException Resolver
        register(CommandException.class, (ctx, ex) -> {
            ctx.getSender().sendMessage(ex.getAdventureMessage());
        });

        // IllegalArgumentException Resolver
        register(IllegalArgumentException.class, (ctx, ex) -> {
            ctx.getSender().sendMessage(
                    CommandMessages.COMMAND_UNKNOWN_ARG.get()
                            .hoverEvent(Component.text(ex.getMessage(), NamedTextColor.GRAY)));
        });

        // General Exception Resolver
        register(Exception.class, (ctx, ex) -> {
            ctx.getSender().sendMessage(
                    CommandMessages.COMMAND_FAILED.get()
                            .hoverEvent(Component.text(ex.getMessage(), NamedTextColor.GRAY)));
            ex.printStackTrace();
        });
    }
}

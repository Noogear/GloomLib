package gloomlib.command.api.exception;

import gloomlib.command.api.context.GloomCommandContext;

/**
 * Exception Resolver Interface.
 *
 * <p>
 * Used to handle specific exceptions during command execution.
 * </p>
 *
 * @param <T> Exception type
 */
@FunctionalInterface
public interface ExceptionResolver<T extends Throwable> {

    /**
     * Resolves an exception.
     *
     * @param context   Command context
     * @param exception Exception
     */
    void resolve(GloomCommandContext context, T exception);
}

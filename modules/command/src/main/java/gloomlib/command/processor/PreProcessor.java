package gloomlib.command.processor;

import gloomlib.command.context.GloomCommandContext;

/**
 * Command Pre-Processor Interface.
 *
 * <p>
 * Called before command execution, can be used for permission checks, cooldown
 * checks, logging, etc.
 * </p>
 */
@FunctionalInterface
public interface PreProcessor extends CommandProcessor {

    /**
     * Pre-processing result enum.
     */
    enum Result {
        /** Continue execution */
        CONTINUE,
        /** Halt execution (no error message sent) */
        HALT,
        /** Halt execution (error message already sent) */
        HANDLED
    }

    /**
     * Executes pre-processing.
     *
     * @param context Command context
     * @return Processing result
     */
    Result preProcess(GloomCommandContext context);
}

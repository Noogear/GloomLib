package gloomlib.command.processor;

import gloomlib.command.context.CommandResult;
import gloomlib.command.context.GloomCommandContext;

/**
 * Command Post-Processor Interface.
 *
 * <p>
 * Called after command execution, can be used for logging, statistics, cleanup,
 * etc.
 * </p>
 */
@FunctionalInterface
public interface PostProcessor extends CommandProcessor {

    /**
     * Executes post-processing.
     *
     * @param context Command context
     * @param result  Command execution result
     */
    void postProcess(GloomCommandContext context, CommandResult result);
}

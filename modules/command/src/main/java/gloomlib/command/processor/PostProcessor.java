package gloomlib.command.processor;

import gloomlib.command.context.CommandResult;
import gloomlib.command.context.GloomCommandContext;

/**
 * Post-processor for command execution logging and cleanup.
 */
@FunctionalInterface
public interface PostProcessor extends CommandProcessor {

    void postProcess(GloomCommandContext context, CommandResult result);
}

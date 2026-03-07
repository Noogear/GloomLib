package gloomlib.command.api.processor;

import gloomlib.command.api.context.CommandResult;
import gloomlib.command.api.context.GloomCommandContext;

/**
 * Post-processor for command execution logging and cleanup.
 */
@FunctionalInterface
public interface PostProcessor extends CommandProcessor {

    void postProcess(GloomCommandContext context, CommandResult result);
}

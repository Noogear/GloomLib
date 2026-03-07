package gloomlib.command.api.processor;

import gloomlib.command.api.context.GloomCommandContext;

/**
 * Pre-processor for command execution validation and setup.
 */
@FunctionalInterface
public interface PreProcessor extends CommandProcessor {

    Result preProcess(GloomCommandContext context);

    enum Result {
        CONTINUE,
        HALT,
        HANDLED
    }
}

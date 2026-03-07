package gloomlib.command.core.processor;

import gloomlib.command.api.context.CommandResult;
import gloomlib.command.api.context.GloomCommandContext;
import gloomlib.command.api.exception.CommandException;
import gloomlib.command.api.processor.PostProcessor;
import gloomlib.command.api.processor.PreProcessor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Executes pre/post processors in priority order.
 *
 * <h2>Execution Flow</h2>
 * <pre>
 * PreProcessors (priority: 0 → 1000)
 *    ├─> PermissionProcessor (0)
 *    ├─> CooldownProcessor (100)
 *    ├─> ValidationProcessor (200)
 *    └─> LoggingProcessor (1000)
 *    ↓
 * HALT/HANDLED → Stop | CONTINUE → Method Invocation
 *    ↓
 * PostProcessors (priority order)
 * </pre>
 */
public class ProcessorPipeline {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(ProcessorPipeline.class);

    private static final Comparator<PreProcessor> PRE_PROCESSOR_COMPARATOR = Comparator
            .comparingInt(PreProcessor::getPriority);
    private static final Comparator<PostProcessor> POST_PROCESSOR_COMPARATOR = Comparator
            .comparingInt(PostProcessor::getPriority);

    private final List<PreProcessor> preProcessors = new ArrayList<>();
    private final List<PostProcessor> postProcessors = new ArrayList<>();

    public void registerPreProcessor(PreProcessor processor) {
        preProcessors.add(processor);
        preProcessors.sort(PRE_PROCESSOR_COMPARATOR);
    }

    public void registerPostProcessor(PostProcessor processor) {
        postProcessors.add(processor);
        postProcessors.sort(POST_PROCESSOR_COMPARATOR);
    }

    public boolean runPreProcessors(GloomCommandContext context) {
        for (PreProcessor processor : preProcessors) {
            try {
                PreProcessor.Result result = processor.preProcess(context);
                if (result == PreProcessor.Result.HALT) {
                    return false;
                }
                if (result == PreProcessor.Result.HANDLED) {
                    return false;
                }
            } catch (CommandException e) {
                context.sendMessage(e.getAdventureMessage());
                return false;
            } catch (Exception e) {
                LOGGER.debug("PreProcessor execution failed", e);
                return false;
            }
        }
        return true;
    }

    public void runPostProcessors(GloomCommandContext context, CommandResult result) {
        for (PostProcessor processor : postProcessors) {
            try {
                processor.postProcess(context, result);
            } catch (Exception e) {
                LOGGER.debug("PostProcessor execution failed", e);
            }
        }
    }

    public List<PreProcessor> getPreProcessors() {
        return Collections.unmodifiableList(preProcessors);
    }

    public List<PostProcessor> getPostProcessors() {
        return Collections.unmodifiableList(postProcessors);
    }
}

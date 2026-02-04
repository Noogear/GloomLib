package gloomlib.command.processor;

import gloomlib.command.context.CommandResult;
import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.exception.CommandException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Command Processor Pipeline.
 *
 * <p>
 * Manages and executes command pre-processing and post-processing logic.
 * Processors are executed in priority order.
 * </p>
 */
public class ProcessorPipeline {

    private static final Comparator<PreProcessor> PRE_PROCESSOR_COMPARATOR = Comparator
            .comparingInt(PreProcessor::getPriority);
    private static final Comparator<PostProcessor> POST_PROCESSOR_COMPARATOR = Comparator
            .comparingInt(PostProcessor::getPriority);

    private final List<PreProcessor> preProcessors = new ArrayList<>();
    private final List<PostProcessor> postProcessors = new ArrayList<>();

    /**
     * Registers a pre-processor.
     *
     * @param processor Pre-processor
     */
    public void registerPreProcessor(PreProcessor processor) {
        preProcessors.add(processor);
        preProcessors.sort(PRE_PROCESSOR_COMPARATOR);
    }

    /**
     * Registers a post-processor.
     *
     * @param processor Post-processor
     */
    public void registerPostProcessor(PostProcessor processor) {
        postProcessors.add(processor);
        postProcessors.sort(POST_PROCESSOR_COMPARATOR);
    }

    /**
     * Executes the pre-processing pipeline.
     *
     * @param context Command context
     * @return Whether to continue execution
     */
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
                // Processor throws CommandException, send message and stop
                context.sendMessage(e.getAdventureMessage());
                return false;
            } catch (Exception e) {
                // Processor internal error, print stack trace and stop
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    /**
     * Executes the post-processing pipeline.
     *
     * @param context Command context
     * @param result  Command execution result
     */
    public void runPostProcessors(GloomCommandContext context, CommandResult result) {
        for (PostProcessor processor : postProcessors) {
            try {
                processor.postProcess(context, result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets all pre-processors (read-only).
     *
     * @return List of pre-processors
     */
    public List<PreProcessor> getPreProcessors() {
        return Collections.unmodifiableList(preProcessors);
    }

    /**
     * Gets all post-processors (read-only).
     *
     * @return List of post-processors
     */
    public List<PostProcessor> getPostProcessors() {
        return Collections.unmodifiableList(postProcessors);
    }
}

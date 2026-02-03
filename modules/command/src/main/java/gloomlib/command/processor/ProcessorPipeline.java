package gloomlib.command.processor;

import gloomlib.command.context.CommandResult;
import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.exception.CommandException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 命令处理器管道。
 *
 * <p>
 * 管理并执行命令的预处理和后处理逻辑。
 * 处理器按优先级排序执行。
 * </p>
 */
public class ProcessorPipeline {

    private final List<PreProcessor> preProcessors = new ArrayList<>();
    private final List<PostProcessor> postProcessors = new ArrayList<>();

    /**
     * 注册预处理器。
     *
     * @param processor 预处理器
     */
    public void registerPreProcessor(PreProcessor processor) {
        preProcessors.add(processor);
        preProcessors.sort(Comparator.comparingInt(PreProcessor::getPriority));
    }

    /**
     * 注册后处理器。
     *
     * @param processor 后处理器
     */
    public void registerPostProcessor(PostProcessor processor) {
        postProcessors.add(processor);
        postProcessors.sort(Comparator.comparingInt(PostProcessor::getPriority));
    }

    /**
     * 执行预处理管道。
     *
     * @param context 命令上下文
     * @return 是否继续执行
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
                // 处理器抛出命令异常，发送消息并停止
                context.sendMessage(e.getAdventureMessage());
                return false;
            } catch (Exception e) {
                // 处理器内部错误，打印堆栈并停止
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    /**
     * 执行后处理管道。
     *
     * @param context 命令上下文
     * @param result  命令执行结果
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
     * 获取所有预处理器（只读）。
     *
     * @return 预处理器列表
     */
    public List<PreProcessor> getPreProcessors() {
        return Collections.unmodifiableList(preProcessors);
    }

    /**
     * 获取所有后处理器（只读）。
     *
     * @return 后处理器列表
     */
    public List<PostProcessor> getPostProcessors() {
        return Collections.unmodifiableList(postProcessors);
    }
}

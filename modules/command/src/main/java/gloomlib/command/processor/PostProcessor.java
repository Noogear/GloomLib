package gloomlib.command.processor;

import gloomlib.command.context.CommandResult;
import gloomlib.command.context.GloomCommandContext;

/**
 * 命令后处理器接口。
 *
 * <p>
 * 在命令执行后调用，可用于日志记录、统计、清理等。
 * </p>
 */
@FunctionalInterface
public interface PostProcessor extends CommandProcessor {

    /**
     * 执行后处理。
     *
     * @param context 命令上下文
     * @param result  命令执行结果
     */
    void postProcess(GloomCommandContext context, CommandResult result);
}

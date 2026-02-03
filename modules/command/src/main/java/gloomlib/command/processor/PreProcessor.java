package gloomlib.command.processor;

import gloomlib.command.context.GloomCommandContext;

/**
 * 命令预处理器接口。
 *
 * <p>
 * 在命令执行前调用，可用于权限检查、冷却检查、日志记录等。
 * </p>
 */
@FunctionalInterface
public interface PreProcessor extends CommandProcessor {

    /**
     * 预处理结果枚举。
     */
    enum Result {
        /** 继续执行 */
        CONTINUE,
        /** 停止执行（不发送错误消息） */
        HALT,
        /** 停止执行（已发送错误消息） */
        HANDLED
    }

    /**
     * 执行预处理。
     *
     * @param context 命令上下文
     * @return 处理结果
     */
    Result preProcess(GloomCommandContext context);
}

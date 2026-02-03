package gloomlib.command.processor;

import gloomlib.command.context.GloomCommandContext;

/**
 * 命令处理器接口。
 *
 * <p>
 * 处理器可以在命令执行前后进行处理。
 * </p>
 */
public interface CommandProcessor {

    /**
     * 获取处理器优先级。
     * 数值越小优先级越高。
     *
     * @return 优先级
     */
    default int getPriority() {
        return 0;
    }
}

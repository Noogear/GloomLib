package gloomlib.command.exception;

import gloomlib.command.context.GloomCommandContext;

/**
 * 异常解析器接口。
 *
 * <p>
 * 用于处理命令执行过程中的特定异常。
 * </p>
 *
 * @param <T> 异常类型
 */
@FunctionalInterface
public interface ExceptionResolver<T extends Throwable> {

    /**
     * 解析异常。
     *
     * @param context   命令上下文
     * @param exception 异常
     */
    void resolve(GloomCommandContext context, T exception);
}

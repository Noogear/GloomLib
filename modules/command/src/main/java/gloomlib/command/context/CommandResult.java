package gloomlib.command.context;

import org.jetbrains.annotations.Nullable;

/**
 * 命令执行结果。
 */
public class CommandResult {

    /** 成功结果 */
    public static final CommandResult SUCCESS = new CommandResult(true, null, null);

    /** 失败结果（无错误信息） */
    public static final CommandResult FAILURE = new CommandResult(false, null, null);

    private final boolean success;
    private final @Nullable Object returnValue;
    private final @Nullable Throwable exception;

    private CommandResult(boolean success, @Nullable Object returnValue, @Nullable Throwable exception) {
        this.success = success;
        this.returnValue = returnValue;
        this.exception = exception;
    }

    /**
     * 创建成功结果。
     *
     * @return 成功结果
     */
    public static CommandResult success() {
        return SUCCESS;
    }

    /**
     * 创建带返回值的成功结果。
     *
     * @param returnValue 返回值
     * @return 成功结果
     */
    public static CommandResult success(@Nullable Object returnValue) {
        return new CommandResult(true, returnValue, null);
    }

    /**
     * 创建失败结果。
     *
     * @return 失败结果
     */
    public static CommandResult failure() {
        return FAILURE;
    }

    /**
     * 创建带异常的失败结果。
     *
     * @param exception 异常
     * @return 失败结果
     */
    public static CommandResult failure(Throwable exception) {
        return new CommandResult(false, null, exception);
    }

    /**
     * 检查命令是否执行成功。
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 检查命令是否执行失败。
     *
     * @return 是否失败
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * 获取返回值。
     *
     * @return 返回值，或 null
     */
    public @Nullable Object getReturnValue() {
        return returnValue;
    }

    /**
     * 获取异常。
     *
     * @return 异常，或 null
     */
    public @Nullable Throwable getException() {
        return exception;
    }

    /**
     * 检查是否有异常。
     *
     * @return 是否有异常
     */
    public boolean hasException() {
        return exception != null;
    }
}

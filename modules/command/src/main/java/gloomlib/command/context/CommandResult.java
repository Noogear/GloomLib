package gloomlib.command.context;

import org.jetbrains.annotations.Nullable;

/**
 * Command Execution Result.
 */
public class CommandResult {

    /** Success result */
    public static final CommandResult SUCCESS = new CommandResult(true, null, null);

    /** Failure result (no error message) */
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
     * Creates a success result.
     *
     * @return Success result
     */
    public static CommandResult success() {
        return SUCCESS;
    }

    /**
     * Creates a success result with a return value.
     *
     * @param returnValue Return value
     * @return Success result
     */
    public static CommandResult success(@Nullable Object returnValue) {
        return new CommandResult(true, returnValue, null);
    }

    /**
     * Creates a failure result.
     *
     * @return Failure result
     */
    public static CommandResult failure() {
        return FAILURE;
    }

    /**
     * Creates a failure result with an exception.
     *
     * @param exception Exception
     * @return Failure result
     */
    public static CommandResult failure(Throwable exception) {
        return new CommandResult(false, null, exception);
    }

    /**
     * Checks if command executed successfully.
     *
     * @return True if success
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Checks if command execution failed.
     *
     * @return True if failed
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * Gets return value.
     *
     * @return Return value, or null
     */
    public @Nullable Object getReturnValue() {
        return returnValue;
    }

    /**
     * Gets exception.
     *
     * @return Exception, or null
     */
    public @Nullable Throwable getException() {
        return exception;
    }

    /**
     * Checks if there is an exception.
     *
     * @return True if exception exists
     */
    public boolean hasException() {
        return exception != null;
    }
}

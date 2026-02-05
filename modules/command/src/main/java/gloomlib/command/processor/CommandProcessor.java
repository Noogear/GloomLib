package gloomlib.command.processor;

/**
 * Command Processor Interface.
 *
 * <p>
 * Processors can perform operations before or after command execution.
 * </p>
 */
public interface CommandProcessor {

    /**
     * Gets processor priority.
     * Lower value means higher priority.
     *
     * @return Priority
     */
    default int getPriority() {
        return 0;
    }
}

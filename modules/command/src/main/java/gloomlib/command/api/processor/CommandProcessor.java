package gloomlib.command.api.processor;

/**
 * Base interface for command processors.
 */
public interface CommandProcessor {

    /**
     * Gets processor priority (lower = higher priority).
     */
    default int getPriority() {
        return 0;
    }
}

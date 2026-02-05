package gloomlib.command.exception;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Command Exception Base Class.
 *
 * <p>
 * All exceptions thrown by the command framework should extend this class.
 * </p>
 */
public class CommandException extends RuntimeException {

    private final @Nullable Component adventureMessage;

    /**
     * Creates a command exception.
     *
     * @param message Error message
     */
    public CommandException(String message) {
        super(message);
        this.adventureMessage = null;
    }

    /**
     * Creates a command exception (Adventure Component message).
     *
     * @param message Adventure component message
     */
    public CommandException(Component message) {
        super(componentToString(message));
        this.adventureMessage = message;
    }

    /**
     * Creates a command exception (with cause).
     *
     * @param message Error message
     * @param cause   Cause
     */
    public CommandException(String message, Throwable cause) {
        super(message, cause);
        this.adventureMessage = null;
    }

    /**
     * Creates a command exception (Adventure message + cause).
     *
     * @param message Adventure component message
     * @param cause   Cause
     */
    public CommandException(Component message, Throwable cause) {
        super(componentToString(message), cause);
        this.adventureMessage = message;
    }

    /**
     * Simple Component to String converter (for super constructor).
     */
    private static String componentToString(Component component) {
        // Simplified implementation, using Adventure's plain text serializer
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText()
                .serialize(component);
    }

    /**
     * Gets the Adventure component message.
     * If the exception was created with a string, returns a plain text component.
     *
     * @return Adventure component message
     */
    public Component getAdventureMessage() {
        if (adventureMessage != null) {
            return adventureMessage;
        }
        return Component.text(getMessage() != null ? getMessage() : "Unknown Error");
    }

    /**
     * Checks if there is an Adventure message.
     *
     * @return True if Adventure message exists
     */
    public boolean hasAdventureMessage() {
        return adventureMessage != null;
    }
}

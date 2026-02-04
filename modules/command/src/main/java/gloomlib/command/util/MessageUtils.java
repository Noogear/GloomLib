package gloomlib.command.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Message utility class.
 *
 * <p>
 * Provides unified MiniMessage instance and message formatting methods.
 * </p>
 */
public final class MessageUtils {

    /** Global MiniMessage instance (Thread-safe) */
    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Deserializes a MiniMessage formatted string.
     *
     * @param message Message template
     * @return Component object
     */
    public static Component deserialize(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    /**
     * Deserializes a message with placeholders.
     *
     * @param message   Message template
     * @param resolvers Placeholder resolvers
     * @return Component object
     */
    public static Component deserialize(String message, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(message, resolvers);
    }

    /**
     * Creates an error message.
     *
     * @param message Error info
     * @return Red error message Component
     */
    public static Component createErrorMessage(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    /**
     * Creates a success message.
     *
     * @param message Success info
     * @return Green success message Component
     */
    public static Component createSuccessMessage(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    /**
     * Creates a warning message.
     *
     * @param message Warning info
     * @return Yellow warning message Component
     */
    public static Component createWarningMessage(String message) {
        return Component.text(message, NamedTextColor.YELLOW);
    }

    /**
     * Creates an info message.
     *
     * @param message Info content
     * @return Aqua info message Component
     */
    public static Component createInfoMessage(String message) {
        return Component.text(message, NamedTextColor.AQUA);
    }
}

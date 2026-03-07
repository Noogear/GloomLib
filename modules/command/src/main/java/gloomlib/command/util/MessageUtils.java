package gloomlib.command.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Modern message utility using MiniMessage.
 *
 * <p>
 * Provides a global MiniMessage instance and MiniMessage deserialization methods.
 * All other formatting should use MiniMessage tags directly.
 * </p>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * {@code
 * // Simple message
 * Component msg = MessageUtils.deserialize("<red>Error!</red>");
 *
 * // With placeholders (use unparsed for safety)
 * Component msg = MessageUtils.deserialize(
 *     "<gold>Hello <player>!</gold>",
 *     Placeholder.unparsed("player", playerName)
 * );
 * }
 * </pre>
 */
public final class MessageUtils {

    /**
     * Global MiniMessage instance (Thread-safe and reusable).
     * Reusing this instance improves performance.
     */
    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Deserializes a MiniMessage formatted string.
     *
     * @param message Message template with MiniMessage tags
     * @return Component object
     */
    public static Component deserialize(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    /**
     * Deserializes a message with placeholders.
     *
     * <p>
     * <b>Security Note:</b> Always use {@code Placeholder.unparsed()} for user input
     * to prevent MiniMessage injection attacks.
     * </p>
     *
     * @param message   Message template
     * @param resolvers Placeholder resolvers
     * @return Component object
     */
    public static Component deserialize(String message, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(message, resolvers);
    }
}

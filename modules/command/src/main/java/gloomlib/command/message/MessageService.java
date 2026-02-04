package gloomlib.command.message;

import gloomlib.command.util.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Message Service (based on Adventure API).
 *
 * <p>
 * Provides unified message sending and formatting capabilities, supporting
 * MiniMessage format.
 * </p>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * MessageService messages = new MessageService();
 * messages.setMessage("teleport.success", "<green>Teleported to <yellow>{target}</yellow>!</green>");
 *
 * messages.send(player, "teleport.success", "target", "Steve");
 * }</pre>
 */
public class MessageService {

    private final Map<String, String> messages = new HashMap<>();

    public static final Component DEFAULT_SUCCESS_PREFIX = Component.text("✓ ", NamedTextColor.GREEN);
    public static final Component DEFAULT_ERROR_PREFIX = Component.text("✗ ", NamedTextColor.RED);
    public static final Component DEFAULT_WARNING_PREFIX = Component.text("⚠ ", NamedTextColor.YELLOW);
    public static final Component DEFAULT_INFO_PREFIX = Component.text("ℹ ", NamedTextColor.AQUA);

    /** Success prefix */
    private Component successPrefix = DEFAULT_SUCCESS_PREFIX;

    /** Error prefix */
    private Component errorPrefix = DEFAULT_ERROR_PREFIX;

    /** Warning prefix */
    private Component warningPrefix = DEFAULT_WARNING_PREFIX;

    /** Info prefix */
    private Component infoPrefix = DEFAULT_INFO_PREFIX;

    /**
     * Sets message template.
     *
     * @param key     Message key
     * @param message MiniMessage format template
     */
    public void setMessage(String key, String message) {
        messages.put(key, message);
    }

    /**
     * Sets message templates in batch.
     *
     * @param messages Message map
     */
    public void setMessages(Map<String, String> messages) {
        this.messages.putAll(messages);
    }

    /**
     * Gets message template.
     *
     * @param key Message key
     * @return Message template, or null
     */
    public String getMessage(String key) {
        return messages.get(key);
    }

    /**
     * Sends message.
     *
     * @param sender       Receiver
     * @param key          Message key
     * @param placeholders Placeholders (key-value pairs: key1, value1, key2,
     *                     value2...)
     */
    public void send(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = parse(template, placeholders);
        sender.sendMessage(message);
    }

    /**
     * Sends success message (with prefix).
     *
     * @param sender       Receiver
     * @param key          Message key
     * @param placeholders Placeholders
     */
    public void success(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = successPrefix.append(parse(template, placeholders));
        sender.sendMessage(message);
    }

    /**
     * Sends error message (with prefix).
     *
     * @param sender       Receiver
     * @param key          Message key
     * @param placeholders Placeholders
     */
    public void error(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = errorPrefix.append(parse(template, placeholders));
        sender.sendMessage(message);
    }

    /**
     * Sends warning message (with prefix).
     *
     * @param sender       Receiver
     * @param key          Message key
     * @param placeholders Placeholders
     */
    public void warning(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = warningPrefix.append(parse(template, placeholders));
        sender.sendMessage(message);
    }

    /**
     * Sends info message (with prefix).
     *
     * @param sender       Receiver
     * @param key          Message key
     * @param placeholders Placeholders
     */
    public void info(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = infoPrefix.append(parse(template, placeholders));
        sender.sendMessage(message);
    }

    /**
     * Sends raw MiniMessage string.
     *
     * @param sender       Receiver
     * @param miniMessage  MiniMessage format string
     * @param placeholders Placeholders
     */
    public void sendRaw(CommandSender sender, String miniMessage, Object... placeholders) {
        sender.sendMessage(parse(miniMessage, placeholders));
    }

    /**
     * Sends raw Component.
     *
     * @param sender    Receiver
     * @param component Adventure Component
     */
    public void send(CommandSender sender, Component component) {
        sender.sendMessage(component);
    }

    /**
     * Parses MiniMessage format string.
     *
     * @param template     Template
     * @param placeholders Placeholders
     * @return Adventure Component
     */
    public Component parse(String template, Object... placeholders) {
        if (placeholders.length == 0) {
            return MessageUtils.MINI_MESSAGE.deserialize(template);
        }

        List<TagResolver> resolvers = new ArrayList<>();

        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String key = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];

            if (value instanceof Component component) {
                resolvers.add(Placeholder.component(key, component));
            } else {
                resolvers.add(Placeholder.unparsed(key, String.valueOf(value)));
            }
        }

        return MessageUtils.MINI_MESSAGE.deserialize(template, resolvers.toArray(TagResolver[]::new));
    }

    /**
     * Sets success prefix.
     *
     * @param prefix Prefix component
     */
    public void setSuccessPrefix(Component prefix) {
        this.successPrefix = prefix;
    }

    /**
     * Sets error prefix.
     *
     * @param prefix Prefix component
     */
    public void setErrorPrefix(Component prefix) {
        this.errorPrefix = prefix;
    }

    /**
     * Sets warning prefix.
     *
     * @param prefix Prefix component
     */
    public void setWarningPrefix(Component prefix) {
        this.warningPrefix = prefix;
    }

    /**
     * Sets info prefix.
     *
     * @param prefix Prefix component
     */
    public void setInfoPrefix(Component prefix) {
        this.infoPrefix = prefix;
    }
}

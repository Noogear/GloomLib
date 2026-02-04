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
 * 消息服务（基于 Adventure API）。
 *
 * <p>
 * 提供统一的消息发送和格式化功能，支持 MiniMessage 格式。
 * </p>
 *
 * <h2>用法示例</h2>
 * 
 * <pre>{@code
 * MessageService messages = new MessageService();
 * messages.setMessage("teleport.success", "<green>已传送到 <yellow>{target}</yellow>！</green>");
 *
 * messages.send(player, "teleport.success", "target", "Steve");
 * }</pre>
 */
public class MessageService {

    private final Map<String, String> messages = new HashMap<>();

    /** 成功前缀 */
    private Component successPrefix = Component.text("✓ ", NamedTextColor.GREEN);

    /** 错误前缀 */
    private Component errorPrefix = Component.text("✗ ", NamedTextColor.RED);

    /** 警告前缀 */
    private Component warningPrefix = Component.text("⚠ ", NamedTextColor.YELLOW);

    /** 信息前缀 */
    private Component infoPrefix = Component.text("ℹ ", NamedTextColor.AQUA);

    /**
     * 设置消息模板。
     *
     * @param key     消息键
     * @param message MiniMessage 格式的消息模板
     */
    public void setMessage(String key, String message) {
        messages.put(key, message);
    }

    /**
     * 批量设置消息模板。
     *
     * @param messages 消息映射
     */
    public void setMessages(Map<String, String> messages) {
        this.messages.putAll(messages);
    }

    /**
     * 获取消息模板。
     *
     * @param key 消息键
     * @return 消息模板，或 null
     */
    public String getMessage(String key) {
        return messages.get(key);
    }

    /**
     * 发送消息。
     *
     * @param sender       接收者
     * @param key          消息键
     * @param placeholders 占位符（键值对形式：key1, value1, key2, value2...）
     */
    public void send(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = parse(template, placeholders);
        sender.sendMessage(message);
    }

    /**
     * 发送成功消息（带前缀）。
     *
     * @param sender       接收者
     * @param key          消息键
     * @param placeholders 占位符
     */
    public void success(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = successPrefix.append(parse(template, placeholders));
        sender.sendMessage(message);
    }

    /**
     * 发送错误消息（带前缀）。
     *
     * @param sender       接收者
     * @param key          消息键
     * @param placeholders 占位符
     */
    public void error(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = errorPrefix.append(parse(template, placeholders));
        sender.sendMessage(message);
    }

    /**
     * 发送警告消息（带前缀）。
     *
     * @param sender       接收者
     * @param key          消息键
     * @param placeholders 占位符
     */
    public void warning(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = warningPrefix.append(parse(template, placeholders));
        sender.sendMessage(message);
    }

    /**
     * 发送信息消息（带前缀）。
     *
     * @param sender       接收者
     * @param key          消息键
     * @param placeholders 占位符
     */
    public void info(CommandSender sender, String key, Object... placeholders) {
        String template = messages.getOrDefault(key, key);
        Component message = infoPrefix.append(parse(template, placeholders));
        sender.sendMessage(message);
    }

    /**
     * 直接发送 MiniMessage 格式的消息。
     *
     * @param sender       接收者
     * @param miniMessage  MiniMessage 格式字符串
     * @param placeholders 占位符
     */
    public void sendRaw(CommandSender sender, String miniMessage, Object... placeholders) {
        sender.sendMessage(parse(miniMessage, placeholders));
    }

    /**
     * 直接发送 Component。
     *
     * @param sender    接收者
     * @param component Adventure Component
     */
    public void send(CommandSender sender, Component component) {
        sender.sendMessage(component);
    }

    /**
     * 解析 MiniMessage 格式字符串。
     *
     * @param template     模板
     * @param placeholders 占位符
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
     * 设置成功前缀。
     *
     * @param prefix 前缀组件
     */
    public void setSuccessPrefix(Component prefix) {
        this.successPrefix = prefix;
    }

    /**
     * 设置错误前缀。
     *
     * @param prefix 前缀组件
     */
    public void setErrorPrefix(Component prefix) {
        this.errorPrefix = prefix;
    }

    /**
     * 设置警告前缀。
     *
     * @param prefix 前缀组件
     */
    public void setWarningPrefix(Component prefix) {
        this.warningPrefix = prefix;
    }

    /**
     * 设置信息前缀。
     *
     * @param prefix 前缀组件
     */
    public void setInfoPrefix(Component prefix) {
        this.infoPrefix = prefix;
    }
}

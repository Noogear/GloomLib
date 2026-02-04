package gloomlib.command.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * 消息处理工具类。
 *
 * <p>
 * 提供统一的 MiniMessage 实例和消息格式化方法。
 * </p>
 */
public final class MessageUtils {

    /** 全局 MiniMessage 实例（线程安全） */
    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 反序列化 MiniMessage 格式的字符串。
     *
     * @param message 消息模板
     * @return Component 对象
     */
    public static Component deserialize(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    /**
     * 反序列化带占位符的消息。
     *
     * @param message   消息模板
     * @param resolvers 占位符解析器
     * @return Component 对象
     */
    public static Component deserialize(String message, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(message, resolvers);
    }

    /**
     * 创建错误消息。
     *
     * @param message 错误信息
     * @return 红色的错误消息 Component
     */
    public static Component createErrorMessage(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    /**
     * 创建成功消息。
     *
     * @param message 成功信息
     * @return 绿色的成功消息 Component
     */
    public static Component createSuccessMessage(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    /**
     * 创建警告消息。
     *
     * @param message 警告信息
     * @return 黄色的警告消息 Component
     */
    public static Component createWarningMessage(String message) {
        return Component.text(message, NamedTextColor.YELLOW);
    }

    /**
     * 创建信息消息。
     *
     * @param message 信息内容
     * @return 浅蓝色的信息消息 Component
     */
    public static Component createInfoMessage(String message) {
        return Component.text(message, NamedTextColor.AQUA);
    }
}

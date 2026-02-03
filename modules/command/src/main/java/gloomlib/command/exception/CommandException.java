package gloomlib.command.exception;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 命令执行异常基类。
 *
 * <p>
 * 所有命令框架抛出的异常都应继承此类。
 * </p>
 */
public class CommandException extends RuntimeException {

    private final @Nullable Component adventureMessage;

    /**
     * 创建命令异常。
     *
     * @param message 错误消息
     */
    public CommandException(String message) {
        super(message);
        this.adventureMessage = null;
    }

    /**
     * 创建命令异常（Adventure Component 消息）。
     *
     * @param message Adventure 组件消息
     */
    public CommandException(Component message) {
        super(componentToString(message));
        this.adventureMessage = message;
    }

    /**
     * 创建命令异常（带原因）。
     *
     * @param message 错误消息
     * @param cause   原因
     */
    public CommandException(String message, Throwable cause) {
        super(message, cause);
        this.adventureMessage = null;
    }

    /**
     * 创建命令异常（Adventure 消息 + 原因）。
     *
     * @param message Adventure 组件消息
     * @param cause   原因
     */
    public CommandException(Component message, Throwable cause) {
        super(componentToString(message), cause);
        this.adventureMessage = message;
    }

    /**
     * 获取 Adventure 组件消息。
     * 如果异常是用字符串创建的，则返回纯文本组件。
     *
     * @return Adventure 组件消息
     */
    public Component getAdventureMessage() {
        if (adventureMessage != null) {
            return adventureMessage;
        }
        return Component.text(getMessage() != null ? getMessage() : "未知错误");
    }

    /**
     * 检查是否有 Adventure 消息。
     *
     * @return 是否有 Adventure 消息
     */
    public boolean hasAdventureMessage() {
        return adventureMessage != null;
    }

    /**
     * 简单的 Component 转字符串（用于 super 构造）。
     */
    private static String componentToString(Component component) {
        // 简化实现，使用 Adventure 的纯文本序列化
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText()
                .serialize(component);
    }
}

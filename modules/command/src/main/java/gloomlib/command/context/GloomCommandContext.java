package gloomlib.command.context;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * 命令执行上下文，封装 Paper Brigadier 原生上下文。
 *
 * <p>
 * 提供便捷的方法访问命令执行者、参数和发送消息。
 * </p>
 */
public class GloomCommandContext {

    private final CommandContext<CommandSourceStack> brigadierContext;
    private final CommandSender sender;

    /**
     * 创建命令上下文。
     *
     * @param brigadierContext Paper Brigadier 原生上下文
     */
    public GloomCommandContext(CommandContext<CommandSourceStack> brigadierContext) {
        this.brigadierContext = brigadierContext;
        this.sender = brigadierContext.getSource().getSender();
    }

    /**
     * 获取 Paper Brigadier 原生上下文。
     *
     * @return Brigadier 命令上下文
     */
    public CommandContext<CommandSourceStack> getBrigadierContext() {
        return brigadierContext;
    }

    /**
     * 获取命令源 Stack。
     *
     * @return Paper CommandSourceStack
     */
    public CommandSourceStack getSource() {
        return brigadierContext.getSource();
    }

    /**
     * 获取命令执行者。
     *
     * @return 命令发送者
     */
    public CommandSender getSender() {
        return sender;
    }

    /**
     * 获取执行者作为玩家。
     * 如果执行者不是玩家，返回 null。
     *
     * @return 玩家，或 null
     */
    public Player getPlayer() {
        return sender instanceof Player player ? player : null;
    }

    /**
     * 检查执行者是否为玩家。
     *
     * @return 是否为玩家
     */
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    /**
     * 获取参数值。
     *
     * @param name  参数名
     * @param clazz 参数类型
     * @param <T>   类型
     * @return 参数值
     */
    public <T> T getArgument(String name, Class<T> clazz) {
        return brigadierContext.getArgument(name, clazz);
    }

    /**
     * 发送消息给执行者（Adventure Component）。
     *
     * @param message Adventure 组件消息
     */
    public void sendMessage(Component message) {
        sender.sendMessage(message);
    }

    /**
     * 发送纯文本消息给执行者。
     *
     * @param message 纯文本消息
     */
    public void sendMessage(String message) {
        sender.sendMessage(Component.text(message));
    }

    /**
     * 向执行者回复消息（sendMessage 的语义化别名）。
     *
     * @param message Adventure 组件消息
     */
    public void reply(Component message) {
        sendMessage(message);
    }

    /**
     * 如果执行者是玩家，执行消费者操作。
     *
     * @param consumer 玩家消费者
     */
    public void ifPlayer(Consumer<Player> consumer) {
        if (sender instanceof Player player) {
            consumer.accept(player);
        }
    }

    /**
     * 检查执行者是否拥有权限。
     *
     * @param permission 权限节点
     * @return 是否拥有权限
     */
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }
}

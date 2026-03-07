package gloomlib.command.context;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Command execution context, encapsulates Paper Brigadier native context.
 *
 * <p>
 * Provides convenient methods to access command sender, arguments, and send
 * messages.
 * </p>
 */
public class GloomCommandContext {

    private final CommandContext<CommandSourceStack> brigadierContext;
    private final CommandSender sender;

    /**
     * Creates a command context.
     *
     * @param brigadierContext Paper Brigadier native context
     */
    public GloomCommandContext(CommandContext<CommandSourceStack> brigadierContext) {
        this.brigadierContext = brigadierContext;
        this.sender = brigadierContext.getSource().getSender();
    }

    /**
     * Gets Paper Brigadier native context.
     *
     * @return Brigadier command context
     */
    public CommandContext<CommandSourceStack> getBrigadierContext() {
        return brigadierContext;
    }

    /**
     * Gets command source stack.
     *
     * @return Paper CommandSourceStack
     */
    public CommandSourceStack getSource() {
        return brigadierContext.getSource();
    }

    /**
     * Gets command sender.
     *
     * @return Command sender
     */
    public CommandSender getSender() {
        return sender;
    }

    /**
     * Gets sender as Player.
     * Returns null if sender is not a player.
     *
     * @return Player, or null
     */
    public Player getPlayer() {
        return sender instanceof Player player ? player : null;
    }

    /**
     * Checks if sender is a player.
     *
     * @return True if player
     */
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    /**
     * Gets argument value.
     *
     * @param name  Argument name
     * @param clazz Argument type class
     * @param <T>   Type
     * @return Argument value
     */
    public <T> T getArgument(String name, Class<T> clazz) {
        return brigadierContext.getArgument(name, clazz);
    }

    /**
     * Sends a message to the sender (Adventure Component).
     *
     * @param message Adventure component message
     */
    public void sendMessage(Component message) {
        sender.sendMessage(message);
    }

    /**
     * Sends a plain text message to the sender.
     *
     * @param message Plain text message
     */
    public void sendMessage(String message) {
        sender.sendMessage(Component.text(message));
    }

    /**
     * Replies with a message (semantic alias for sendMessage).
     *
     * @param message Adventure component message
     */
    public void reply(Component message) {
        sendMessage(message);
    }

    /**
     * Executes consumer action if sender is a player.
     *
     * @param consumer Player consumer
     */
    public void ifPlayer(Consumer<Player> consumer) {
        if (sender instanceof Player player) {
            consumer.accept(player);
        }
    }

    /**
     * Checks if sender has permission.
     *
     * @param permission Permission node
     * @return True if has permission
     */
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }
}

package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.exception.CommandException;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Parameter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 离线玩家参数解析器。
 *
 * <p>
 * 支持在线玩家和离线玩家的解析。
 * </p>
 */
public class OfflinePlayerResolver implements ArgumentResolver<OfflinePlayer> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // 使用玩家选择器，也支持玩家名
        return ArgumentTypes.player();
    }

    @Override
    public OfflinePlayer resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        try {
            // 首先尝试作为在线玩家解析
            PlayerSelectorArgumentResolver selector = context.getArgument(name, PlayerSelectorArgumentResolver.class);
            List<Player> players = selector.resolve(context.getSource());

            if (!players.isEmpty()) {
                return players.get(0);
            }
        } catch (Exception e) {
            // 忽略，尝试作为离线玩家名解析
        }

        // 尝试作为字符串（玩家名）解析
        try {
            String playerName = context.getArgument(name, String.class);
            return Bukkit.getOfflinePlayer(playerName);
        } catch (Exception e) {
            throw new CommandException(
                    Component.text("无法解析玩家: " + name).color(NamedTextColor.RED));
        }
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining().toLowerCase();

        // 建议在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(remaining)) {
                builder.suggest(player.getName());
            }
        }

        return builder.buildFuture();
    }

    @Override
    public Class<OfflinePlayer> getType() {
        return OfflinePlayer.class;
    }
}

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
import gloomlib.command.message.CommandMessages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Parameter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Offline Player Argument Resolver.
 *
 * <p>
 * Supports resolution of online and offline players.
 * </p>
 */
public class OfflinePlayerResolver implements ArgumentResolver<OfflinePlayer> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // Use player selector, also supports player names
        return ArgumentTypes.player();
    }

    @Override
    public OfflinePlayer resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        try {
            // First try resolving as online player
            PlayerSelectorArgumentResolver selector = context.getArgument(name, PlayerSelectorArgumentResolver.class);
            List<Player> players = selector.resolve(context.getSource());

            if (!players.isEmpty()) {
                return players.get(0);
            }
        } catch (Exception e) {
            // Ignore, try resolving as offline player name
        }

        // Try parsing as string (player name)
        try {
            String playerName = context.getArgument(name, String.class);
            return Bukkit.getOfflinePlayer(playerName);
        } catch (Exception e) {
            throw new CommandException(CommandMessages.PLAYER_UNKNOWN.get());
        }
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining().toLowerCase();

        // Suggest online players
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

package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.exception.CommandException;
import gloomlib.command.message.CommandMessages;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Offline Player Argument Resolver.
 *
 * <p>
 * Supports resolution of online and offline players.
 * Includes player name suggestion cache (5 seconds) to improve performance.
 * </p>
 */
public class OfflinePlayerResolver implements ArgumentResolver<OfflinePlayer> {

    /**
     * Cache validity duration (5 seconds).
     */
    private static final long CACHE_DURATION_MS = 5000;
    /**
     * Cached player names for suggestions.
     */
    private volatile List<String> cachedPlayerNames = null;
    /**
     * Last cache update time (milliseconds).
     */
    private volatile long lastCacheUpdate = 0;

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

        // Update cache if expired or not initialized
        long now = System.currentTimeMillis();
        if (cachedPlayerNames == null || now - lastCacheUpdate > CACHE_DURATION_MS) {
            updatePlayerNameCache();
        }

        // Filter cached names by remaining input
        for (String playerName : cachedPlayerNames) {
            if (playerName.toLowerCase().startsWith(remaining)) {
                builder.suggest(playerName);
            }
        }

        return builder.buildFuture();
    }

    /**
     * Updates the player name cache.
     * Thread-safe double-checked locking pattern.
     */
    private void updatePlayerNameCache() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            // Double-check inside synchronized block
            if (cachedPlayerNames == null || now - lastCacheUpdate > CACHE_DURATION_MS) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                cachedPlayerNames = names;
                lastCacheUpdate = now;
            }
        }
    }

    /**
     * Clears the player name cache.
     * Should be called when framework is reloaded or server is reloading.
     *
     * <p>Thread-safe operation.</p>
     */
    public void clearCache() {
        synchronized (this) {
            cachedPlayerNames = null;
            lastCacheUpdate = 0;
        }
    }

    @Override
    public Class<OfflinePlayer> getType() {
        return OfflinePlayer.class;
    }
}

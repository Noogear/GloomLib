package gloomlib.command.suggestion.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

/**
 * Game Rules Suggestion Provider.
 *
 * <p>
 * Provides auto-completion suggestions for all Minecraft game rules.
 * </p>
 */
public class GameRulesSuggestionProvider implements SuggestionProvider {

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();

        // Try getting world
        org.bukkit.World world = null;
        if (context.getSource().getExecutor() instanceof org.bukkit.entity.Entity entity) {
            world = entity.getWorld();
        } else if (!org.bukkit.Bukkit.getWorlds().isEmpty()) {
            world = org.bukkit.Bukkit.getWorlds().get(0);
        }

        if (world != null) {
            for (String rule : world.getGameRules()) {
                if (rule.toLowerCase().startsWith(remaining) || rule.toLowerCase().contains(remaining)) {
                    builder.suggest(rule);
                }
            }
        }

        return builder.buildFuture();
    }
}

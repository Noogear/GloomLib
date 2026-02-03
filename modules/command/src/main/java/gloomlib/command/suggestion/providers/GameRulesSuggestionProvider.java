package gloomlib.command.suggestion.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

/**
 * 游戏规则建议提供器。
 *
 * <p>
 * 提供所有 Minecraft 游戏规则的自动补全建议。
 * </p>
 */
public class GameRulesSuggestionProvider implements SuggestionProvider {

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();

        // 尝试获取世界
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

package gloomlib.command.suggestion.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * 在线玩家建议提供器。
 */
public class OnlinePlayersSuggestionProvider implements SuggestionProvider {

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(remaining)) {
                builder.suggest(player.getName()); // 移除 Component 工具提示
            }
        }

        return builder.buildFuture();
    }
}

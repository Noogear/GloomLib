package gloomlib.command.suggestion.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

/**
 * 世界名建议提供器。
 */
public class WorldsSuggestionProvider implements SuggestionProvider {

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();

        for (World world : Bukkit.getWorlds()) {
            if (world.getName().toLowerCase().startsWith(remaining)) {
                builder.suggest(world.getName()); // 移除 Component 工具提示
            }
        }

        return builder.buildFuture();
    }
}

package gloomlib.command.suggestion.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.EntityType;

import java.util.concurrent.CompletableFuture;

/**
 * 实体类型建议提供器。
 *
 * <p>
 * 提供所有可生成实体类型的自动补全建议。
 * </p>
 */
public class EntityTypesSuggestionProvider implements SuggestionProvider {

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();

        for (EntityType type : EntityType.values()) {
            // 只包含可生成的实体
            if (!type.isSpawnable()) {
                continue;
            }

            String name = type.name().toLowerCase();
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }

        return builder.buildFuture();
    }
}

package gloomlib.command.suggestion.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.EntityType;

import java.util.concurrent.CompletableFuture;

/**
 * Entity Type Suggestion Provider.
 *
 * <p>
 * Provides auto-completion suggestions for all spawnable entity types.
 * </p>
 */
public class EntityTypesSuggestionProvider implements SuggestionProvider {

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();

        for (EntityType type : EntityType.values()) {
            // Only include spawnable entities
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

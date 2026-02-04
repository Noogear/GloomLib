package gloomlib.command.suggestion.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.Material;

import java.util.concurrent.CompletableFuture;

/**
 * Material Name Suggestion Provider.
 */
public class MaterialsSuggestionProvider implements SuggestionProvider {

    private final boolean itemsOnly;
    private final boolean blocksOnly;

    /**
     * Creates a material suggestion provider (all materials).
     */
    public MaterialsSuggestionProvider() {
        this(false, false);
    }

    /**
     * Creates a material suggestion provider.
     *
     * @param itemsOnly  Show items only
     * @param blocksOnly Show blocks only
     */
    public MaterialsSuggestionProvider(boolean itemsOnly, boolean blocksOnly) {
        this.itemsOnly = itemsOnly;
        this.blocksOnly = blocksOnly;
    }

    /**
     * Items only material provider.
     */
    public static MaterialsSuggestionProvider itemsOnly() {
        return new MaterialsSuggestionProvider(true, false);
    }

    /**
     * Blocks only material provider.
     */
    public static MaterialsSuggestionProvider blocksOnly() {
        return new MaterialsSuggestionProvider(false, true);
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        int count = 0;

        for (Material material : Material.values()) {
            // Filter
            if (itemsOnly && !material.isItem())
                continue;
            if (blocksOnly && !material.isBlock())
                continue;
            if (material.isLegacy())
                continue;

            String name = material.getKey().getKey();
            if (name.startsWith(remaining) || name.contains(remaining)) {
                builder.suggest(name);
                if (++count >= 50)
                    break; // Limit number of suggestions
            }
        }

        return builder.buildFuture();
    }
}

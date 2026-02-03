package gloomlib.command.suggestion.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.Material;

import java.util.concurrent.CompletableFuture;

/**
 * 材料名建议提供器。
 */
public class MaterialsSuggestionProvider implements SuggestionProvider {

    private final boolean itemsOnly;
    private final boolean blocksOnly;

    /**
     * 创建材料建议提供器（所有材料）。
     */
    public MaterialsSuggestionProvider() {
        this(false, false);
    }

    /**
     * 创建材料建议提供器。
     *
     * @param itemsOnly  仅显示物品
     * @param blocksOnly 仅显示方块
     */
    public MaterialsSuggestionProvider(boolean itemsOnly, boolean blocksOnly) {
        this.itemsOnly = itemsOnly;
        this.blocksOnly = blocksOnly;
    }

    /**
     * 仅物品材料提供器。
     */
    public static MaterialsSuggestionProvider itemsOnly() {
        return new MaterialsSuggestionProvider(true, false);
    }

    /**
     * 仅方块材料提供器。
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
            // 过滤
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
                    break; // 限制建议数量
            }
        }

        return builder.buildFuture();
    }
}

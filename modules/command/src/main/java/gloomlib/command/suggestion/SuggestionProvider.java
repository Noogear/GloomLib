package gloomlib.command.suggestion;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

/**
 * Command Argument Tab Suggestion Provider Interface.
 *
 * <p>
 * Implement this interface to provide custom Tab completion suggestions.
 * </p>
 *
 * <p>
 * Usage Example:
 * </p>
 *
 * <pre>{@code
 * public class WarpSuggestionProvider implements SuggestionProvider {
 *     @Override
 *     public CompletableFuture<Suggestions> suggest(
 *             CommandContext<CommandSourceStack> context,
 *             SuggestionsBuilder builder) {
 *         WarpManager warpManager = // Get manager
 *                 warpManager.getWarps().forEach(warp -> builder.suggest(warp.getName()));
 *         return builder.buildFuture();
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface SuggestionProvider {

    /**
     * Provides Tab completion suggestions.
     *
     * @param context Paper Brigadier command context
     * @param builder Suggestions builder
     * @return Async suggestions list
     */
    CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder);
}

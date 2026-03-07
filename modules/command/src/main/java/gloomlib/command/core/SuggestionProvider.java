package gloomlib.command.core;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

/**
 * Functional interface for providing command argument suggestions.
 *
 * <p>This interface simplifies Brigadier's {@code SuggestionProvider<CommandSourceStack>}
 * by fixing the generic type parameter. Use this to implement custom tab completion
 * providers for your commands.
 *
 * <p>Usage example:
 * <pre>{@code
 * public class WarpSuggestionProvider implements SuggestionProvider {
 *     @Override
 *     public CompletableFuture<Suggestions> suggest(
 *             CommandContext<CommandSourceStack> context,
 *             SuggestionsBuilder builder) {
 *         warpManager.getWarpNames().forEach(builder::suggest);
 *         return builder.buildFuture();
 *     }
 * }
 *
 * // Use with @Suggest annotation
 * @SubCommand("warp")
 * public void warp(@Arg @Suggest(WarpSuggestionProvider.class) String warpName) {
 *     // Auto-completion for warp names
 * }
 * }</pre>
 */
@FunctionalInterface
public interface SuggestionProvider {

    /**
     * Provides suggestions for command arguments.
     *
     * @param context Current command context
     * @param builder Builder for creating suggestions
     * @return CompletableFuture of suggestions
     */
    CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder);
}

package gloomlib.command.resolver;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * Command Argument Resolver Interface.
 *
 * <p>
 * Implement this interface to support parsing and suggestions for custom
 * argument types.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     public class RankResolver implements ArgumentResolver<Rank> {
 *         &#64;Override
 *         public ArgumentType<?> createArgumentType(Parameter parameter) {
 *             return StringArgumentType.word();
 *         }
 *
 *         &#64;Override
 *         public Rank resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
 *             String rankName = context.getArgument(name, String.class);
 *             return rankManager.getRank(rankName);
 *         }
 *
 *         @Override
 *         public CompletableFuture<Suggestions> suggest(
 *                 CommandContext<CommandSourceStack> context,
 *                 SuggestionsBuilder builder,
 *                 Parameter parameter) {
 *             rankManager.getRanks().forEach(rank -> builder.suggest(rank.getName()));
 *             return builder.buildFuture();
 *         }
 *     }
 * }
 * </pre>
 *
 * @param <T> Argument type
 */
public interface ArgumentResolver<T> {

    /**
     * Creates a Brigadier argument type.
     * Returns a Paper API compatible ArgumentType.
     *
     * @param parameter Method parameter reflection object
     * @return Brigadier argument type
     */
    ArgumentType<?> createArgumentType(Parameter parameter);

    /**
     * Resolves argument value from Brigadier context.
     *
     * @param context   Paper Brigadier command context
     * @param name      Argument name
     * @param parameter Method parameter reflection object
     * @return Resolved argument value
     */
    T resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter);

    /**
     * Provides Tab completion suggestions.
     * Defaults to empty suggestions (Paper may provide built-in suggestions).
     *
     * @param context   Paper Brigadier command context
     * @param builder   Suggestions builder
     * @param parameter Method parameter reflection object
     * @return Async suggestions list
     */
    default CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        return Suggestions.empty();
    }

    /**
     * Gets the type supported by this resolver.
     * Used for auto-registration.
     *
     * @return Supported type
     */
    Class<T> getType();
}

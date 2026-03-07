package gloomlib.command.api.resolver;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * Resolver for command method parameters.
 *
 * <p>Implementations define how to:
 * <ul>
 * <li>Create Brigadier ArgumentType for parameter</li>
 * <li>Extract and convert parameter from CommandContext</li>
 * <li>Provide auto-completion suggestions</li>
 * </ul>
 *
 * @param <T> The Java type this resolver handles
 * @see gloomlib.command.core.resolver.registry.BrigadierResolver
 */
public interface ArgumentResolver<T> {

    /**
     * Creates Brigadier ArgumentType for this parameter.
     *
     * @param parameter Method parameter metadata
     * @return ArgumentType instance for command tree
     */
    ArgumentType<?> createArgumentType(Parameter parameter);

    /**
     * Resolves parameter value from command context.
     *
     * @param context Brigadier command context
     * @param name Parameter name in command tree
     * @param parameter Method parameter metadata
     * @return Resolved parameter value
     * @throws CommandSyntaxException If resolution fails
     */
    T resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter)
            throws CommandSyntaxException;

    /**
     * Provides auto-completion suggestions.
     *
     * @param context Brigadier command context
     * @param builder Suggestions builder
     * @param parameter Method parameter metadata
     * @return Future with suggestions
     */
    default CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        return Suggestions.empty();
    }

    /**
     * Gets the Java type this resolver handles.
     *
     * @return Type class
     */
    Class<T> getType();

    /**
     * Clears internal cache if any.
     */
    default void clearCache() {
    }
}

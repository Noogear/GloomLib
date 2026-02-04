package gloomlib.command.builder;

import gloomlib.command.annotation.*;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.suggestion.SuggestionProvider;

import gloomlib.command.util.ParameterUtils;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * Brigadier Command Tree Builder.
 *
 * <p>
 * Responsible for building Brigadier command trees, including:
 * </p>
 * <ul>
 * <li>Method branch building</li>
 * <li>Argument chain building</li>
 * <li>Optional argument handling</li>
 * <li>Suggestion provider registration</li>
 * <li>Command executor binding</li>
 * </ul>
 */
public class BrigadierTreeBuilder {

    private final ArgumentResolverRegistry resolverRegistry;
    private final Map<Class<? extends SuggestionProvider>, SuggestionProvider> suggestionCache = new HashMap<>();

    /**
     * Command execution function type.
     */
    @FunctionalInterface
    public interface ExecutionFunction {
        int execute(CommandContext<CommandSourceStack> ctx) throws Exception;
    }

    public BrigadierTreeBuilder(ArgumentResolverRegistry resolverRegistry) {
        this.resolverRegistry = resolverRegistry;
    }

    /**
     * Builds Brigadier branch for a command method.
     *
     * @param builder     Command builder
     * @param method      Command method
     * @param executionFn Execution function
     */
    public void buildMethodBranch(
            LiteralArgumentBuilder<CommandSourceStack> builder,
            Method method,
            ExecutionFunction executionFn) {

        Parameter[] parameters = method.getParameters();
        int startIndex = ParameterUtils.getStartParameterIndex(parameters);

        if (startIndex >= parameters.length) {
            // No-arg command
            builder.executes(ctx -> {
                try {
                    return executionFn.execute(ctx);
                } catch (Exception e) {
                    e.printStackTrace();
                    return 0;
                }
            });
        } else {
            // Arg command, build argument chain
            buildArgumentChain(builder, method, parameters, startIndex, executionFn);
        }
    }

    /**
     * Recursively builds argument chain.
     *
     * @param builder     Command builder
     * @param method      Command method
     * @param parameters  Method parameter array
     * @param paramIndex  Current parameter index
     * @param executionFn Execution function
     */
    private void buildArgumentChain(
            ArgumentBuilder<CommandSourceStack, ?> builder,
            Method method,
            Parameter[] parameters,
            int paramIndex,
            ExecutionFunction executionFn) {

        // Recursion termination condition
        if (paramIndex >= parameters.length) {
            builder.executes(ctx -> {
                try {
                    return executionFn.execute(ctx);
                } catch (Exception e) {
                    e.printStackTrace();
                    return 0;
                }
            });
            return;
        }

        Parameter param = parameters[paramIndex];

        // Skip Flag and Switch parameters (not parsed from command line)
        if (param.isAnnotationPresent(Flag.class) || param.isAnnotationPresent(Switch.class)) {
            buildArgumentChain(builder, method, parameters, paramIndex + 1, executionFn);
            return;
        }

        // Build argument node
        String argName = ParameterUtils.getParameterName(param);
        ArgumentResolver<?> resolver = resolverRegistry.getResolver(param.getType());

        if (resolver == null) {
            throw new IllegalArgumentException(String.format(
                    "Unsupported parameter type: %s (param: %s, method: %s)",
                    param.getType().getName(),
                    argName,
                    method.getName()));
        }

        RequiredArgumentBuilder<CommandSourceStack, ?> argumentBuilder = Commands.argument(argName,
                resolver.createArgumentType(param));

        // Register suggestion provider
        registerSuggestions(argumentBuilder, param, resolver);

        // If optional parameter, add execution point here
        if (param.isAnnotationPresent(Optional.class)) {
            builder.executes(ctx -> {
                try {
                    return executionFn.execute(ctx);
                } catch (Exception e) {
                    e.printStackTrace();
                    return 0;
                }
            });
        }

        // Recursively build next argument
        buildArgumentChain(argumentBuilder, method, parameters, paramIndex + 1, executionFn);

        // Attach argument node to current node
        builder.then(argumentBuilder);
    }

    /**
     * Registers suggestion provider for arguments.
     *
     * @param argumentBuilder Argument builder
     * @param param           Parameter definition
     * @param resolver        Argument resolver
     */
    private void registerSuggestions(
            RequiredArgumentBuilder<CommandSourceStack, ?> argumentBuilder,
            Parameter param,
            ArgumentResolver<?> resolver) {

        // Check for @Suggest annotation
        Suggest suggestAnnotation = param.getAnnotation(Suggest.class);
        if (suggestAnnotation != null) {
            SuggestionProvider provider = getSuggestionProvider(suggestAnnotation.value());
            argumentBuilder.suggests((ctx, suggestionsBuilder) -> provider.suggest(ctx, suggestionsBuilder));
        } else {
            // Use resolver's default suggestions
            argumentBuilder.suggests((ctx, suggestionsBuilder) -> resolver.suggest(ctx, suggestionsBuilder, param));
        }
    }

    /**
     * Gets or creates suggestion provider instance.
     *
     * @param providerClass Provider class
     * @return Provider instance
     */
    private SuggestionProvider getSuggestionProvider(Class<? extends SuggestionProvider> providerClass) {
        return suggestionCache.computeIfAbsent(providerClass, clazz -> {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("Could not instantiate suggestion provider: %s", clazz.getName()),
                        e);
            }
        });
    }
}

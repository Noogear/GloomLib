package gloomlib.command.core;

import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.api.annotation.*;
import gloomlib.command.api.context.AsyncContext;
import gloomlib.command.api.context.GloomCommandContext;
import gloomlib.command.api.exception.CommandException;
import gloomlib.command.core.processor.ValidationProcessor;
import gloomlib.command.api.resolver.ArgumentResolver;
import gloomlib.command.core.resolver.ArgumentResolverRegistry;
import gloomlib.command.core.util.ParameterUtils;
import gloomlib.command.core.util.TypeConverterUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Parameter;

/**
 * Command Argument Parser.
 *
 * <p>
 * Responsible for parsing arguments from Brigadier CommandContext, supporting:
 * </p>
 * <ul>
 * <li>Special parameter injection (CommandSender, Player, GloomCommandContext,
 * AsyncContext)</li>
 * <li>Custom argument resolvers</li>
 * <li>Default value handling</li>
 * <li>Optional parameters</li>
 * <li>Switches and Flags</li>
 * <li>Range validation</li>
 * </ul>
 *
 * <h2>Parameter Resolution Flow</h2>
 * 
 * <pre>
 * Parameter
 *    ↓
 * 1. Check if special type (Player, CommandSender, Context) → Auto-inject
 *    ↓ (no)
 * 2. Check @Switch/@Flag → Boolean handling
 *    ↓ (no)
 * 3. Lookup ArgumentResolver by type
 *    ↓
 * 4. Extract from CommandContext via resolver
 *    ↓
 * 5. Apply @Range validation (if present)
 *    ↓
 * 6. Fallback to @Default value (if provided and arg missing)
 *    ↓
 * 7. Handle @Optional (return null if missing)
 *    ↓
 * Result: Typed argument or exception
 * </pre>
 */
public class ArgumentParser {

    private final JavaPlugin plugin;
    private final ArgumentResolverRegistry resolverRegistry;
    private final ValidationProcessor validationProcessor;

    /**
     * Creates an argument parser.
     *
     * @param plugin           Plugin instance
     * @param resolverRegistry Argument resolver registry
     */
    public ArgumentParser(JavaPlugin plugin, ArgumentResolverRegistry resolverRegistry) {
        this.plugin = plugin;
        this.resolverRegistry = resolverRegistry;
        this.validationProcessor = new ValidationProcessor();
    }

    /**
     * Resolves all parameters for a method.
     *
     * @param ctx        Brigadier CommandContext
     * @param parameters Method parameter array
     * @param sender     Command sender
     * @return Resolved argument values array
     * @throws CommandException If parsing or validation fails
     */
    public Object[] resolveArguments(
            CommandContext<CommandSourceStack> ctx,
            Parameter[] parameters,
            CommandSender sender) throws CommandException {

        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            args[i] = resolveParameter(ctx, param, sender, i);
        }

        return args;
    }

    /**
     * Resolves a single parameter.
     *
     * @param ctx    Brigadier CommandContext
     * @param param  Parameter definition
     * @param sender Command sender
     * @param index  Parameter index
     * @return Resolved argument value
     * @throws CommandException If parsing or validation fails
     */
    private Object resolveParameter(
            CommandContext<CommandSourceStack> ctx,
            Parameter param,
            CommandSender sender,
            int index) throws CommandException {

        Class<?> paramType = param.getType();

        // 1. Special Parameters - Auto Injection
        if (CommandSender.class.isAssignableFrom(paramType)) {
            return sender;
        }
        if (Player.class.equals(paramType) && index == 0) {
            return sender;
        }
        if (AsyncContext.class.isAssignableFrom(paramType)) {
            return new AsyncContext(ctx, plugin);
        }
        if (GloomCommandContext.class.isAssignableFrom(paramType)) {
            return new GloomCommandContext(ctx);
        }

        // 2. Switch Parameters - Default to false
        if (param.isAnnotationPresent(Switch.class)) {
            return false;
        }

        // 3. Flag Parameters - Default to null
        if (param.isAnnotationPresent(Flag.class)) {
            return null;
        }

        // 4. Custom Parameter Resolution
        return resolveCustomParameter(ctx, param, sender);
    }

    /**
     * Resolves custom parameter (using ArgumentResolver).
     *
     * @param ctx    Brigadier CommandContext
     * @param param  Parameter definition
     * @param sender Command sender
     * @return Resolved argument value
     * @throws CommandException If parsing or validation fails
     */
    private Object resolveCustomParameter(
            CommandContext<CommandSourceStack> ctx,
            Parameter param,
            CommandSender sender) throws CommandException {

        Class<?> paramType = param.getType();
        String argName = ParameterUtils.getParameterName(param);

        try {
            ArgumentResolver<?> resolver = resolverRegistry.getResolver(paramType);
            if (resolver == null) {
                throw new IllegalArgumentException(
                        String.format("Argument resolver not found: %s", paramType.getName()));
            }

            Object resolvedValue = resolver.resolve(ctx, argName, param);

            // Numeric range validation
            if (resolvedValue instanceof Number numberValue) {
                validateRange(numberValue, param);
            }

            return resolvedValue;

        } catch (Exception e) {
            // Try default value
            return resolveWithDefault(e, param, sender);
        }
    }

    /**
     * Attempts to resolve with default value when parsing fails.
     *
     * @param originalException Original exception
     * @param param             Parameter definition
     * @param sender            Command sender
     * @return Default value, or rethrows exception if no default
     * @throws CommandException If no default value and parameter is not optional
     */
    private Object resolveWithDefault(
            Exception originalException,
            Parameter param,
            CommandSender sender) throws CommandException {

        // Check for @Default annotation
        Default defaultAnnotation = param.getAnnotation(Default.class);
        if (defaultAnnotation != null) {
            return TypeConverterUtils.convertDefault(
                    defaultAnnotation.value(),
                    param.getType(),
                    sender).orElse(null);
        }

        // Check for @Optional annotation
        if (param.isAnnotationPresent(Optional.class)) {
            return null;
        }

        // No default value and not optional, rethrow exception
        if (originalException instanceof CommandException cmdEx) {
            throw cmdEx;
        }
        throw new CommandException(originalException.getMessage());
    }

    /**
     * Validates numeric range.
     *
     * @param value Numeric value
     * @param param Parameter definition
     * @throws CommandException If validation fails
     */
    private void validateRange(Number value, Parameter param) throws CommandException {
        if (!param.isAnnotationPresent(Range.class)) {
            return;
        }

        Range range = param.getAnnotation(Range.class);
        ValidationProcessor.ValidationResult result = validationProcessor.validateRange(value, range, param);

        if (!result.valid()) {
            throw new CommandException(result.errorMessage());
        }
    }
}

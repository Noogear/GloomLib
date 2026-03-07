package gloomlib.command.core;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.api.annotation.Condition;
import gloomlib.command.api.annotation.ConsoleOnly;
import gloomlib.command.api.annotation.Cooldown;
import gloomlib.command.api.annotation.PlayerOnly;
import gloomlib.command.api.condition.CommandCondition;
import gloomlib.command.api.condition.CommandConditionRegistry;
import gloomlib.command.api.context.CommandResult;
import gloomlib.command.api.context.GloomCommandContext;
import gloomlib.command.api.exception.CommandException;
import gloomlib.command.api.exception.ExceptionResolver;
import gloomlib.command.api.exception.ExceptionResolverRegistry;
import gloomlib.command.core.message.CommandMessages;
import gloomlib.command.core.processor.MethodInvoker;
import gloomlib.command.core.processor.ProcessorPipeline;
import gloomlib.command.core.processor.CooldownProcessor;
import gloomlib.command.core.util.MessageUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Command Executor.
 *
 * <p>
 * Responsible for actually executing command methods, handling:
 * </p>
 * <ul>
 * <li>Async execution (@Async annotation)</li>
 * <li>Processor Pipeline (PreProcessor and PostProcessor)</li>
 * <li>Permission checks (@PlayerOnly, @ConsoleOnly)</li>
 * <li>Cooldown limits (@Cooldown annotation)</li>
 * <li>Error handling (@OnError annotation)</li>
 * <li>Method invocation (using MethodInvoker)</li>
 * </ul>
 */
public class CommandExecutor {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(CommandExecutor.class);

    private final JavaPlugin plugin;
    private final ProcessorPipeline pipeline;
    private final CooldownProcessor cooldownProcessor;
    private final CommandConditionRegistry conditionRegistry;
    private final ExceptionResolverRegistry exceptionResolverRegistry;

    /**
     * Creates a command executor.
     *
     * @param plugin                  Plugin instance
     * @param pipeline                Processor pipeline
     * @param cooldownProcessor       Cooldown processor
     * @param conditionRegistry       Named condition registry (may be null)
     * @param exceptionResolverRegistry Global exception resolver registry (may be null)
     */
    public CommandExecutor(
            JavaPlugin plugin,
            ProcessorPipeline pipeline,
            CooldownProcessor cooldownProcessor,
            CommandConditionRegistry conditionRegistry,
            ExceptionResolverRegistry exceptionResolverRegistry) {
        this.plugin = plugin;
        this.pipeline = pipeline;
        this.cooldownProcessor = cooldownProcessor;
        this.conditionRegistry = conditionRegistry;
        this.exceptionResolverRegistry = exceptionResolverRegistry;
    }

    /**
     * Executes a command method.
     *
     * @param ctx           Brigadier CommandContext
     * @param method        Command method
     * @param instance      Command class instance
     * @param args          Parsed argument array
     * @param invoker       Method invoker
     * @param isAsync       Whether to execute asynchronously
     * @param cooldownKey   Cooldown key (if any)
     * @param errorHandlers Error handler map
     * @return Brigadier command return value
     */
    public int execute(
            CommandContext<CommandSourceStack> ctx,
            Method method,
            Object instance,
            Object[] args,
            MethodInvoker invoker,
            boolean isAsync,
            String cooldownKey,
            Map<Class<? extends Throwable>, Method> errorHandlers) {

        // Async execution
        if (isAsync) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    executeInternal(ctx, method, instance, args, invoker, cooldownKey, errorHandlers);
                } catch (Throwable e) {
                    LOGGER.debug("Async command execution failed", e);
                }
            });
            return Command.SINGLE_SUCCESS;
        }

        // Sync execution
        return executeInternal(ctx, method, instance, args, invoker, cooldownKey, errorHandlers);
    }

    /**
     * Internal execution logic.
     *
     * @param ctx           Brigadier CommandContext
     * @param method        Command method
     * @param instance      Command class instance
     * @param args          Parsed argument array
     * @param invoker       Method invoker
     * @param cooldownKey   Cooldown key (if any)
     * @param errorHandlers Error handler map
     * @return Brigadier command return value
     */
    private int executeInternal(
            CommandContext<CommandSourceStack> ctx,
            Method method,
            Object instance,
            Object[] args,
            MethodInvoker invoker,
            String cooldownKey,
            Map<Class<? extends Throwable>, Method> errorHandlers) {

        GloomCommandContext context = new GloomCommandContext(ctx);

        try {
            // 1. Run PreProcessors
            if (!pipeline.runPreProcessors(context)) {
                return Command.SINGLE_SUCCESS;
            }

            // 1.5 Check Conditions (@Condition annotation)
            if (!checkConditions(method, context)) {
                return Command.SINGLE_SUCCESS;
            }

            CommandSender sender = ctx.getSource().getSender();

            // 2. Check Execution Permission (@PlayerOnly / @ConsoleOnly)
            if (!checkSenderType(method, sender)) {
                return 0;
            }

            // 3. Check Cooldown
            if (!checkCooldown(method, sender, cooldownKey)) {
                return Command.SINGLE_SUCCESS;
            }

            // 4. Execute Method
            Object result = invoker.invoke(instance, args);

            // 5. Run PostProcessors
            pipeline.runPostProcessors(context, CommandResult.success(result));

            return result instanceof Integer ? (Integer) result : Command.SINGLE_SUCCESS;

        } catch (Throwable t) {
            return handleError(t, ctx, instance, errorHandlers);
        }
    }

    /**
     * Checks checks sender type (@PlayerOnly / @ConsoleOnly).
     *
     * @param method Command method
     * @param sender Command sender
     * @return true if check passes
     */
    private boolean checkSenderType(Method method, CommandSender sender) {
        // @PlayerOnly check
        PlayerOnly playerOnly = method.getAnnotation(PlayerOnly.class);
        if (playerOnly != null && !(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.deserialize(playerOnly.message()));
            return false;
        }

        // @ConsoleOnly check
        ConsoleOnly consoleOnly = method.getAnnotation(ConsoleOnly.class);
        if (consoleOnly != null && sender instanceof Player) {
            sender.sendMessage(MessageUtils.deserialize(consoleOnly.message()));
            return false;
        }

        return true;
    }

    /**
     * Runs all {@code @Condition} checks on the given method.
     * Conditions are evaluated in declaration order; the first failure halts the chain.
     *
     * @param method  Command method (may have {@link Condition} annotation)
     * @param context Command context
     * @return {@code true} if all conditions pass (or no conditions declared)
     */
    private boolean checkConditions(Method method, GloomCommandContext context) {
        Condition conditionAnnotation = method.getAnnotation(Condition.class);
        if (conditionAnnotation == null || conditionRegistry == null) {
            return true;
        }
        for (String name : conditionAnnotation.value()) {
            CommandCondition condition = conditionRegistry.get(name);
            if (condition == null) continue; // Unknown condition — skip
            CommandCondition.ConditionResult result = condition.test(context);
            if (!result.passed()) {
                if (result.failureMessage() != null) {
                    context.getSender().sendMessage(result.failureMessage());
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Checks command cooldown.
     *
     * @param method      Command method
     * @param sender      Command sender
     * @param cooldownKey Cooldown key
     * @return true if check passes (no cooldown or cooldown expired)
     */
    private boolean checkCooldown(Method method, CommandSender sender, String cooldownKey) {
        if (cooldownKey == null || !(sender instanceof Player player)) {
            return true;
        }

        Cooldown cooldown = method.getAnnotation(Cooldown.class);
        if (cooldown == null) {
            return true;
        }

        // Check bypass permission
        if (!cooldown.bypassPermission().isEmpty() &&
                player.hasPermission(cooldown.bypassPermission())) {
            return true;
        }

        // Check remaining cooldown
        // Check remaining cooldown
        long remaining = cooldownProcessor.getRemainingCooldown(cooldownKey, player.getUniqueId());
        if (remaining > 0) {
            String timeStr = CooldownProcessor.formatRemainingTime(remaining);
            if (cooldown.message().isEmpty()) {
                sender.sendMessage(CommandMessages.COOLDOWN_WAIT.get(Component.text(timeStr, NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage(MessageUtils.deserialize(cooldown.message())
                        .replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                                .match("%time%") // Simple placeholder
                                .replacement(timeStr)
                                .build())
                        .replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                                .match("\\{remaining\\}") // MiniMessage style placeholder
                                .replacement(timeStr)
                                .build()));
            }
            return false;
        }

        // Set new cooldown
        cooldownProcessor.setCooldown(cooldownKey, player.getUniqueId(), cooldown.value());
        return true;
    }

    /**
     * Handles command execution errors.
     *
     * @param throwable     Exception
     * @param ctx           Brigadier CommandContext
     * @param instance      Command class instance
     * @param errorHandlers Error handler map
     * @return Brigadier command return value (0 means failure)
     */
    private int handleError(
            Throwable throwable,
            CommandContext<CommandSourceStack> ctx,
            Object instance,
            Map<Class<? extends Throwable>, Method> errorHandlers) {

        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

        // Try custom error handlers
        for (Map.Entry<Class<? extends Throwable>, Method> entry : errorHandlers.entrySet()) {
            if (entry.getKey().isAssignableFrom(cause.getClass())) {
                try {
                    Method handler = entry.getValue();
                    handler.setAccessible(true);
                    handler.invoke(instance, new GloomCommandContext(ctx), cause);
                    return 0;
                } catch (Exception handlerEx) {
                    LOGGER.debug("Error handler invocation failed", handlerEx);
                }
            }
        }

        // Try global exception resolvers
        if (exceptionResolverRegistry != null) {
            @SuppressWarnings("unchecked")
            ExceptionResolver<Throwable> globalResolver =
                    (ExceptionResolver<Throwable>) exceptionResolverRegistry.getResolver(
                            (Class<Throwable>) cause.getClass());
            if (globalResolver != null) {
                try {
                    globalResolver.resolve(new GloomCommandContext(ctx), cause);
                } catch (Exception resolverEx) {
                    LOGGER.debug("Global exception resolver failed", resolverEx);
                }
                return 0;
            }
        }

        // Default error handling
        CommandSender sender = ctx.getSource().getSender();
        if (cause instanceof CommandException cmdEx) {
            sender.sendMessage(cmdEx.getAdventureMessage());
        } else {
            sender.sendMessage(
                    CommandMessages.COMMAND_FAILED.get()
                            .hoverEvent(Component.text(cause.getMessage(), NamedTextColor.GRAY)));
            LOGGER.debug("Command execution failed", cause);
        }

        return 0;
    }
}

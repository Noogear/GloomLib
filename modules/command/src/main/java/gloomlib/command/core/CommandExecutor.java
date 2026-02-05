package gloomlib.command.core;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.annotation.ConsoleOnly;
import gloomlib.command.annotation.Cooldown;
import gloomlib.command.annotation.PlayerOnly;
import gloomlib.command.context.CommandResult;
import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.exception.CommandException;
import gloomlib.command.message.CommandMessages;
import gloomlib.command.processor.MethodInvoker;
import gloomlib.command.processor.ProcessorPipeline;
import gloomlib.command.processor.processors.CooldownProcessor;
import gloomlib.command.util.MessageUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    private final JavaPlugin plugin;
    private final ProcessorPipeline pipeline;
    private final CooldownProcessor cooldownProcessor;

    public CommandExecutor(
            JavaPlugin plugin,
            ProcessorPipeline pipeline,
            CooldownProcessor cooldownProcessor) {
        this.plugin = plugin;
        this.pipeline = pipeline;
        this.cooldownProcessor = cooldownProcessor;
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
                    e.printStackTrace();
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
                    handlerEx.printStackTrace();
                }
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
            cause.printStackTrace();
        }

        return 0;
    }
}

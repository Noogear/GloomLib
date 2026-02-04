package gloomlib.command.executor;

import gloomlib.command.annotation.*;
import gloomlib.command.context.CommandResult;
import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.exception.CommandException;
import gloomlib.command.processor.MethodInvoker;
import gloomlib.command.processor.ProcessorPipeline;
import gloomlib.command.processor.processors.CooldownProcessor;
import gloomlib.command.util.MessageUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 命令执行器。
 *
 * <p>
 * 负责实际执行命令方法，处理：
 * </p>
 * <ul>
 * <li>异步执行（@Async 注解）</li>
 * <li>处理器管道（PreProcessor 和 PostProcessor）</li>
 * <li>权限检查（@PlayerOnly, @ConsoleOnly）</li>
 * <li>冷却限制（@Cooldown 注解）</li>
 * <li>错误处理（@OnError 注解）</li>
 * <li>方法调用（使用 MethodInvoker）</li>
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
     * 执行命令方法。
     *
     * @param ctx           Brigadier CommandContext
     * @param method        命令方法
     * @param instance      命令类实例
     * @param args          解析后的参数数组
     * @param invoker       方法调用器
     * @param isAsync       是否异步执行
     * @param cooldownKey   冷却键（如果有）
     * @param errorHandlers 错误处理器映射
     * @return Brigadier 命令返回值
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
        
        // 异步执行
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
        
        // 同步执行
        return executeInternal(ctx, method, instance, args, invoker, cooldownKey, errorHandlers);
    }

    /**
     * 内部执行逻辑。
     *
     * @param ctx           Brigadier CommandContext
     * @param method        命令方法
     * @param instance      命令类实例
     * @param args          解析后的参数数组
     * @param invoker       方法调用器
     * @param cooldownKey   冷却键（如果有）
     * @param errorHandlers 错误处理器映射
     * @return Brigadier 命令返回值
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
            // 1. 运行预处理器
            if (!pipeline.runPreProcessors(context)) {
                return Command.SINGLE_SUCCESS;
            }
            
            CommandSender sender = ctx.getSource().getSender();
            
            // 2. 检查执行权限（@PlayerOnly / @ConsoleOnly）
            if (!checkSenderType(method, sender)) {
                return 0;
            }
            
            // 3. 检查冷却
            if (!checkCooldown(method, sender, cooldownKey)) {
                return Command.SINGLE_SUCCESS;
            }
            
            // 4. 执行方法
            Object result = invoker.invoke(instance, args);
            
            // 5. 运行后处理器
            pipeline.runPostProcessors(context, CommandResult.success(result));
            
            return result instanceof Integer ? (Integer) result : Command.SINGLE_SUCCESS;
            
        } catch (Throwable t) {
            return handleError(t, ctx, instance, errorHandlers);
        }
    }

    /**
     * 检查发送者类型（@PlayerOnly / @ConsoleOnly）。
     *
     * @param method 命令方法
     * @param sender 命令发送者
     * @return true 如果检查通过
     */
    private boolean checkSenderType(Method method, CommandSender sender) {
        // @PlayerOnly 检查
        PlayerOnly playerOnly = method.getAnnotation(PlayerOnly.class);
        if (playerOnly != null && !(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.deserialize(playerOnly.message()));
            return false;
        }
        
        // @ConsoleOnly 检查
        ConsoleOnly consoleOnly = method.getAnnotation(ConsoleOnly.class);
        if (consoleOnly != null && sender instanceof Player) {
            sender.sendMessage(MessageUtils.deserialize(consoleOnly.message()));
            return false;
        }
        
        return true;
    }

    /**
     * 检查命令冷却。
     *
     * @param method      命令方法
     * @param sender      命令发送者
     * @param cooldownKey 冷却键
     * @return true 如果检查通过（无冷却或冷却已结束）
     */
    private boolean checkCooldown(Method method, CommandSender sender, String cooldownKey) {
        if (cooldownKey == null || !(sender instanceof Player player)) {
            return true;
        }
        
        Cooldown cooldown = method.getAnnotation(Cooldown.class);
        if (cooldown == null) {
            return true;
        }
        
        // 检查绕过权限
        if (!cooldown.bypassPermission().isEmpty() && 
            player.hasPermission(cooldown.bypassPermission())) {
            return true;
        }
        
        // 检查剩余冷却时间
        long remaining = cooldownProcessor.getRemainingCooldown(cooldownKey, player.getName());
        if (remaining > 0) {
            sender.sendMessage(MessageUtils.deserialize(cooldown.message())
                    .replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                            .match("%time%")
                            .replacement(String.format("%.1f", remaining / 1000.0))
                            .build()));
            return false;
        }
        
        // 设置新冷却
        cooldownProcessor.setCooldown(cooldownKey, player.getName(), cooldown.value());
        return true;
    }

    /**
     * 处理命令执行错误。
     *
     * @param throwable     异常
     * @param ctx           Brigadier CommandContext
     * @param instance      命令类实例
     * @param errorHandlers 错误处理器映射
     * @return Brigadier 命令返回值（0 表示失败）
     */
    private int handleError(
            Throwable throwable,
            CommandContext<CommandSourceStack> ctx,
            Object instance,
            Map<Class<? extends Throwable>, Method> errorHandlers) {
        
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        
        // 尝试使用自定义错误处理器
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
        
        // 默认错误处理
        CommandSender sender = ctx.getSource().getSender();
        if (cause instanceof CommandException cmdEx) {
            sender.sendMessage(cmdEx.getAdventureMessage());
        } else {
            sender.sendMessage(
                Component.translatable("command.failed", NamedTextColor.RED)
                    .hoverEvent(Component.text(cause.getMessage(), NamedTextColor.GRAY)));
            cause.printStackTrace();
        }
        
        return 0;
    }
}

package gloomlib.command.registry;

import gloomlib.command.annotation.*;
import gloomlib.command.context.CommandResult;
import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.exception.CommandException;
import gloomlib.command.processor.MethodInvoker;
import gloomlib.command.processor.ProcessorPipeline;
import gloomlib.command.processor.processors.CooldownProcessor;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.util.MessageUtils;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 快速路径命令执行器。
 *
 * <p>
 * 针对简单命令（无复杂参数、无可选参数）提供绕过 Brigadier 完整调度的快速执行路径。
 * 这可以显著减少命令执行延迟，特别是对于高频调用的简单命令。
 * </p>
 *
 * <h2>性能优化原理</h2>
 * <ul>
 * <li>跳过 Brigadier 的完整树遍历（约 6000ns 开销）</li>
 * <li>使用预编译的参数解析器直接解析</li>
 * <li>减少对象分配和 GC 压力</li>
 * </ul>
 *
 * <h2>适用场景</h2>
 * <ul>
 * <li>简单命令（1-3 个固定参数）</li>
 * <li>无可选参数的命令</li>
 * <li>无复杂权限检查的命令</li>
 * </ul>
 */
public class FastPathExecutor {

    /**
     * 快速路径命令定义。
     */
    public static class FastPathCommand {
        private final MethodInvoker invoker;
        private final Object instance;
        private final Parameter[] parameters;
        private final ArgumentResolver<?>[] resolvers;
        private final String[] paramNames;
        private final int startIndex;
        private final boolean isAsync;
        private final String cooldownKey;
        private final Cooldown cooldown;
        private final PlayerOnly playerOnly;
        private final ConsoleOnly consoleOnly;
        private final Map<Class<? extends Throwable>, Method> errorHandlers;

        public FastPathCommand(
                MethodInvoker invoker,
                Object instance,
                Method method,
                Parameter[] parameters,
                ArgumentResolver<?>[] resolvers,
                String[] paramNames,
                int startIndex,
                boolean isAsync,
                String cooldownKey,
                Cooldown cooldown,
                Map<Class<? extends Throwable>, Method> errorHandlers) {
            this.invoker = invoker;
            this.instance = instance;
            this.parameters = parameters;
            this.resolvers = resolvers;
            this.paramNames = paramNames;
            this.startIndex = startIndex;
            this.isAsync = isAsync;
            this.cooldownKey = cooldownKey;
            this.cooldown = cooldown;
            this.playerOnly = method.getAnnotation(PlayerOnly.class);
            this.consoleOnly = method.getAnnotation(ConsoleOnly.class);
            this.errorHandlers = errorHandlers;
        }

        public MethodInvoker getInvoker() { return invoker; }
        public Object getInstance() { return instance; }
        public Parameter[] getParameters() { return parameters; }
        public ArgumentResolver<?>[] getResolvers() { return resolvers; }
        public String[] getParamNames() { return paramNames; }
        public int getStartIndex() { return startIndex; }
        public boolean isAsync() { return isAsync; }
        public String getCooldownKey() { return cooldownKey; }
        public Cooldown getCooldown() { return cooldown; }
        public PlayerOnly getPlayerOnly() { return playerOnly; }
        public ConsoleOnly getConsoleOnly() { return consoleOnly; }
        public Map<Class<? extends Throwable>, Method> getErrorHandlers() { return errorHandlers; }
    }

    // ThreadLocal 对象池：减少 GC 压力
    private static final ThreadLocal<Object[]> ARGS_POOL_1 = ThreadLocal.withInitial(() -> new Object[1]);
    private static final ThreadLocal<Object[]> ARGS_POOL_2 = ThreadLocal.withInitial(() -> new Object[2]);
    private static final ThreadLocal<Object[]> ARGS_POOL_3 = ThreadLocal.withInitial(() -> new Object[3]);
    private static final ThreadLocal<Object[]> ARGS_POOL_4 = ThreadLocal.withInitial(() -> new Object[4]);
    private static final ThreadLocal<Object[]> ARGS_POOL_5 = ThreadLocal.withInitial(() -> new Object[5]);
    private static final ThreadLocal<Object[]> ARGS_POOL_6 = ThreadLocal.withInitial(() -> new Object[6]);

    /** 快速路径命令注册表：命令名 -> 快速路径命令 */
    private final Map<String, FastPathCommand> fastPathCommands = new ConcurrentHashMap<>();

    /** 子命令快速路径：主命令名 + 子命令名 -> 快速路径命令 */
    private final Map<String, FastPathCommand> subCommandFastPaths = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final ArgumentResolverRegistry resolverRegistry;
    private final ProcessorPipeline pipeline;
    private final CooldownProcessor cooldownProcessor;

    public FastPathExecutor(
            JavaPlugin plugin,
            ArgumentResolverRegistry resolverRegistry,
            ProcessorPipeline pipeline,
            CooldownProcessor cooldownProcessor) {
        this.plugin = plugin;
        this.resolverRegistry = resolverRegistry;
        this.pipeline = pipeline;
        this.cooldownProcessor = cooldownProcessor;
    }

    /**
     * 注册快速路径命令。
     *
     * @param commandPath 命令路径（如 "give" 或 "admin:reload"）
     * @param command     快速路径命令定义
     */
    public void register(String commandPath, FastPathCommand command) {
        if (commandPath.contains(":")) {
            subCommandFastPaths.put(commandPath, command);
        } else {
            fastPathCommands.put(commandPath, command);
        }
    }

    /**
     * 检查命令是否支持快速路径。
     *
     * @param method 命令方法
     * @return true 如果支持快速路径
     */
    public static boolean isFastPathEligible(Method method) {
        Parameter[] params = method.getParameters();

        for (Parameter param : params) {
            // 有可选参数的不适合快速路径
            if (param.isAnnotationPresent(Optional.class)) {
                return false;
            }
            // 有 Flag 的不适合
            if (param.isAnnotationPresent(Flag.class)) {
                return false;
            }
            // 有 Switch 的不适合
            if (param.isAnnotationPresent(Switch.class)) {
                return false;
            }
        }

        // 参数过多的命令不适合快速路径
        return params.length <= 6;
    }

    /**
     * 尝试快速路径执行。
     *
     * @param ctx         命令上下文
     * @param commandName 命令名
     * @param subCommand  子命令名（可为 null）
     * @return 执行结果，如果不支持快速路径返回 null
     */
    public Integer tryFastPathExecution(
            CommandContext<CommandSourceStack> ctx,
            String commandName,
            String subCommand) {
        
        FastPathCommand cmd = subCommand != null
                ? subCommandFastPaths.get(commandName + ":" + subCommand)
                : fastPathCommands.get(commandName);

        if (cmd == null) {
            return null; // 不支持快速路径，回退到 Brigadier
        }

        CommandSender sender = ctx.getSource().getSender();

        // 快速权限检查
        if (cmd.getPlayerOnly() != null && !(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.deserialize(cmd.getPlayerOnly().message()));
            return 0;
        }

        if (cmd.getConsoleOnly() != null && sender instanceof Player) {
            sender.sendMessage(MessageUtils.deserialize(cmd.getConsoleOnly().message()));
            return 0;
        }

        // 冷却检查
        if (cmd.getCooldownKey() != null && sender instanceof Player player) {
            Cooldown cooldown = cmd.getCooldown();
            if (cooldown != null && !cooldown.bypassPermission().isEmpty() 
                    && !player.hasPermission(cooldown.bypassPermission())) {
                long remaining = cooldownProcessor.getRemainingCooldown(cmd.getCooldownKey(), player.getName());
                if (remaining > 0) {
                    sender.sendMessage(MessageUtils.deserialize(cooldown.message())
                            .replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                                    .match("%time%")
                                    .replacement(String.format("%.1f", remaining / 1000.0))
                                    .build()));
                    return 1;
                }
                cooldownProcessor.setCooldown(cmd.getCooldownKey(), player.getName(), cooldown.value());
            }
        }

        // 异步执行
        if (cmd.isAsync()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    executeInternal(ctx, cmd, sender);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
            return 1;
        }

        return executeInternal(ctx, cmd, sender);
    }

    /**
     * 内部执行方法。
     */
    private int executeInternal(
            CommandContext<CommandSourceStack> ctx, 
            FastPathCommand cmd, 
            CommandSender sender) {
        
        GloomCommandContext context = new GloomCommandContext(ctx);

        try {
            if (!pipeline.runPreProcessors(context)) {
                return 1;
            }

            // 快速参数解析
            Object[] args = resolveArgumentsFast(ctx, cmd, sender);

            Object result = cmd.getInvoker().invoke(cmd.getInstance(), args);

            pipeline.runPostProcessors(context, CommandResult.success(result));

            return result instanceof Integer ? (Integer) result : 1;

        } catch (Throwable t) {
            handleError(ctx, cmd, sender, t);
            return 0;
        }
    }

    /**
     * 快速参数解析 - 使用对象池减少分配。
     */
    private Object[] resolveArgumentsFast(
            CommandContext<CommandSourceStack> ctx,
            FastPathCommand cmd,
            CommandSender sender) throws CommandException {
        
        Parameter[] params = cmd.getParameters();
        int len = params.length;

        // 使用 ThreadLocal 对象池
        Object[] args = getPooledArray(len);

        try {
            for (int i = 0; i < len; i++) {
                Parameter param = params[i];
                Class<?> paramType = param.getType();

                if (i == 0 && (CommandSender.class.isAssignableFrom(paramType) 
                        || Player.class.isAssignableFrom(paramType))) {
                    args[i] = sender;
                } else if (i < cmd.getStartIndex()) {
                    // 特殊参数处理
                    if (GloomCommandContext.class.isAssignableFrom(paramType)) {
                        args[i] = new GloomCommandContext(ctx);
                    } else {
                        args[i] = sender;
                    }
                } else {
                    // 使用预编译的解析器
                    int resolverIdx = i - cmd.getStartIndex();
                    if (resolverIdx >= 0 && resolverIdx < cmd.getResolvers().length) {
                        ArgumentResolver<?> resolver = cmd.getResolvers()[resolverIdx];
                        String argName = cmd.getParamNames()[resolverIdx];
                        args[i] = resolver.resolve(ctx, argName, param);
                    }
                }
            }
        } catch (Exception e) {
            // 处理默认值
            for (int i = 0; i < len; i++) {
                if (args[i] == null) {
                    Default def = params[i].getAnnotation(Default.class);
                    if (def != null) {
                        args[i] = resolveDefaultValue(def.value(), params[i].getType(), sender);
                    }
                }
            }
        }

        return args;
    }

    /**
     * 获取对象池中的数组。
     */
    private Object[] getPooledArray(int size) {
        return switch (size) {
            case 1 -> ARGS_POOL_1.get();
            case 2 -> ARGS_POOL_2.get();
            case 3 -> ARGS_POOL_3.get();
            case 4 -> ARGS_POOL_4.get();
            case 5 -> ARGS_POOL_5.get();
            case 6 -> ARGS_POOL_6.get();
            default -> new Object[size];
        };
    }

    /**
     * 解析默认值。
     */
    private Object resolveDefaultValue(String defaultValue, Class<?> type, CommandSender sender) {
        if ("self".equals(defaultValue) && Player.class.isAssignableFrom(type)) {
            return sender instanceof Player ? sender : null;
        }
        if (type == String.class) return defaultValue;
        if (type == Integer.class || type == int.class) return Integer.parseInt(defaultValue);
        if (type == Double.class || type == double.class) return Double.parseDouble(defaultValue);
        if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(defaultValue);
        if (type == Long.class || type == long.class) return Long.parseLong(defaultValue);
        return null;
    }

    /**
     * 错误处理。
     */
    private void handleError(
            CommandContext<CommandSourceStack> ctx,
            FastPathCommand cmd, 
            CommandSender sender, 
            Throwable t) {
        
        Throwable cause = t.getCause() != null ? t.getCause() : t;

        for (Map.Entry<Class<? extends Throwable>, Method> entry : cmd.getErrorHandlers().entrySet()) {
            if (entry.getKey().isAssignableFrom(cause.getClass())) {
                try {
                    Method handler = entry.getValue();
                    handler.setAccessible(true);
                    handler.invoke(cmd.getInstance(), new GloomCommandContext(ctx), cause);
                    return;
                } catch (Exception handlerEx) {
                    handlerEx.printStackTrace();
                }
            }
        }

        if (cause instanceof CommandException cmdEx) {
            sender.sendMessage(cmdEx.getAdventureMessage());
        }
        cause.printStackTrace();
    }

    /**
     * 获取已注册的快速路径命令数量。
     */
    public int getRegisteredCount() {
        return fastPathCommands.size() + subCommandFastPaths.size();
    }

    /**
     * 清除所有快速路径注册。
     */
    public void clear() {
        fastPathCommands.clear();
        subCommandFastPaths.clear();
    }
}

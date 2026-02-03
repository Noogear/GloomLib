package gloomlib.command.registry;

import gloomlib.command.annotation.*;
import gloomlib.command.annotation.Optional;
import gloomlib.command.context.AsyncContext;
import gloomlib.command.context.CommandResult;
import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.exception.CommandException;
import gloomlib.command.processor.MethodInvoker;
import gloomlib.command.processor.ProcessorPipeline;
import gloomlib.command.processor.processors.ValidationProcessor;
import gloomlib.command.processor.processors.CooldownProcessor;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.suggestion.SuggestionProvider;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令注册器。
 *
 * <p>
 * 负责扫描命令类、构建 Brigadier 命令树并注册到 Paper 生命周期事件中。
 * 处理注解解析、参数解析、权限检查以及命令的具体执行逻辑。
 * </p>
 */
public class CommandRegistry {

    private static final String MSG_REQUIRE_ANNOTATION = "Class %s must have @Command annotation";
    private static final String MSG_UNSUPPORTED_TYPE = "Unsupported parameter type: %s (param: %s, method: %s)";
    private static final String MSG_RESOLVER_NOT_FOUND = "Argument resolver not found: %s";
    private static final String MSG_PROVIDER_INIT_ERROR = "Could not instantiate suggestion provider: %s";

    private static final String VALUE_SELF = "self";

    private final JavaPlugin plugin;
    private final ArgumentResolverRegistry resolverRegistry;
    private final ProcessorPipeline pipeline;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<Class<? extends SuggestionProvider>, SuggestionProvider> suggestionCache = new HashMap<>();
    private final Map<Method, MethodInvoker> methodInvokerCache = new ConcurrentHashMap<>();

    // Performance Caches
    private final Map<Method, String> cooldownKeyCache = new ConcurrentHashMap<>();
    private final Map<Method, Boolean> asyncCache = new ConcurrentHashMap<>();

    private final ValidationProcessor validationProcessor = new ValidationProcessor();
    private final CooldownProcessor cooldownProcessor = new CooldownProcessor();

    /**
     * 创建命令注册器。
     *
     * @param plugin           插件实例
     * @param resolverRegistry 参数解析器注册表
     * @param pipeline         处理器管道
     */
    public CommandRegistry(JavaPlugin plugin, ArgumentResolverRegistry resolverRegistry, ProcessorPipeline pipeline) {
        this.plugin = plugin;
        this.resolverRegistry = resolverRegistry;
        this.pipeline = pipeline;
    }

    /**
     * 注册命令实例到 Paper。
     *
     * @param commandInstance 命令类实例
     * @param commands        Paper Commands 注册器
     */
    public void registerCommand(Object commandInstance, Commands commands) {
        Class<?> clazz = commandInstance.getClass();

        // 检查 @Command 注解
        gloomlib.command.annotation.Command cmdAnnotation = clazz
                .getAnnotation(gloomlib.command.annotation.Command.class);

        if (cmdAnnotation == null) {
            throw new IllegalArgumentException(String.format(MSG_REQUIRE_ANNOTATION, clazz.getName()));
        }

        String commandName = cmdAnnotation.value();
        String[] aliases = cmdAnnotation.aliases();

        Description descAnnotation = clazz.getAnnotation(Description.class);
        String description = descAnnotation != null ? descAnnotation.value() : "";

        Permission classPermission = clazz.getAnnotation(Permission.class);

        LiteralArgumentBuilder<CommandSourceStack> rootBuilder = Commands.literal(commandName);

        if (classPermission != null) {
            rootBuilder.requires(source -> checkPermission(source.getSender(), classPermission));
        }

        List<Method> usageMethods = new ArrayList<>();
        List<Method> subCommandMethods = new ArrayList<>();
        List<Method> rootAliasMethods = new ArrayList<>();
        Map<Class<? extends Throwable>, Method> errorHandlers = new HashMap<>();

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Usage.class)) {
                usageMethods.add(method);
            } else if (method.isAnnotationPresent(SubCommand.class)) {
                subCommandMethods.add(method);
            } else if (method.isAnnotationPresent(gloomlib.command.annotation.Command.class)) {
                rootAliasMethods.add(method);
            } else if (method.isAnnotationPresent(OnError.class)) {
                OnError onError = method.getAnnotation(OnError.class);
                for (Class<? extends Throwable> exType : onError.value()) {
                    errorHandlers.put(exType, method);
                }
            }
        }

        // 预热缓存
        for (Method method : usageMethods)
            prepareInvoker(method);
        for (Method method : subCommandMethods)
            prepareInvoker(method);
        for (Method method : rootAliasMethods)
            prepareInvoker(method);
        for (Method method : errorHandlers.values())
            prepareInvoker(method);

        // 构建各分支
        for (Method method : usageMethods) {
            buildMethodBranch(rootBuilder, method, commandInstance, errorHandlers);
        }

        for (Method method : subCommandMethods) {
            SubCommand subCmd = method.getAnnotation(SubCommand.class);
            LiteralArgumentBuilder<CommandSourceStack> subBuilder = Commands.literal(subCmd.value());

            Permission methodPermission = method.getAnnotation(Permission.class);
            if (methodPermission != null) {
                subBuilder.requires(source -> checkPermission(source.getSender(), methodPermission));
            }

            buildMethodBranch(subBuilder, method, commandInstance, errorHandlers);
            rootBuilder.then(subBuilder);

            for (String alias : subCmd.aliases()) {
                LiteralArgumentBuilder<CommandSourceStack> aliasBuilder = Commands.literal(alias);
                if (methodPermission != null) {
                    aliasBuilder.requires(source -> checkPermission(source.getSender(), methodPermission));
                }
                buildMethodBranch(aliasBuilder, method, commandInstance, errorHandlers);
                rootBuilder.then(aliasBuilder);
            }
        }

        commands.register(rootBuilder.build(), description, Arrays.asList(aliases));

        for (Method method : rootAliasMethods) {
            gloomlib.command.annotation.Command rootAliasAnnotation = method
                    .getAnnotation(gloomlib.command.annotation.Command.class);

            LiteralArgumentBuilder<CommandSourceStack> aliasRoot = Commands.literal(rootAliasAnnotation.value());

            Permission methodPermission = method.getAnnotation(Permission.class);
            if (methodPermission != null) {
                aliasRoot.requires(source -> checkPermission(source.getSender(), methodPermission));
            }

            buildMethodBranch(aliasRoot, method, commandInstance, errorHandlers);

            Description aliasDesc = method.getAnnotation(Description.class);
            commands.register(
                    aliasRoot.build(),
                    aliasDesc != null ? aliasDesc.value() : "",
                    Arrays.asList(rootAliasAnnotation.aliases()));
        }
    }

    private void prepareInvoker(Method method) {
        methodInvokerCache.computeIfAbsent(method, MethodInvoker::of);

        // Cache Cooldown Key to reduce string concat at runtime
        if (method.isAnnotationPresent(Cooldown.class)) {
            cooldownKeyCache.put(method, method.getDeclaringClass().getName() + "#" + method.getName());
        }

        // Cache Async status
        asyncCache.put(method, method.isAnnotationPresent(Async.class));
    }

    private void buildMethodBranch(
            LiteralArgumentBuilder<CommandSourceStack> builder,
            Method method,
            Object instance,
            Map<Class<? extends Throwable>, Method> errorHandlers) {
        Parameter[] parameters = method.getParameters();
        int startIndex = getStartParameterIndex(parameters);

        if (startIndex >= parameters.length) {
            builder.executes(ctx -> executeMethod(ctx, method, instance, parameters, errorHandlers));
        } else {
            buildArgumentChain(builder, method, instance, parameters, startIndex, errorHandlers);
        }
    }

    private void buildArgumentChain(
            ArgumentBuilder<CommandSourceStack, ?> builder,
            Method method,
            Object instance,
            Parameter[] parameters,
            int paramIndex,
            Map<Class<? extends Throwable>, Method> errorHandlers) {
        if (paramIndex >= parameters.length) {
            builder.executes(ctx -> executeMethod(ctx, method, instance, parameters, errorHandlers));
            return;
        }

        Parameter param = parameters[paramIndex];

        if (param.isAnnotationPresent(Flag.class) || param.isAnnotationPresent(Switch.class)) {
            buildArgumentChain(builder, method, instance, parameters, paramIndex + 1, errorHandlers);
            return;
        }

        String argName = getParameterName(param);
        ArgumentResolver<?> resolver = resolverRegistry.getResolver(param.getType());
        if (resolver == null) {
            throw new IllegalArgumentException(
                    String.format(MSG_UNSUPPORTED_TYPE, param.getType().getName(), argName, method.getName()));
        }

        RequiredArgumentBuilder<CommandSourceStack, ?> argumentBuilder = Commands.argument(argName,
                resolver.createArgumentType(param));

        Suggest suggestAnnotation = param.getAnnotation(Suggest.class);
        if (suggestAnnotation != null) {
            SuggestionProvider provider = getSuggestionProvider(suggestAnnotation.value());
            argumentBuilder.suggests((ctx, suggestionsBuilder) -> provider.suggest(ctx, suggestionsBuilder));
        } else {
            argumentBuilder.suggests((ctx, suggestionsBuilder) -> resolver.suggest(ctx, suggestionsBuilder, param));
        }

        if (param.isAnnotationPresent(Optional.class)) {
            builder.executes(ctx -> executeMethod(ctx, method, instance, parameters, errorHandlers));
        }

        buildArgumentChain(argumentBuilder, method, instance, parameters, paramIndex + 1, errorHandlers);
        builder.then(argumentBuilder);
    }

    private int executeMethod(
            CommandContext<CommandSourceStack> ctx,
            Method method,
            Object instance,
            Parameter[] parameters,
            Map<Class<? extends Throwable>, Method> errorHandlers) {

        // Use cached Async status
        boolean isAsync = asyncCache.getOrDefault(method, false);

        if (isAsync) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    executeMethodInternal(ctx, method, instance, parameters, errorHandlers);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
            return Command.SINGLE_SUCCESS;
        } else {
            return executeMethodInternal(ctx, method, instance, parameters, errorHandlers);
        }
    }

    private int executeMethodInternal(
            CommandContext<CommandSourceStack> ctx,
            Method method,
            Object instance,
            Parameter[] parameters,
            Map<Class<? extends Throwable>, Method> errorHandlers) {

        GloomCommandContext context = new GloomCommandContext(ctx);

        try {
            if (!pipeline.runPreProcessors(context)) {
                return Command.SINGLE_SUCCESS;
            }

            CommandSender sender = ctx.getSource().getSender();

            PlayerOnly playerOnly = method.getAnnotation(PlayerOnly.class);
            if (playerOnly != null && !(sender instanceof Player)) {
                sender.sendMessage(miniMessage.deserialize(playerOnly.message()));
                return 0;
            }

            ConsoleOnly consoleOnly = method.getAnnotation(ConsoleOnly.class);
            if (consoleOnly != null && sender instanceof Player) {
                sender.sendMessage(miniMessage.deserialize(consoleOnly.message()));
                return 0;
            }

            // Optimized Cooldown Check using Cached Key
            String commandKey = cooldownKeyCache.get(method);
            if (commandKey != null && sender instanceof Player player) {
                Cooldown cooldown = method.getAnnotation(Cooldown.class);

                if (!cooldown.bypassPermission().isEmpty() && player.hasPermission(cooldown.bypassPermission())) {
                    // bypass
                } else {
                    long remaining = cooldownProcessor.getRemainingCooldown(commandKey, player.getName());
                    if (remaining > 0) {
                        sender.sendMessage(miniMessage.deserialize(cooldown.message())
                                .replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                                        .match("%time%")
                                        .replacement(String.format("%.1f", remaining / 1000.0))
                                        .build()));
                        return Command.SINGLE_SUCCESS;
                    }
                    cooldownProcessor.setCooldown(commandKey, player.getName(), cooldown.value());
                }
            }

            Object[] args = resolveArguments(ctx, method, parameters, sender);

            MethodInvoker invoker = methodInvokerCache.get(method);

            Object result = invoker.invoke(instance, args);

            pipeline.runPostProcessors(context, CommandResult.success(result));

            return result instanceof Integer ? (Integer) result : Command.SINGLE_SUCCESS;

        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;

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

            CommandSender sender = ctx.getSource().getSender();
            if (cause instanceof CommandException cmdEx) {
                sender.sendMessage(cmdEx.getAdventureMessage());
                sender.sendMessage(
                        Component.translatable("command.failed", NamedTextColor.RED)
                                .hoverEvent(Component.text(cause.getMessage(), NamedTextColor.GRAY)));
                cause.printStackTrace();
            }
            return 0;
        }
    }

    private Object[] resolveArguments(
            CommandContext<CommandSourceStack> ctx,
            Method method,
            Parameter[] parameters,
            CommandSender sender) throws CommandException {
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();
            Object resolvedValue = null;

            if (CommandSender.class.isAssignableFrom(paramType)) {
                resolvedValue = sender;
            } else if (Player.class.equals(paramType) && i == 0) {
                resolvedValue = sender;
            } else if (AsyncContext.class.isAssignableFrom(paramType)) {
                resolvedValue = new AsyncContext(ctx, plugin);
            } else if (GloomCommandContext.class.isAssignableFrom(paramType)) {
                resolvedValue = new GloomCommandContext(ctx);
            } else if (param.isAnnotationPresent(Switch.class)) {
                resolvedValue = false;
            } else if (param.isAnnotationPresent(Flag.class)) {
                resolvedValue = null;
            } else {
                String argName = getParameterName(param);
                try {
                    ArgumentResolver<?> resolver = resolverRegistry.getResolver(paramType);
                    if (resolver != null) {
                        resolvedValue = resolver.resolve(ctx, argName, param);
                    } else {
                        throw new IllegalArgumentException(String.format(MSG_RESOLVER_NOT_FOUND, paramType.getName()));
                    }
                } catch (Exception e) {
                    Default defaultAnnotation = param.getAnnotation(Default.class);
                    if (defaultAnnotation != null) {
                        resolvedValue = resolveDefaultValue(defaultAnnotation.value(), paramType, sender);
                    } else if (param.isAnnotationPresent(Optional.class)) {
                        resolvedValue = null;
                    } else {
                        throw e;
                    }
                }
            }

            if (resolvedValue instanceof Number numberValue) {
                if (param.isAnnotationPresent(Range.class)) {
                    Range range = param.getAnnotation(Range.class);
                    ValidationProcessor.ValidationResult result = validationProcessor.validateRange(numberValue, range,
                            param);
                    if (!result.isValid()) {
                        throw new CommandException(result.getErrorMessage());
                    }
                }
            }
            args[i] = resolvedValue;
        }
        return args;
    }

    private Object resolveDefaultValue(String defaultValue, Class<?> type, CommandSender sender) {
        if (VALUE_SELF.equals(defaultValue) && Player.class.isAssignableFrom(type)) {
            return sender instanceof Player ? sender : null;
        }
        if (type == String.class)
            return defaultValue;
        if (type == Integer.class || type == int.class)
            return Integer.parseInt(defaultValue);
        if (type == Double.class || type == double.class)
            return Double.parseDouble(defaultValue);
        if (type == Boolean.class || type == boolean.class)
            return Boolean.parseBoolean(defaultValue);
        if (type == Long.class || type == long.class)
            return Long.parseLong(defaultValue);
        return null;
    }

    private int getStartParameterIndex(Parameter[] parameters) {
        if (parameters.length == 0)
            return 0;
        Class<?> firstType = parameters[0].getType();
        if (CommandSender.class.isAssignableFrom(firstType) ||
                Player.class.isAssignableFrom(firstType) ||
                GloomCommandContext.class.isAssignableFrom(firstType) ||
                AsyncContext.class.isAssignableFrom(firstType)) {
            return 1;
        }
        return 0;
    }

    private String getParameterName(Parameter param) {
        Arg arg = param.getAnnotation(Arg.class);
        if (arg != null && !arg.value().isEmpty()) {
            return arg.value();
        }
        return param.getName();
    }

    private boolean checkPermission(CommandSender sender, Permission permission) {
        return switch (permission.mode()) {
            case OP -> sender.isOp();
            case ANY -> true;
            case REQUIRE -> sender.hasPermission(permission.value());
        };
    }

    private SuggestionProvider getSuggestionProvider(Class<? extends SuggestionProvider> providerClass) {
        return suggestionCache.computeIfAbsent(providerClass, clazz -> {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(String.format(MSG_PROVIDER_INIT_ERROR, clazz.getName()), e);
            }
        });
    }
}

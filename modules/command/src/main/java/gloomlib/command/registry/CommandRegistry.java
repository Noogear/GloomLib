package gloomlib.command.registry;

import gloomlib.command.annotation.*;
import gloomlib.command.exception.CommandException;
import gloomlib.command.processor.MethodInvoker;
import gloomlib.command.processor.ProcessorPipeline;
import gloomlib.command.processor.processors.CooldownProcessor;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.util.CommandMessages;
import gloomlib.command.util.ParameterUtils;
import gloomlib.command.parser.ArgumentParser;
import gloomlib.command.executor.CommandExecutor;
import gloomlib.command.builder.BrigadierTreeBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令注册器（协调者）。
 *
 * <p>
 * 负责协调各组件完成命令注册流程，包括：
 * </p>
 * <ul>
 * <li>扫描命令类的注解（@Command, @Usage, @SubCommand 等）</li>
 * <li>预热缓存（MethodInvoker, Cooldown Key, Async 状态）</li>
 * <li>注册快速路径命令（FastPathExecutor）</li>
 * <li>构建 Brigadier 命令树（BrigadierTreeBuilder）</li>
 * <li>协调命令执行（ArgumentParser + CommandExecutor）</li>
 * </ul>
 *
 * <h2>架构组件</h2>
 * <ul>
 * <li>{@link ArgumentParser} - 参数解析器，负责从 CommandContext 解析参数</li>
 * <li>{@link CommandExecutor} - 命令执行器，负责运行命令方法</li>
 * <li>{@link BrigadierTreeBuilder} - Brigadier 树构建器，负责构建命令树</li>
 * <li>{@link FastPathExecutor} - 快速路径执行器，绕过 Brigadier 提升性能</li>
 * </ul>
 */
public class CommandRegistry {

    private final JavaPlugin plugin;
    private final ArgumentResolverRegistry resolverRegistry;
    private final ProcessorPipeline pipeline;
    private final Map<Method, MethodInvoker> methodInvokerCache = new ConcurrentHashMap<>();

    // Performance Caches
    private final Map<Method, String> cooldownKeyCache = new ConcurrentHashMap<>();
    private final Map<Method, Boolean> asyncCache = new ConcurrentHashMap<>();

    private final CooldownProcessor cooldownProcessor = new CooldownProcessor();

    /** 快速路径执行器 - 用于优化简单命令的执行性能 */
    private final FastPathExecutor fastPathExecutor;

    // 新组件
    private final ArgumentParser argumentParser;
    private final CommandExecutor commandExecutor;
    private final BrigadierTreeBuilder treeBuilder;

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
        this.fastPathExecutor = new FastPathExecutor(plugin, resolverRegistry, pipeline, cooldownProcessor);
        
        // 初始化新组件
        this.argumentParser = new ArgumentParser(plugin, resolverRegistry);
        this.commandExecutor = new CommandExecutor(plugin, pipeline, cooldownProcessor);
        this.treeBuilder = new BrigadierTreeBuilder(resolverRegistry);
    }

    /**
     * 获取快速路径执行器。
     *
     * @return 快速路径执行器实例
     */
    public FastPathExecutor getFastPathExecutor() {
        return fastPathExecutor;
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
            throw new IllegalArgumentException(String.format(CommandMessages.MSG_REQUIRE_ANNOTATION, clazz.getName()));
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

        // 注册快速路径（符合条件的命令）
        for (Method method : usageMethods) {
            registerFastPathIfEligible(commandName, method, commandInstance, errorHandlers);
        }
        for (Method method : subCommandMethods) {
            SubCommand subCmd = method.getAnnotation(SubCommand.class);
            registerFastPathIfEligible(commandName + ":" + subCmd.value(), method, commandInstance, errorHandlers);
        }

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

    /**
     * 为命令方法注册快速路径（如果符合条件）。
     *
     * @param commandPath   命令路径
     * @param method        命令方法
     * @param instance      命令实例
     * @param errorHandlers 错误处理器
     */
    private void registerFastPathIfEligible(
            String commandPath,
            Method method,
            Object instance,
            Map<Class<? extends Throwable>, Method> errorHandlers) {
        
        if (!FastPathExecutor.isFastPathEligible(method)) {
            return;
        }

        Parameter[] parameters = method.getParameters();
        int startIndex = ParameterUtils.getStartParameterIndex(parameters);
        int argCount = parameters.length - startIndex;

        // 构建解析器数组
        ArgumentResolver<?>[] resolvers = new ArgumentResolver<?>[argCount];
        String[] paramNames = new String[argCount];

        for (int i = startIndex; i < parameters.length; i++) {
            Parameter param = parameters[i];
            int idx = i - startIndex;
            resolvers[idx] = resolverRegistry.getResolver(param.getType());
            paramNames[idx] = ParameterUtils.getParameterName(param);
        }

        MethodInvoker invoker = methodInvokerCache.get(method);
        String cooldownKey = cooldownKeyCache.get(method);
        Cooldown cooldown = method.getAnnotation(Cooldown.class);
        boolean isAsync = asyncCache.getOrDefault(method, false);

        FastPathExecutor.FastPathCommand fastCmd = new FastPathExecutor.FastPathCommand(
                invoker,
                instance,
                method,
                parameters,
                resolvers,
                paramNames,
                startIndex,
                isAsync,
                cooldownKey,
                cooldown,
                errorHandlers
        );

        fastPathExecutor.register(commandPath, fastCmd);
    }

    private void buildMethodBranch(
            LiteralArgumentBuilder<CommandSourceStack> builder,
            Method method,
            Object instance,
            Map<Class<? extends Throwable>, Method> errorHandlers) {
        
        treeBuilder.buildMethodBranch(builder, method, ctx -> 
            executeMethod(ctx, method, instance, methodInvokerCache.get(method), errorHandlers));
    }



    private int executeMethod(
            CommandContext<CommandSourceStack> ctx,
            Method method,
            Object instance,
            MethodInvoker invoker,
            Map<Class<? extends Throwable>, Method> errorHandlers) {
        
        // 解析参数
        Object[] args;
        try {
            args = argumentParser.resolveArguments(ctx, method.getParameters(), ctx.getSource().getSender());
        } catch (CommandException e) {
            ctx.getSource().getSender().sendMessage(e.getAdventureMessage());
            return 0;
        }
        
        // 执行命令
        boolean isAsync = asyncCache.getOrDefault(method, false);
        String cooldownKey = cooldownKeyCache.get(method);
        
        return commandExecutor.execute(
            ctx, method, instance, args, invoker, 
            isAsync, cooldownKey, errorHandlers);
    }


    private boolean checkPermission(CommandSender sender, Permission permission) {
        return switch (permission.mode()) {
            case OP -> sender.isOp();
            case ANY -> true;
            case REQUIRE -> sender.hasPermission(permission.value());
        };
    }

}

package gloomlib.command.registry;

import gloomlib.command.annotation.*;
import gloomlib.command.exception.CommandException;
import gloomlib.command.processor.MethodInvoker;
import gloomlib.command.processor.ProcessorPipeline;
import gloomlib.command.processor.processors.CooldownProcessor;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;

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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command Registry (Coordinator).
 *
 * <p>
 * Responsible for coordinating various components to complete the command
 * registration process, including:
 * </p>
 * <ul>
 * <li>Scanning command class annotations (@Command, @Usage, @SubCommand,
 * etc.)</li>
 * <li>Warming up caches (MethodInvoker, Cooldown Key, Async status)</li>
 * <li>Building Brigadier command trees (BrigadierTreeBuilder)</li>
 * <li>Coordinating command execution (ArgumentParser + CommandExecutor)</li>
 * </ul>
 *
 * <h2>Architectural Components</h2>
 * <ul>
 * <li>{@link ArgumentParser} - Argument parser, responsible for parsing
 * arguments from CommandContext</li>
 * <li>{@link CommandExecutor} - Command executor, responsible for running
 * command methods</li>
 * <li>{@link BrigadierTreeBuilder} - Brigadier tree builder, responsible for
 * building command trees</li>
 * </ul>
 */
public class CommandRegistry {

    private final Map<Method, MethodInvoker> methodInvokerCache = new ConcurrentHashMap<>();

    // Performance Caches
    private final Map<Method, String> cooldownKeyCache = new ConcurrentHashMap<>();
    private final Map<Method, Boolean> asyncCache = new ConcurrentHashMap<>();

    private final CooldownProcessor cooldownProcessor = new CooldownProcessor();

    // Components
    private final ArgumentParser argumentParser;
    private final CommandExecutor commandExecutor;
    private final BrigadierTreeBuilder treeBuilder;

    /**
     * Creates a command registry.
     *
     * @param plugin           Plugin instance
     * @param resolverRegistry Argument resolver registry
     * @param pipeline         Processor pipeline
     */
    public CommandRegistry(JavaPlugin plugin, ArgumentResolverRegistry resolverRegistry, ProcessorPipeline pipeline) {
        // Initialize components
        this.argumentParser = new ArgumentParser(plugin, resolverRegistry);
        this.commandExecutor = new CommandExecutor(plugin, pipeline, cooldownProcessor);
        this.treeBuilder = new BrigadierTreeBuilder(resolverRegistry);
    }

    /**
     * Registers a command instance to Paper.
     *
     * @param commandInstance Command class instance
     * @param commands        Paper Commands registrar
     */
    public void registerCommand(Object commandInstance, Commands commands) {
        Class<?> clazz = commandInstance.getClass();

        // Check @Command annotation
        gloomlib.command.annotation.Command cmdAnnotation = clazz
                .getAnnotation(gloomlib.command.annotation.Command.class);

        if (cmdAnnotation == null) {
            throw new IllegalArgumentException(
                    String.format("Class %s must have @Command annotation", clazz.getName()));
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

        // Warm-up cache
        for (Method method : usageMethods)
            prepareInvoker(method);
        for (Method method : subCommandMethods)
            prepareInvoker(method);
        for (Method method : rootAliasMethods)
            prepareInvoker(method);
        for (Method method : errorHandlers.values())
            prepareInvoker(method);

        // Build branches
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

        treeBuilder.buildMethodBranch(builder, method,
                ctx -> executeMethod(ctx, method, instance, methodInvokerCache.get(method), errorHandlers));
    }

    private int executeMethod(
            CommandContext<CommandSourceStack> ctx,
            Method method,
            Object instance,
            MethodInvoker invoker,
            Map<Class<? extends Throwable>, Method> errorHandlers) {

        // Resolve arguments
        Object[] args;
        try {
            args = argumentParser.resolveArguments(ctx, method.getParameters(), ctx.getSource().getSender());
        } catch (CommandException e) {
            ctx.getSource().getSender().sendMessage(e.getAdventureMessage());
            return 0;
        }

        // Execute command
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

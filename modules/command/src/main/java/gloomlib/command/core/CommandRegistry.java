package gloomlib.command.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.annotation.*;
import gloomlib.command.exception.CommandException;
import gloomlib.command.processor.MethodInvoker;
import gloomlib.command.processor.ProcessorPipeline;
import gloomlib.command.processor.processors.CooldownProcessor;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

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

    private final JavaPlugin plugin;
    private final Logger logger;
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
        this(plugin, plugin.getLogger(), resolverRegistry, pipeline);
    }

    /**
     * Creates a command registry with custom logger.
     *
     * @param plugin           Plugin instance
     * @param logger           Custom logger (if null, uses plugin.getLogger())
     * @param resolverRegistry Argument resolver registry
     * @param pipeline         Processor pipeline
     */
    public CommandRegistry(JavaPlugin plugin, Logger logger, ArgumentResolverRegistry resolverRegistry, ProcessorPipeline pipeline) {
        this.plugin = plugin;
        this.logger = logger;
        // Initialize components
        this.argumentParser = new ArgumentParser(plugin, resolverRegistry);
        this.commandExecutor = new CommandExecutor(plugin, pipeline, cooldownProcessor);
        this.treeBuilder = new BrigadierTreeBuilder(resolverRegistry);
    }

    /**
     * Registers a command instance to Paper.
     *
     * <p>
     * Returns the actual registered names, which may include namespaced versions
     * (e.g., "myplugin:fly") if the command name conflicts with existing commands.
     * </p>
     *
     * @param commandInstance Command class instance
     * @param commands        Paper Commands registrar
     * @param pluginMeta      Plugin metadata for namespace
     * @return Set of actually registered command names (may include namespace)
     */
    public java.util.Set<String> registerCommand(Object commandInstance, Commands commands, io.papermc.paper.plugin.configuration.PluginMeta pluginMeta) {
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

        // Scan methods
        Methods methods = scanMethods(clazz);

        // Warmup caches
        warmup(methods);

        // Build command tree
        LiteralArgumentBuilder<CommandSourceStack> rootBuilder = Commands.literal(commandName);
        if (classPermission != null) {
            rootBuilder.requires(source -> checkPermission(source.getSender(), classPermission));
        }

        buildTree(rootBuilder, commandInstance, methods);

        // Register main command
        java.util.Set<String> actualRegisteredNames = new java.util.HashSet<>();
        actualRegisteredNames.addAll(commands.registerWithFlags(
                pluginMeta, rootBuilder.build(), description,
                Arrays.asList(aliases),
                Collections.emptySet()
        ));

        // Register root alias commands
        for (Method method : methods.rootAliases) {
            gloomlib.command.annotation.Command rootAliasAnnotation = method
                    .getAnnotation(gloomlib.command.annotation.Command.class);

            LiteralArgumentBuilder<CommandSourceStack> aliasRoot = Commands.literal(rootAliasAnnotation.value());
            Permission methodPermission = method.getAnnotation(Permission.class);
            if (methodPermission != null) {
                aliasRoot.requires(source -> checkPermission(source.getSender(), methodPermission));
            }

            buildMethodBranch(aliasRoot, method, commandInstance, methods.errorHandlers);

            Description aliasDesc = method.getAnnotation(Description.class);
            actualRegisteredNames.addAll(commands.registerWithFlags(
                    pluginMeta, aliasRoot.build(),
                    aliasDesc != null ? aliasDesc.value() : "",
                    Arrays.asList(rootAliasAnnotation.aliases()),
                    Collections.emptySet()
            ));
        }

        // Conflict detection
        logConflict(commandName, aliases, actualRegisteredNames);

        return actualRegisteredNames;
    }

    private Methods scanMethods(Class<?> clazz) {
        Methods result = new Methods();

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Usage.class)) {
                result.usage.add(method);
            } else if (method.isAnnotationPresent(SubCommand.class)) {
                result.subCommands.add(method);
            } else if (method.isAnnotationPresent(gloomlib.command.annotation.Command.class)) {
                result.rootAliases.add(method);
            } else if (method.isAnnotationPresent(OnError.class)) {
                OnError onError = method.getAnnotation(OnError.class);
                for (Class<? extends Throwable> exType : onError.value()) {
                    result.errorHandlers.put(exType, method);
                }
            }
        }

        return result;
    }

    private void warmup(Methods methods) {
        for (Method method : methods.usage) prepareInvoker(method);
        for (Method method : methods.subCommands) prepareInvoker(method);
        for (Method method : methods.rootAliases) prepareInvoker(method);
        for (Method method : methods.errorHandlers.values()) prepareInvoker(method);
    }

    private void buildTree(LiteralArgumentBuilder<CommandSourceStack> rootBuilder,
                           Object commandInstance, Methods methods) {
        // Build usage branches
        for (Method method : methods.usage) {
            buildMethodBranch(rootBuilder, method, commandInstance, methods.errorHandlers);
        }

        // Build subcommand branches
        for (Method method : methods.subCommands) {
            SubCommand subCmd = method.getAnnotation(SubCommand.class);
            LiteralArgumentBuilder<CommandSourceStack> subBuilder = Commands.literal(subCmd.value());

            Permission methodPermission = method.getAnnotation(Permission.class);
            if (methodPermission != null) {
                subBuilder.requires(source -> checkPermission(source.getSender(), methodPermission));
            }

            buildMethodBranch(subBuilder, method, commandInstance, methods.errorHandlers);
            rootBuilder.then(subBuilder);

            // Build subcommand aliases
            for (String alias : subCmd.aliases()) {
                LiteralArgumentBuilder<CommandSourceStack> aliasBuilder = Commands.literal(alias);
                if (methodPermission != null) {
                    aliasBuilder.requires(source -> checkPermission(source.getSender(), methodPermission));
                }
                buildMethodBranch(aliasBuilder, method, commandInstance, methods.errorHandlers);
                rootBuilder.then(aliasBuilder);
            }
        }
    }

    private void logConflict(String commandName, String[] aliases,
                             java.util.Set<String> actualRegisteredNames) {
        java.util.Set<String> requestedNames = new java.util.HashSet<>();
        requestedNames.add(commandName.toLowerCase());
        for (String alias : aliases) {
            requestedNames.add(alias.toLowerCase());
        }

        boolean hasNamespaced = actualRegisteredNames.stream()
                .anyMatch(name -> name.contains(":"));

        if (hasNamespaced || actualRegisteredNames.size() != requestedNames.size()) {
            logger.warning(String.format(
                    "Command '%s' conflict detected: requested [%s], registered [%s]",
                    commandName,
                    String.join(", ", requestedNames),
                    String.join(", ", actualRegisteredNames)
            ));
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

    private static class Methods {
        List<Method> usage = new ArrayList<>();
        List<Method> subCommands = new ArrayList<>();
        List<Method> rootAliases = new ArrayList<>();
        Map<Class<? extends Throwable>, Method> errorHandlers = new HashMap<>();
    }

}

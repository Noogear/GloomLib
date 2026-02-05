package gloomlib.command;

import gloomlib.command.core.CommandRegistry;
import gloomlib.command.injection.DependencyInjector;
import gloomlib.command.internal.CommandMetadata;
import gloomlib.command.internal.CommandTracker;
import gloomlib.command.internal.CommandUnregistrar;
import gloomlib.command.processor.ProcessorPipeline;
import gloomlib.command.processor.processors.LoggingProcessor;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.resolver.registry.BuiltInResolvers;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * GloomCommand Framework Entry Point.
 *
 * <p>
 * A modern command framework based on Paper API and Adventure API.
 * </p>
 *
 * <h2>Instance Isolation</h2>
 * <p>
 * Each {@code GloomCommand} instance is <b>fully isolated</b>:
 * </p>
 * <ul>
 * <li><b>Independent command registry</b>: Commands registered in one instance
 * do not interfere with another instance</li>
 * <li><b>Separate caches</b>: Each instance has its own resolver caches,
 * dependency injection, and command tracking</li>
 * <li><b>No cross-plugin interference</b>: One plugin cannot accidentally
 * unregister or affect another plugin's commands</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * public class MyPlugin extends JavaPlugin {
 *     @Override
 *     public void onEnable() {
 *         GloomCommand gloom = GloomCommand.builder(this)
 *                 .build();
 *
 *         gloom.registerService(MyService.class, new MyService());
 *         gloom.registerCommand(new GameModeCommand());
 *     }
 * }
 * }</pre>
 *
 * <h2>Command Class Example</h2>
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Command("gamemode")
 *     &#64;Permission("server.gamemode")
 *     &#64;Description("Change game mode")
 *     public class GameModeCommand {
 *
 *         &#64;Usage
 *         @PlayerOnly
 *         public void setMode(Player player, @Arg GameMode mode) {
 *             player.setGameMode(mode);
 *             player.sendMessage(Component.text("Switched to " + mode.name()));
 *         }
 *     }
 * }
 * </pre>
 */
public final class GloomCommand {

    private final JavaPlugin plugin;
    private final ArgumentResolverRegistry resolverRegistry;
    private final DependencyInjector injector;
    private final CommandRegistry commandRegistry;
    private final ProcessorPipeline pipeline;
    private final List<Object> pendingCommands = new ArrayList<>();

    private final CommandTracker tracker = new CommandTracker();
    private volatile CommandUnregistrar unregistrar = null;

    private boolean initialized = false;
    private volatile io.papermc.paper.command.brigadier.Commands commands = null;

    private GloomCommand(Builder builder) {
        this.plugin = builder.plugin;
        this.resolverRegistry = new ArgumentResolverRegistry();
        this.injector = new DependencyInjector();
        this.pipeline = new ProcessorPipeline();

        // Register default processors
        pipeline.registerPreProcessor(new LoggingProcessor(plugin));

        this.commandRegistry = new CommandRegistry(plugin, resolverRegistry, pipeline);

        // Register built-in resolvers
        initializeBuiltInResolvers();

        // Register Paper Lifecycle event handlers
        registerLifecycleHandler();
    }

    /**
     * Creates a Builder.
     *
     * @param plugin Paper plugin instance
     * @return Builder
     */
    public static Builder builder(JavaPlugin plugin) {
        return new Builder(plugin);
    }

    /**
     * Initializes built-in argument resolvers.
     */
    private void initializeBuiltInResolvers() {
        BuiltInResolvers.registerAll(resolverRegistry);
    }

    /**
     * Registers Paper Lifecycle event handler.
     */
    private void registerLifecycleHandler() {
        plugin.getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> {
                    initialized = true;
                    commands = event.registrar();

                    // Initialize CommandUnregistrar
                    unregistrar = new CommandUnregistrar(commands, tracker);

                    // Register all pending commands
                    for (Object command : pendingCommands) {
                        Set<String> actualNames = commandRegistry.registerCommand(
                                command, commands, plugin.getPluginMeta()
                        );

                        if (actualNames != null && !actualNames.isEmpty()) {
                            tracker.track(command, actualNames);
                        }
                    }
                    pendingCommands.clear();
                });
    }

    /**
     * Registers a command instance.
     *
     * @param commandInstance Command class instance
     * @return this (chainable)
     */
    public GloomCommand registerCommand(Object commandInstance) {
        injector.injectDependencies(commandInstance);

        if (initialized) {
            // Register directly if already initialized
            plugin.getLifecycleManager().registerEventHandler(
                    LifecycleEvents.COMMANDS,
                    event -> {
                        Set<String> actualNames = commandRegistry.registerCommand(
                                commandInstance, event.registrar(), plugin.getPluginMeta()
                        );

                        if (actualNames != null && !actualNames.isEmpty()) {
                            tracker.track(commandInstance, actualNames);
                        }
                    });
        } else {
            pendingCommands.add(commandInstance);
        }

        return this;
    }

    /**
     * Registers a service (for dependency injection).
     *
     * @param type     Service type
     * @param instance Service instance
     * @param <T>      Type
     * @return this (chainable)
     */
    public <T> GloomCommand registerService(Class<T> type, T instance) {
        injector.registerSingleton(type, instance);
        return this;
    }

    /**
     * Registers a service with a qualifier.
     *
     * @param qualifier Qualifier
     * @param instance  Service instance
     * @param <T>       Type
     * @return this (chainable)
     */
    public <T> GloomCommand registerService(String qualifier, T instance) {
        injector.registerBean(qualifier, instance);
        return this;
    }

    /**
     * Registers a custom argument resolver.
     *
     * @param type     Argument type
     * @param resolver Resolver
     * @param <T>      Type
     * @return this (chainable)
     */
    public <T> GloomCommand registerArgumentResolver(Class<T> type, ArgumentResolver<T> resolver) {
        resolverRegistry.register(type, resolver);
        return this;
    }

    /**
     * Gets the dependency injector.
     *
     * @return dependency injector
     */
    public DependencyInjector getInjector() {
        return injector;
    }

    /**
     * Unregisters a command by name with ownership verification.
     *
     * @param commandName Command name (without '/')
     * @return true if successfully unregistered
     */
    public boolean unregisterCommand(String commandName) {
        if (unregistrar == null) {
            plugin.getLogger().warning("Cannot unregister: Framework not initialized");
            return false;
        }

        boolean success = unregistrar.unregister(commandName);
        if (success) {
            if (resolverRegistry != null) {
                resolverRegistry.clearAllResolverCaches();
                resolverRegistry.clearCache();
            }
        }
        return success;
    }

    /**
     * Unregisters a command by instance.
     *
     * @param commandInstance Command instance to unregister
     * @return true if successfully unregistered
     */
    public boolean unregisterCommand(Object commandInstance) {
        if (commandInstance == null) {
            return false;
        }

        String commandName = CommandMetadata.getName(commandInstance);
        if (commandName == null) {
            plugin.getLogger().warning("Cannot unregister: no @Command annotation");
            return false;
        }

        return unregisterCommand(commandName);
    }

    /**
     * Checks if a command is registered.
     *
     * @param commandName Command name to check
     * @return true if registered
     */
    public boolean isCommandRegistered(String commandName) {
        return commandName != null && tracker.owns(commandName);
    }

    /**
     * Gets all registered command names.
     *
     * @return Unmodifiable set of registered command names
     */
    public Set<String> getRegisteredCommandNames() {
        return tracker.getTrackedNames();
    }

    /**
     * Gets the resolver registry.
     *
     * @return Resolver registry
     */
    public ArgumentResolverRegistry getResolverRegistry() {
        return resolverRegistry;
    }

    /**
     * Gets the plugin instance.
     *
     * @return plugin instance
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * GloomCommand Builder.
     */
    public static final class Builder {

        private final JavaPlugin plugin;

        private Builder(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        /**
         * Builds GloomCommand instance.
         *
         * @return GloomCommand instance
         */
        public GloomCommand build() {
            return new GloomCommand(this);
        }
    }
}

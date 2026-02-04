package gloomlib.command;

import gloomlib.command.injection.DependencyInjector;
import gloomlib.command.processor.ProcessorPipeline;
import gloomlib.command.processor.processors.LoggingProcessor;
import gloomlib.command.registry.CommandRegistry;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.resolver.resolvers.*;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * GloomCommand Framework Entry Point.
 *
 * <p>
 * A modern command framework based on Paper API and Adventure API.
 * </p>
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
    private boolean initialized = false;

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
     * Initializes built-in argument resolvers.
     */
    private void initializeBuiltInResolvers() {
        // Basic Types
        resolverRegistry.register(String.class, new StringResolver());
        resolverRegistry.register(Integer.class, new IntegerResolver());
        resolverRegistry.register(Long.class, new LongResolver());
        resolverRegistry.register(Float.class, new FloatResolver());
        resolverRegistry.register(Double.class, new DoubleResolver());
        resolverRegistry.register(Boolean.class, new BooleanResolver());

        // Paper API Types
        resolverRegistry.register(Player.class, new PlayerResolver());
        resolverRegistry.register(org.bukkit.OfflinePlayer.class, new OfflinePlayerResolver());
        resolverRegistry.register(World.class, new WorldResolver());
        resolverRegistry.register(GameMode.class, new GameModeResolver());
        resolverRegistry.register(org.bukkit.Material.class, new MaterialResolver());
        resolverRegistry.register(org.bukkit.Location.class, new LocationResolver());

        // Adventure API Types
        resolverRegistry.register(net.kyori.adventure.text.Component.class, new ComponentResolver());
        resolverRegistry.register(net.kyori.adventure.text.format.TextColor.class, new TextColorResolver());

        // Utility Types
        resolverRegistry.register(java.time.Duration.class, new DurationResolver());
    }

    /**
     * Registers Paper Lifecycle event handler.
     */
    private void registerLifecycleHandler() {
        plugin.getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> {
                    initialized = true;

                    // Register all pending commands
                    for (Object command : pendingCommands) {
                        commandRegistry.registerCommand(command, event.registrar());
                    }
                    pendingCommands.clear();
                });
    }

    /**
     * Registers a command instance.
     *
     * <p>
     * The command class must be annotated with {@code @Command}.
     * </p>
     *
     * @param commandInstance Command class instance
     * @return this (chainable)
     */
    public GloomCommand registerCommand(Object commandInstance) {
        // Inject dependencies
        injector.injectDependencies(commandInstance);

        if (initialized) {
            // Already initialized, register directly (via re-triggering event or direct
            // registration if supported,
            // here we attach to lifecycle again which is safe in Paper)
            plugin.getLifecycleManager().registerEventHandler(
                    LifecycleEvents.COMMANDS,
                    event -> commandRegistry.registerCommand(commandInstance, event.registrar()));
        } else {
            // Not initialized, add to pending list
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
     * Gets the argument resolver registry.
     *
     * @return resolver registry
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
     * Creates a Builder.
     *
     * @param plugin Paper plugin instance
     * @return Builder
     */
    public static Builder builder(JavaPlugin plugin) {
        return new Builder(plugin);
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

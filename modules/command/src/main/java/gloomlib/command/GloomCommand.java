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
 * GloomCommand 框架主入口类。
 *
 * <p>
 * 基于 Paper API 和 Adventure API 的现代化命令框架。
 * </p>
 *
 * <h2>快速开始</h2>
 * 
 * <pre>{@code
 * public class MyPlugin extends JavaPlugin {
 *     @Override
 *     public void onEnable() {
 *         GloomCommand glooom = GloomCommand.builder(this)
 *                 .build();
 *
 *         gloom.registerService(MyService.class, new MyService());
 *         gloom.registerCommand(new GameModeCommand());
 *     }
 * }
 * }</pre>
 *
 * <h2>命令类示例</h2>
 * 
 * <pre>
 * {
 *     &#64;code
 *     &#64;Command("gamemode")
 *     &#64;Permission("server.gamemode")
 *     &#64;Description("更改游戏模式")
 *     public class GameModeCommand {
 *
 *         &#64;Usage
 *         @PlayerOnly
 *         public void setMode(Player player, @Arg GameMode mode) {
 *             player.setGameMode(mode);
 *             player.sendMessage(Component.text("已切换到 " + mode.name()));
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

        // 注册默认处理器
        pipeline.registerPreProcessor(new LoggingProcessor(plugin));

        this.commandRegistry = new CommandRegistry(plugin, resolverRegistry, pipeline);

        // 注册内置解析器
        initializeBuiltInResolvers();

        // 注册 Paper Lifecycle 事件处理器
        registerLifecycleHandler();
    }

    /**
     * 初始化内置参数解析器。
     */
    private void initializeBuiltInResolvers() {
        // 基础类型
        resolverRegistry.register(String.class, new StringResolver());
        resolverRegistry.register(Integer.class, new IntegerResolver());
        resolverRegistry.register(Long.class, new LongResolver());
        resolverRegistry.register(Float.class, new FloatResolver());
        resolverRegistry.register(Double.class, new DoubleResolver());
        resolverRegistry.register(Boolean.class, new BooleanResolver());

        // Paper API 类型
        resolverRegistry.register(Player.class, new PlayerResolver());
        resolverRegistry.register(org.bukkit.OfflinePlayer.class, new OfflinePlayerResolver());
        resolverRegistry.register(World.class, new WorldResolver());
        resolverRegistry.register(GameMode.class, new GameModeResolver());
        resolverRegistry.register(org.bukkit.Material.class, new MaterialResolver());
        resolverRegistry.register(org.bukkit.Location.class, new LocationResolver());

        // Adventure API 类型
        resolverRegistry.register(net.kyori.adventure.text.Component.class, new ComponentResolver());
        resolverRegistry.register(net.kyori.adventure.text.format.TextColor.class, new TextColorResolver());

        // 实用类型
        resolverRegistry.register(java.time.Duration.class, new DurationResolver());
    }

    /**
     * 注册 Paper Lifecycle 事件处理器。
     */
    private void registerLifecycleHandler() {
        plugin.getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> {
                    initialized = true;

                    // 注册所有待处理的命令
                    for (Object command : pendingCommands) {
                        commandRegistry.registerCommand(command, event.registrar());
                    }
                    pendingCommands.clear();
                });
    }

    /**
     * 注册命令实例。
     *
     * <p>
     * 命令类必须使用 {@code @Command} 注解。
     * </p>
     *
     * @param commandInstance 命令类实例
     * @return this（链式调用）
     */
    public GloomCommand registerCommand(Object commandInstance) {
        // 注入依赖
        injector.injectDependencies(commandInstance);

        if (initialized) {
            // 已初始化，直接注册（通过再次触发事件）
            plugin.getLifecycleManager().registerEventHandler(
                    LifecycleEvents.COMMANDS,
                    event -> commandRegistry.registerCommand(commandInstance, event.registrar()));
        } else {
            // 未初始化，添加到待处理列表
            pendingCommands.add(commandInstance);
        }

        return this;
    }

    /**
     * 注册服务（用于依赖注入）。
     *
     * @param type     服务类型
     * @param instance 服务实例
     * @param <T>      类型
     * @return this（链式调用）
     */
    public <T> GloomCommand registerService(Class<T> type, T instance) {
        injector.registerSingleton(type, instance);
        return this;
    }

    /**
     * 注册带限定符的服务。
     *
     * @param qualifier 限定符
     * @param instance  服务实例
     * @param <T>       类型
     * @return this（链式调用）
     */
    public <T> GloomCommand registerService(String qualifier, T instance) {
        injector.registerBean(qualifier, instance);
        return this;
    }

    /**
     * 注册自定义参数解析器。
     *
     * @param type     参数类型
     * @param resolver 解析器
     * @param <T>      类型
     * @return this（链式调用）
     */
    public <T> GloomCommand registerArgumentResolver(Class<T> type, ArgumentResolver<T> resolver) {
        resolverRegistry.register(type, resolver);
        return this;
    }

    /**
     * 获取依赖注入器。
     *
     * @return 依赖注入器
     */
    public DependencyInjector getInjector() {
        return injector;
    }

    /**
     * 获取参数解析器注册表。
     *
     * @return 解析器注册表
     */
    public ArgumentResolverRegistry getResolverRegistry() {
        return resolverRegistry;
    }

    /**
     * 获取插件实例。
     *
     * @return 插件实例
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * 创建 Builder。
     *
     * @param plugin Paper 插件实例
     * @return Builder
     */
    public static Builder builder(JavaPlugin plugin) {
        return new Builder(plugin);
    }

    /**
     * GloomCommand Builder。
     */
    public static final class Builder {

        private final JavaPlugin plugin;

        private Builder(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        /**
         * 构建 GloomCommand 实例。
         *
         * @return GloomCommand 实例
         */
        public GloomCommand build() {
            return new GloomCommand(this);
        }
    }
}

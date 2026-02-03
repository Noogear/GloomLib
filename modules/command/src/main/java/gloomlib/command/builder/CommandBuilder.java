package gloomlib.command.builder;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 命令构建器（流式 API）。
 *
 * <p>
 * 用于以编程方式创建命令，无需使用注解。
 * </p>
 *
 * <h2>用法示例</h2>
 * 
 * <pre>{@code
 * CommandBuilder.create("hello")
 *         .description("打招呼命令")
 *         .permission("server.hello")
 *         .executes(ctx -> {
 *             ctx.getSender().sendMessage(Component.text("Hello!"));
 *         })
 *         .subCommand("world")
 *         .executes(ctx -> {
 *             ctx.getSender().sendMessage(Component.text("Hello, World!"));
 *         })
 *         .end()
 *         .build();
 * }</pre>
 */
public class CommandBuilder {

    private final String name;
    private String description = "";
    private final List<String> aliases = new ArrayList<>();
    private String permission = null;
    private Predicate<CommandSender> requirement = sender -> true;
    private Consumer<GloomCommandContext> executor = null;
    private final List<ArgumentBuilder<?>> arguments = new ArrayList<>();
    private final List<SubCommandBuilder> subCommands = new ArrayList<>();

    private CommandBuilder(String name) {
        this.name = name;
    }

    /**
     * 创建命令构建器。
     *
     * @param name 命令名
     * @return 构建器
     */
    public static CommandBuilder create(String name) {
        return new CommandBuilder(name);
    }

    /**
     * 设置描述。
     *
     * @param description 命令描述
     * @return this
     */
    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * 添加别名。
     *
     * @param aliases 别名
     * @return this
     */
    public CommandBuilder aliases(String... aliases) {
        this.aliases.addAll(Arrays.asList(aliases));
        return this;
    }

    /**
     * 设置权限。
     *
     * @param permission 权限节点
     * @return this
     */
    public CommandBuilder permission(String permission) {
        this.permission = permission;
        return this;
    }

    /**
     * 设置自定义执行条件。
     *
     * @param requirement 条件谓词
     * @return this
     */
    public CommandBuilder requires(Predicate<CommandSender> requirement) {
        this.requirement = requirement;
        return this;
    }

    /**
     * 限制仅玩家可用。
     *
     * @return this
     */
    public CommandBuilder playerOnly() {
        this.requirement = sender -> sender instanceof Player;
        return this;
    }

    /**
     * 限制仅 OP 可用。
     *
     * @return this
     */
    public CommandBuilder opOnly() {
        this.requirement = CommandSender::isOp;
        return this;
    }

    /**
     * 设置执行器。
     *
     * @param executor 执行器
     * @return this
     */
    public CommandBuilder executes(Consumer<GloomCommandContext> executor) {
        this.executor = executor;
        return this;
    }

    /**
     * 添加必需参数。
     *
     * @param name         参数名
     * @param argumentType Brigadier 参数类型
     * @param <T>          参数类型
     * @return 参数构建器
     */
    public <T> ArgumentBuilder<T> argument(String name, ArgumentType<T> argumentType) {
        ArgumentBuilder<T> argBuilder = new ArgumentBuilder<>(this, name, argumentType);
        arguments.add(argBuilder);
        return argBuilder;
    }

    /**
     * 添加子命令。
     *
     * @param name 子命令名
     * @return 子命令构建器
     */
    public SubCommandBuilder subCommand(String name) {
        SubCommandBuilder subBuilder = new SubCommandBuilder(this, name);
        subCommands.add(subBuilder);
        return subBuilder;
    }

    /**
     * 构建 Brigadier 命令节点。
     *
     * @return Brigadier 命令节点
     */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(name);

        // 设置权限/条件
        if (permission != null) {
            final String perm = permission;
            builder.requires(source -> source.getSender().hasPermission(perm) && requirement.test(source.getSender()));
        } else {
            builder.requires(source -> requirement.test(source.getSender()));
        }

        // 设置执行器
        if (executor != null) {
            builder.executes(ctx -> {
                executor.accept(new GloomCommandContext(ctx));
                return Command.SINGLE_SUCCESS;
            });
        }

        // 添加参数
        if (!arguments.isEmpty()) {
            buildArgumentChain(builder, 0);
        }

        // 添加子命令
        for (SubCommandBuilder subCommand : subCommands) {
            builder.then(subCommand.build());
        }

        return builder;
    }

    /**
     * 构建并注册到 Paper。
     *
     * @param commands Paper Commands 注册器
     */
    public void register(Commands commands) {
        commands.register(build().build(), description, aliases);
    }

    /**
     * 递归构建参数链。
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void buildArgumentChain(
            com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> parent,
            int index) {
        if (index >= arguments.size()) {
            return;
        }

        ArgumentBuilder<?> arg = arguments.get(index);
        RequiredArgumentBuilder<CommandSourceStack, ?> argNode = arg.buildNode();

        // 如果是最后一个参数，添加执行器
        if (index == arguments.size() - 1 && arg.executor != null) {
            argNode.executes(ctx -> {
                arg.executor.accept(new GloomCommandContext(ctx));
                return Command.SINGLE_SUCCESS;
            });
        }

        // 递归添加下一个参数
        if (index < arguments.size() - 1) {
            buildArgumentChain(argNode, index + 1);
        }

        parent.then(argNode);
    }

    /**
     * 获取命令名。
     */
    public String getName() {
        return name;
    }

    /**
     * 获取描述。
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取别名。
     */
    public List<String> getAliases() {
        return aliases;
    }

    /**
     * 参数构建器。
     *
     * @param <T> 参数类型
     */
    public static class ArgumentBuilder<T> {

        private final CommandBuilder parent;
        private final String name;
        private final ArgumentType<T> argumentType;
        private SuggestionProvider suggestionProvider = null;
        private Consumer<GloomCommandContext> executor = null;

        ArgumentBuilder(CommandBuilder parent, String name, ArgumentType<T> argumentType) {
            this.parent = parent;
            this.name = name;
            this.argumentType = argumentType;
        }

        /**
         * 设置建议提供器。
         *
         * @param provider 建议提供器
         * @return this
         */
        public ArgumentBuilder<T> suggests(SuggestionProvider provider) {
            this.suggestionProvider = provider;
            return this;
        }

        /**
         * 设置执行器。
         *
         * @param executor 执行器
         * @return this
         */
        public ArgumentBuilder<T> executes(Consumer<GloomCommandContext> executor) {
            this.executor = executor;
            return this;
        }

        /**
         * 添加下一个参数。
         *
         * @param name         参数名
         * @param argumentType Brigadier 参数类型
         * @param <U>          参数类型
         * @return 参数构建器
         */
        public <U> ArgumentBuilder<U> argument(String name, ArgumentType<U> argumentType) {
            return parent.argument(name, argumentType);
        }

        /**
         * 返回父构建器。
         *
         * @return 父构建器
         */
        public CommandBuilder end() {
            return parent;
        }

        /**
         * 构建 Brigadier 参数节点。
         */
        RequiredArgumentBuilder<CommandSourceStack, T> buildNode() {
            RequiredArgumentBuilder<CommandSourceStack, T> node = Commands.argument(name, argumentType);

            if (suggestionProvider != null) {
                node.suggests((ctx, builder) -> suggestionProvider.suggest(ctx, builder));
            }

            return node;
        }
    }

    /**
     * 子命令构建器。
     */
    public static class SubCommandBuilder {

        private final CommandBuilder parent;
        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private String permission = null;
        private Predicate<CommandSender> requirement = sender -> true;
        private Consumer<GloomCommandContext> executor = null;
        private final List<ArgumentBuilder<?>> arguments = new ArrayList<>();
        private final List<SubCommandBuilder> subCommands = new ArrayList<>();

        SubCommandBuilder(CommandBuilder parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        /**
         * 添加别名。
         */
        public SubCommandBuilder aliases(String... aliases) {
            this.aliases.addAll(Arrays.asList(aliases));
            return this;
        }

        /**
         * 设置权限。
         */
        public SubCommandBuilder permission(String permission) {
            this.permission = permission;
            return this;
        }

        /**
         * 设置执行条件。
         */
        public SubCommandBuilder requires(Predicate<CommandSender> requirement) {
            this.requirement = requirement;
            return this;
        }

        /**
         * 限制仅玩家可用。
         */
        public SubCommandBuilder playerOnly() {
            this.requirement = sender -> sender instanceof Player;
            return this;
        }

        /**
         * 设置执行器。
         */
        public SubCommandBuilder executes(Consumer<GloomCommandContext> executor) {
            this.executor = executor;
            return this;
        }

        /**
         * 添加参数。
         */
        public <T> ArgumentBuilder<T> argument(String name, ArgumentType<T> argumentType) {
            ArgumentBuilder<T> argBuilder = new ArgumentBuilder<>(parent, name, argumentType);
            arguments.add(argBuilder);
            return argBuilder;
        }

        /**
         * 添加子命令。
         */
        public SubCommandBuilder subCommand(String name) {
            SubCommandBuilder subBuilder = new SubCommandBuilder(parent, name);
            subCommands.add(subBuilder);
            return subBuilder;
        }

        /**
         * 返回父构建器。
         */
        public CommandBuilder end() {
            return parent;
        }

        /**
         * 构建 Brigadier 子命令节点。
         */
        LiteralArgumentBuilder<CommandSourceStack> build() {
            LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(name);

            // 设置权限/条件
            if (permission != null) {
                final String perm = permission;
                builder.requires(
                        source -> source.getSender().hasPermission(perm) && requirement.test(source.getSender()));
            } else {
                builder.requires(source -> requirement.test(source.getSender()));
            }

            // 设置执行器
            if (executor != null) {
                builder.executes(ctx -> {
                    executor.accept(new GloomCommandContext(ctx));
                    return Command.SINGLE_SUCCESS;
                });
            }

            // 添加子命令
            for (SubCommandBuilder subCommand : subCommands) {
                builder.then(subCommand.build());
            }

            return builder;
        }
    }
}

package gloomlib.command.core;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import gloomlib.command.context.GloomCommandContext;
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
 * Command Builder (Fluent API).
 *
 * <p>
 * Allows programmatic creation of commands without using annotations.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * CommandBuilder.create("hello")
 *         .description("Hello command")
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
    private final List<String> aliases = new ArrayList<>();
    private final List<ArgumentBuilder<?>> arguments = new ArrayList<>();
    private final List<SubCommandBuilder> subCommands = new ArrayList<>();
    private String description = "";
    private String permission = null;
    private Predicate<CommandSender> requirement = sender -> true;
    private Consumer<GloomCommandContext> executor = null;

    private CommandBuilder(String name) {
        this.name = name;
    }

    /**
     * Creates a command builder.
     *
     * @param name Command name
     * @return Builder
     */
    public static CommandBuilder create(String name) {
        return new CommandBuilder(name);
    }

    /**
     * Sets the description.
     *
     * @param description Command description
     * @return this
     */
    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Adds aliases.
     *
     * @param aliases Command aliases
     * @return this
     */
    public CommandBuilder aliases(String... aliases) {
        this.aliases.addAll(Arrays.asList(aliases));
        return this;
    }

    /**
     * Sets the permission.
     *
     * @param permission Permission node
     * @return this
     */
    public CommandBuilder permission(String permission) {
        this.permission = permission;
        return this;
    }

    /**
     * Sets custom execution requirement.
     *
     * @param requirement Requirement predicate
     * @return this
     */
    public CommandBuilder requires(Predicate<CommandSender> requirement) {
        this.requirement = requirement;
        return this;
    }

    /**
     * Restricts to players only.
     *
     * @return this
     */
    public CommandBuilder playerOnly() {
        this.requirement = sender -> sender instanceof Player;
        return this;
    }

    /**
     * Restricts to OPs only.
     *
     * @return this
     */
    public CommandBuilder opOnly() {
        this.requirement = CommandSender::isOp;
        return this;
    }

    /**
     * Sets execution logic.
     *
     * @param executor Command executor
     * @return this
     */
    public CommandBuilder executes(Consumer<GloomCommandContext> executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Adds a required argument.
     *
     * @param name         Argument name
     * @param argumentType Brigadier argument type
     * @param <T>          Argument type
     * @return Argument builder
     */
    public <T> ArgumentBuilder<T> argument(String name, ArgumentType<T> argumentType) {
        ArgumentBuilder<T> argBuilder = new ArgumentBuilder<>(this, name, argumentType);
        arguments.add(argBuilder);
        return argBuilder;
    }

    /**
     * Adds a subcommand.
     *
     * @param name Subcommand name
     * @return Subcommand builder
     */
    public SubCommandBuilder subCommand(String name) {
        SubCommandBuilder subBuilder = new SubCommandBuilder(this, name);
        subCommands.add(subBuilder);
        return subBuilder;
    }

    /**
     * Builds Brigadier command node.
     *
     * @return Brigadier command node
     */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(name);

        // Set permission/requirement
        if (permission != null) {
            final String perm = permission;
            builder.requires(source -> source.getSender().hasPermission(perm) && requirement.test(source.getSender()));
        } else {
            builder.requires(source -> requirement.test(source.getSender()));
        }

        // Set executor
        if (executor != null) {
            builder.executes(ctx -> {
                executor.accept(new GloomCommandContext(ctx));
                return Command.SINGLE_SUCCESS;
            });
        }

        // Add arguments
        if (!arguments.isEmpty()) {
            buildArgumentChain(builder, 0);
        }

        // Add subcommands
        for (SubCommandBuilder subCommand : subCommands) {
            builder.then(subCommand.build());
        }

        return builder;
    }

    /**
     * Builds and registers to Paper.
     *
     * @param commands Paper Commands registrar
     */
    public void register(Commands commands) {
        commands.register(build().build(), description, aliases);
    }

    /**
     * Recursively builds argument chain.
     */
    private void buildArgumentChain(
            com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> parent,
            int index) {
        if (index >= arguments.size()) {
            return;
        }

        ArgumentBuilder<?> arg = arguments.get(index);
        RequiredArgumentBuilder<CommandSourceStack, ?> argNode = arg.buildNode();

        // If it's the last argument, add executor
        if (index == arguments.size() - 1 && arg.executor != null) {
            argNode.executes(ctx -> {
                arg.executor.accept(new GloomCommandContext(ctx));
                return Command.SINGLE_SUCCESS;
            });
        }

        // Recursively add next argument
        if (index < arguments.size() - 1) {
            buildArgumentChain(argNode, index + 1);
        }

        parent.then(argNode);
    }

    /**
     * Gets command name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets aliases.
     */
    public List<String> getAliases() {
        return aliases;
    }

    /**
     * Argument Builder.
     *
     * @param <T> Argument type
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
         * Sets suggestion provider.
         *
         * @param provider Suggestion provider
         * @return this
         */
        public ArgumentBuilder<T> suggests(SuggestionProvider provider) {
            this.suggestionProvider = provider;
            return this;
        }

        /**
         * Sets executor.
         *
         * @param executor Executor
         * @return this
         */
        public ArgumentBuilder<T> executes(Consumer<GloomCommandContext> executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Adds next argument.
         *
         * @param name         Argument name
         * @param argumentType Brigadier argument type
         * @param <U>          Argument type
         * @return Argument builder
         */
        public <U> ArgumentBuilder<U> argument(String name, ArgumentType<U> argumentType) {
            return parent.argument(name, argumentType);
        }

        /**
         * Returns parent builder.
         *
         * @return Parent builder
         */
        public CommandBuilder end() {
            return parent;
        }

        /**
         * Builds Brigadier argument node.
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
     * Subcommand Builder.
     */
    public static class SubCommandBuilder {

        private final CommandBuilder parent;
        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private final List<ArgumentBuilder<?>> arguments = new ArrayList<>();
        private final List<SubCommandBuilder> subCommands = new ArrayList<>();
        private String permission = null;
        private Predicate<CommandSender> requirement = sender -> true;
        private Consumer<GloomCommandContext> executor = null;

        SubCommandBuilder(CommandBuilder parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        /**
         * Adds aliases.
         */
        public SubCommandBuilder aliases(String... aliases) {
            this.aliases.addAll(Arrays.asList(aliases));
            return this;
        }

        /**
         * Sets permission.
         */
        public SubCommandBuilder permission(String permission) {
            this.permission = permission;
            return this;
        }

        /**
         * Sets execution requirement.
         */
        public SubCommandBuilder requires(Predicate<CommandSender> requirement) {
            this.requirement = requirement;
            return this;
        }

        /**
         * Restricts to players only.
         */
        public SubCommandBuilder playerOnly() {
            this.requirement = sender -> sender instanceof Player;
            return this;
        }

        /**
         * Sets executor.
         */
        public SubCommandBuilder executes(Consumer<GloomCommandContext> executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Adds argument.
         */
        public <T> ArgumentBuilder<T> argument(String name, ArgumentType<T> argumentType) {
            ArgumentBuilder<T> argBuilder = new ArgumentBuilder<>(parent, name, argumentType);
            arguments.add(argBuilder);
            return argBuilder;
        }

        /**
         * Adds subcommand.
         */
        public SubCommandBuilder subCommand(String name) {
            SubCommandBuilder subBuilder = new SubCommandBuilder(parent, name);
            subCommands.add(subBuilder);
            return subBuilder;
        }

        /**
         * Returns parent builder.
         */
        public CommandBuilder end() {
            return parent;
        }

        /**
         * Builds Brigadier subcommand node.
         */
        LiteralArgumentBuilder<CommandSourceStack> build() {
            LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(name);

            // Set permission/requirement
            if (permission != null) {
                final String perm = permission;
                builder.requires(
                        source -> source.getSender().hasPermission(perm) && requirement.test(source.getSender()));
            } else {
                builder.requires(source -> requirement.test(source.getSender()));
            }

            // Set executor
            if (executor != null) {
                builder.executes(ctx -> {
                    executor.accept(new GloomCommandContext(ctx));
                    return Command.SINGLE_SUCCESS;
                });
            }

            // Add subcommands
            for (SubCommandBuilder subCommand : subCommands) {
                builder.then(subCommand.build());
            }

            return builder;
        }
    }
}

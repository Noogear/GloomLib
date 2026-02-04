package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.exception.CommandException;
import gloomlib.command.message.CommandMessages;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;

import java.lang.reflect.Parameter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家参数解析器（使用 Paper API）。
 *
 * <p>
 * 使用 Paper 提供的 {@link ArgumentTypes#player()} 参数类型，
 * 支持 Minecraft 原生的玩家选择器语法（如 {@code @p}, {@code @a}）。
 * </p>
 */
public class PlayerResolver implements ArgumentResolver<Player> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // 使用 Paper 提供的玩家参数类型
        return ArgumentTypes.player();
    }

    @Override
    public Player resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        try {
            PlayerSelectorArgumentResolver selector = context.getArgument(name, PlayerSelectorArgumentResolver.class);
            List<Player> players = selector.resolve(context.getSource());

            if (players.isEmpty()) {
                throw new CommandException(CommandMessages.PLAYER_NOT_FOUND.get());
            }

            return players.get(0);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new CommandException(CommandMessages.SELECTOR_UNKNOWN.get());
        }
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        // Paper API 会自动处理玩家补全
        return Suggestions.empty();
    }

    @Override
    public Class<Player> getType() {
        return Player.class;
    }
}

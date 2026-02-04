package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.exception.CommandException;
import gloomlib.command.message.CommandMessages;
import gloomlib.command.exception.CommandException;
import gloomlib.command.message.CommandMessages;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * 游戏模式参数解析器。
 *
 * <p>
 * 支持完整名称和缩写（如 {@code creative}, {@code c}, {@code 1}）。
 * </p>
 */
public class GameModeResolver implements ArgumentResolver<GameMode> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        return StringArgumentType.word();
    }

    @Override
    public GameMode resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        String input = context.getArgument(name, String.class).toLowerCase();

        return switch (input) {
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            case "spectator", "sp", "3" -> GameMode.SPECTATOR;
            default -> {
                // 尝试精确匹配
                for (GameMode mode : GameMode.values()) {
                    if (mode.name().equalsIgnoreCase(input)) {
                        yield mode;
                    }
                }
                throw new CommandException(CommandMessages.ARG_GAMEMODE_INVALID.get(Component.text(input)));
            }
        };
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining().toLowerCase();

        for (GameMode mode : GameMode.values()) {
            String name = mode.name().toLowerCase();
            if (name.startsWith(remaining)) {
                builder.suggest(name); // 使用简单字符串，不使用工具提示
            }
        }

        // 添加缩写建议
        if ("s".startsWith(remaining))
            builder.suggest("s");
        if ("c".startsWith(remaining))
            builder.suggest("c");
        if ("a".startsWith(remaining))
            builder.suggest("a");
        if ("sp".startsWith(remaining))
            builder.suggest("sp");

        return builder.buildFuture();
    }

    @Override
    public Class<GameMode> getType() {
        return GameMode.class;
    }
}

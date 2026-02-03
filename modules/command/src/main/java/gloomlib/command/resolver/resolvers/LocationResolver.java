package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.exception.CommandException;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * 位置参数解析器（使用 Paper API）。
 *
 * <p>
 * 使用 Paper 提供的 {@link ArgumentTypes#finePosition()} 参数类型，
 * 支持 Minecraft 原生的坐标语法（如 {@code ~ ~ ~}, {@code ^1 ^0 ^1}）。
 * </p>
 *
 * <h2>支持的格式</h2>
 * <ul>
 * <li>{@code 100 64 200} — 绝对坐标</li>
 * <li>{@code ~ ~ ~} — 相对坐标（相对于执行者位置）</li>
 * <li>{@code ~10 ~5 ~-10} — 带偏移的相对坐标</li>
 * <li>{@code ^ ^ ^5} — 局部坐标（相对于执行者朝向）</li>
 * </ul>
 */
public class LocationResolver implements ArgumentResolver<Location> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // 使用 Paper 提供的精确位置参数类型
        return ArgumentTypes.finePosition();
    }

    @Override
    public Location resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        try {
            FinePositionResolver resolver = context.getArgument(name, FinePositionResolver.class);
            return resolver.resolve(context.getSource()).toLocation(context.getSource().getLocation().getWorld());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new CommandException(
                    Component.text("无效的坐标格式！").color(NamedTextColor.RED));
        }
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining();

        // 提供常用坐标建议
        if (remaining.isEmpty()) {
            builder.suggest("~ ~ ~");
            builder.suggest("^ ^ ^");
        } else if (remaining.startsWith("~") || remaining.startsWith("^")) {
            // 已经输入了相对坐标标记，不提供额外建议
        } else {
            // 提供相对坐标建议
            builder.suggest("~ ~ ~");
        }

        return builder.buildFuture();
    }

    @Override
    public Class<Location> getType() {
        return Location.class;
    }
}

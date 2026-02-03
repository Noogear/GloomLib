package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * 世界参数解析器（使用 Paper API）。
 *
 * <p>
 * 使用 Paper 提供的 {@link ArgumentTypes#world()} 参数类型。
 * </p>
 */
public class WorldResolver implements ArgumentResolver<World> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // 使用 Paper 提供的世界参数类型
        return ArgumentTypes.world();
    }

    @Override
    public World resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        return context.getArgument(name, World.class);
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining().toLowerCase();

        for (World world : Bukkit.getWorlds()) {
            if (world.getName().toLowerCase().startsWith(remaining)) {
                builder.suggest(world.getName()); // 移除 Component 工具提示
            }
        }

        return builder.buildFuture();
    }

    @Override
    public Class<World> getType() {
        return World.class;
    }
}

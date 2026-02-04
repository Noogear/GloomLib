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
 * World Argument Resolver (using Paper API).
 *
 * <p>
 * Uses Paper provided {@link ArgumentTypes#world()} argument type.
 * </p>
 */
public class WorldResolver implements ArgumentResolver<World> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // Use Paper provided world argument type
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
                builder.suggest(world.getName()); // Remove Component tooltip
            }
        }

        return builder.buildFuture();
    }

    @Override
    public Class<World> getType() {
        return World.class;
    }
}

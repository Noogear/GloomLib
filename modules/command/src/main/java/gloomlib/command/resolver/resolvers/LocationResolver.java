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
import gloomlib.command.message.CommandMessages;
import org.bukkit.Location;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * Location Argument Resolver (using Paper API).
 *
 * <p>
 * Uses Paper provided {@link ArgumentTypes#finePosition()} argument type,
 * supporting Minecraft native coordinate syntax (e.g. {@code ~ ~ ~},
 * {@code ^1 ^0 ^1}).
 * </p>
 *
 * <h2>Supported Formats</h2>
 * <ul>
 * <li>{@code 100 64 200} — Absolute coordinates</li>
 * <li>{@code ~ ~ ~} — Relative coordinates (relative to sender's position)</li>
 * <li>{@code ~10 ~5 ~-10} — Relative coordinates with offset</li>
 * <li>{@code ^ ^ ^5} — Local coordinates (relative to sender's facing
 * direction)</li>
 * </ul>
 */
public class LocationResolver implements ArgumentResolver<Location> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // Use Paper provided fine position argument type
        return ArgumentTypes.finePosition();
    }

    @Override
    public Location resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        try {
            FinePositionResolver resolver = context.getArgument(name, FinePositionResolver.class);
            return resolver.resolve(context.getSource()).toLocation(context.getSource().getLocation().getWorld());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new CommandException(CommandMessages.POS_MISSING.get());
        }
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining();

        // Provide common coordinate suggestions
        if (remaining.isEmpty()) {
            builder.suggest("~ ~ ~");
            builder.suggest("^ ^ ^");
        } else if (remaining.startsWith("~") || remaining.startsWith("^")) {
            // Already entered relative coordinate marker, no extra suggestions
        } else {
            // Provide relative coordinate suggestions
            builder.suggest("~ ~ ~");
        }

        return builder.buildFuture();
    }

    @Override
    public Class<Location> getType() {
        return Location.class;
    }
}

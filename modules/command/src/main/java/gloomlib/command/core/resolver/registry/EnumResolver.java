package gloomlib.command.core.resolver.registry;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.api.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * Enum argument resolver that reads a word argument as a {@link String} and
 * converts it to the target enum constant via {@link Enum#valueOf}.
 *
 * <p>Auto-completion suggestions are provided from the enum's constants.</p>
 *
 * @param <E> The enum type
 */
public final class EnumResolver<E extends Enum<E>> implements ArgumentResolver<E> {

    private final Class<E> enumClass;

    private EnumResolver(@NotNull Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    /**
     * Creates an {@link EnumResolver} for the given enum class.
     *
     * @param enumClass the enum class
     * @param <E>       the enum type
     * @return a new resolver instance
     */
    public static <E extends Enum<E>> EnumResolver<E> of(@NotNull Class<E> enumClass) {
        return new EnumResolver<>(enumClass);
    }

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        return StringArgumentType.word();
    }

    @Override
    public E resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter)
            throws CommandSyntaxException {
        String value = context.getArgument(name, String.class);
        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        StringBuilder valid = new StringBuilder();
        for (E constant : enumClass.getEnumConstants()) {
            if (!valid.isEmpty()) valid.append(", ");
            valid.append(constant.name());
        }
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                .literalIncorrect()
                .create(value + " (expected one of: " + valid + ")");
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining().toLowerCase();
        for (E constant : enumClass.getEnumConstants()) {
            String name = constant.name();
            if (name.toLowerCase().startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Class<E> getType() {
        return enumClass;
    }
}

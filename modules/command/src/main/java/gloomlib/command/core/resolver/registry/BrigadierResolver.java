package gloomlib.command.core.resolver.registry;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.api.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Parameter;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Brigadier-based argument resolver.
 *
 * <p>Wraps Paper's Brigadier ArgumentTypes for automatic parameter resolution.
 * Use factory methods to create instances:
 * <ul>
 * <li>{@link #of(Class, Supplier)} - Fixed ArgumentType</li>
 * <li>{@link #of(Class, Function)} - Per-parameter ArgumentType</li>
 * </ul>
 *
 * @param <T> The Java type this resolver handles
 * @see io.papermc.paper.command.brigadier.argument.ArgumentTypes
 */
public final class BrigadierResolver<T> implements ArgumentResolver<T> {

    private final Class<T> type;
    private final Function<Parameter, ArgumentType<?>> argumentTypeFactory;

    private BrigadierResolver(
            @NotNull Class<T> type,
            @NotNull Function<Parameter, ArgumentType<?>> argumentTypeFactory) {
        this.type = type;
        this.argumentTypeFactory = argumentTypeFactory;
    }

    public static <T> BrigadierResolver<T> of(
            @NotNull Class<T> type,
            @NotNull Supplier<ArgumentType<?>> factory) {
        return new BrigadierResolver<>(type, param -> factory.get());
    }

    public static <T> BrigadierResolver<T> of(
            @NotNull Class<T> type,
            @NotNull Function<Parameter, ArgumentType<?>> factory) {
        return new BrigadierResolver<>(type, factory);
    }

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        return argumentTypeFactory.apply(parameter);
    }

    @Override
    public T resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        return context.getArgument(name, type);
    }

    @Override
    public Class<T> getType() {
        return type;
    }
}

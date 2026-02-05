package gloomlib.command.resolver.registry;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.annotation.Range;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Parameter;

/**
 * Numeric argument resolver helpers.
 */
public final class NumericResolvers {

    private NumericResolvers() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void registerByteShort(@NotNull ArgumentResolverRegistry registry) {
        registerShort(registry);
        registerByte(registry);
    }

    public static IntegerArgumentType intArgument(Parameter parameter) {
        Range range = parameter.getAnnotation(Range.class);
        if (range != null) {
            return IntegerArgumentType.integer((int) range.min(), (int) range.max());
        }
        return IntegerArgumentType.integer();
    }

    public static IntegerArgumentType intArgument(Parameter parameter, int min, int max) {
        Range range = parameter.getAnnotation(Range.class);
        if (range != null) {
            return IntegerArgumentType.integer((int) range.min(), (int) range.max());
        }
        return IntegerArgumentType.integer(min, max);
    }

    public static LongArgumentType longArgument(Parameter parameter) {
        Range range = parameter.getAnnotation(Range.class);
        if (range != null) {
            return LongArgumentType.longArg((long) range.min(), (long) range.max());
        }
        return LongArgumentType.longArg();
    }

    public static FloatArgumentType floatArgument(Parameter parameter) {
        Range range = parameter.getAnnotation(Range.class);
        if (range != null) {
            return FloatArgumentType.floatArg((float) range.min(), (float) range.max());
        }
        return FloatArgumentType.floatArg();
    }

    public static DoubleArgumentType doubleArgument(Parameter parameter) {
        Range range = parameter.getAnnotation(Range.class);
        if (range != null) {
            return DoubleArgumentType.doubleArg(range.min(), range.max());
        }
        return DoubleArgumentType.doubleArg();
    }

    private static void registerShort(@NotNull ArgumentResolverRegistry registry) {
        registry.register(Short.class, new ArgumentResolver<Short>() {
            @Override
            public ArgumentType<?> createArgumentType(Parameter parameter) {
                return intArgument(parameter, Short.MIN_VALUE, Short.MAX_VALUE);
            }

            @Override
            public Short resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
                return context.getArgument(name, Integer.class).shortValue();
            }

            @Override
            public Class<Short> getType() {
                return Short.class;
            }
        });
    }

    private static void registerByte(@NotNull ArgumentResolverRegistry registry) {
        registry.register(Byte.class, new ArgumentResolver<Byte>() {
            @Override
            public ArgumentType<?> createArgumentType(Parameter parameter) {
                return intArgument(parameter, Byte.MIN_VALUE, Byte.MAX_VALUE);
            }

            @Override
            public Byte resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
                return context.getArgument(name, Integer.class).byteValue();
            }

            @Override
            public Class<Byte> getType() {
                return Byte.class;
            }
        });
    }
}

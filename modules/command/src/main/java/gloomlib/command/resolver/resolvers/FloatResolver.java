package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.annotation.Range;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;

/**
 * 浮点数参数解析器。
 *
 * <p>
 * 支持 {@code @Range} 注解指定范围约束。
 * </p>
 */
public class FloatResolver implements ArgumentResolver<Float> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        Range range = parameter.getAnnotation(Range.class);

        if (range != null) {
            float min = (float) Math.max(range.min(), -Float.MAX_VALUE);
            float max = (float) Math.min(range.max(), Float.MAX_VALUE);
            return FloatArgumentType.floatArg(min, max);
        }

        return FloatArgumentType.floatArg();
    }

    @Override
    public Float resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        return context.getArgument(name, Float.class);
    }

    @Override
    public Class<Float> getType() {
        return Float.class;
    }
}

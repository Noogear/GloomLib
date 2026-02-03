package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.annotation.Range;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;

/**
 * 双精度浮点数参数解析器。
 *
 * <p>
 * 支持 {@code @Range} 注解指定范围约束。
 * </p>
 */
public class DoubleResolver implements ArgumentResolver<Double> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        Range range = parameter.getAnnotation(Range.class);

        if (range != null) {
            return DoubleArgumentType.doubleArg(range.min(), range.max());
        }

        return DoubleArgumentType.doubleArg();
    }

    @Override
    public Double resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        return context.getArgument(name, Double.class);
    }

    @Override
    public Class<Double> getType() {
        return Double.class;
    }
}

package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.annotation.Range;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;

/**
 * 整数参数解析器。
 *
 * <p>
 * 支持 {@code @Range} 注解指定范围约束。
 * </p>
 */
public class IntegerResolver implements ArgumentResolver<Integer> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        Range range = parameter.getAnnotation(Range.class);

        if (range != null) {
            int min = (int) Math.max(range.min(), Integer.MIN_VALUE);
            int max = (int) Math.min(range.max(), Integer.MAX_VALUE);
            return IntegerArgumentType.integer(min, max);
        }

        return IntegerArgumentType.integer();
    }

    @Override
    public Integer resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        return context.getArgument(name, Integer.class);
    }

    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }
}

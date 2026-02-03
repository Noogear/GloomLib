package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.annotation.Range;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;

/**
 * 长整型参数解析器。
 *
 * <p>
 * 支持 {@code @Range} 注解指定范围约束。
 * </p>
 */
public class LongResolver implements ArgumentResolver<Long> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        Range range = parameter.getAnnotation(Range.class);

        if (range != null) {
            long min = (long) Math.max(range.min(), Long.MIN_VALUE);
            long max = (long) Math.min(range.max(), Long.MAX_VALUE);
            return LongArgumentType.longArg(min, max);
        }

        return LongArgumentType.longArg();
    }

    @Override
    public Long resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        return context.getArgument(name, Long.class);
    }

    @Override
    public Class<Long> getType() {
        return Long.class;
    }
}

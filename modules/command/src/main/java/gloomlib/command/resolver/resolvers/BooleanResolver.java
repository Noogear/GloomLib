package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;

/**
 * 布尔参数解析器。
 */
public class BooleanResolver implements ArgumentResolver<Boolean> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        return BoolArgumentType.bool();
    }

    @Override
    public Boolean resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        return context.getArgument(name, Boolean.class);
    }

    @Override
    public Class<Boolean> getType() {
        return Boolean.class;
    }
}

package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gloomlib.command.annotation.Greedy;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;

/**
 * 字符串参数解析器。
 *
 * <p>
 * 支持三种模式：
 * </p>
 * <ul>
 * <li>单词模式（默认）：不含空格的单个词</li>
 * <li>引号模式：用引号包围的字符串</li>
 * <li>贪婪模式（{@code @Greedy}）：消耗所有剩余输入</li>
 * </ul>
 */
public class StringResolver implements ArgumentResolver<String> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // 检查是否为贪婪参数
        if (parameter.isAnnotationPresent(Greedy.class)) {
            return StringArgumentType.greedyString();
        }

        // 默认使用单词模式
        return StringArgumentType.word();
    }

    @Override
    public String resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        return context.getArgument(name, String.class);
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}

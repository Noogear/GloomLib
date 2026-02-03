package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * Adventure Component 参数解析器。
 *
 * <p>
 * 使用 Paper 提供的 {@link ArgumentTypes#component()} 参数类型，
 * 支持 JSON 格式和 MiniMessage 格式的文本组件。
 * </p>
 *
 * <h2>支持的格式</h2>
 * <ul>
 * <li>JSON 格式：{@code {"text":"Hello","color":"red"}}</li>
 * <li>简单文本：{@code Hello World}</li>
 * </ul>
 */
public class ComponentResolver implements ArgumentResolver<Component> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // 使用 Paper 提供的 Adventure Component 参数类型
        return ArgumentTypes.component();
    }

    @Override
    public Component resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        return context.getArgument(name, Component.class);
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining();

        // 提供 JSON 格式建议
        if (remaining.isEmpty()) {
            builder.suggest("{\"text\":\"\"}", null);
        }

        return builder.buildFuture();
    }

    @Override
    public Class<Component> getType() {
        return Component.class;
    }
}

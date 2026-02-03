package gloomlib.command.resolver;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * 枚举类型通用参数解析器。
 *
 * @param <E> 枚举类型
 */
public class EnumArgumentResolver<E extends Enum<E>> implements ArgumentResolver<E> {

    private final Class<E> enumClass;
    private final E[] constants;

    /**
     * 创建枚举解析器。
     *
     * @param enumClass 枚举类
     */
    public EnumArgumentResolver(Class<E> enumClass) {
        this.enumClass = enumClass;
        this.constants = enumClass.getEnumConstants();
    }

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        return StringArgumentType.word();
    }

    @Override
    public E resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        String input = context.getArgument(name, String.class);

        // 尝试精确匹配（忽略大小写）
        for (E constant : constants) {
            if (constant.name().equalsIgnoreCase(input)) {
                return constant;
            }
        }

        // 尝试前缀匹配
        for (E constant : constants) {
            if (constant.name().toLowerCase().startsWith(input.toLowerCase())) {
                return constant;
            }
        }

        throw new IllegalArgumentException("无效的 " + enumClass.getSimpleName() + " 值: " + input +
                "。可用值: " + Arrays.toString(constants));
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining().toLowerCase();

        for (E constant : constants) {
            String name = constant.name().toLowerCase();
            if (name.startsWith(remaining)) {
                builder.suggest(constant.name());
            }
        }

        return builder.buildFuture();
    }

    @Override
    public Class<E> getType() {
        return enumClass;
    }
}

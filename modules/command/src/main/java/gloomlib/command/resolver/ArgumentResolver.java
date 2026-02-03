package gloomlib.command.resolver;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * 命令参数解析器接口。
 *
 * <p>
 * 实现此接口以支持自定义参数类型的解析和建议。
 * </p>
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     public class RankResolver implements ArgumentResolver<Rank> {
 *         &#64;Override
 *         public ArgumentType<?> createArgumentType(Parameter parameter) {
 *             return StringArgumentType.word();
 *         }
 *
 *         &#64;Override
 *         public Rank resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
 *             String rankName = context.getArgument(name, String.class);
 *             return rankManager.getRank(rankName);
 *         }
 *
 *         @Override
 *         public CompletableFuture<Suggestions> suggest(
 *                 CommandContext<CommandSourceStack> context,
 *                 SuggestionsBuilder builder,
 *                 Parameter parameter) {
 *             rankManager.getRanks().forEach(rank -> builder.suggest(rank.getName()));
 *             return builder.buildFuture();
 *         }
 *     }
 * }
 * </pre>
 *
 * @param <T> 参数类型
 */
public interface ArgumentResolver<T> {

    /**
     * 创建 Brigadier 参数类型。
     * 返回 Paper API 兼容的 ArgumentType。
     *
     * @param parameter 方法参数反射对象
     * @return Brigadier 参数类型
     */
    ArgumentType<?> createArgumentType(Parameter parameter);

    /**
     * 从 Brigadier 上下文解析参数值。
     *
     * @param context   Paper Brigadier 命令上下文
     * @param name      参数名
     * @param parameter 方法参数反射对象
     * @return 解析后的参数值
     */
    T resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter);

    /**
     * 提供 Tab 补全建议。
     * 默认返回空建议（Paper 可能会提供内置建议）。
     *
     * @param context   Paper Brigadier 命令上下文
     * @param builder   建议构建器
     * @param parameter 方法参数反射对象
     * @return 异步建议列表
     */
    default CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        return Suggestions.empty();
    }

    /**
     * 获取此解析器支持的类型。
     * 用于自动注册。
     *
     * @return 支持的类型
     */
    Class<T> getType();
}

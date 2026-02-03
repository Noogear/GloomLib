package gloomlib.command.suggestion;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

/**
 * 命令参数的 Tab 补全建议提供器接口。
 *
 * <p>
 * 实现此接口以提供自定义的 Tab 补全建议。
 * </p>
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * public class WarpSuggestionProvider implements SuggestionProvider {
 *     @Override
 *     public CompletableFuture<Suggestions> suggest(
 *             CommandContext<CommandSourceStack> context,
 *             SuggestionsBuilder builder) {
 *         WarpManager warpManager = // 获取管理器
 *                 warpManager.getWarps().forEach(warp -> builder.suggest(warp.getName()));
 *         return builder.buildFuture();
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface SuggestionProvider {

    /**
     * 提供 Tab 补全建议。
     *
     * @param context Paper Brigadier 命令上下文
     * @param builder 建议构建器
     * @return 异步建议列表
     */
    CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder);
}

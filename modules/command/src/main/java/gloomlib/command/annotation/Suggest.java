package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import gloomlib.command.suggestion.SuggestionProvider;

/**
 * 指定参数的 Tab 补全建议提供器。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("warp")
 * public void warp(Player player,
 *                  @Arg @Suggest(WarpSuggestionProvider.class) String warpName) {
 *     // 使用自定义建议提供器
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Suggest {

    /**
     * 建议提供器类。
     *
     * @return 建议提供器类
     */
    Class<? extends SuggestionProvider> value();
}

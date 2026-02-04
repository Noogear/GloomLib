package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import gloomlib.command.suggestion.SuggestionProvider;

/**
 * Specifies the Tab completion suggestion provider for an argument.
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("warp")
 * public void warp(Player player,
 *         @Arg @Suggest(WarpSuggestionProvider.class) String warpName) {
 *     // Use custom suggestion provider
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Suggest {

    /**
     * Suggestion provider class.
     *
     * @return suggestion provider class
     */
    Class<? extends SuggestionProvider> value();
}

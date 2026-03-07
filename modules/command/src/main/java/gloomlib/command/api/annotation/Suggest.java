package gloomlib.command.api.annotation;

import gloomlib.command.api.SuggestionProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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

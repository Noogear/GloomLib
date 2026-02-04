package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Command description information, used to help display commands.
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>{@code
 * @Command("gamemode")
 * @Description("Change game mode")
 * public class GameModeCommand {
 *     // ...
 * }
 * }</pre>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Description {

    /**
     * Command description text.
     *
     * @return description text
     */
    String value();
}

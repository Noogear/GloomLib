package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a subcommand.
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>{@code
 * @SubCommand("create")
 * @Permission("rank.create")
 * public void createRank(CommandSender sender, @Arg String name) {
 *     // /rank create <name>
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubCommand {

    /**
     * Subcommand name.
     *
     * @return subcommand name
     */
    String value();

    /**
     * List of subcommand aliases.
     *
     * @return alias array
     */
    String[] aliases() default {};
}

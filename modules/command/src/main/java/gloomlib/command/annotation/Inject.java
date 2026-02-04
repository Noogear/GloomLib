package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Dependency injection annotation for injecting services and dependencies.
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     &#64;Command("economy")
 *     public class EconomyCommand {
 *
 *         &#64;Inject
 *         private EconomyService economyService;
 *
 *         &#64;Inject("mainDatabase")
 *         private DatabaseService database;
 *
 *         @Usage
 *         public void balance(Player player) {
 *             double balance = economyService.getBalance(player);
 *             // ...
 *         }
 *     }
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {

    /**
     * Qualifier to distinguish multiple instances of the same type.
     * If empty, matches by type.
     *
     * @return qualifier name
     */
    String value() default "";
}

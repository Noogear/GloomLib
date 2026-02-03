package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 依赖注入注解，用于注入服务和依赖。
 *
 * <p>
 * 用法示例：
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
     * 限定符，用于区分同类型的多个实例。
     * 如果为空，则按类型匹配。
     *
     * @return 限定符名称
     */
    String value() default "";
}

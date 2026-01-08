package gloomlib.configuration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * Marks a method to be executed <b>before</b> the configuration synchronization (loading/saving).
 * <p>
 * This is useful for initializing default data or setting up dynamic defaults.
 * The method must be public and have no parameters.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreLoad {
    /**
     * The execution priority. Lower values run first.
     *
     * @return the priority
     */
    int priority() default 0;
}

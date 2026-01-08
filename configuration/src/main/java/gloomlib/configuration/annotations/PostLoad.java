package gloomlib.configuration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be executed <b>after</b> the configuration synchronization.
 * <p>
 * This is useful for data validation, logic correction, or building caches based on loaded config.
 * The method must be public and have no parameters.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostLoad {
    /**
     * The execution priority. Lower values run first.
     *
     * @return the priority
     */
    int priority() default 0;
}
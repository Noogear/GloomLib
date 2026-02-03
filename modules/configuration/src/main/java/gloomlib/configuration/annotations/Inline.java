package gloomlib.configuration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds inline comments to the right of a configuration value.
 * <p>
 * Note: Inline comments are generally supported for single-line values.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inline {
    /**
     * The comment text.
     *
     * @return an array of comment strings
     */
    String[] value();
}

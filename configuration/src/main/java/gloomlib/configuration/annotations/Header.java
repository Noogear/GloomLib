package gloomlib.configuration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds header comments to the top of the YAML file.
 * <p>
 * This annotation should be applied to the {@link gloomlib.configuration.ConfigurationFile} class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Header {
    /**
     * The lines of comments to be added.
     *
     * @return an array of comment strings
     */
    String[] value();
}

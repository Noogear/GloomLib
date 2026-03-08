package gloomlib.configuration.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares default resource paths to copy from the JAR when the target directory is empty.
 * <p>
 * Place this on a {@link gloomlib.configuration.api.ConfigurationPart} subclass used as the
 * value type of a {@link gloomlib.configuration.api.DirectoryConfiguration}.
 * When combined with a {@link gloomlib.configuration.api.ResourceProvider},
 * the listed resources will be copied into the directory on first load.
 * </p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @DefaultResources({
 *     "indicator/damage-indicator.yml",
 *     "indicator/regain-indicator.yml"
 * })
 * @Template(name = "default")
 * public class IndicatorEntry extends ConfigurationPart { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultResources {

    /**
     * Resource paths inside the JAR to copy into the directory.
     *
     * @return array of resource paths
     */
    String[] value();
}

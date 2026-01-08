package gloomlib.configuration.annotations;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * Defines the default generation strategy for {@code Map<String, ConfigurationPart>} fields.
 * <p>
 * This annotation should be placed on the {@code ConfigurationPart} subclass used as the map's value.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigTemplate {
    /**
     * The strategy to use.
     *
     * @return the strategy
     */
    TemplateStrategy value() default TemplateStrategy.FORCE_DEFAULT;

    /**
     * Strategies for generating default keys in configuration maps.
     */
    public enum TemplateStrategy {
        /**
         * Always ensures a "default" key exists in the map.
         * If missing, a new instance will be created and added.
         */
        FORCE_DEFAULT,

        /**
         * Only generates a "default" key if the map is completely empty (first run).
         * If the user deletes the default key later, it will not be regenerated.
         */
        SMART,

        /**
         * Never automatically generates any keys.
         */
        STRICT
    }
}



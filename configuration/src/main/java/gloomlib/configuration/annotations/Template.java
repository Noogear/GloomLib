package gloomlib.configuration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the default generation strategy for {@code Map<String, ConfigurationPart>} fields.
 * <p>
 * This annotation should be placed on the {@link gloomlib.configuration.ConfigurationPart} subclass used as the map's value.
 * It controls whether a default key (e.g., "default") is automatically created in the map.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Template {
    /**
     * The strategy to use.
     *
     * @return the strategy, default is {@link Strategy#FORCE}
     */
    Strategy value() default Strategy.FORCE;

    /**
     * Strategies for generating default keys in configuration maps.
     */
    enum Strategy {
        /**
         * Always ensures a "default" key exists in the map.
         * If missing, a new instance will be created and added.
         * <p>
         * Use this when your plugin relies on a fallback configuration.
         */
        FORCE,

        /**
         * Only generates a "default" key if the map is completely empty (e.g., first run).
         * If the user deletes the "default" key later, it will NOT be regenerated.
         * <p>
         * Use this for providing examples without enforcing them.
         */
        SMART,

        /**
         * Never automatically generates any keys.
         * <p>
         * Use this for strict configuration where defaults are not desired.
         */
        STRICT
    }
}



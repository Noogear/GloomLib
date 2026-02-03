package gloomlib.configuration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as containing sensitive information that should be hidden from logs and monitoring tools.
 * When integrated with Spark profiler or other monitoring tools, fields marked with this annotation
 * will be masked or excluded from configuration exports.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
    /**
     * The mask to use when displaying the value.
     *
     * @return the mask string (default: "***")
     */
    String mask() default "***";

    /**
     * Whether to completely exclude this field from external monitoring tools.
     *
     * @return true to hide completely, false to just mask the value
     */
    boolean hideFromMonitoring() default true;
}

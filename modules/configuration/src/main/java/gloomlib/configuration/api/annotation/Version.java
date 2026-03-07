package gloomlib.configuration.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the version field in a configuration file for automatic version management.
 * When the configuration version changes, the old file will be automatically backed up
 * and a new one will be generated with default values.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Version {
    /**
     * The expected version number.
     * If not specified, uses the field's default value.
     *
     * @return the expected version
     */
    int value() default -1;

    /**
     * Whether to enable automatic backup when version changes.
     *
     * @return true to enable backup (default), false to disable
     */
    boolean autoBackup() default true;

    /**
     * Whether to attempt migrating data from the old configuration.
     *
     * @return true to enable migration (default), false to skip
     */
    boolean migrate() default true;
}

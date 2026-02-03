package gloomlib.configuration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Assigns a validator to a configuration field to automatically check and correct invalid values.
 * <p>
 * Two modes are supported:
 * <ol>
 * <li><b>Interface Mode:</b> Specify a class implementing the {@link Validator} interface via {@link #value()}.</li>
 * <li><b>Reflection Mode:</b> Specify a class and a method name via {@link #cls()} and {@link #method()}.</li>
 * </ol>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Check {
    /**
     * [Interface Mode] The validator class implementing {@link Validator}.
     *
     * @return the validator class
     */
    @SuppressWarnings("rawtypes")
    Class<? extends Validator> value() default NoOpValidator.class;

    /**
     * [Reflection Mode] The class containing the validation method.
     *
     * @return the class
     */
    Class<?> cls() default void.class;

    /**
     * [Reflection Mode] The name of the validation method.
     * <p>
     * The method must accept one parameter (the field value) and return the corrected value.
     *
     * @return the method name
     */
    String method() default "";

    /**
     * Functional interface for defining validation logic.
     *
     * @param <T> the type of the field being validated
     */
    interface Validator<T> {
        /**
         * Validates and optionally corrects the value.
         *
         * @param value the raw value read from the configuration
         * @return the validated (and potentially corrected) value
         */
        T validate(T value);
    }

    /**
     * Default no-op validator (used when no validator is specified).
     */
    @SuppressWarnings("rawtypes")
    /**
     * No-op validator used as a safe default value for annotations.
     */
    final class NoOpValidator implements Validator {
        @Override
        public Object validate(Object value) {
            return value;
        }
    }
}

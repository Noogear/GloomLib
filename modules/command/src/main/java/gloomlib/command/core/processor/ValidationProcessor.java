package gloomlib.command.core.processor;

import gloomlib.command.api.annotation.Range;
import gloomlib.command.core.message.CommandMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.lang.reflect.Parameter;

/**
 * Parameter validation processor.
 */
public class ValidationProcessor {

    /**
     * Validates numeric range.
     *
     * @param value     Value to validate
     * @param range     Range annotation
     * @param parameter Parameter info
     * @return Validation result
     */
    public ValidationResult validateRange(Number value, Range range, Parameter parameter) {
        if (value == null) {
            return ValidationResult.success();
        }

        double val = value.doubleValue();
        double min = range.min();
        double max = range.max();

        if (val < min) {
            CommandMessages msg = (value instanceof Integer || value instanceof Long) ? CommandMessages.INTEGER_TOO_LOW
                    : CommandMessages.DOUBLE_TOO_LOW;
            return ValidationResult.failure(msg.get(Component.text(min), Component.text(value.toString())));
        }

        if (val > max) {
            CommandMessages msg = (value instanceof Integer || value instanceof Long) ? CommandMessages.INTEGER_TOO_HIGH
                    : CommandMessages.DOUBLE_TOO_HIGH;
            return ValidationResult.failure(msg.get(Component.text(max), Component.text(value.toString())));
        }

        return ValidationResult.success();
    }

    /**
     * Validates string is not empty.
     *
     * @param value     Value to validate
     * @param parameter Parameter info
     * @return Validation result
     */
    public ValidationResult validateNotEmpty(String value, Parameter parameter) {
        if (value == null || value.isEmpty()) {
            return ValidationResult.failure(
                    CommandMessages.VALIDATION_EMPTY.get(Component.text(parameter.getName(), NamedTextColor.YELLOW)));
        }
        return ValidationResult.success();
    }

    /**
     * Verify object is not null.
     *
     * @param value     value to check
     * @param parameter parameter info
     * @return validation result
     */
    public ValidationResult validateNotNull(Object value, Parameter parameter) {
        if (value == null) {
            return ValidationResult.failure(
                    CommandMessages.VALIDATION_NULL.get(Component.text(parameter.getName(), NamedTextColor.YELLOW)));
        }
        return ValidationResult.success();
    }

    /**
     * Verify string length.
     *
     * @param value     value to check
     * @param minLength min length
     * @param maxLength max length
     * @param parameter parameter info
     * @return validation result
     */
    public ValidationResult validateLength(String value, int minLength, int maxLength, Parameter parameter) {
        if (value == null) {
            return ValidationResult.success();
        }

        int length = value.length();
        if (length < minLength) {
            return ValidationResult.failure(
                    CommandMessages.VALIDATION_LENGTH_MIN.get(
                            Component.text(parameter.getName(), NamedTextColor.YELLOW),
                            Component.text(minLength)));
        }

        if (length > maxLength) {
            return ValidationResult.failure(
                    CommandMessages.VALIDATION_LENGTH_MAX.get(
                            Component.text(parameter.getName(), NamedTextColor.YELLOW),
                            Component.text(maxLength)));
        }

        return ValidationResult.success();
    }

    /**
     * Validation Result.
     *
     * @param valid        whether validation passed
     * @param errorMessage error message (null if valid)
     */
    public record ValidationResult(boolean valid, Component errorMessage) {

        /**
         * Creates success result.
         *
         * @return Successful validation result
         */
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        /**
         * Creates failure result.
         *
         * @param errorMessage Error message
         * @return Failed validation result
         */
        public static ValidationResult failure(Component errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }
}

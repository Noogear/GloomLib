package gloomlib.command.processor.processors;

import gloomlib.command.annotation.Range;
import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.processor.PreProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.lang.reflect.Parameter;

/**
 * 参数验证处理器。
 *
 * <p>
 * 对命令参数进行验证，支持：
 * <ul>
 * <li>范围验证 ({@code @Range})</li>
 * <li>非空验证</li>
 * <li>自定义验证规则</li>
 * </ul>
 * </p>
 */
public class ValidationProcessor implements PreProcessor {

    @Override
    public Result preProcess(GloomCommandContext context) {
        // 默认实现：验证在参数解析阶段已完成
        return Result.CONTINUE;
    }

    @Override
    public int getPriority() {
        return 50; // 在权限检查之后，冷却检查之前
    }

    /**
     * 验证数值范围。
     *
     * @param value     要验证的值
     * @param range     范围注解
     * @param parameter 参数信息
     * @return 验证结果
     */
    public ValidationResult validateRange(Number value, Range range, Parameter parameter) {
        if (value == null) {
            return ValidationResult.success();
        }

        double val = value.doubleValue();
        double min = range.min();
        double max = range.max();

        if (val < min) {
            String key = (value instanceof Integer || value instanceof Long) ? "argument.integer.low"
                    : "argument.double.low";
            return ValidationResult.failure(Component.translatable(key, NamedTextColor.RED,
                    Component.text(min), Component.text(value.toString())));
        }

        if (val > max) {
            String key = (value instanceof Integer || value instanceof Long) ? "argument.integer.big"
                    : "argument.double.big";
            return ValidationResult.failure(Component.translatable(key, NamedTextColor.RED,
                    Component.text(max), Component.text(value.toString())));
        }

        return ValidationResult.success();
    }

    /**
     * 验证字符串非空。
     *
     * @param value     要验证的值
     * @param parameter 参数信息
     * @return 验证结果
     */
    public ValidationResult validateNotEmpty(String value, Parameter parameter) {
        if (value == null || value.isEmpty()) {
            return ValidationResult.failure(
                    Component.text()
                            .append(Component.text("Argument "))
                            .append(Component.text(parameter.getName(), NamedTextColor.YELLOW))
                            .append(Component.text(" cannot be empty!", NamedTextColor.RED))
                            .build());
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
                    Component.text()
                            .append(Component.text("Argument "))
                            .append(Component.text(parameter.getName(), NamedTextColor.YELLOW))
                            .append(Component.text(" cannot be null!", NamedTextColor.RED))
                            .build());
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
                    Component.text()
                            .append(Component.text("Argument "))
                            .append(Component.text(parameter.getName(), NamedTextColor.YELLOW))
                            .append(Component.text(" length must be at least "))
                            .append(Component.text(String.valueOf(minLength), NamedTextColor.GREEN))
                            .append(Component.text(" characters"))
                            .build());
        }

        if (length > maxLength) {
            return ValidationResult.failure(
                    Component.text()
                            .append(Component.text("Argument "))
                            .append(Component.text(parameter.getName(), NamedTextColor.YELLOW))
                            .append(Component.text(" length cannot exceed "))
                            .append(Component.text(String.valueOf(maxLength), NamedTextColor.GREEN))
                            .append(Component.text(" characters"))
                            .build());
        }

        return ValidationResult.success();
    }

    /**
     * 验证结果。
     */
    public static class ValidationResult {

        private final boolean valid;
        private final Component errorMessage;

        private ValidationResult(boolean valid, Component errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        /**
         * 创建成功结果。
         *
         * @return 成功的验证结果
         */
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        /**
         * 创建失败结果。
         *
         * @param errorMessage 错误消息
         * @return 失败的验证结果
         */
        public static ValidationResult failure(Component errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        /**
         * 是否验证通过。
         *
         * @return 是否通过
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * 获取错误消息。
         *
         * @return 错误消息组件
         */
        public Component getErrorMessage() {
            return errorMessage;
        }
    }
}

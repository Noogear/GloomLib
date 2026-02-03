package gloomlib.command.condition;

import gloomlib.command.context.GloomCommandContext;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 命令条件接口。
 *
 * <p>
 * 用于定义命令执行的前提条件。
 * </p>
 */
@FunctionalInterface
public interface CommandCondition {

    /**
     * 检查条件是否满足。
     *
     * @param context 命令上下文
     * @return 条件检查结果
     */
    ConditionResult check(GloomCommandContext context);

    /**
     * 条件检查结果。
     */
    record ConditionResult(boolean passed, @Nullable Component failureMessage) {

        /** 条件通过 */
        public static final ConditionResult PASS = new ConditionResult(true, null);

        /**
         * 创建通过结果。
         */
        public static ConditionResult pass() {
            return PASS;
        }

        /**
         * 创建失败结果。
         *
         * @param message 失败消息
         */
        public static ConditionResult fail(Component message) {
            return new ConditionResult(false, message);
        }

        /**
         * 创建失败结果（纯文本消息）。
         *
         * @param message 失败消息
         */
        public static ConditionResult fail(String message) {
            return new ConditionResult(false, Component.text(message));
        }
    }
}

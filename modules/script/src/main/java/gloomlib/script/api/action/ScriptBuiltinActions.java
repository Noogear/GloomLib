package gloomlib.script.api.action;

import gloomlib.script.api.ScriptErrorHandler;
import gloomlib.script.api.action.ActionRegistry.ScriptAction;

import java.util.logging.Level;

/**
 * 框架级内置动作——纯 Java 实现，不依赖任何平台 API（Bukkit/Paper 等）。
 * <p>
 * 所有方法均为 {@code consumesPayload = false}，参数完全由 YAML {@code args} 提供。
 * 由 {@link gloomlib.script.core.handler.ActionNodeHandler} 在静态初始化阶段自动注册，
 * 无需调用方手动 {@code scanAndRegister}。
 *
 * <h3>YAML 用法示例</h3>
 * <pre>{@code
 * flow:
 *   - action: log
 *     args: ["Player {name} took {damage} damage"]
 *   - action: log_warning
 *     args: ["Health below threshold: {hp}"]
 * }</pre>
 */
public final class ScriptBuiltinActions {

    private ScriptBuiltinActions() {
    }

    // ── 日志 ────────────────────────────────────────────────────────────

    /**
     * INFO 级日志输出，接受任意类型参数（自动 toString）。
     * <p>
     * 通过 {@link ScriptErrorHandler} 路由，宿主可通过 {@code setLogger()} 替换底层 Logger。
     */
    @ScriptAction(value = "log", consumesPayload = false)
    public static void log(Object message) {
        ScriptErrorHandler.info(String.valueOf(message));
    }

    /**
     * WARNING 级日志输出。
     */
    @ScriptAction(value = "log_warning", consumesPayload = false)
    public static void logWarning(Object message) {
        ScriptErrorHandler.warning(String.valueOf(message));
    }

    /**
     * SEVERE 级日志输出。
     */
    @ScriptAction(value = "log_error", consumesPayload = false)
    public static void logError(Object message) {
        ScriptErrorHandler.log(Level.SEVERE, String.valueOf(message));
    }

    // ── 取消 ────────────────────────────────────────────────────────────

    /**
     * 空操作——显式标记"此分支无动作"。
     * <p>
     * YAML: {@code action: noop}
     */
    @ScriptAction(value = "noop", consumesPayload = false)
    public static void noop() {
    }
}

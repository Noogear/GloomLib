package gloomlib.script.api;

import gloomlib.script.core.CheckOp;
import gloomlib.script.core.ScriptIR.IRType;
import gloomlib.script.core.parser.ScriptParser;

/**
 * API 层值解析与校验门面。
 * <p>
 * 将 {@link ScriptParser.ValueParser} 和 {@link CheckOp} 的公用能力暴露给 api 包，
 * 使 {@link ScriptBuilder} 无需直接依赖 core 内部类。
 */
public final class ValueParsing {

    private ValueParsing() {
    }

    /**
     * 推导值的 IR 类型。
     *
     * @see ScriptParser.ValueParser#inferType(Object)
     */
    public static IRType inferType(Object value) {
        return ScriptParser.ValueParser.inferType(value);
    }

    /**
     * 安全解析数字字符串（int → long → double 依次尝试），非数字原样返回。
     *
     * @see ScriptParser.ValueParser#parseNumber(String)
     */
    public static Object parseNumber(String s) {
        return ScriptParser.ValueParser.parseNumber(s);
    }

    /**
     * 校验操作符合法性。不合法时抛出 {@link ScriptCompileException}（含拼写建议）。
     *
     * @see CheckOp#resolve(String)
     */
    public static void validateOperator(String op) {
        CheckOp.resolve(op);
    }

    /**
     * INVOKE 节点安全黑名单：禁止脚本调用的 Object 基类方法。
     */
    public static final java.util.Set<String> INVOKE_BLACKLIST = java.util.Set.of(
            "getClass", "wait", "notify", "notifyAll", "clone", "finalize"
    );

    /**
     * 校验 INVOKE 方法名合法性。
     *
     * @throws IllegalArgumentException 方法名为空
     * @throws ScriptCompileException   方法名命中黑名单
     */
    public static void validateInvokeMethod(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("INVOKE requires a non-blank method name.");
        }
        if (INVOKE_BLACKLIST.contains(methodName)) {
            throw ScriptCompileException.create(
                    null, null,
                    gloomlib.diagnostic.DiagnosticCategory.SEMANTIC,
                    "Method '" + methodName + "' is blacklisted and cannot be invoked from scripts.");
        }
    }
}

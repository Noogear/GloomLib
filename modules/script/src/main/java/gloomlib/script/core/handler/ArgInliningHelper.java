package gloomlib.script.core.handler;

import com.google.common.collect.ImmutableList;
import gloomlib.script.api.ScriptCompileException;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.ScriptIR;
import gloomlib.script.core.ScriptIR.FlowNode;
import gloomlib.script.core.ScriptIR.IRType;
import gloomlib.script.core.codegen.ASMUtils;
import gloomlib.script.core.codegen.BytecodeCompiler;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

/**
 * 参数内联协议的共享实现。
 * <p>
 * 优化器的 {@code variableInlining} pass 需要三步协议才能将生产者内联到消费者：
 * <ol>
 *   <li>{@link #scanConsumedVariable} — 扫描 args 列表，报告消费的变量名</li>
 *   <li>{@link #buildInlineAction} — 将生产者注入为 {@code conditionAction}，记录注入位置</li>
 *   <li>{@link #emitConditionAction} — 在 emit 阶段执行内联的生产者，结果留在栈顶</li>
 * </ol>
 * ACTION 和 INVOKE 节点共享此逻辑，消除重复代码。
 */
public final class ArgInliningHelper {

    private ArgInliningHelper() {}

    /**
     * 校验变量参数类型是否与方法期望类型兼容（ACTION / INVOKE 共用）。
     *
     * @param callerLabel 调用来源标签（用于错误消息，如 "Action 'foo'" 或 "invoke 'bar'"）
     */
    public static void validateVarArgType(String callerLabel, int paramIndex, String argStr,
                                          IRType expected, CompilationContext ctx, FlowNode node) {
        String varName = argStr.substring(1, argStr.length() - 1);
        IRType actual = (ctx.getSlot(varName) == 1)
                ? IRType.fromClass(ctx.payloadClass())
                : ctx.getType(varName);
        if (expected.isAssignableFrom(actual)) return;

        Class<?> narrowed = ctx.getNarrowedClass(varName);
        if (narrowed != null && expected.isAssignableFrom(IRType.fromClass(narrowed))) return;

        throw ScriptCompileException.type(node, String.format(
                "%s expects %s at argument %d, but variable '{%s}' is of type %s.",
                callerLabel, expected, paramIndex, varName, actual));
    }

    /**
     * 从 {@code {var}} 或 {@code {entity.name}} 形式的括号参数提取基础变量名。
     * 点链引用取头部：{@code {entity.health}} → {@code entity}。
     */
    public static String baseVarOf(String bracketedArg) {
        String inner = bracketedArg.substring(1, bracketedArg.length() - 1);
        return ScriptIR.isDottedPart(inner) ? ScriptIR.splitDotted(inner)[0] : inner;
    }

    /**
     * 扫描参数列表，提取唯一引用的基础变量名。
     * 若参数引用多个不同变量，返回 null（不可内联）。
     *
     * @param args 节点的参数列表
     * @return 唯一消费的变量名，或 null
     */
    public static String scanConsumedVariable(ImmutableList<String> args) {
        String foundVar = null;
        for (String arg : args) {
            String baseVar = null;
            if (ScriptIR.isSingleVar(arg) || ScriptIR.isDottedSingleRef(arg)) {
                baseVar = baseVarOf(arg);
            } else if (ScriptIR.isTemplate(arg)) {
                for (String bv : ScriptIR.templateBaseVars(arg)) {
                    if (baseVar == null) baseVar = bv;
                    else if (!baseVar.equals(bv)) return null;
                }
            }
            if (baseVar != null) {
                if (foundVar != null && !foundVar.equals(baseVar)) return null;
                foundVar = baseVar;
            }
        }
        return foundVar;
    }

    /**
     * 收集参数列表中所有引用的变量（用于活跃变量分析）。
     *
     * @param args 节点的参数列表
     * @return 所有引用的基础变量名
     */
    public static List<String> collectAllConsumedVariables(ImmutableList<String> args) {
        List<String> vars = new java.util.ArrayList<>();
        for (String arg : args) {
            if (ScriptIR.isSingleVar(arg) || ScriptIR.isDottedSingleRef(arg)) {
                String bv = baseVarOf(arg);
                if (!vars.contains(bv)) vars.add(bv);
            } else if (ScriptIR.isTemplate(arg)) {
                for (String bv : ScriptIR.templateBaseVars(arg)) {
                    if (!vars.contains(bv)) vars.add(bv);
                }
            }
        }
        return vars;
    }

    /**
     * 构建内联动作：定位应被替换的参数位置，将生产者注入为 {@code conditionAction}。
     *
     * @param node       消费者节点
     * @param inlineHook 生产者节点（已 strip 掉 store）
     * @param args       消费者的参数列表
     * @return 携带 {@code conditionAction} 和 {@code _sink_arg_index} 的新节点
     */
    public static FlowNode buildInlineAction(FlowNode node, FlowNode inlineHook,
                                             ImmutableList<String> args) {
        int targetIndex = -1;
        String sinkingVarName = inlineHook.getAttrOrDefault("_var_name", null);

        // 第一轮：精确匹配单变量引用
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (ScriptIR.isSingleVar(arg)) {
                if (sinkingVarName == null || sinkingVarName.equals(baseVarOf(arg))) {
                    targetIndex = i;
                    break;
                }
            }
        }

        // 第二轮：点链引用下沉（如 {cause.name} 中的 cause）
        FlowNode effectiveHook = inlineHook;
        if (targetIndex < 0 && sinkingVarName != null) {
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (ScriptIR.isDottedSingleRef(arg) && sinkingVarName.equals(baseVarOf(arg))) {
                    targetIndex = i;
                    String inner = arg.substring(1, arg.length() - 1);
                    String suffix = inner.substring(sinkingVarName.length() + 1);
                    String baseProp = inlineHook.getAttrOrDefault("_sinking_property", "");
                    effectiveHook = inlineHook.withAttr("_sinking_property", baseProp + "." + suffix);
                    break;
                }
            }
        }

        return node.withAttr("conditionAction", effectiveHook).withAttr("_sink_arg_index", targetIndex);
    }

    /**
     * 在 emit 阶段执行内联的 conditionAction，将计算结果留在操作数栈顶。
     * <p>
     * 处理两条路径：
     * <ul>
     *   <li>虚拟属性下沉 — {@code _sinking_property} 存在时，发射属性加载链</li>
     *   <li>真实生产者 — ACTION 走 leave-on-stack 路径，其他类型直接 emit</li>
     * </ul>
     *
     * @param mv              方法访问器
     * @param ctx             编译上下文
     * @param conditionAction 内联的生产者节点
     * @param reqType         消费方期望的 Java 类型（用于装箱/拆箱适配）
     */
    public static void emitConditionAction(MethodVisitor mv, CompilationContext ctx,
                                           FlowNode conditionAction, Class<?> reqType) {
        String sinkingProp = conditionAction.getAttrOrDefault("_sinking_property", null);
        Class<?> unwrappedType = com.google.common.primitives.Primitives.unwrap(reqType);

        if (sinkingProp != null) {
            // 属性下沉（虚拟 VarDecl Producer）路径
            IRType returnType = conditionAction.getRequiredAttr("returnType");
            BytecodeCompiler.emitSunkPropertyLoad(mv, ctx, sinkingProp);
            if (!unwrappedType.isPrimitive() && returnType.isPrimitive()) {
                ASMUtils.emitBox(mv, returnType);
            }
        } else {
            // 真实节点内联路径 — 通过 InlineEmitter 接口统一分发，无需硬编码 FlowNodeType
            ScriptIR.FlowNodeHandler handler = conditionAction.handler();
            if (handler instanceof ScriptIR.InlineEmitter ie) {
                ie.emitInline(conditionAction, mv, ctx);
                IRType resultType = ie.inlineResultType(conditionAction, ctx);
                if (!unwrappedType.isPrimitive() && resultType.isPrimitive()) {
                    ASMUtils.emitBox(mv, resultType);
                }
            } else {
                handler.emit(conditionAction, mv, ctx);
            }
        }
    }
}

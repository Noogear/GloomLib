package gloomlib.script.core.handler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import gloomlib.script.core.CheckOp;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.ParseContext;
import gloomlib.script.core.ScriptIR;
import gloomlib.script.core.ScriptIR.FlowNode;
import gloomlib.script.core.ScriptIR.FlowNodeType;
import gloomlib.script.core.ScriptIR.IRType;
import gloomlib.script.core.ScriptIR.NodeCapability;
import gloomlib.script.core.codegen.ASMUtils;
import gloomlib.script.core.codegen.BytecodeCompiler;
import gloomlib.script.core.codegen.CheckOpEmitters;
import gloomlib.script.core.optimizer.ScriptOptimizer;
import gloomlib.script.core.parser.ScriptParser;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * CHECK 节点处理器（增强版）。
 * <p>
 * 支持 10 个基础操作符，所有操作符前均可加 {@code !} 前缀取反。
 * 编译时根据 {@link IRType} 智能选择零装箱字节码指令。
 * <p>
 * 操作符：{@code null, ==, >, <, >=, <=, contains, starts_with, ends_with, matches, instanceof, in, between}
 */
@SuppressWarnings("null")
public final class CheckNodeHandler
        implements ScriptIR.FlowNodeHandler, ScriptIR.ConditionEmitter, ScriptIR.NodeTraverser,
        ScriptIR.ConstantHoister, ScriptIR.ConstantFolder, ScriptIR.RangePropagator, ScriptIR.VariableConsumer {

    static {
        FlowNodeType.registerHandler(FlowNodeType.CHECK, CheckNodeHandler::new);
    }

    public static void init() {
    }

    /**
     * 从 FlowNode 'op' 属性解析 CheckOp.Resolved 的便利方法。
     */
    private static CheckOp.Resolved resolveOp(FlowNode node) {
        return CheckOp.resolve(node.getRequiredAttr("op"));
    }

    /**
     * 操作符是否为数值类比较（决定 value 字段是否可尝试作为数学表达式解析）。
     */
    private static boolean isNumericOp(String rawOp) {
        if (rawOp == null) return false;
        CheckOp op = CheckOp.resolve(rawOp).op();
        return op.isNumeric();
    }


    @Override
    @SuppressWarnings("unchecked")
    public FlowNode parse(ParseContext ctx) {
        Object variableObj = ctx.get("variable");
        String variable = null;
        FlowNode conditionAction = null;

        if (variableObj instanceof Map) {
            conditionAction = ScriptParser.parseFlowNode(ctx.withAttrs((Map<String, Object>) variableObj));
        } else if (variableObj != null) {
            variable = variableObj.toString();
        }

        String op = ctx.get("op");
        Object value = ctx.get("value");

        // AOT: 无 conditionAction 时 op 必填；同时进行 resolve 验证合法性 & 规范化多重 ! 前缀
        if (conditionAction == null && op == null) {
            throw ctx.error("CHECK node requires an 'op' field when not using inline conditionAction.");
        }
        if (op != null) {
            CheckOp.Resolved resolved = CheckOp.resolve(op);
            // 规范化后回写（!! → 空, !!! → !）
            op = resolved.toSymbol();
        }

        // AOT: instanceof 类名编译期验证
        if (op != null && CheckOp.fromSymbol(op.startsWith("!") ? op.substring(1) : op) == CheckOp.INSTANCEOF
                && value instanceof String className) {
            try {
                Class.forName(className.replace('/', '.'));
            } catch (ClassNotFoundException e) {
                throw ctx.error("instanceof check references unknown class: " + className);
            }
        }

        ImmutableMap.Builder<String, Object> nodeAttrs = ImmutableMap.builder();
        if (variable != null) {
            nodeAttrs.put("variable", variable);
        }
        if (conditionAction != null) {
            nodeAttrs.put("conditionAction", conditionAction);
        }
        if (op != null) {
            nodeAttrs.put("op", op);
        }

        double numericValue = 0.0;

        if (value != null) {
            if (value instanceof String s) {
                value = ScriptParser.ValueParser.parseNumber(s);
            }

            // 缺口2：若 value 仍为字符串且操作符为数值类，尝试作为数学表达式解析
            // 跳过 ENUM 字面量（ALL_CAPS 标识符），避免被误识别为数学变量引用
            if (value instanceof String mathStr && isNumericOp(op)
                    && ScriptParser.ValueParser.inferType(value) != IRType.ENUM) {
                try {
                    gloomlib.math.api.MathNode mathNode = gloomlib.math.api.MathParser.parse(mathStr);
                    if (mathNode instanceof gloomlib.math.api.MathNode.LiteralNode(double value1)) {
                        // 纯常量表达式（如 "5*3+2"）——直接折叠为数值
                        value = value1;
                    } else {
                        // 含变量的表达式（如 "{maxHp} * 0.5"）——存储 MathNode 供运行时发射
                        nodeAttrs.put("valueNode", mathNode);
                    }
                } catch (IllegalArgumentException ignored) {
                    // 解析失败则保持原始字符串
                }
            }

            nodeAttrs.put("value", value);
            nodeAttrs.put("valueType", ScriptParser.ValueParser.inferType(value));

            // 数值存入 numericValue 字段（零装箱路径）
            if (value instanceof Number n) {
                numericValue = n.doubleValue();
            }
        }

        // in 操作符的值列表
        if (value instanceof List<?> list) {
            nodeAttrs.put("valueList", ImmutableList.copyOf(list));
        }

        // 解析 on_fail 列表
        List<?> onFailRaw = ctx.get("on_fail");
        if (onFailRaw != null) {
            nodeAttrs.put("onFailNodes", ScriptParser.parseFlow(onFailRaw));
        }

        return new FlowNode(FlowNodeType.CHECK, nodeAttrs.build(), numericValue, 0);
    }

    @Override
    public void emit(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        int jumpOp = emitCondition(node, mv, ctx);

        Label continueLabel = new Label();
        mv.visitJumpInsn(jumpOp, continueLabel);

        // 分支失败处理：执行所有的 on_fail 动作
        ASMUtils.emitOnFail(node, mv, ctx);

        // 根据方法的实际返回类型决定 return 指令（避免 void RETURN 在 Object 方法中非法）
        ASMUtils.emitEarlyReturn(mv, ctx);
        mv.visitLabel(continueLabel);

        // instanceof 成功路径：将变量窄化为目标类型，供后续节点使用。
        // 只处理面向顺序流的顶层 emit （非复合条件内部）；取反的 !instanceof 不注册。
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        if (conditionAction == null) {
            CheckOp.Resolved info = resolveOp(node);
            if (info.op() == CheckOp.INSTANCEOF && !info.negate()) {
                String variable = node.getRequiredAttr("variable");
                String rawClass = node.<String>getRequiredAttr("value").replace('/', '.');
                try {
                    ctx.narrowType(variable, Class.forName(rawClass));
                } catch (ClassNotFoundException e) {
                    // parse 阶段已验证，这里不应到达
                    throw gloomlib.script.api.ScriptCompileException.create(node,
                            "[instanceof narrow] class not found at emit: " + rawClass);
                }
            }
        }
    }


    /**
     * 发射单个条件的比较字节码，返回"条件成立时应跳转"的 opcode。
     * <p>
     * 实装 {@link ScriptIR.ConditionEmitter} 接口供复合节点复用。
     * 已包含 negate（{@code !} 前缀）处理。
     */
    @Override
    public int emitCondition(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        // op 可能在优化器下沉后仍保留于节点属性中（conditionAction != null 时 variable 被移除，但 op/value 保留）
        String rawOp = node.getAttrOrDefault("op", null);
        CheckOp op = null;
        boolean negate = false;
        if (rawOp != null) {
            CheckOp.Resolved info = CheckOp.resolve(rawOp);
            op = info.op();
            negate = info.negate();
        }

        int jumpOp;

        if (conditionAction != null) {
            String sinkingProp = conditionAction.getAttrOrDefault("_sinking_property", null);
            if (sinkingProp != null) {
                int tempSlot = ctx.nextSlot();
                IRType exactType = conditionAction.getRequiredAttr("returnType");
                BytecodeCompiler.emitSunkPropertyLoadWithUnbox(mv, ctx, sinkingProp, exactType);

                mv.visitVarInsn(ASMUtils.storeOpcode(exactType), tempSlot);

                jumpOp = emitSinkingCheck(mv, op, tempSlot, exactType, node, ctx);
            } else {
                conditionAction.type().handler().emit(conditionAction, mv, ctx);
                if (conditionAction.type() == FlowNodeType.MATH) {
                    jumpOp = CheckOpEmitters.emitDoubleComparisonOnStack(mv, node, op != null ? op : CheckOp.EQ, ctx);
                } else {
                    jumpOp = Opcodes.IFNE;
                }
            }
        } else {
            String variable = node.getRequiredAttr("variable");
            int slot = ctx.getSlot(variable);
            IRType type = ctx.getType(variable);

            op.validateType(variable, type);

            jumpOp = emitSinkingCheck(mv, op, slot, type, node, ctx);
        }

        if (negate)
            jumpOp = ASMUtils.invertJump(jumpOp);

        return jumpOp;
    }

    private int emitSinkingCheck(MethodVisitor mv, CheckOp op, int slot, IRType type, FlowNode node,
                                 CompilationContext ctx) {
        return CheckOpEmitters.forOp(op).emit(mv, op, slot, type, node, ctx);
    }

    @Override
    public EnumSet<NodeCapability> capabilities() {
        return EnumSet.of(NodeCapability.HAS_CONDITION, NodeCapability.FOLDABLE);
    }


    @Override
    public Iterable<FlowNode> traverseChildren(FlowNode node) {
        ArrayList<FlowNode> children = new ArrayList<>();
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        if (conditionAction != null) {
            children.add(conditionAction);
        }
        ImmutableList<FlowNode> onFailNodes = node.getAttrOrDefault("onFailNodes", null);
        if (onFailNodes != null) {
            children.addAll(onFailNodes);
        }
        return children;
    }

    @Override
    public FlowNode hoistConstants(FlowNode node, List<CompilationContext.ConstantDef> defs, int[] counter) {
        String rawOp = node.getAttrOrDefault("op", null);
        if (rawOp == null)
            return node;
        CheckOp op = CheckOp.resolve(rawOp).op();
        String key = null;
        CompilationContext.ConstantKind kind = null;
        Object payload = null;

        switch (op.hoistKind()) {
            case PATTERN -> {
                String pattern = node.getAttrOrDefault("value", null);
                if (pattern != null) {
                    // 内容哈希键：相同 pattern 跨脚本共享同一 CallSite
                    key = "P/" + pattern;
                    kind = CompilationContext.ConstantKind.PATTERN;
                    payload = pattern;
                }
            }
            case IN_SET -> {
                ImmutableList<?> list = node.getAttrOrDefault("valueList", null);
                if (list == null) list = node.getAttrOrDefault("value", null);
                if (list instanceof ImmutableList<?> vals && vals.size() > CheckOp.IN_SET_THRESHOLD) {
                    // 排序后 join，使顺序无关的相同集合得到同一 key
                    String sorted = vals.stream().map(Object::toString)
                            .sorted().collect(java.util.stream.Collectors.joining(","));
                    key = "S/" + sorted;
                    kind = CompilationContext.ConstantKind.STRING_SET;
                    payload = vals;
                }
            }
            case RANGE_ARRAY -> {
                ImmutableList<?> range = node.getAttrOrDefault("valueList", null);
                if (range == null) range = node.getAttrOrDefault("value", null);
                if (range instanceof ImmutableList<?> vals && vals.size() == 2) {
                    double lo = ((Number) vals.get(0)).doubleValue();
                    double hi = ((Number) vals.get(1)).doubleValue();
                    double[] arr = {lo, hi};
                    key = "D/" + Double.toHexString(lo) + "/" + Double.toHexString(hi);
                    kind = CompilationContext.ConstantKind.DOUBLE_ARRAY;
                    payload = arr;
                }
            }
            case NONE -> {
            }
        }

        if (key != null) {
            defs.add(new CompilationContext.ConstantDef(key, kind, payload));
            return node.withAttr("_hoistedField", key);
        }
        return node;
    }

    @Override
    public Boolean evaluateFold(ScriptIR.FlowNode node, gloomlib.script.core.CompilationContext ctx) {
        String varName = node.getAttrOrDefault("variable", null);
        if (varName == null || !ctx.isConstant(varName))
            return null;

        CheckOp.Resolved info = resolveOp(node);

        Object varValue = ctx.getConstant(varName);
        Boolean result = evaluateBaseOp(info.op(), varValue, node);
        if (result != null && info.negate())
            result = !result;
        return result;
    }

    private Boolean evaluateBaseOp(CheckOp op, Object varValue, ScriptIR.FlowNode node) {
        if (op == CheckOp.NULL)
            return varValue == null;
        if (varValue == null)
            return null;

        Object cmpValue = node.getAttrOrDefault("value", null);
        if (cmpValue == null && op == CheckOp.EQ && varValue instanceof Boolean b) {
            return b;
        }
        if (cmpValue == null)
            return null;

        if (varValue instanceof Number v && cmpValue instanceof Number c) {
            return op.foldNumeric(v.doubleValue(), c.doubleValue());
        }
        return op.foldObject(varValue, cmpValue);
    }

    @Override
    public Boolean tryFoldWithRange(ScriptIR.FlowNode node, ScriptOptimizer.ValueRange range) {
        CheckOp.Resolved info = resolveOp(node);

        Boolean foldResult = tryFoldWithRangeOp(range, info.op(), node);
        if (foldResult != null && info.negate()) {
            return !foldResult;
        }
        return foldResult;
    }

    private Boolean tryFoldWithRangeOp(ScriptOptimizer.ValueRange range, CheckOp op,
                                       ScriptIR.FlowNode node) {
        if (op.isRangeFoldable()) {
            Object value = node.getAttrOrDefault("value", null);
            if (value instanceof Number n) {
                return range.canFold(op, n.doubleValue());
            }
            return range.canFoldExact(op, value);
        }
        if (op == CheckOp.BETWEEN) {
            double[] bounds = resolveBetweenBounds(node);
            if (bounds != null) {
                // 值域完全落入 [lo, hi] → 恒真
                if (range.min() >= bounds[0] && range.max() <= bounds[1]) return Boolean.TRUE;
                // 值域完全在 [lo, hi] 之外 → 恒假
                if (range.min() > bounds[1] || range.max() < bounds[0]) return Boolean.FALSE;
            }
            return null;
        }
        if (op == CheckOp.NULL && range.nonNull())
            return Boolean.FALSE;
        return null;
    }

    @Override
    public ScriptOptimizer.ValueRange updateRange(ScriptIR.FlowNode node, ScriptOptimizer.ValueRange range) {
        CheckOp.Resolved info = resolveOp(node);

        Object value = node.getAttrOrDefault("value", null);
        double d = value instanceof Number n ? n.doubleValue() : 0;

        return switch (info.op()) {
            case GT -> range.withMin(d + Double.MIN_VALUE);
            case GTE -> range.withMin(d);
            case LT -> range.withMax(d - Double.MIN_VALUE);
            case LTE -> range.withMax(d);
            case EQ -> value != null ? range.withExact(value) : range;
            case BETWEEN -> {
                double[] bounds = resolveBetweenBounds(node);
                yield bounds != null ? range.withMin(bounds[0]).withMax(bounds[1]) : range;
            }
            case NULL -> info.negate() ? range.withNonNull() : range;
            default -> range;
        };
    }

    /**
     * 从节点属性中提取 BETWEEN 的 [lo, hi] 边界。
     */
    private static double[] resolveBetweenBounds(ScriptIR.FlowNode node) {
        ImmutableList<?> bounds = node.getAttrOrDefault("valueList", null);
        if (bounds == null) bounds = node.getAttrOrDefault("value", null);
        if (bounds instanceof ImmutableList<?> vals && vals.size() == 2) {
            return new double[]{
                    ((Number) vals.get(0)).doubleValue(),
                    ((Number) vals.get(1)).doubleValue()
            };
        }
        return null;
    }

    /**
     * 覆写以包含 valueNode 中引用的变量（活跃变量分析需要）。
     */
    @Override
    public java.util.List<String> getAllConsumedVariables(FlowNode node) {
        java.util.List<String> vars = new ArrayList<>();
        String main = getConsumedVariable(node);
        if (main != null) vars.add(main);
        gloomlib.math.api.MathNode valueNode = node.getAttrOrDefault("valueNode", null);
        if (valueNode != null) vars.addAll(gloomlib.math.api.MathNode.collectVarNames(valueNode));
        return vars;
    }
}

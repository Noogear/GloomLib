package gloomlib.script.core.handler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import gloomlib.script.api.ScriptCompileException;
import gloomlib.script.api.ValueParsing;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.ParseContext;
import gloomlib.script.core.ScriptIR;
import gloomlib.script.core.ScriptIR.FlowNode;
import gloomlib.script.core.ScriptIR.FlowNodeType;
import gloomlib.script.core.ScriptIR.IRType;
import gloomlib.script.core.ScriptIR.NodeCapability;
import gloomlib.script.core.codegen.ASMUtils;
import gloomlib.script.core.codegen.BytecodeCompiler;
import gloomlib.script.core.parser.ScriptParser;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * INVOKE 节点处理器 — 直接调用 payload 或变量上的实例方法。
 * <p>
 * 消除 YAML 用户必须为 payload 已有方法（如 {@code setCancelled}、{@code setDamage}）
 * 编写 {@code @ScriptAction} 包装器的负担。编译期通过反射解析方法签名，
 * 运行期生成 INVOKEVIRTUAL / INVOKEINTERFACE 字节码，零反射开销。
 *
 * <h3>YAML 语法</h3>
 * <pre>{@code
 * # 调 payload 方法（默认）
 * - invoke: setCancelled
 *   args: ["true"]
 *
 * # 调变量方法
 * - invoke: setHealth
 *   on: "{target}"
 *   args: ["{value}"]
 *
 * # 捕获返回值
 * - invoke: getName
 *   on: "{source}"
 *   store: attackerName
 * }</pre>
 */
@SuppressWarnings("null")
public final class InvokeNodeHandler implements ScriptIR.FlowNodeHandler,
        ScriptIR.VariableProducer, ScriptIR.VariableConsumer, ScriptIR.TypeValidator,
        ScriptIR.NodeTraverser, ScriptIR.InlineEmitter {

    // ===================== parse =====================

    @Override
    public FlowNode parse(ParseContext ctx) {
        String methodName = ctx.get("invoke");
        if (methodName == null || methodName.isBlank()) {
            throw ctx.error("INVOKE node requires a method name.");
        }

        // 黑名单校验（parse 阶段即拒绝，无需等到 emit）
        if (ValueParsing.INVOKE_BLACKLIST.contains(methodName)) {
            throw ctx.error("Method '" + methodName + "' is blacklisted and cannot be invoked from scripts.");
        }

        String target = ctx.get("on");
        String store = ctx.get("store");
        List<String> args = ctx.getOrDefault("args", List.of());

        ImmutableMap.Builder<String, Object> nodeAttrs = ImmutableMap.builder();
        nodeAttrs.put("methodName", methodName);
        nodeAttrs.put("args", ImmutableList.copyOf(args));
        if (target != null) {
            nodeAttrs.put("target", target);
        }
        if (store != null) {
            nodeAttrs.put("store", store);
        }

        return new FlowNode(FlowNodeType.INVOKE, "invoke", nodeAttrs.build());
    }

    // ===================== emit =====================

    @Override
    public void emit(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        String methodName = node.getRequiredAttr("methodName");
        ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
        String target = node.getAttrOrDefault("target", null);
        String store = node.getAttrOrDefault("store", null);

        // 已在 validateTypes() 中校验，此处重新解析（编译期开销无关紧要）
        Class<?> targetClass = resolveTargetClass(target, ctx, node);
        Method method = resolveMethod(targetClass, methodName, args.size(), node);

        // 1. 加载目标对象到操作数栈
        emitTargetLoad(mv, ctx, target, targetClass);

        // 2. 加载参数
        Class<?>[] paramTypes = method.getParameterTypes();
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        int sinkArgIndex = node.getAttrOrDefault("_sink_arg_index", -1);
        for (int i = 0; i < args.size(); i++) {
            if (i == sinkArgIndex && conditionAction != null) {
                ArgInliningHelper.emitConditionAction(mv, ctx, conditionAction, paramTypes[i]);
            } else {
                emitArgLoad(mv, ctx, args.get(i), paramTypes[i], node);
            }
        }

        // 3. 调用方法
        boolean isInterface = targetClass.isInterface();
        int invokeOpcode = isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
        mv.visitMethodInsn(invokeOpcode,
                Type.getInternalName(method.getDeclaringClass()),
                method.getName(),
                Type.getMethodDescriptor(method),
                isInterface);

        // 4. 处理返回值
        Class<?> retType = method.getReturnType();
        boolean hasReturn = retType != void.class;

        if (hasReturn) {
            if (store != null) {
                int slot = ctx.getSlot(store);
                int storeOpcode = Type.getType(retType).getOpcode(Opcodes.ISTORE);
                mv.visitVarInsn(storeOpcode, slot);
            } else {
                int popOpcode = Type.getType(retType).getSize() == 2 ? Opcodes.POP2 : Opcodes.POP;
                mv.visitInsn(popOpcode);
            }
        }
    }

    /**
     * 发射 INVOKE 调用并将返回值留在操作数栈顶，不执行 STORE 或 POP。
     */
    static void emitInvokeLeaveOnStack(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        String methodName = node.getRequiredAttr("methodName");
        ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
        String target = node.getAttrOrDefault("target", null);

        Class<?> targetClass = resolveTargetClass(target, ctx, node);
        Method method = resolveMethod(targetClass, methodName, args.size(), node);

        emitTargetLoad(mv, ctx, target, targetClass);

        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < args.size(); i++) {
            emitArgLoad(mv, ctx, args.get(i), paramTypes[i], node);
        }

        boolean isInterface = targetClass.isInterface();
        int invokeOpcode = isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
        mv.visitMethodInsn(invokeOpcode,
                Type.getInternalName(method.getDeclaringClass()),
                method.getName(),
                Type.getMethodDescriptor(method),
                isInterface);
    }

    // ===================== validateTypes =====================

    @Override
    public void validateTypes(FlowNode node, CompilationContext ctx) {
        String methodName = node.getRequiredAttr("methodName");
        ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
        String target = node.getAttrOrDefault("target", null);
        String store = node.getAttrOrDefault("store", null);

        // 确定目标类型
        Class<?> targetClass = resolveTargetClass(target, ctx, node);

        // 查找方法
        Method method = resolveMethod(targetClass, methodName, args.size(), node);

        // 校验 store
        if (store != null) {
            Class<?> retType = method.getReturnType();
            if (retType == void.class) {
                throw ScriptCompileException.type(node, String.format(
                        "Cannot store result of void method '%s' to variable '%s'.",
                        methodName, store));
            }
        }

        // 校验参数类型
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < args.size(); i++) {
            String argStr = args.get(i);
            Class<?> reqType = paramTypes[i];
            IRType reqIRType = IRType.fromClass(reqType);

            if (ScriptIR.isSingleVar(argStr)) {
                ArgInliningHelper.validateVarArgType(
                        "invoke '" + methodName + "'", i, argStr, reqIRType, ctx, node);
            } else if (ScriptIR.isTemplate(argStr)) {
                if (reqIRType != IRType.STRING && reqIRType != IRType.OBJECT) {
                    throw ScriptCompileException.type(node, String.format(
                            "invoke '%s' expects %s at argument %d, but a string template '%s' was provided.",
                            methodName, reqIRType, i, argStr));
                }
            }
            // 字面量的类型兼容性在 parse 阶段已做基础校验（数字/布尔）
        }
    }

    // ===================== capabilities =====================

    @Override
    public EnumSet<NodeCapability> capabilities() {
        return EnumSet.of(NodeCapability.SIDE_EFFECT, NodeCapability.DOTTED_ARG_SINK);
    }

    // ===================== InlineEmitter =====================

    @Override
    public void emitInline(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        emitInvokeLeaveOnStack(node, mv, ctx);
    }

    @Override
    public ScriptIR.IRType inlineResultType(FlowNode node, CompilationContext ctx) {
        String methodName = node.getRequiredAttr("methodName");
        ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
        String target = node.getAttrOrDefault("target", null);
        Class<?> targetClass = resolveTargetClass(target, ctx, node);
        Method method = resolveMethod(targetClass, methodName, args.size(), node);
        return ScriptIR.IRType.fromClass(method.getReturnType());
    }

    // ===================== resolveProducedType =====================

    @Override
    public ScriptIR.IRType resolveProducedType(FlowNode node, Class<?> payloadClass, ScriptIR.ScriptUnit unit) {
        return resolveReturnTypeFromUnit(node, payloadClass, unit);
    }

    // ===================== VariableProducer =====================

    @Override
    public String getProducedVariable(FlowNode node) {
        return node.getAttrOrDefault("store", null);
    }

    @Override
    public FlowNode stripProducedVariable(FlowNode node) {
        return node.withoutAttr("store");
    }

    // ===================== VariableConsumer =====================

    @Override
    public String getConsumedVariable(FlowNode node) {
        ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
        return ArgInliningHelper.scanConsumedVariable(args);
    }

    @Override
    public FlowNode inlineAction(FlowNode node, FlowNode inlineHook) {
        ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
        return ArgInliningHelper.buildInlineAction(node, inlineHook, args);
    }

    @Override
    public List<String> getAllConsumedVariables(FlowNode node) {
        List<String> vars = new java.util.ArrayList<>();
        String target = node.getAttrOrDefault("target", null);
        if (target != null && (ScriptIR.isSingleVar(target) || ScriptIR.isDottedSingleRef(target))) {
            vars.add(ArgInliningHelper.baseVarOf(target));
        }
        ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
        vars.addAll(ArgInliningHelper.collectAllConsumedVariables(args));
        return vars;
    }

    // ===================== NodeTraverser =====================

    @Override
    public Iterable<FlowNode> traverseChildren(FlowNode node) {
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        if (conditionAction != null) {
            return List.of(conditionAction);
        }
        return List.of();
    }

    // ===================== 内部方法 =====================

    /**
     * 确定 invoke 目标对象的 Class。
     */
    private static Class<?> resolveTargetClass(String target, CompilationContext ctx, FlowNode node) {
        if (target == null) {
            // 默认 payload
            return ctx.payloadClass();
        }

        if (ScriptIR.isSingleVar(target)) {
            String varName = target.substring(1, target.length() - 1);
            // 优先用窄化类型
            Class<?> narrowed = ctx.getNarrowedClass(varName);
            if (narrowed != null) return narrowed;

            IRType varType = ctx.getType(varName);
            // payload 别名（slot 1）
            if (ctx.getSlot(varName) == 1) {
                return ctx.payloadClass();
            }
            return varType.getToken().getRawType();
        }

        if (ScriptIR.isDottedSingleRef(target)) {
            String inner = target.substring(1, target.length() - 1);
            String[] parts = ScriptIR.splitDotted(inner);
            String varName = parts[0];
            String propPath = parts[1];

            Class<?> baseClass = ctx.getNarrowedClass(varName);
            if (baseClass == null) {
                if (ctx.getSlot(varName) == 1) {
                    baseClass = ctx.payloadClass();
                } else {
                    baseClass = ctx.getType(varName).getToken().getRawType();
                }
            }

            IRType resultType = ScriptParser.PropertyResolver.resolveType(baseClass, propPath, ctx.scriptId());
            return resultType.getToken().getRawType();
        }

        throw ScriptCompileException.create(node,
                "Invalid 'on' target: '" + target + "'. Expected {variable} or {variable.property}.");
    }

    /**
     * 在目标类上解析方法。
     */
    private static Method resolveMethod(Class<?> targetClass, String methodName, int argCount, FlowNode node) {
        List<Method> candidates = Arrays.stream(targetClass.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .filter(m -> m.getParameterCount() == argCount)
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !ValueParsing.INVOKE_BLACKLIST.contains(m.getName()))
                .toList();

        if (candidates.isEmpty()) {
            throw ScriptCompileException.create(node, String.format(
                    "No public instance method '%s' with %d parameter(s) found on %s.",
                    methodName, argCount, targetClass.getName()));
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 多个候选：尝试找最具体的（非 bridge 非 synthetic）
        List<Method> nonBridge = candidates.stream()
                .filter(m -> !m.isBridge() && !m.isSynthetic())
                .toList();

        if (nonBridge.size() == 1) {
            return nonBridge.get(0);
        }

        // 仍有歧义
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Ambiguous method '%s' on %s with %d parameter(s). Candidates:\n",
                methodName, targetClass.getSimpleName(), argCount));
        for (Method m : candidates) {
            sb.append("  - ").append(m.toGenericString()).append('\n');
        }
        throw ScriptCompileException.create(node, sb.toString());
    }

    /**
     * 发射目标对象加载字节码。
     */
    private static void emitTargetLoad(MethodVisitor mv, CompilationContext ctx,
                                       String target, Class<?> targetClass) {
        if (target == null) {
            // payload
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            if (!targetClass.isAssignableFrom(ctx.payloadClass())) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(targetClass));
            }
            return;
        }

        if (ScriptIR.isSingleVar(target)) {
            String varName = target.substring(1, target.length() - 1);
            int slot = ctx.getSlot(varName);
            mv.visitVarInsn(Opcodes.ALOAD, slot);
            // 引用类型需要 CHECKCAST 到目标类
            if (targetClass != Object.class) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(targetClass));
            }
            return;
        }

        if (ScriptIR.isDottedSingleRef(target)) {
            String inner = target.substring(1, target.length() - 1);
            BytecodeCompiler.emitNarrowedPropertyLoad(mv, ctx, inner);
            if (targetClass != Object.class) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(targetClass));
            }
            return;
        }

        throw new IllegalStateException("Invalid invoke target: " + target);
    }

    /**
     * 发射单个参数加载字节码。
     */
    private static void emitArgLoad(MethodVisitor mv, CompilationContext ctx,
                                    String arg, Class<?> reqType, FlowNode node) {
        if (ScriptIR.isSingleVar(arg) && reqType != String.class) {
            // 纯变量引用
            String varName = arg.substring(1, arg.length() - 1);
            int slot = ctx.getSlot(varName);
            IRType varType = ctx.getType(varName);
            Class<?> unwrappedReq = com.google.common.primitives.Primitives.unwrap(reqType);

            if (unwrappedReq.isPrimitive()) {
                if (varType.isPrimitive()) {
                    int loadOp = switch (varType.base()) {
                        case INT, BOOLEAN -> Opcodes.ILOAD;
                        case LONG -> Opcodes.LLOAD;
                        case DOUBLE -> Opcodes.DLOAD;
                        default -> Opcodes.ALOAD;
                    };
                    mv.visitVarInsn(loadOp, slot);
                } else {
                    mv.visitVarInsn(Opcodes.ALOAD, slot);
                    ASMUtils.emitUnbox(mv, IRType.fromClass(unwrappedReq));
                }
            } else {
                ASMUtils.emitLoadBoxed(mv, slot, varType);
                if (reqType != Object.class && reqType != Enum.class) {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(reqType));
                }
            }
        } else if (ScriptIR.isDottedSingleRef(arg)) {
            String inner = arg.substring(1, arg.length() - 1);
            BytecodeCompiler.emitNarrowedPropertyLoad(mv, ctx, inner);
            Class<?> unwrappedReq = com.google.common.primitives.Primitives.unwrap(reqType);
            if (!unwrappedReq.isPrimitive() && reqType != Object.class && reqType != Enum.class) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(reqType));
            }
        } else if (ScriptIR.isTemplate(arg)) {
            BytecodeCompiler.emitStringConcat(mv, arg, ctx);
        } else {
            // 字面量
            Class<?> unwrappedType = com.google.common.primitives.Primitives.unwrap(reqType);
            if (unwrappedType.isEnum()) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Enum<?> enumVal = Enum.valueOf((Class<Enum>) unwrappedType, arg.toUpperCase());
                mv.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(unwrappedType),
                        enumVal.name(), Type.getDescriptor(unwrappedType));
            } else if (unwrappedType.isPrimitive()) {
                Object parsed = unwrappedType == boolean.class
                        ? Boolean.parseBoolean(arg)
                        : ScriptParser.ValueParser.parseNumber(arg);
                ASMUtils.emitPrimitiveLiteral(mv, parsed, unwrappedType);
            } else {
                mv.visitLdcInsn(arg);
            }
        }
    }

    /**
     * 从 ScriptUnit 的变量声明和反射解析 INVOKE 返回类型（编译管线槽位分配阶段使用）。
     */
    static ScriptIR.IRType resolveReturnTypeFromUnit(ScriptIR.FlowNode node,
                                                     Class<?> payloadClass,
                                                     ScriptIR.ScriptUnit unit) {
        String methodName = node.getAttrOrDefault("methodName", null);
        String target = node.getAttrOrDefault("target", null);
        int argCount = ((java.util.List<?>) node.getAttrOrDefault("args", java.util.List.of())).size();

        Class<?> targetClass;
        if (target == null) {
            targetClass = payloadClass;
        } else if (ScriptIR.isSingleVar(target)) {
            String varName = target.substring(1, target.length() - 1);
            targetClass = resolveVarClassFromUnit(varName, payloadClass, unit);
        } else if (ScriptIR.isDottedSingleRef(target)) {
            String inner = target.substring(1, target.length() - 1);
            String[] parts = ScriptIR.splitDotted(inner);
            Class<?> baseClass = resolveVarClassFromUnit(parts[0], payloadClass, unit);
            targetClass = ScriptParser.PropertyResolver
                    .resolveType(baseClass, parts[1], unit.id()).getToken().getRawType();
        } else {
            return ScriptIR.IRType.OBJECT;
        }

        try {
            for (java.lang.reflect.Method m : targetClass.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == argCount
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    return ScriptIR.IRType.fromClass(m.getReturnType());
                }
            }
        } catch (Exception ignored) {}
        return ScriptIR.IRType.OBJECT;
    }

    private static Class<?> resolveVarClassFromUnit(String varName, Class<?> payloadClass,
                                                    ScriptIR.ScriptUnit unit) {
        for (ScriptIR.VarDecl var : unit.vars()) {
            if (var.name().equals(varName)) {
                if (var.isPayloadAlias()) return payloadClass;
                return var.type().getToken().getRawType();
            }
        }
        return Object.class;
    }

}

package gloomlib.script.core.handler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import gloomlib.script.api.action.ActionRegistry;
import gloomlib.script.api.action.ScriptBuiltinActions;
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

import java.util.EnumSet;
import java.util.List;

/**
 * ACTION 节点处理器。
 * <p>
 * 统一通过 {@link ActionRegistry.ActionDef} 分发所有动作调用，
 * 不再对特定动作做硬编码 switch 特殊处理。
 * 字符串模板使用 {@code invokedynamic StringConcatFactory}。
 */
@SuppressWarnings("null")
public final class ActionNodeHandler implements ScriptIR.FlowNodeHandler, ScriptIR.VariableProducer,
        ScriptIR.VariableConsumer, ScriptIR.NodeTraverser, ScriptIR.TypeValidator, ScriptIR.InlineEmitter {

    private static final ActionRegistry REGISTRY = new ActionRegistry();

    public static ActionRegistry registry() {
        return REGISTRY;
    }



    private static void validateTemplateArgType(String action, int paramIndex, String argStr,
                                                IRType expected, FlowNode node) {
        if (expected == IRType.STRING || expected == IRType.OBJECT)
            return;

        throw gloomlib.script.api.ScriptCompileException.type(node, String.format(
                "Action '%s' expects %s at argument %d, but a string template '%s' was provided.",
                action, expected, paramIndex, argStr));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void validateLiteralArgType(String action, int paramIndex, String argStr,
                                               Class<?> expectedJavaType, FlowNode node) {
        if (!expectedJavaType.isEnum())
            return;

        try {
            Enum.valueOf((Class<Enum>) expectedJavaType, argStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw gloomlib.script.api.ScriptCompileException.type(node, String.format(
                    "Invalid enum value '%s' for action '%s' at argument %d. Expected enum type %s",
                    argStr, action, paramIndex, expectedJavaType.getSimpleName()));
        }
    }

    @Override
    public FlowNode parse(ParseContext ctx) {
        String action = ctx.get("action");
        String store = ctx.get("store");

        List<String> args = ctx.getOrDefault("args", List.of());

        // 验证动作存在
        ActionRegistry.ActionDef def = REGISTRY.lookup(action);

        // 参数个数校验：consumesPayload=true 时第一位由引擎自动注入，args 对应第二位起；
        // consumesPayload=false 时为纯工具方法，所有参数由 args 提供
        int expectedArgs = def.consumesPayload()
                ? Math.max(0, def.paramCount() - 1)
                : def.paramCount();

        if (args.size() != expectedArgs) {
            throw ctx.error(
                    String.format("Action '%s' expects %d %s, but got %d.",
                            action, expectedArgs,
                            def.consumesPayload()
                                    ? "user argument(s) (payload is auto-injected as first param)"
                                    : "argument(s)",
                            args.size()));
        }

        // 类型静态校验（简单推测验证：数字型强转）
        Class<?>[] pTypes = def.paramTypes();
        int paramOffset = def.consumesPayload() ? 1 : 0;
        for (int i = 0; i < args.size(); i++) {
            String argStr = args.get(i);
            int methodParamIndex = i + paramOffset;

            // 跳过包含模板变量的参数（因为在运行时拼接，暂时无法纯静态检查）
            if (ScriptIR.isTemplate(argStr)) {
                continue;
            }

            Class<?> reqType = pTypes[methodParamIndex];
            IRType reqIRType = IRType.fromClass(reqType);

            if (reqIRType.isNumeric()) {
                Object parsed = gloomlib.script.core.parser.ScriptParser.ValueParser.parseNumber(argStr);
                boolean isNumber = (parsed instanceof Number);
                if (!isNumber && !argStr.matches("-?\\d+(\\.\\d+)?")) {
                    throw ctx.error(
                            String.format("Action '%s' expects a numeric value at argument %d (type %s), but got '%s'.",
                                    action, methodParamIndex, reqType.getSimpleName(), argStr));
                }
            } else if (reqIRType == IRType.BOOLEAN) {
                if (!argStr.equalsIgnoreCase("true") && !argStr.equalsIgnoreCase("false")) {
                    throw ctx.error(
                            String.format("Action '%s' expects a boolean (true/false) at argument %d, but got '%s'.",
                                    action, methodParamIndex, argStr));
                }
            } else if (reqIRType.base() == gloomlib.script.core.ScriptIR.BaseType.ENUM) {
                try {
                    @SuppressWarnings({"unchecked", "rawtypes", "unused"})
                    Object ignored = Enum.valueOf((Class<Enum>) reqType, argStr);
                } catch (IllegalArgumentException e) {
                    throw ctx.error(
                            String.format(
                                    "Action '%s' expects an enum value of %s at argument %d, but got invalid constant '%s'.",
                                    action, reqType.getSimpleName(), methodParamIndex, argStr));
                }
            } else if (reqType == Enum.class) {
                // 开放枚举参数（参数类型为原始 Enum，由 @EnumClass 提供具体类提示）
                // 字面量路径：使用 hint 校验常量合法性；变量引用路径由 validateTypes 处理
                Class<?> hint = def.enumHint(methodParamIndex);
                if (hint != null && !ScriptIR.isSingleVar(argStr)) {
                    try {
                        @SuppressWarnings({"unchecked", "rawtypes", "unused"})
                        Object ignored = Enum.valueOf((Class<Enum>) hint, argStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw ctx.error(String.format(
                                "Action '%s' expects an enum constant of %s (declared via @EnumClass) at argument %d,"
                                        + " but got invalid value '%s'.",
                                action, hint.getSimpleName(), methodParamIndex, argStr));
                    }
                }
            }
        }

        // 验证 store (不能存 void)
        if (store != null) {
            if (def.returnType() == void.class || def.returnType() == Void.class) {
                throw ctx.error(
                        String.format("Action '%s' does not return a value, cannot store to '%s'", action, store));
            }
        }

        IRType returnIRType = IRType.fromClass(def.returnType());

        ImmutableMap.Builder<String, Object> nodeAttrs = ImmutableMap.builder();
        nodeAttrs.put("action", action);
        nodeAttrs.put("args", ImmutableList.copyOf(args));
        nodeAttrs.put("def", def);
        if (store != null) {
            nodeAttrs.put("store", store);
            nodeAttrs.put("returnType", returnIRType);
        }

        return new FlowNode(FlowNodeType.ACTION, "action", nodeAttrs.build());
    }

    @Override
    public void emit(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        ImmutableList<String> args = node.getRequiredAttr("args");
        ActionRegistry.ActionDef def = node.getRequiredAttr("def");
        String store = node.getAttrOrDefault("store", null);

        // 统一分发：加载参数 → 调用方法
        emitActionCall(mv, def, args, ctx, node);

        // 处理返回值栈平衡与保存
        Class<?> retClass = def.returnType();
        boolean hasReturn = (retClass != void.class && retClass != Void.class);

        if (hasReturn) {
            if (store != null) {
                int slot;
                try {
                    slot = ctx.getSlot(store);
                } catch (gloomlib.diagnostic.DiagnosticException e) {
                    throw gloomlib.script.api.ScriptCompileException.create(node,
                            String.format("Undefined store variable '%s' for action '%s'. "
                                            + "If using ScriptBuilder.actionStore(), this is likely an internal error — "
                                            + "the variable should have been auto-declared.",
                                    store, node.getAttrOrDefault("action", "?")));
                }
                int storeOpcode = org.objectweb.asm.Type.getType(retClass).getOpcode(Opcodes.ISTORE);
                mv.visitVarInsn(storeOpcode, slot);
            } else {
                // 未被 store 但方法返回了值，必须 POP 清理栈避免 VerifyError
                int popOpcode = org.objectweb.asm.Type.getType(retClass).getSize() == 2 ? Opcodes.POP2 : Opcodes.POP;
                mv.visitInsn(popOpcode);
            }
        }
    }

    // ===================== InlineEmitter =====================

    @Override
    public void emitInline(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        ImmutableList<String> args = node.getRequiredAttr("args");
        ActionRegistry.ActionDef def = node.getRequiredAttr("def");
        emitActionCall(mv, def, args, ctx, node);
    }

    @Override
    public IRType inlineResultType(FlowNode node, CompilationContext ctx) {
        ActionRegistry.ActionDef def = node.getRequiredAttr("def");
        return IRType.fromClass(def.returnType());
    }

    /**
     * 统一动作调用发射。
     * <p>
     * 根据 ActionDef 的参数类型智能加载参数，
     * 字符串模板使用 invokedynamic StringConcatFactory。
     */
    static void emitActionCall(MethodVisitor mv, ActionRegistry.ActionDef def,
                        ImmutableList<String> args, CompilationContext ctx, FlowNode node) {
        // 根据 consumesPayload 决定是否自动注入 payload（slot 1）作为第一个方法参数
        if (def.consumesPayload()) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            // NARROWED 场景：方法要求 payload 子类但脚本 payload 是父类
            // （此时 validateTypes 已确认 instanceof 窄化存在）→ 追加 CHECKCAST
            Class<?> firstParamType = def.paramTypes()[0];
            if (!firstParamType.isAssignableFrom(ctx.payloadClass())) {
                mv.visitTypeInsn(Opcodes.CHECKCAST,
                        org.objectweb.asm.Type.getInternalName(firstParamType));
            }
        }

        Class<?>[] pTypes = def.paramTypes();
        int paramOffset = def.consumesPayload() ? 1 : 0;

        // Load arguments
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        int sinkArgIndex = node.getAttrOrDefault("_sink_arg_index", -1);

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            int methodParamIndex = i + paramOffset;
            Class<?> reqType = pTypes[methodParamIndex];

            if (i == sinkArgIndex && conditionAction != null) {
                ArgInliningHelper.emitConditionAction(mv, ctx, conditionAction, reqType);
            } else if (ScriptIR.isSingleVar(arg) && reqType != String.class) {
                // 纯变量引用 → 直传对象（非 String 参数场景）
                String varName = arg.substring(1, arg.length() - 1);
                int slot = ctx.getSlot(varName);
                if (slot >= 0) {
                    IRType varType = ctx.getType(varName);
                    Class<?> unwrappedReq = com.google.common.primitives.Primitives.unwrap(reqType);
                    if (unwrappedReq.isPrimitive()) {
                        // 方法要求原始类型
                        if (varType.isPrimitive()) {
                            // 变量本身是原始类型 → 直接 XLOAD
                            int loadOp;
                            switch (varType.base()) {
                                case INT:
                                case BOOLEAN:
                                    loadOp = Opcodes.ILOAD;
                                    break;
                                case LONG:
                                    loadOp = Opcodes.LLOAD;
                                    break;
                                case DOUBLE:
                                    loadOp = Opcodes.DLOAD;
                                    break;
                                default:
                                    loadOp = Opcodes.ALOAD;
                                    break;
                            }
                            mv.visitVarInsn(loadOp, slot);
                        } else {
                            // 变量是引用类型但方法要原始类型 → ALOAD + 拆箱
                            mv.visitVarInsn(Opcodes.ALOAD, slot);
                            ASMUtils.emitUnbox(mv, IRType.fromClass(unwrappedReq));
                        }
                    } else {
                        // 方法要求引用类型 → 加载并按需装箱
                        // 利用窄化类型：若变量已经过 instanceof 窄化，且窄化类是 reqType 的子类，直接 cast 到窄化类
                        Class<?> narrowed = ctx.getNarrowedClass(varName);
                        Class<?> castTarget = (narrowed != null && reqType.isAssignableFrom(narrowed))
                                ? narrowed : reqType;
                        ASMUtils.emitLoadBoxed(mv, slot, varType);
                        // Enum.class 是所有枚举的公共父类，无需 CHECKCAST（等同于 Object.class 的逻辑）
                        if (castTarget != Object.class && castTarget != Enum.class) {
                            mv.visitTypeInsn(Opcodes.CHECKCAST,
                                    org.objectweb.asm.Type.getInternalName(castTarget));
                        }
                    }
                } else {
                    // 变量未找到，fallback 到字符串
                    mv.visitLdcInsn(arg);
                }
            } else if (ScriptIR.isDottedSingleRef(arg)) {
                // 纯点链引用 {entity.name} → 直接发射窄化属性加载（不经过 StringConcat）
                String inner = arg.substring(1, arg.length() - 1);
                BytecodeCompiler.emitNarrowedPropertyLoad(mv, ctx, inner);
                // 若方法要求具体子类型则追加 CHECKCAST；
                // Enum.class 是公共父类，等同于 Object.class，无需 CHECKCAST
                Class<?> unwrappedReq = com.google.common.primitives.Primitives.unwrap(reqType);
                if (!unwrappedReq.isPrimitive() && reqType != Object.class && reqType != Enum.class) {
                    mv.visitTypeInsn(Opcodes.CHECKCAST,
                            org.objectweb.asm.Type.getInternalName(reqType));
                }

            } else if (ScriptIR.isTemplate(arg)) {
                // 模板字符串（含字面量 + 占位符） → invokedynamic StringConcatFactory
                BytecodeCompiler.emitStringConcat(mv, arg, ctx);
            } else {
                Class<?> unwrappedType = com.google.common.primitives.Primitives.unwrap(reqType);
                // 解析有效枚举类：具体枚举参数直接使用，开放枚举（Enum.class）使用 @EnumClass 提示。
                // 两种路径均发射 GETSTATIC，运行期零开销。
                Class<?> effectiveEnumType = unwrappedType.isEnum() ? unwrappedType
                        : (unwrappedType == Enum.class ? def.enumHint(methodParamIndex) : null);
                if (effectiveEnumType != null) {
                    mv.visitFieldInsn(Opcodes.GETSTATIC, org.objectweb.asm.Type.getInternalName(effectiveEnumType),
                            arg.toUpperCase(), org.objectweb.asm.Type.getDescriptor(effectiveEnumType));
                } else if (unwrappedType.isPrimitive()) {
                    // 直接发射原始类型常量，无装箱需求
                    Object parsed = unwrappedType == boolean.class
                            ? Boolean.parseBoolean(arg)
                            : ScriptParser.ValueParser.parseNumber(arg);
                    ASMUtils.emitPrimitiveLiteral(mv, parsed, unwrappedType);
                } else {
                    // String 或包装类型：直接 LDC
                    mv.visitLdcInsn(arg);
                }
            }
        }

        // 调用目标方法
        boolean isInterface = def.invokeType() == Opcodes.INVOKEINTERFACE;
        mv.visitMethodInsn(def.invokeType(), def.owner(), def.method(),
                def.descriptor(), isInterface);
    }

    @Override
    public EnumSet<NodeCapability> capabilities() {
        return EnumSet.of(NodeCapability.SIDE_EFFECT, NodeCapability.DOTTED_ARG_SINK);
    }

    @Override
    public String getProducedVariable(FlowNode node) {
        return node.getAttrOrDefault("store", null);
    }

    @Override
    public FlowNode stripProducedVariable(FlowNode node) {
        return node.withoutAttr("store");
    }

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
    public Iterable<FlowNode> traverseChildren(FlowNode node) {
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        if (conditionAction != null) {
            return List.of(conditionAction);
        }
        return List.of();
    }

    @Override
    public List<String> getAllConsumedVariables(FlowNode node) {
        ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
        return ArgInliningHelper.collectAllConsumedVariables(args);
    }

    @Override
    public void validateTypes(FlowNode node, CompilationContext ctx) {
        ActionRegistry.ActionDef def = node.getRequiredAttr("def");
        String actionName = node.getRequiredAttr("action");
        ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
        com.google.common.reflect.TypeToken<?>[] genericPTypes = def.genericParamTypes();

        // 校验 payload 参数类型兼容性（consumesPayload=true 且方法至少有一个参数时）
        if (def.consumesPayload() && def.paramCount() > 0) {
            Class<?> firstParamType = def.paramTypes()[0];
            Class<?> payloadClass = ctx.payloadClass();
            if (!firstParamType.isAssignableFrom(payloadClass)) {
                if (!payloadClass.isAssignableFrom(firstParamType)) {
                    // 完全无继承关系 → 直接幹错
                    throw gloomlib.script.api.ScriptCompileException.type(node, String.format(
                            "Action '%s' requires payload type '%s', but script payload is '%s'. "
                                    + "These types are unrelated \u2014 this action cannot be called from this script.",
                            actionName, firstParamType.getSimpleName(), payloadClass.getSimpleName()));
                }
                // NARROWED 场景：payload 是 firstParamType 的父类，需要前置 instanceof 检查
                Class<?> narrowed = ctx.getNarrowedClass("payload");
                // 同时检查 slot-1 上注册的别名（如 $self）
                if (narrowed == null) {
                    String slot1Var = ctx.getVarName(1);
                    if (slot1Var != null) narrowed = ctx.getNarrowedClass(slot1Var);
                }
                if (narrowed == null || !firstParamType.isAssignableFrom(narrowed)) {
                    throw gloomlib.script.api.ScriptCompileException.type(node, String.format(
                            "Action '%s' requires payload subtype '%s', but current payload is '%s'. "
                                    + "Add a 'check: instanceof: %s' guard before this action to narrow the type.",
                            actionName, firstParamType.getSimpleName(), payloadClass.getSimpleName(),
                            firstParamType.getName()));
                }
            }
        }

        int paramOffset = def.consumesPayload() ? 1 : 0;
        for (int i = 0; i < args.size(); i++) {
            int paramIndex = i + paramOffset;
            com.google.common.reflect.TypeToken<?> expectedToken = genericPTypes[paramIndex];
            IRType expectedIR = IRType.fromToken(expectedToken);
            String argStr = args.get(i);

            if (ScriptIR.isSingleVar(argStr)) {
                ArgInliningHelper.validateVarArgType(
                        "Action '" + actionName + "'", paramIndex, argStr, expectedIR, ctx, node);
            } else if (ScriptIR.isTemplate(argStr)) {
                validateTemplateArgType(actionName, paramIndex, argStr, expectedIR, node);
            } else {
                validateLiteralArgType(actionName, paramIndex, argStr, expectedToken.getRawType(), node);
            }
        }
    }
}

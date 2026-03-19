package gloomlib.script.core.handler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.NodeRegistry;
import gloomlib.script.core.ParseContext;
import gloomlib.script.core.ScriptIR;
import gloomlib.script.core.ScriptIR.BaseType;
import gloomlib.script.core.ScriptIR.FlowNode;
import gloomlib.script.core.ScriptIR.FlowNodeType;
import gloomlib.script.core.ScriptIR.IRType;
import gloomlib.script.core.ScriptIR.NodeCapability;
import gloomlib.script.core.codegen.ASMUtils;
import gloomlib.script.core.codegen.BytecodeCompiler;
import gloomlib.script.core.parser.ScriptParser;
import gloomlib.script.core.parser.accessor.PropertyAccessor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * SWITCH 节点处理器。
 * <p>
 * 枚举类型编译为 {@code ordinal()} + {@code LOOKUPSWITCH}，
 * 其他稀疏值使用 hashCode + {@code LOOKUPSWITCH} + equals 验证。
 */
@SuppressWarnings("null")
public final class SwitchNodeHandler
        implements ScriptIR.FlowNodeHandler, ScriptIR.NodeTraverser, ScriptIR.VariableConsumer,
        ScriptIR.BranchReorderer {

    /**
     * 检查 case key 是否像合法的枚举常量名（Java 标识符规则）。
     */
    private static boolean isValidEnumName(String name) {
        if (name == null || name.isEmpty())
            return false;
        if (!Character.isJavaIdentifierStart(name.charAt(0)))
            return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i)))
                return false;
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public FlowNode parse(ParseContext ctx) {
        String variable = ctx.get("variable");
        if (variable == null) {
            throw ctx.error("SWITCH node requires a 'variable' field.");
        }

        Map<String, Object> casesRaw = ctx.get("cases");
        if (casesRaw == null || casesRaw.isEmpty()) {
            throw ctx.error("SWITCH node requires at least one case in 'cases'.");
        }

        ImmutableMap.Builder<String, ImmutableList<FlowNode>> cases = ImmutableMap.builder();
        for (Map.Entry<String, Object> entry : casesRaw.entrySet()) {
            String key = entry.getKey();
            List<Map<String, Object>> actions = (List<Map<String, Object>>) entry.getValue();
            ImmutableList.Builder<FlowNode> actionNodes = ImmutableList.builder();
            for (Map<String, Object> actionYaml : actions) {
                actionNodes.add(NodeRegistry.handler("action").parse(ctx.withAttrs(actionYaml)));
            }
            cases.put(key, actionNodes.build());
        }

        return new FlowNode(FlowNodeType.SWITCH, "switch", ImmutableMap.of(
                "variable", variable,
                "cases", cases.build()));
    }

    @Override
    public void emit(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        ImmutableMap<String, ImmutableList<FlowNode>> cases = node.getRequiredAttr("cases");

        String variable = null;
        int slot;
        IRType type;
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);

        if (conditionAction != null) {
            String sinkingProp = conditionAction.getAttrOrDefault("_sinking_property", null);
            if (sinkingProp != null) {
                // Property Sinking 闭包：即时解析并提取
                slot = ctx.nextSlot();
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                List<PropertyAccessor> accessors = ScriptParser.PropertyResolver
                        .resolveAccessors(
                                com.google.common.reflect.TypeToken.of(ctx.payloadClass()), sinkingProp, ctx.scriptId());
                BytecodeCompiler.emitAccessorChain(accessors, mv, ctx);
                type = conditionAction.getRequiredAttr("returnType");
                int storeOp = ASMUtils.storeOpcode(type);
                mv.visitVarInsn(storeOp, slot);
            } else {
                // 普通 Action 压入栈（注意：对于 Switch，由于多分支操作需要反复读取变量，所以依然要存临时 Slot）
                slot = ctx.nextSlot();
                if (conditionAction.handler() instanceof ScriptIR.InlineEmitter ie) {
                    ie.emitInline(conditionAction, mv, ctx);
                    type = ie.inlineResultType(conditionAction, ctx);
                } else {
                    conditionAction.handler().emit(conditionAction, mv, ctx);
                    type = conditionAction.getAttrOrDefault("returnType", IRType.OBJECT);
                }
                int storeOp = ASMUtils.storeOpcode(type);
                mv.visitVarInsn(storeOp, slot);
            }
        } else {
            variable = node.getRequiredAttr("variable");
            slot = ctx.getSlot(variable);
            type = ctx.getType(variable);
        }

        // 小 case 数快速路径：≤2 个 case 直接内联 if-else，跳过策略选择开销
        if (cases.size() <= 2) {
            emitCascadeIfElseSwitch(mv, slot, type, cases, ctx);
            return;
        }

        // 如果仍保留外部传入的实验性策略则运用，否则在运行时进行即时降级策略演算
        String strategy = node.getAttrOrDefault("_switchStrategy", "AUTO");
        Class<?> resolvedEnumClass = null;

        if ("AUTO".equals(strategy) || "CASCADE".equals(strategy)) {
            if (type.base() == BaseType.ENUM) {
                // 尝试 TABLE_ENUM：已知具体枚举类时用 ordinal() + TABLESWITCH，O(1)
                Class<?> enumClass = type.getToken().getRawType();
                if (enumClass != Enum.class && enumClass.isEnum()) {
                    Enum<?>[] constants = (Enum<?>[]) enumClass.getEnumConstants();
                    java.util.Map<String, Integer> nameToOrd = new java.util.HashMap<>(constants.length);
                    for (Enum<?> e : constants) nameToOrd.put(e.name(), e.ordinal());

                    boolean allValid = true;
                    int minOrd = Integer.MAX_VALUE, maxOrd = Integer.MIN_VALUE;
                    for (String key : cases.keySet()) {
                        Integer ord = nameToOrd.get(key);
                        if (ord == null) { allValid = false; break; }
                        minOrd = Math.min(minOrd, ord);
                        maxOrd = Math.max(maxOrd, ord);
                    }
                    if (allValid) {
                        long span = (long) maxOrd - minOrd + 1;
                        if (span <= cases.size() * 2.5 && span <= 1000) {
                            strategy = "TABLE_ENUM";
                            resolvedEnumClass = enumClass;
                        }
                    }
                }

                if (!"TABLE_ENUM".equals(strategy)) {
                    // 回退到基于 hashCode 的哈希查表
                    strategy = "LOOKUP_STRING";
                    for (String key : cases.keySet()) {
                        if (!isValidEnumName(key)) {
                            java.util.logging.Logger.getLogger("WarriorView-Script").warning(
                                    String.format("SWITCH case key '%s' does not look like a valid enum constant name.",
                                            key));
                        }
                    }
                }
            } else if (type == IRType.INT) {
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                boolean allInts = true;

                for (String key : cases.keySet()) {
                    try {
                        int v = Integer.parseInt(key);
                        min = Math.min(min, v);
                        max = Math.max(max, v);
                    } catch (NumberFormatException e) {
                        allInts = false;
                        break;
                    }
                }

                if (allInts && cases.size() > 0) {
                    long span = (long) max - min + 1;
                    if (span <= cases.size() * 2.5 && span <= 1000) {
                        strategy = "TABLE_INT";
                    } else {
                        strategy = "LOOKUP_INT";
                    }
                } else {
                    strategy = "CASCADE";
                }
            } else if (type == IRType.STRING) {
                strategy = "LOOKUP_STRING";
            } else if (type == IRType.LONG) {
                boolean allLongs = true;
                for (String key : cases.keySet()) {
                    try {
                        Long.parseLong(key);
                    } catch (NumberFormatException e) {
                        allLongs = false;
                        break;
                    }
                }
                strategy = (allLongs && cases.size() > 2) ? "LOOKUP_LONG" : "CASCADE";
            } else {
                strategy = "CASCADE";
            }
        }

        switch (strategy) {
            case "TABLE_INT":
                emitTableIntSwitch(mv, slot, cases, ctx);
                break;
            case "TABLE_ENUM":
                emitTableEnumSwitch(mv, slot, resolvedEnumClass, cases, ctx, node);
                break;
            case "LOOKUP_INT":
            case "LOOKUP_STRING":
                emitLookupSwitch(mv, slot, type, cases, ctx);
                break;
            case "LOOKUP_LONG":
                emitLookupLongSwitch(mv, slot, cases, ctx);
                break;
            case "CASCADE":
            default:
                emitCascadeIfElseSwitch(mv, slot, type, cases, ctx);
                break;
        }
    }

    private void emitLookupSwitch(MethodVisitor mv, int slot, IRType type,
                                  ImmutableMap<String, ImmutableList<FlowNode>> cases,
                                  CompilationContext ctx) {
        Label defaultLabel = new Label();
        Label endLabel = new Label();

        // ENUM 预处理：将 Enum.name() 结果缓存到临时 slot，避免循环中重复提取（从 2N+1 次降为 1 次）
        int stringSlot;
        if (type.base() == BaseType.ENUM) {
            stringSlot = ctx.nextSlot();
            mv.visitVarInsn(Opcodes.ALOAD, slot);
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Enum");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Enum", "name",
                    "()Ljava/lang/String;", false);
            mv.visitVarInsn(Opcodes.ASTORE, stringSlot);
        } else {
            stringSlot = slot; // STRING 类型直接使用原始 slot
        }

        mv.visitVarInsn(Opcodes.ALOAD, stringSlot);
        ASMUtils.emitHashCode(mv);

        String[] caseNames = cases.keySet().toArray(new String[0]);
        int n = caseNames.length;

        // 合并同 hashCode 的 case（LOOKUPSWITCH 要求 key 严格递增不重复）
        java.util.TreeMap<Integer, java.util.List<Integer>> hashToIdxList = new java.util.TreeMap<>();
        for (int i = 0; i < n; i++) {
            int h = caseNames[i].hashCode();
            hashToIdxList.computeIfAbsent(h, k -> new java.util.ArrayList<>()).add(i);
        }

        int uniqueCount = hashToIdxList.size();
        int[] keys = new int[uniqueCount];
        Label[] labels = new Label[uniqueCount];
        int pos = 0;
        for (var entry : hashToIdxList.entrySet()) {
            keys[pos] = entry.getKey();
            labels[pos] = new Label();
            pos++;
        }

        mv.visitLookupSwitchInsn(defaultLabel, keys, labels);

        pos = 0;
        for (var entry : hashToIdxList.entrySet()) {
            mv.visitLabel(labels[pos]);
            java.util.List<Integer> indices = entry.getValue();
            for (int gi = 0; gi < indices.size(); gi++) {
                int idx = indices.get(gi);
                // hashCode 碰撞保护——使用 equals 精确验证
                mv.visitVarInsn(Opcodes.ALOAD, stringSlot);
                mv.visitLdcInsn(caseNames[idx]);
                ASMUtils.emitEquals(mv);
                Label nextCheck = (gi < indices.size() - 1) ? new Label() : defaultLabel;
                mv.visitJumpInsn(Opcodes.IFEQ, nextCheck);

                // 匹配成功：发射分支 action 并跳到 end
                ImmutableList<FlowNode> actions = cases.get(caseNames[idx]);
                for (FlowNode action : actions) {
                    action.handler().emit(action, mv, ctx);
                }
                mv.visitJumpInsn(Opcodes.GOTO, endLabel);

                // 不匹配：继续组内下一个候选（最后一个自然跳到 defaultLabel）
                if (gi < indices.size() - 1) {
                    mv.visitLabel(nextCheck);
                }
            }
            pos++;
        }

        mv.visitLabel(defaultLabel);
        mv.visitLabel(endLabel);
    }

    /**
     * Long 类型 LOOKUPSWITCH：用 {@link Long#hashCode(long)} 做查表键，LCMP 做碰撞验证。
     * <p>
     * 相比 CASCADE 的 O(N) 线性 if-else，LOOKUPSWITCH 为 O(log N) 二分查找。
     */
    private void emitLookupLongSwitch(MethodVisitor mv, int slot,
                                      ImmutableMap<String, ImmutableList<FlowNode>> cases,
                                      CompilationContext ctx) {
        Label defaultLabel = new Label();
        Label endLabel = new Label();

        String[] caseNames = cases.keySet().toArray(new String[0]);
        int n = caseNames.length;
        long[] longValues = new long[n];
        for (int i = 0; i < n; i++) {
            longValues[i] = Long.parseLong(caseNames[i]);
        }

        // 合并同 hashCode 的 case（LOOKUPSWITCH 要求 key 严格递增不重复）
        java.util.TreeMap<Integer, java.util.List<Integer>> hashToIdxList = new java.util.TreeMap<>();
        for (int i = 0; i < n; i++) {
            int h = Long.hashCode(longValues[i]);
            hashToIdxList.computeIfAbsent(h, k -> new java.util.ArrayList<>()).add(i);
        }

        int uniqueCount = hashToIdxList.size();
        int[] keys = new int[uniqueCount];
        Label[] labels = new Label[uniqueCount];
        int pos = 0;
        for (var entry : hashToIdxList.entrySet()) {
            keys[pos] = entry.getKey();
            labels[pos] = new Label();
            pos++;
        }

        // LLOAD → Long.hashCode(long) → LOOKUPSWITCH
        mv.visitVarInsn(Opcodes.LLOAD, slot);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "hashCode",
                "(J)I", false);
        mv.visitLookupSwitchInsn(defaultLabel, keys, labels);

        pos = 0;
        for (var entry : hashToIdxList.entrySet()) {
            mv.visitLabel(labels[pos]);
            java.util.List<Integer> indices = entry.getValue();
            for (int gi = 0; gi < indices.size(); gi++) {
                int idx = indices.get(gi);
                // hashCode 碰撞保护：LCMP 精确验证
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                mv.visitLdcInsn(longValues[idx]);
                mv.visitInsn(Opcodes.LCMP);
                Label nextCheck = (gi < indices.size() - 1) ? new Label() : defaultLabel;
                mv.visitJumpInsn(Opcodes.IFNE, nextCheck);

                for (FlowNode action : cases.get(caseNames[idx])) {
                    action.handler().emit(action, mv, ctx);
                }
                mv.visitJumpInsn(Opcodes.GOTO, endLabel);

                if (gi < indices.size() - 1) {
                    mv.visitLabel(nextCheck);
                }
            }
            pos++;
        }

        mv.visitLabel(defaultLabel);
        mv.visitLabel(endLabel);
    }

    private void emitTableIntSwitch(MethodVisitor mv, int slot,
                                    ImmutableMap<String, ImmutableList<FlowNode>> cases,
                                    CompilationContext ctx) {
        Label defaultLabel = new Label();
        Label endLabel = new Label();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (String key : cases.keySet()) {
            int v = Integer.parseInt(key);
            if (v < min)
                min = v;
            if (v > max)
                max = v;
        }

        int size = max - min + 1;
        Label[] labels = new Label[size];
        for (int i = 0; i < size; i++) {
            labels[i] = defaultLabel;
        }

        Label[] caseLabels = new Label[cases.size()];
        String[] caseNames = cases.keySet().toArray(new String[0]);
        for (int i = 0; i < caseNames.length; i++) {
            int v = Integer.parseInt(caseNames[i]);
            Label targetLabel = new Label();
            caseLabels[i] = targetLabel;
            labels[v - min] = targetLabel;
        }

        mv.visitVarInsn(Opcodes.ILOAD, slot);
        mv.visitTableSwitchInsn(min, max, defaultLabel, labels);

        for (int i = 0; i < caseNames.length; i++) {
            mv.visitLabel(caseLabels[i]);
            for (FlowNode action : cases.get(caseNames[i])) {
                action.handler().emit(action, mv, ctx);
            }
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        }

        mv.visitLabel(defaultLabel);
        mv.visitLabel(endLabel);
    }

    /**
     * 已知具体枚举类时：ordinal() + TABLESWITCH，O(1) 分派。
     */
    private void emitTableEnumSwitch(MethodVisitor mv, int slot, Class<?> enumClass,
                                     ImmutableMap<String, ImmutableList<FlowNode>> cases,
                                     CompilationContext ctx, FlowNode switchNode) {
        Label defaultLabel = new Label();
        Label endLabel = new Label();

        Enum<?>[] constants = (Enum<?>[]) enumClass.getEnumConstants();
        java.util.Map<String, Integer> nameToOrd = new java.util.HashMap<>(constants.length);
        for (Enum<?> e : constants) nameToOrd.put(e.name(), e.ordinal());

        // 校验所有 case 键是否为目标枚举类的有效常量名，防止 unboxing NPE
        String[] caseNames = cases.keySet().toArray(new String[0]);
        for (String key : caseNames) {
            if (!nameToOrd.containsKey(key)) {
                throw gloomlib.script.api.ScriptCompileException.create(ctx.scriptId(), switchNode,
                        String.format("SWITCH case '%s' is not a valid constant of enum %s. Valid values: %s",
                                key, enumClass.getSimpleName(), nameToOrd.keySet()));
            }
        }

        int minOrd = Integer.MAX_VALUE, maxOrd = Integer.MIN_VALUE;
        for (String key : caseNames) {
            int ord = nameToOrd.get(key);
            minOrd = Math.min(minOrd, ord);
            maxOrd = Math.max(maxOrd, ord);
        }

        int size = maxOrd - minOrd + 1;
        Label[] tableLabels = new Label[size];
        for (int i = 0; i < size; i++) tableLabels[i] = defaultLabel;

        Label[] caseLabels = new Label[caseNames.length];
        for (int i = 0; i < caseNames.length; i++) {
            Label lbl = new Label();
            caseLabels[i] = lbl;
            tableLabels[nameToOrd.get(caseNames[i]) - minOrd] = lbl;
        }

        mv.visitVarInsn(Opcodes.ALOAD, slot);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Enum");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Enum", "ordinal", "()I", false);
        mv.visitTableSwitchInsn(minOrd, maxOrd, defaultLabel, tableLabels);

        for (int i = 0; i < caseNames.length; i++) {
            mv.visitLabel(caseLabels[i]);
            for (FlowNode action : cases.get(caseNames[i])) {
                action.handler().emit(action, mv, ctx);
            }
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        }

        mv.visitLabel(defaultLabel);
        mv.visitLabel(endLabel);
    }

    private void emitCascadeIfElseSwitch(MethodVisitor mv, int slot, IRType type,
                                         ImmutableMap<String, ImmutableList<FlowNode>> cases,
                                         CompilationContext ctx) {
        Label endLabel = new Label();
        Label defaultLabel = new Label(); // 如果没有写 default 则指向 end

        String[] caseNames = cases.keySet().toArray(new String[0]);
        Label[] caseBlockLabels = new Label[cases.size()];
        for (int i = 0; i < cases.size(); i++) {
            caseBlockLabels[i] = new Label();
        }

        for (int i = 0; i < caseNames.length; i++) {
            String key = caseNames[i];
            Label nextCheckLabel = (i == caseNames.length - 1) ? defaultLabel : new Label();

            // 将 YAML 键强制重解析匹配实际目标的常量比对
            if (type == IRType.INT || type == IRType.BOOLEAN) {
                int expected = type == IRType.BOOLEAN ? (Boolean.parseBoolean(key) ? 1 : 0) : Integer.parseInt(key);
                mv.visitVarInsn(Opcodes.ILOAD, slot);
                ASMUtils.emitIntConst(mv, expected);
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, nextCheckLabel);
            } else if (type == IRType.LONG) {
                long expected = Long.parseLong(key);
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                mv.visitLdcInsn(expected);
                mv.visitInsn(Opcodes.LCMP);
                mv.visitJumpInsn(Opcodes.IFNE, nextCheckLabel);
            } else if (type == IRType.DOUBLE) {
                double expected = Double.parseDouble(key);
                mv.visitVarInsn(Opcodes.DLOAD, slot);
                ASMUtils.emitDoubleConst(mv, expected);
                mv.visitInsn(Opcodes.DCMPG); // 使用统一比对
                mv.visitJumpInsn(Opcodes.IFNE, nextCheckLabel);
            } else {
                // FALLBACK TO OBJECT .equals() 检测
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                mv.visitJumpInsn(Opcodes.IFNULL, nextCheckLabel);
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                if (type.base() == BaseType.ENUM) {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Enum");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Enum", "name",
                            "()Ljava/lang/String;", false);
                } else {
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString",
                            "()Ljava/lang/String;", false);
                }
                mv.visitLdcInsn(key);
                ASMUtils.emitEquals(mv);
                mv.visitJumpInsn(Opcodes.IFEQ, nextCheckLabel);
            }

            // 匹配成功，跳转执行区块
            mv.visitLabel(caseBlockLabels[i]);
            for (FlowNode action : cases.get(key)) {
                action.handler().emit(action, mv, ctx);
            }
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);

            // 放置下一个条件的锚点
            if (i < caseNames.length - 1) {
                mv.visitLabel(nextCheckLabel);
            }
        }

        mv.visitLabel(defaultLabel);
        mv.visitLabel(endLabel);
    }

    @Override
    public EnumSet<NodeCapability> capabilities() {
        return EnumSet.noneOf(NodeCapability.class);
    }

    @Override
    public Iterable<FlowNode> traverseChildren(FlowNode node) {
        ArrayList<FlowNode> children = new ArrayList<>();
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        if (conditionAction != null) {
            children.add(conditionAction);
        }
        ImmutableMap<String, ImmutableList<FlowNode>> cases = node.getAttrOrDefault("cases", null);
        if (cases != null) {
            for (ImmutableList<FlowNode> actionNodes : cases.values()) {
                children.addAll(actionNodes);
            }
        }
        return children;
    }

    @Override
    public FlowNode reorderBranches(FlowNode node, CompilationContext ctx) {
        String variable = node.getAttrOrDefault("variable", null);
        ImmutableMap<String, ImmutableList<FlowNode>> cases = node.getAttrOrDefault("cases", null);
        double[] weights = ctx.getBranchWeights(variable);

        if (weights != null && weights.length == cases.size()) {
            List<String> keys = new ArrayList<>(cases.keySet());
            java.util.Map<String, Integer> indexMap = new java.util.HashMap<>(keys.size());
            for (int i = 0; i < keys.size(); i++) {
                indexMap.put(keys.get(i), i);
            }

            keys.sort(java.util.Comparator.comparingDouble(k -> -weights[indexMap.get(k)]));

            ImmutableMap.Builder<String, ImmutableList<FlowNode>> sorted = ImmutableMap.builder();
            for (String key : keys) {
                sorted.put(key, cases.get(key));
            }
            return node.withAttr("cases", sorted.build());
        }
        return node;
    }
}

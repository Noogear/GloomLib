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
import gloomlib.script.core.parser.ScriptParser;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * COLLECT 节点处理器——集合谓词操作。
 * <p>
 * 对 COLLECTION 类型变量进行迭代，使用 {@code match} 子条件对每个元素执行判定，
 * 根据 {@code op} 聚合结果。
 * <p>
 * 操作分为三类行为模式：
 * <ul>
 *   <li><b>量词（Quantifier）</b>——短路检查，控制流走向：
 *     <ul>
 *       <li>{@code exists} / {@code !exists} — ∃ 量词：任一匹配即短路</li>
 *       <li>{@code all} / {@code !all} — ∀ 量词：任一不匹配即短路</li>
 *     </ul>
 *   </li>
 *   <li><b>首匹配（First-match）</b>——找到第一个匹配后短路退出：
 *     <ul>
 *       <li>{@code index} — 返回首个匹配元素的索引（int, -1 表示未找到）</li>
 *       <li>{@code find} — 返回首个匹配元素本身（Object, null 表示未找到）</li>
 *     </ul>
 *   </li>
 *   <li><b>全扫描（Full-scan）</b>——遍历全部元素，累积结果：
 *     <ul>
 *       <li>{@code count} — 统计匹配元素数量（int）</li>
 *       <li>{@code filter} — 收集匹配元素为新 List（COLLECTION）</li>
 *     </ul>
 *   </li>
 * </ul>
 * <p>
 * match 子条件复用 {@link CheckOp} + {@link CheckOpEmitters} 的全部操作符体系，零重复代码。
 * 字节码等价于手写 Java 的 Iterator 循环 + 条件短路。
 */
@SuppressWarnings("null")
public final class CollectNodeHandler
        implements ScriptIR.FlowNodeHandler, ScriptIR.NodeMutator, ScriptIR.VariableConsumer {

    static {
        FlowNodeType.registerHandler(FlowNodeType.COLLECT, CollectNodeHandler::new);
    }

    public static void init() {
    }

    /**
     * 集合操作类型。
     */
    public enum CollectOp {
        EXISTS, ALL, COUNT, INDEX, FIND, FILTER;

        /**
         * 解析操作字符串，支持 {@code !exists} / {@code !all} 取反。
         *
         * @return [CollectOp, negate]
         */
        public static Resolved resolve(String raw) {
            if (raw == null) {
                throw gloomlib.script.api.ScriptCompileException.parse(
                        "COLLECT node requires an 'op' field.");
            }
            String stripped = raw.strip();
            boolean negate = false;
            if (stripped.startsWith("!")) {
                negate = true;
                stripped = stripped.substring(1).strip();
            }
            return switch (stripped.toLowerCase()) {
                case "exists" -> new Resolved(EXISTS, negate);
                case "all" -> new Resolved(ALL, negate);
                case "count" -> {
                    if (negate) throw gloomlib.script.api.ScriptCompileException.parse(
                            "COLLECT op 'count' does not support '!' negation.");
                    yield new Resolved(COUNT, false);
                }
                case "index" -> {
                    if (negate) throw gloomlib.script.api.ScriptCompileException.parse(
                            "COLLECT op 'index' does not support '!' negation.");
                    yield new Resolved(INDEX, false);
                }
                case "find" -> {
                    if (negate) throw gloomlib.script.api.ScriptCompileException.parse(
                            "COLLECT op 'find' does not support '!' negation.");
                    yield new Resolved(FIND, false);
                }
                case "filter" -> {
                    if (negate) throw gloomlib.script.api.ScriptCompileException.parse(
                            "COLLECT op 'filter' does not support '!' negation.");
                    yield new Resolved(FILTER, false);
                }
                default -> throw gloomlib.script.api.ScriptCompileException.parse(
                        "Unknown COLLECT op: '" + raw
                                + "'. Valid ops: exists, !exists, all, !all, count, index, find, filter.");
            };
        }

        public record Resolved(CollectOp op, boolean negate) {
        }
    }

    /**
     * 集合迭代策略——根据集合类型的运行时特征选择最优遍历方式。
     */
    enum CollectionKind {
        /** 标准 java.util.Collection（List, Set, Queue 等）—— 使用 Iterator 遍历 */
        ITERABLE,
        /** Java 数组（T[]）—— 使用索引循环，天然 RandomAccess */
        ARRAY,
        /** java.util.Map —— 先提取 values()/entrySet()，再按 ITERABLE 遍历 */
        MAP
    }


    @Override
    @SuppressWarnings("unchecked")
    public FlowNode parse(ParseContext ctx) {
        // variable：集合变量名
        String variable = ctx.get("variable");
        if (variable == null) {
            throw ctx.error("COLLECT node requires a 'variable' (collection variable name).");
        }

        // op：操作类型
        String rawOp = ctx.get("op");
        CollectOp.Resolved resolved = CollectOp.resolve(rawOp);

        // match：子条件列表
        List<?> matchRaw = ctx.get("match");
        if (matchRaw == null || matchRaw.isEmpty()) {
            throw ctx.error("COLLECT node requires a non-empty 'match' list of conditions.");
        }

        ImmutableList.Builder<FlowNode> matchConditions = ImmutableList.builder();
        List<FlowNode> condList = new ArrayList<>();
        for (Object item : matchRaw) {
            if (item instanceof Map<?, ?> rawMap) {
                condList.add(parseMatchCondition(ctx, (Map<String, Object>) rawMap));
            } else {
                throw ctx.error("Invalid match condition in COLLECT node: " + item);
            }
        }
        // 子条件重排：按操作符开销升序排列，廉价检查优先以最大化短路概率
        condList.sort(java.util.Comparator.comparingInt(CollectNodeHandler::matchConditionCost));
        matchConditions.addAll(condList);

        // store：结果存储变量名
        String store = ctx.get("store");
        boolean needsStore = switch (resolved.op()) {
            case COUNT, INDEX, FIND, FILTER -> true;
            default -> false;
        };
        if (needsStore && store == null) {
            throw ctx.error("COLLECT op '" + resolved.op().name().toLowerCase()
                    + "' requires a 'store' field to save the result.");
        }

        ImmutableMap.Builder<String, Object> attrs = ImmutableMap.builder();
        attrs.put("variable", variable);
        attrs.put("collectOp", resolved.op().name());
        attrs.put("collectNegate", resolved.negate());
        attrs.put("matchConditions", matchConditions.build());
        if (store != null) {
            IRType returnType = switch (resolved.op()) {
                case FIND -> IRType.OBJECT;
                case FILTER -> IRType.COLLECTION;
                default -> IRType.INT; // count, index
            };
            attrs.put("store", store);
            attrs.put("returnType", returnType);
        }

        // on_fail（量词类 op 可用）
        List<?> onFailRaw = ctx.get("on_fail");
        if (onFailRaw != null) {
            attrs.put("onFailNodes", ScriptParser.parseFlow(onFailRaw));
        }

        return new FlowNode(FlowNodeType.COLLECT, attrs.build());
    }

    /**
     * 解析单个 match 子条件为轻量 CHECK FlowNode。
     * <p>
     * 复用 {@link CheckOp#resolve} 进行操作符验证和规范化，
     * 复用 {@link ScriptParser.ValueParser} 进行值类型推导。
     */
    private FlowNode parseMatchCondition(ParseContext parentCtx, Map<String, Object> matchAttrs) {
        String op = (String) matchAttrs.get("op");
        if (op == null) {
            throw parentCtx.error("Match condition requires an 'op' field.");
        }
        // 验证并规范化操作符
        CheckOp.Resolved resolved = CheckOp.resolve(op);
        op = resolved.toSymbol();

        Object value = matchAttrs.get("value");
        ImmutableMap.Builder<String, Object> attrs = ImmutableMap.builder();
        attrs.put("op", op);

        double numericValue = 0.0;
        if (value != null) {
            if (value instanceof String s) {
                value = ScriptParser.ValueParser.parseNumber(s);
            }
            attrs.put("value", value);
            attrs.put("valueType", ScriptParser.ValueParser.inferType(value));
            if (value instanceof Number n) {
                numericValue = n.doubleValue();
            }
            if (value instanceof List<?> list) {
                attrs.put("valueList", ImmutableList.copyOf(list));
            }
        }

        return new FlowNode(FlowNodeType.CHECK, attrs.build(), numericValue, 0);
    }


    @Override
    public void emit(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        CollectOp collectOp = CollectOp.valueOf(node.<String>getRequiredAttr("collectOp"));
        boolean negate = node.<Boolean>getAttrOrDefault("collectNegate", false);
        ImmutableList<FlowNode> matchConditions = node.getRequiredAttr("matchConditions");

        int collectionSlot;
        IRType collectionType;
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        if (conditionAction != null) {
            String sinkingProp = conditionAction.getAttrOrDefault("_sinking_property", null);
            collectionSlot = ctx.nextSlot();
            if (sinkingProp != null) {
                BytecodeCompiler.emitSunkPropertyLoad(mv, ctx, sinkingProp);
            } else {
                conditionAction.type().handler().emit(conditionAction, mv, ctx);
            }
            mv.visitVarInsn(Opcodes.ASTORE, collectionSlot);
            collectionType = conditionAction.getAttrOrDefault("returnType", IRType.COLLECTION);
        } else {
            String variable = node.getRequiredAttr("variable");
            collectionSlot = ctx.getSlot(variable);
            collectionType = ctx.getType(variable);
        }

        // 检测集合迭代策略
        CollectionKind kind = detectKind(collectionType);

        // 解析元素类型（在 MAP 转换前完成，因为转换后 kind 变为 ITERABLE）
        IRType elementType = resolveElementType(collectionType, kind);

        // MAP → 提取 values() 转为 ITERABLE（使用独立临时槽位，避免覆盖命名变量）
        if (kind == CollectionKind.MAP) {
            int valuesSlot = Math.max(ctx.nextSlot(), collectionSlot + 1);
            mv.visitVarInsn(Opcodes.ALOAD, collectionSlot);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "values",
                    "()Ljava/util/Collection;", true);
            mv.visitVarInsn(Opcodes.ASTORE, valuesSlot);
            collectionSlot = valuesSlot;
            kind = CollectionKind.ITERABLE;
        }

        // 预提取数组组件类型（仅 ARRAY 路径需要，ITERABLE/MAP 为 null）
        Class<?> componentType = kind == CollectionKind.ARRAY
                ? collectionType.getToken().getRawType().getComponentType()
                : null;

        switch (collectOp) {
            case EXISTS -> emitQuantifier(node, mv, ctx, collectionSlot, elementType,
                    kind, matchConditions, negate, componentType, true);
            case ALL -> emitQuantifier(node, mv, ctx, collectionSlot, elementType,
                    kind, matchConditions, negate, componentType, false);
            case COUNT -> emitFullScan(node, mv, ctx, collectionSlot, elementType,
                    kind, matchConditions, componentType, false);
            case FILTER -> emitFullScan(node, mv, ctx, collectionSlot, elementType,
                    kind, matchConditions, componentType, true);
            case INDEX -> emitFirstMatch(node, mv, ctx, collectionSlot, elementType,
                    kind, matchConditions, componentType, false);
            case FIND -> emitFirstMatch(node, mv, ctx, collectionSlot, elementType,
                    kind, matchConditions, componentType, true);
        }
    }

    // ======================== 量词（exists / all）========================

    /**
     * 发射 exists / all 量词的字节码。
     * <p>
     * {@code exists}（isExists=true）：∃ 语义——匹配成功时短路跳 pass，遍历完无匹配跳 fail。
     * {@code all}（isExists=false）：∀ 语义——匹配失败时短路跳 fail，遍历完全通过跳 pass。
     *
     * @param negate    取反标志（{@code !exists} / {@code !all}），交换 pass/fail 最终指向
     * @param isExists  {@code true} = exists 模式, {@code false} = all 模式
     */
    private void emitQuantifier(FlowNode node, MethodVisitor mv, CompilationContext ctx,
                                int collectionSlot, IRType elementType,
                                CollectionKind kind,
                                ImmutableList<FlowNode> matchConditions, boolean negate,
                                Class<?> componentType, boolean isExists) {
        Label failLabel = new Label();
        Label passLabel = new Label();
        Label loopLabel = new Label();

        // all 空集合 → 空真（pass），exists 空集合 → fail
        Label emptyTarget = isExists ? (negate ? passLabel : failLabel)
                : (negate ? failLabel : passLabel);
        // all 遍历完毕 → 全通过（pass），exists 遍历完毕 → 无匹配（fail）
        Label exhaustedTarget = isExists ? (negate ? passLabel : failLabel)
                : (negate ? failLabel : passLabel);

        int baseTemp = Math.max(ctx.nextSlot(), collectionSlot + 1);
        boolean needsUnbox = elementType.isPrimitive() && hasAnyNumericOp(matchConditions);

        if (kind == CollectionKind.ARRAY) {
            int lengthSlot = baseTemp;
            int indexSlot = baseTemp + 1;
            int elementSlot = baseTemp + 2;
            int primitiveSlot = needsUnbox ? baseTemp + 3 : -1;

            // length = array.length; if 0 → empty target
            mv.visitVarInsn(Opcodes.ALOAD, collectionSlot);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ISTORE, lengthSlot);
            mv.visitJumpInsn(Opcodes.IFEQ, emptyTarget);

            ASMUtils.emitIntConst(mv, 0);
            mv.visitVarInsn(Opcodes.ISTORE, indexSlot);

            mv.visitLabel(loopLabel);
            mv.visitVarInsn(Opcodes.ILOAD, indexSlot);
            mv.visitVarInsn(Opcodes.ILOAD, lengthSlot);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, exhaustedTarget);

            emitExtractElement(mv, kind, collectionSlot, indexSlot, elementSlot,
                    elementType, componentType);
            if (needsUnbox) {
                emitUnboxToSlot(mv, elementSlot, primitiveSlot, elementType);
            }

            if (isExists) {
                // exists: 子条件不满足 → nextLabel → 继续循环；全通过 → 短路 pass
                Label nextLabel = new Label();
                emitMatchConditions(mv, matchConditions, elementSlot, elementType,
                        primitiveSlot, nextLabel, ctx);
                mv.visitJumpInsn(Opcodes.GOTO, negate ? failLabel : passLabel);
                mv.visitLabel(nextLabel);
            } else {
                // all: 子条件不满足 → 短路 fail；全通过 → 继续循环
                Label matchFailLabel = new Label();
                emitMatchConditions(mv, matchConditions, elementSlot, elementType,
                        primitiveSlot, matchFailLabel, ctx);
                // 全通过 → i++, 继续循环
                mv.visitIincInsn(indexSlot, 1);
                mv.visitJumpInsn(Opcodes.GOTO, loopLabel);
                // 不满足 → 短路跳到失败
                mv.visitLabel(matchFailLabel);
                mv.visitJumpInsn(Opcodes.GOTO, negate ? passLabel : failLabel);
            }

            mv.visitIincInsn(indexSlot, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopLabel);

        } else { // ITERABLE
            int iterSlot = baseTemp;
            int elementSlot = baseTemp + 1;
            int primitiveSlot = needsUnbox ? baseTemp + 2 : -1;

            mv.visitVarInsn(Opcodes.ALOAD, collectionSlot);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "isEmpty",
                    "()Z", true);
            mv.visitJumpInsn(Opcodes.IFNE, emptyTarget);

            mv.visitVarInsn(Opcodes.ALOAD, collectionSlot);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "iterator",
                    "()Ljava/util/Iterator;", true);
            mv.visitVarInsn(Opcodes.ASTORE, iterSlot);

            mv.visitLabel(loopLabel);
            mv.visitVarInsn(Opcodes.ALOAD, iterSlot);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext",
                    "()Z", true);
            mv.visitJumpInsn(Opcodes.IFEQ, exhaustedTarget);

            emitExtractElement(mv, kind, iterSlot, -1, elementSlot,
                    elementType, null);
            if (needsUnbox) {
                emitUnboxToSlot(mv, elementSlot, primitiveSlot, elementType);
            }

            if (isExists) {
                // exists: 不满足 → 跳回循环头；全通过 → 短路 pass
                emitMatchConditions(mv, matchConditions, elementSlot, elementType,
                        primitiveSlot, loopLabel, ctx);
                mv.visitJumpInsn(Opcodes.GOTO, negate ? failLabel : passLabel);
            } else {
                // all: 不满足 → 短路 fail；全通过 → 继续循环
                Label matchFailLabel = new Label();
                emitMatchConditions(mv, matchConditions, elementSlot, elementType,
                        primitiveSlot, matchFailLabel, ctx);
                mv.visitJumpInsn(Opcodes.GOTO, loopLabel);
                mv.visitLabel(matchFailLabel);
                mv.visitJumpInsn(Opcodes.GOTO, negate ? passLabel : failLabel);
            }
        }

        // 失败路径
        mv.visitLabel(failLabel);
        ASMUtils.emitOnFail(node, mv, ctx);
        ASMUtils.emitEarlyReturn(mv, ctx);

        mv.visitLabel(passLabel);
    }

    // ======================== 首匹配（index / find）========================

    /**
     * 发射 index / find 首匹配的字节码。
     * <p>
     * 遍历集合，找到第一个满足全部 match 的元素后短路退出，存储结果到 store 变量。
     *
     * @param returnElement {@code true} = find（存元素引用, null 为未找到），
     *                      {@code false} = index（存 int 索引, -1 为未找到）
     */
    private void emitFirstMatch(FlowNode node, MethodVisitor mv, CompilationContext ctx,
                                int collectionSlot, IRType elementType,
                                CollectionKind kind,
                                ImmutableList<FlowNode> matchConditions,
                                Class<?> componentType, boolean returnElement) {
        String store = node.getRequiredAttr("store");
        Label loopLabel = new Label();
        Label nextLabel = new Label();
        Label doneLabel = new Label();

        int baseTemp = Math.max(ctx.nextSlot(), collectionSlot + 1);
        boolean needsUnbox = elementType.isPrimitive() && hasAnyNumericOp(matchConditions);

        int resultSlot;

        if (kind == CollectionKind.ARRAY) {
            int lengthSlot = baseTemp;
            int indexSlot = baseTemp + 1;
            int elementSlot = baseTemp + 2;
            resultSlot = baseTemp + 3;
            int primitiveSlot = needsUnbox ? baseTemp + 4 : -1;

            // 初始化 result
            if (returnElement) {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitVarInsn(Opcodes.ASTORE, resultSlot);
            } else {
                ASMUtils.emitIntConst(mv, -1);
                mv.visitVarInsn(Opcodes.ISTORE, resultSlot);
            }

            mv.visitVarInsn(Opcodes.ALOAD, collectionSlot);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitVarInsn(Opcodes.ISTORE, lengthSlot);

            ASMUtils.emitIntConst(mv, 0);
            mv.visitVarInsn(Opcodes.ISTORE, indexSlot);

            mv.visitLabel(loopLabel);
            mv.visitVarInsn(Opcodes.ILOAD, indexSlot);
            mv.visitVarInsn(Opcodes.ILOAD, lengthSlot);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, doneLabel);

            emitExtractElement(mv, kind, collectionSlot, indexSlot, elementSlot,
                    elementType, componentType);
            if (needsUnbox) {
                emitUnboxToSlot(mv, elementSlot, primitiveSlot, elementType);
            }

            emitMatchConditions(mv, matchConditions, elementSlot, elementType,
                    primitiveSlot, nextLabel, ctx);

            // 全部通过 → 存结果, 短路退出
            if (returnElement) {
                mv.visitVarInsn(Opcodes.ALOAD, elementSlot);
                mv.visitVarInsn(Opcodes.ASTORE, resultSlot);
            } else {
                mv.visitVarInsn(Opcodes.ILOAD, indexSlot);
                mv.visitVarInsn(Opcodes.ISTORE, resultSlot);
            }
            mv.visitJumpInsn(Opcodes.GOTO, doneLabel);

            mv.visitLabel(nextLabel);
            mv.visitIincInsn(indexSlot, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopLabel);

        } else { // ITERABLE
            int iterSlot = baseTemp;
            int elementSlot = baseTemp + 1;
            resultSlot = baseTemp + 2;
            int idxSlot = returnElement ? -1 : baseTemp + 3;
            int primitiveSlot = needsUnbox ? (returnElement ? baseTemp + 3 : baseTemp + 4) : -1;

            // 初始化 result
            if (returnElement) {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitVarInsn(Opcodes.ASTORE, resultSlot);
            } else {
                ASMUtils.emitIntConst(mv, -1);
                mv.visitVarInsn(Opcodes.ISTORE, resultSlot);
                ASMUtils.emitIntConst(mv, 0);
                mv.visitVarInsn(Opcodes.ISTORE, idxSlot);
            }

            mv.visitVarInsn(Opcodes.ALOAD, collectionSlot);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "iterator",
                    "()Ljava/util/Iterator;", true);
            mv.visitVarInsn(Opcodes.ASTORE, iterSlot);

            mv.visitLabel(loopLabel);
            mv.visitVarInsn(Opcodes.ALOAD, iterSlot);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext",
                    "()Z", true);
            mv.visitJumpInsn(Opcodes.IFEQ, doneLabel);

            emitExtractElement(mv, kind, iterSlot, -1, elementSlot,
                    elementType, null);
            if (needsUnbox) {
                emitUnboxToSlot(mv, elementSlot, primitiveSlot, elementType);
            }

            emitMatchConditions(mv, matchConditions, elementSlot, elementType,
                    primitiveSlot, nextLabel, ctx);

            // 全部通过 → 存结果, 短路退出
            if (returnElement) {
                mv.visitVarInsn(Opcodes.ALOAD, elementSlot);
                mv.visitVarInsn(Opcodes.ASTORE, resultSlot);
            } else {
                mv.visitVarInsn(Opcodes.ILOAD, idxSlot);
                mv.visitVarInsn(Opcodes.ISTORE, resultSlot);
            }
            mv.visitJumpInsn(Opcodes.GOTO, doneLabel);

            mv.visitLabel(nextLabel);
            if (!returnElement) {
                mv.visitIincInsn(idxSlot, 1);
            }
            mv.visitJumpInsn(Opcodes.GOTO, loopLabel);
        }

        // 循环结束 → store
        mv.visitLabel(doneLabel);
        int storeSlot = ctx.getSlot(store);
        if (returnElement) {
            mv.visitVarInsn(Opcodes.ALOAD, resultSlot);
            mv.visitVarInsn(Opcodes.ASTORE, storeSlot);
        } else {
            mv.visitVarInsn(Opcodes.ILOAD, resultSlot);
            mv.visitVarInsn(Opcodes.ISTORE, storeSlot);
        }
    }

    // ======================== 全扫描（count / filter）========================

    /**
     * 发射 count / filter 全扫描的字节码。
     * <p>
     * 遍历全部元素，对每个匹配的元素执行累积操作。
     *
     * @param collectToList {@code true} = filter（累积到 ArrayList），
     *                      {@code false} = count（iinc 计数器）
     */
    private void emitFullScan(FlowNode node, MethodVisitor mv, CompilationContext ctx,
                              int collectionSlot, IRType elementType,
                              CollectionKind kind,
                              ImmutableList<FlowNode> matchConditions,
                              Class<?> componentType, boolean collectToList) {
        String store = node.getRequiredAttr("store");
        Label loopLabel = new Label();
        Label nextLabel = new Label();
        Label doneLabel = new Label();

        int baseTemp = Math.max(ctx.nextSlot(), collectionSlot + 1);
        boolean needsUnbox = elementType.isPrimitive() && hasAnyNumericOp(matchConditions);

        int accSlot;

        if (kind == CollectionKind.ARRAY) {
            int lengthSlot = baseTemp;
            int indexSlot = baseTemp + 1;
            int elementSlot = baseTemp + 2;
            accSlot = baseTemp + 3;
            int primitiveSlot = needsUnbox ? baseTemp + 4 : -1;

            // 初始化累积器
            if (collectToList) {
                mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>",
                        "()V", false);
                mv.visitVarInsn(Opcodes.ASTORE, accSlot);
            } else {
                ASMUtils.emitIntConst(mv, 0);
                mv.visitVarInsn(Opcodes.ISTORE, accSlot);
            }

            mv.visitVarInsn(Opcodes.ALOAD, collectionSlot);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitVarInsn(Opcodes.ISTORE, lengthSlot);

            ASMUtils.emitIntConst(mv, 0);
            mv.visitVarInsn(Opcodes.ISTORE, indexSlot);

            mv.visitLabel(loopLabel);
            mv.visitVarInsn(Opcodes.ILOAD, indexSlot);
            mv.visitVarInsn(Opcodes.ILOAD, lengthSlot);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, doneLabel);

            emitExtractElement(mv, kind, collectionSlot, indexSlot, elementSlot,
                    elementType, componentType);
            if (needsUnbox) {
                emitUnboxToSlot(mv, elementSlot, primitiveSlot, elementType);
            }

            emitMatchConditions(mv, matchConditions, elementSlot, elementType,
                    primitiveSlot, nextLabel, ctx);

            // 全部通过 → 累积
            if (collectToList) {
                mv.visitVarInsn(Opcodes.ALOAD, accSlot);
                mv.visitVarInsn(Opcodes.ALOAD, elementSlot);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add",
                        "(Ljava/lang/Object;)Z", true);
                mv.visitInsn(Opcodes.POP); // 丢弃 boolean 返回值
            } else {
                mv.visitIincInsn(accSlot, 1);
            }

            mv.visitLabel(nextLabel);
            mv.visitIincInsn(indexSlot, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopLabel);

        } else { // ITERABLE
            int iterSlot = baseTemp;
            int elementSlot = baseTemp + 1;
            accSlot = baseTemp + 2;
            int primitiveSlot = needsUnbox ? baseTemp + 3 : -1;

            // 初始化累积器
            if (collectToList) {
                mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>",
                        "()V", false);
                mv.visitVarInsn(Opcodes.ASTORE, accSlot);
            } else {
                ASMUtils.emitIntConst(mv, 0);
                mv.visitVarInsn(Opcodes.ISTORE, accSlot);
            }

            mv.visitVarInsn(Opcodes.ALOAD, collectionSlot);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "iterator",
                    "()Ljava/util/Iterator;", true);
            mv.visitVarInsn(Opcodes.ASTORE, iterSlot);

            mv.visitLabel(loopLabel);
            mv.visitVarInsn(Opcodes.ALOAD, iterSlot);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext",
                    "()Z", true);
            mv.visitJumpInsn(Opcodes.IFEQ, doneLabel);

            emitExtractElement(mv, kind, iterSlot, -1, elementSlot,
                    elementType, null);
            if (needsUnbox) {
                emitUnboxToSlot(mv, elementSlot, primitiveSlot, elementType);
            }

            emitMatchConditions(mv, matchConditions, elementSlot, elementType,
                    primitiveSlot, nextLabel, ctx);

            // 全部通过 → 累积
            if (collectToList) {
                mv.visitVarInsn(Opcodes.ALOAD, accSlot);
                mv.visitVarInsn(Opcodes.ALOAD, elementSlot);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add",
                        "(Ljava/lang/Object;)Z", true);
                mv.visitInsn(Opcodes.POP);
            } else {
                mv.visitIincInsn(accSlot, 1);
            }

            mv.visitLabel(nextLabel);
            mv.visitJumpInsn(Opcodes.GOTO, loopLabel);
        }

        // 循环结束 → store
        mv.visitLabel(doneLabel);
        int storeSlot = ctx.getSlot(store);
        if (collectToList) {
            mv.visitVarInsn(Opcodes.ALOAD, accSlot);
            mv.visitVarInsn(Opcodes.ASTORE, storeSlot);
        } else {
            mv.visitVarInsn(Opcodes.ILOAD, accSlot);
            mv.visitVarInsn(Opcodes.ISTORE, storeSlot);
        }
    }


    // ======================== 集合类型检测 ========================

    /**
     * 根据 IRType 的 TypeToken 推断最优迭代策略。
     */
    private static CollectionKind detectKind(IRType type) {
        Class<?> raw = type.getToken().getRawType();
        if (raw.isArray()) return CollectionKind.ARRAY;
        if (java.util.Map.class.isAssignableFrom(raw)) return CollectionKind.MAP;
        return CollectionKind.ITERABLE;
    }

    /**
     * 根据集合类型和迭代策略解析元素类型。
     * <ul>
     *   <li>ARRAY → 取数组组件类型（{@code String[]} → STRING, {@code int[]} → INT）</li>
     *   <li>MAP → 解析 {@code Map<K,V>} 的 V 类型参数</li>
     *   <li>ITERABLE → 委托 {@link IRType#elementType()}</li>
     * </ul>
     */
    private static IRType resolveElementType(IRType collectionType, CollectionKind kind) {
        if (kind == CollectionKind.MAP) {
            try {
                var vt = collectionType.getToken().resolveType(
                        java.util.Map.class.getTypeParameters()[1]);
                return (vt.getType() instanceof java.lang.reflect.TypeVariable<?>)
                        ? IRType.OBJECT : IRType.fromToken(vt);
            } catch (Exception e) {
                return IRType.OBJECT;
            }
        }
        // ARRAY 和 ITERABLE 均委托 IRType.elementType()（已统一处理数组/泛型集合）
        return collectionType.elementType();
    }

    // ======================== 子条件匹配（提取的公共方法） ========================

    /**
     * 发射全部 match 子条件的字节码。
     * <p>
     * 子条件之间为 ALL 语义：任一不满足 → 跳转到 {@code failLabel}。
     *
     * @param elementSlot   元素的引用槽位（始终 ASTORE 的装箱引用）
     * @param primitiveSlot 元素的基本类型槽位（仅当 elementType 为 primitive 且有数值操作时 ≥ 0）
     */
    private static void emitMatchConditions(MethodVisitor mv, ImmutableList<FlowNode> matchConditions,
                                            int elementSlot, IRType elementType, int primitiveSlot,
                                            Label failLabel, CompilationContext ctx) {
        for (FlowNode cond : matchConditions) {
            CheckOp.Resolved info = CheckOp.resolve(cond.getRequiredAttr("op"));

            int slot;
            IRType type;
            if (primitiveSlot >= 0 && needsPrimitiveSlot(info.op())) {
                // 数值/相等/范围操作 → 使用拆箱后的基本类型槽位
                slot = primitiveSlot;
                type = elementType;
            } else if (elementType.isPrimitive()) {
                // 引用操作（null/instanceof）作用于装箱引用
                slot = elementSlot;
                type = IRType.OBJECT;
            } else {
                // 非基本类型元素 → 直接使用引用槽位
                slot = elementSlot;
                type = elementType;
            }

            int jumpOp = CheckOpEmitters.forOp(info.op())
                    .emit(mv, info.op(), slot, type, cond, ctx);
            mv.visitJumpInsn(info.negate() ? jumpOp : ASMUtils.invertJump(jumpOp), failLabel);
        }
    }

    // ======================== 元素提取 ========================

    /**
     * 从 Collection/Array 中提取元素并存入 elementSlot（始终装箱引用 ASTORE）。
     *
     * @param sourceSlot1 ITERABLE → Iterator 槽位; ARRAY → 数组槽位
     * @param sourceSlot2 ARRAY → 索引槽位（ITERABLE 时忽略）
     */
    private static void emitExtractElement(MethodVisitor mv, CollectionKind kind,
                                           int sourceSlot1, int sourceSlot2,
                                           int elementSlot, IRType elementType,
                                           Class<?> arrayComponent) {
        if (kind == CollectionKind.ARRAY) {
            mv.visitVarInsn(Opcodes.ALOAD, sourceSlot1);   // 数组
            mv.visitVarInsn(Opcodes.ILOAD, sourceSlot2);   // 索引
            if (arrayComponent != null && arrayComponent.isPrimitive()) {
                mv.visitInsn(ASMUtils.arrayLoadOpcode(arrayComponent));
                if (arrayComponent == float.class) {
                    mv.visitInsn(Opcodes.F2D); // float → double（IRType 映射 float → DOUBLE）
                }
                ASMUtils.emitBox(mv, IRType.fromClass(arrayComponent));
            } else {
                mv.visitInsn(Opcodes.AALOAD);
                emitElementCast(mv, elementType);
            }
        } else { // ITERABLE
            mv.visitVarInsn(Opcodes.ALOAD, sourceSlot1);   // Iterator
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next",
                    "()Ljava/lang/Object;", true);
            emitElementCast(mv, elementType);
        }
        mv.visitVarInsn(Opcodes.ASTORE, elementSlot);      // 始终装箱引用
    }

    /**
     * 将装箱引用拆箱并存入基本类型槽位。
     */
    private static void emitUnboxToSlot(MethodVisitor mv, int refSlot, int primitiveSlot,
                                        IRType elementType) {
        mv.visitVarInsn(Opcodes.ALOAD, refSlot);
        ASMUtils.emitUnbox(mv, elementType);
        mv.visitVarInsn(ASMUtils.storeOpcode(elementType), primitiveSlot);
    }



    /**
     * 为已知元素类型发射 CHECKCAST（仅对非 OBJECT 类型）。
     */
    private static void emitElementCast(MethodVisitor mv, IRType elementType) {
        if (elementType != IRType.OBJECT) {
            Class<?> raw = elementType.getToken().getRawType();
            if (!raw.equals(Object.class)) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, org.objectweb.asm.Type.getInternalName(
                        com.google.common.primitives.Primitives.wrap(raw)));
            }
        }
    }

    // ======================== 操作符分类 ========================

    /**
     * 判断该 CheckOp 是否需要基本类型槽位（ILOAD/DLOAD/LLOAD）来执行。
     * <p>
     * 返回 {@code true} 的操作符在 CheckOpEmitters 中使用类型特定的加载指令，
     * 无法直接操作装箱引用。
     */
    private static boolean needsPrimitiveSlot(CheckOp op) {
        return switch (op) {
            case EQ, NEQ, GT, GTE, LT, LTE, IN, BETWEEN -> true;
            default -> false; // NULL, INSTANCEOF, CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES
        };
    }

    /**
     * 检查 matchConditions 中是否存在需要基本类型槽位的操作。
     */
    private static boolean hasAnyNumericOp(ImmutableList<FlowNode> matchConditions) {
        for (FlowNode cond : matchConditions) {
            CheckOp.Resolved info = CheckOp.resolve(cond.getRequiredAttr("op"));
            if (needsPrimitiveSlot(info.op())) return true;
        }
        return false;
    }

    /**
     * 子条件排序开销评估。
     * <p>
     * 值越小越廉价，排序后廉价检查优先执行以最大化短路概率。
     */
    private static int matchConditionCost(FlowNode cond) {
        CheckOp.Resolved info = CheckOp.resolve(cond.getRequiredAttr("op"));
        return switch (info.op()) {
            case NULL -> 0;         // 1 条比较指令
            case INSTANCEOF -> 1;   // 1 条 INSTANCEOF 指令
            case EQ, NEQ -> 2;      // 1 次 equals / IF_ICMPxx
            case GT, GTE, LT, LTE -> 3;
            case STARTS_WITH, ENDS_WITH -> 4;
            case CONTAINS -> 5;
            case IN -> 6;           // Set.contains 或展开比较
            case BETWEEN -> 7;      // 2 次比较
            case MATCHES -> 8;      // 正则匹配，最昂贵
        };
    }

    @Override
    public EnumSet<NodeCapability> capabilities() {
        return EnumSet.of(NodeCapability.SIDE_EFFECT);
    }

    @Override
    public Iterable<FlowNode> traverseChildren(FlowNode node) {
        ArrayList<FlowNode> children = new ArrayList<>();
        ImmutableList<FlowNode> matchConditions = node.getAttrOrDefault("matchConditions", null);
        if (matchConditions != null) {
            children.addAll(matchConditions);
        }
        ImmutableList<FlowNode> onFailNodes = node.getAttrOrDefault("onFailNodes", null);
        if (onFailNodes != null) {
            children.addAll(onFailNodes);
        }
        return children;
    }

    @Override
    public FlowNode mapChildren(FlowNode node, java.util.function.Function<FlowNode, FlowNode> mapper) {
        ImmutableList<FlowNode> matchConditions = node.getAttrOrDefault("matchConditions", null);
        ImmutableList<FlowNode> onFailNodes     = node.getAttrOrDefault("onFailNodes", null);
        FlowNode result = node;
        if (matchConditions != null) {
            ImmutableList<FlowNode> mapped = matchConditions.stream()
                    .map(mapper).collect(ImmutableList.toImmutableList());
            if (!mapped.equals(matchConditions)) {
                result = result.withAttr("matchConditions", mapped);
            }
        }
        if (onFailNodes != null) {
            ImmutableList<FlowNode> mapped = onFailNodes.stream()
                    .map(mapper).collect(ImmutableList.toImmutableList());
            if (!mapped.equals(onFailNodes)) {
                result = result.withAttr("onFailNodes", mapped);
            }
        }
        return result;
    }

    @Override
    public FlowNode filterChildren(FlowNode node, java.util.function.Predicate<FlowNode> keep,
                                   java.util.function.Function<FlowNode, FlowNode> mapper) {
        FlowNode result = node;
        result = ScriptIR.NodeMutator.filterAttr(result, "matchConditions", keep, mapper);
        result = ScriptIR.NodeMutator.filterAttr(result, "onFailNodes", keep, mapper);
        return result;
    }
}

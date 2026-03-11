package gloomlib.script.core.handler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.ParseContext;
import gloomlib.script.core.ScriptIR;
import gloomlib.script.core.ScriptIR.FlowNode;
import gloomlib.script.core.ScriptIR.FlowNodeType;
import gloomlib.script.core.ScriptIR.IRType;
import gloomlib.script.core.ScriptIR.NodeCapability;
import gloomlib.script.core.codegen.ASMUtils;
import gloomlib.script.core.parser.ScriptParser;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * COLLECT 节点处理器——集合谓词操作。
 * <p>
 * 对 COLLECTION 类型变量进行迭代，使用 {@code match} 内联谓词子脚本对每个元素执行判定，
 * 根据 {@code op} 聚合结果。
 * <p>
 * <b>match 内联谓词架构</b>：match 字段是一个完整的流节点列表（与脚本顶层 flow 格式相同），
 * 以当前迭代的集合元素作为"虚拟 payload"。任一节点的 early return 被转换为
 * GOTO failLabel（元素不匹配），全部通过则落入后续指令（元素匹配）。
 * 这使得 match 天然支持 CHECK、SWITCH、ANY/ALL 组合等全部流节点类型，复用整个脚本引擎的
 * 编译基础设施，零重复代码。
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
 */
@SuppressWarnings("null")
public final class CollectNodeHandler
        implements ScriptIR.FlowNodeHandler, ScriptIR.NodeMutator, ScriptIR.VariableConsumer,
        ScriptIR.BranchReorderer {

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
    public FlowNode parse(ParseContext ctx) {
        // variable：集合变量名
        String variable = ctx.get("variable");
        if (variable == null) {
            throw ctx.error("COLLECT node requires a 'variable' (collection variable name).");
        }

        // op：操作类型
        String rawOp = ctx.get("op");
        CollectOp.Resolved resolved = CollectOp.resolve(rawOp);

        // match：内联谓词子脚本（完整流节点列表）
        List<?> matchRaw = ctx.get("match");
        if (matchRaw == null || matchRaw.isEmpty()) {
            throw ctx.error("COLLECT node requires a non-empty 'match' list.");
        }
        ImmutableList<FlowNode> matchFlow = ScriptParser.parseFlow(matchRaw);
        validateMatchFlow(matchFlow, ctx);

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
        attrs.put("matchFlow", matchFlow);
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


    @Override
    public void emit(FlowNode node, MethodVisitor mv, CompilationContext ctx) {
        CollectOp collectOp = CollectOp.valueOf(node.<String>getRequiredAttr("collectOp"));
        boolean negate = node.<Boolean>getAttrOrDefault("collectNegate", false);
        ImmutableList<FlowNode> matchFlow = node.getRequiredAttr("matchFlow");

        int collectionSlot;
        IRType collectionType;
        FlowNode conditionAction = node.getAttrOrDefault("conditionAction", null);
        if (conditionAction != null) {
            String sinkingProp = conditionAction.getAttrOrDefault("_sinking_property", null);
            collectionSlot = ctx.nextSlot();
            if (sinkingProp != null) {
                gloomlib.script.core.codegen.BytecodeCompiler.emitSunkPropertyLoad(mv, ctx, sinkingProp);
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
        // 委托给 IRType.elementType()：COLLECTION/MAP/ARRAY 三路逻辑统一维护于 IRType
        IRType elementType = collectionType.elementType();

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
                    kind, matchFlow, negate, componentType, true);
            case ALL -> emitQuantifier(node, mv, ctx, collectionSlot, elementType,
                    kind, matchFlow, negate, componentType, false);
            case COUNT -> emitFullScan(node, mv, ctx, collectionSlot, elementType,
                    kind, matchFlow, componentType, false);
            case FILTER -> emitFullScan(node, mv, ctx, collectionSlot, elementType,
                    kind, matchFlow, componentType, true);
            case INDEX -> emitFirstMatch(node, mv, ctx, collectionSlot, elementType,
                    kind, matchFlow, componentType, false);
            case FIND -> emitFirstMatch(node, mv, ctx, collectionSlot, elementType,
                    kind, matchFlow, componentType, true);
        }
    }

    // ======================== 量词（exists / all）========================

    /**
     * 发射 exists / all 量词的字节码。
     * <p>
     * {@code exists}（isExists=true）：∃ 语义——匹配成功时短路跳 pass，遍历完无匹配跳 fail。
     * {@code all}（isExists=false）：∀ 语义——匹配失败时短路跳 fail，遍历完全通过跳 pass。
     */
    private void emitQuantifier(FlowNode node, MethodVisitor mv, CompilationContext ctx,
                                int collectionSlot, IRType elementType,
                                CollectionKind kind,
                                ImmutableList<FlowNode> matchFlow, boolean negate,
                                Class<?> componentType, boolean isExists) {
        Label failLabel = new Label();
        Label passLabel = new Label();
        Label loopLabel = new Label();

        Label emptyTarget = isExists ? (negate ? passLabel : failLabel)
                : (negate ? failLabel : passLabel);
        Label exhaustedTarget = isExists ? (negate ? passLabel : failLabel)
                : (negate ? failLabel : passLabel);

        int baseTemp = Math.max(ctx.nextSlot(), collectionSlot + 1);

        if (kind == CollectionKind.ARRAY) {
            int lengthSlot = baseTemp;
            int indexSlot = baseTemp + 1;
            int elementSlot = baseTemp + 2;

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

            if (isExists) {
                Label nextLabel = new Label();
                emitInlinePredicate(mv, ctx, matchFlow, elementSlot, elementType, nextLabel, baseTemp + 3);
                mv.visitJumpInsn(Opcodes.GOTO, negate ? failLabel : passLabel);
                mv.visitLabel(nextLabel);
            } else {
                Label matchFailLabel = new Label();
                emitInlinePredicate(mv, ctx, matchFlow, elementSlot, elementType, matchFailLabel, baseTemp + 3);
                mv.visitIincInsn(indexSlot, 1);
                mv.visitJumpInsn(Opcodes.GOTO, loopLabel);
                mv.visitLabel(matchFailLabel);
                mv.visitJumpInsn(Opcodes.GOTO, negate ? passLabel : failLabel);
            }

            mv.visitIincInsn(indexSlot, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopLabel);

        } else { // ITERABLE
            int iterSlot = baseTemp;
            int elementSlot = baseTemp + 1;

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

            if (isExists) {
                emitInlinePredicate(mv, ctx, matchFlow, elementSlot, elementType, loopLabel, baseTemp + 2);
                mv.visitJumpInsn(Opcodes.GOTO, negate ? failLabel : passLabel);
            } else {
                Label matchFailLabel = new Label();
                emitInlinePredicate(mv, ctx, matchFlow, elementSlot, elementType, matchFailLabel, baseTemp + 2);
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
     */
    private void emitFirstMatch(FlowNode node, MethodVisitor mv, CompilationContext ctx,
                                int collectionSlot, IRType elementType,
                                CollectionKind kind,
                                ImmutableList<FlowNode> matchFlow,
                                Class<?> componentType, boolean returnElement) {
        String store = node.getRequiredAttr("store");
        Label loopLabel = new Label();
        Label nextLabel = new Label();
        Label doneLabel = new Label();

        int baseTemp = Math.max(ctx.nextSlot(), collectionSlot + 1);
        int resultSlot;

        if (kind == CollectionKind.ARRAY) {
            int lengthSlot = baseTemp;
            int indexSlot = baseTemp + 1;
            int elementSlot = baseTemp + 2;
            resultSlot = baseTemp + 3;

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

            emitInlinePredicate(mv, ctx, matchFlow, elementSlot, elementType, nextLabel, baseTemp + 4);

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

            int predicateBase = returnElement ? baseTemp + 3 : baseTemp + 4;
            emitInlinePredicate(mv, ctx, matchFlow, elementSlot, elementType, nextLabel, predicateBase);

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
     */
    private void emitFullScan(FlowNode node, MethodVisitor mv, CompilationContext ctx,
                              int collectionSlot, IRType elementType,
                              CollectionKind kind,
                              ImmutableList<FlowNode> matchFlow,
                              Class<?> componentType, boolean collectToList) {
        String store = node.getRequiredAttr("store");
        Label loopLabel = new Label();
        Label nextLabel = new Label();
        Label doneLabel = new Label();

        int baseTemp = Math.max(ctx.nextSlot(), collectionSlot + 1);
        int accSlot;

        if (kind == CollectionKind.ARRAY) {
            int lengthSlot = baseTemp;
            int indexSlot = baseTemp + 1;
            int elementSlot = baseTemp + 2;
            accSlot = baseTemp + 3;

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

            emitInlinePredicate(mv, ctx, matchFlow, elementSlot, elementType, nextLabel, baseTemp + 4);

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
            mv.visitIincInsn(indexSlot, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopLabel);

        } else { // ITERABLE
            int iterSlot = baseTemp;
            int elementSlot = baseTemp + 1;
            accSlot = baseTemp + 2;

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

            emitInlinePredicate(mv, ctx, matchFlow, elementSlot, elementType, nextLabel, baseTemp + 3);

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


    // ======================== 内联谓词发射 ========================

    /**
     * 内联谓词发射：将 matchFlow 中的每个流节点以"谓词模式"发射到当前循环体中。
     * <p>
     * 任一节点的 early return → GOTO failLabel（元素不匹配），
     * 全部通过 → 顺序落入下一条指令（元素匹配）。
     * <p>
     * 变量生命周期：
     * <ol>
     *   <li>扫描 matchFlow 中所有 CHECK/SWITCH 引用的变量名</li>
     *   <li>对每个变量名，作为元素属性路径解析类型 & 分配临时槽位</li>
     *   <li>发射属性提取字节码（元素 → 属性值 → 槽位）</li>
     *   <li>注册为 {@link CompilationContext} 的动态变量</li>
     *   <li>设置 {@code predicateFailLabel}，发射全部 matchFlow 节点</li>
     *   <li>清除动态变量 & 恢复 predicateFailLabel</li>
     * </ol>
     *
     * @param matchFlow   内联谓词流节点列表
     * @param elementSlot 当前迭代元素的局部变量槽位（始终装箱引用 ASTORE）
     * @param elementType 元素的 IR 类型
     * @param failLabel   匹配失败时跳转的标签
     * @param nextAvail   可用的下一个临时槽位（用于属性变量分配）
     */
    private static void emitInlinePredicate(MethodVisitor mv, CompilationContext ctx,
                                            ImmutableList<FlowNode> matchFlow,
                                            int elementSlot, IRType elementType,
                                            Label failLabel, int nextAvail) {
        // 1. 发现 matchFlow 中引用的所有变量名
        Set<String> varNames = new LinkedHashSet<>();
        for (FlowNode node : matchFlow) {
            discoverVariables(node, varNames);
        }

        // 2. 注册 $it（元素本身）——根据类型决定是否需要拆箱
        if (elementType.isPrimitive()) {
            // 基本类型元素：拆箱到独立槽位供数值操作使用
            int unboxedSlot = nextAvail;
            nextAvail += (elementType == IRType.DOUBLE || elementType == IRType.LONG) ? 2 : 1;
            emitUnboxToSlot(mv, elementSlot, unboxedSlot, elementType);
            ctx.registerDynamicVar("$it", unboxedSlot, elementType);
        } else {
            ctx.registerDynamicVar("$it", elementSlot, elementType);
        }

        // 3. 为每个属性变量分配槽位并发射提取字节码
        for (String varName : varNames) {
            if ("$it".equals(varName)) continue;
            // 解析元素属性类型
            Class<?> elementClass = com.google.common.primitives.Primitives.wrap(
                    elementType.getToken().getRawType());
            IRType propType = ScriptParser.PropertyResolver.resolveType(elementClass, varName, null);
            // 发射属性提取：element → accessor chain → store
            List<gloomlib.script.core.parser.accessor.PropertyAccessor> accessors =
                    ScriptParser.PropertyResolver.resolveAccessors(
                            com.google.common.reflect.TypeToken.of(elementClass), varName);
            mv.visitVarInsn(Opcodes.ALOAD, elementSlot);
            for (var accessor : accessors) {
                accessor.emitLoad(mv);
            }
            // 根据属性类型决定存储方式
            int slot = nextAvail;
            nextAvail += (propType == IRType.DOUBLE || propType == IRType.LONG) ? 2 : 1;
            mv.visitVarInsn(ASMUtils.storeOpcode(propType), slot);
            ctx.registerDynamicVar(varName, slot, propType);
        }

        // 4. 设置谓词失败标签
        Label prevLabel = ctx.getPredicateFailLabel();
        ctx.setPredicateFailLabel(failLabel);

        // 5. 发射 match 流节点
        for (FlowNode node : matchFlow) {
            node.type().handler().emit(node, mv, ctx);
        }

        // 6. 恢复上下文
        ctx.setPredicateFailLabel(prevLabel);
        ctx.clearDynamicVars();
    }

    /**
     * 递归发现流节点树中引用的所有变量名。
     */
    private static void discoverVariables(FlowNode node, Set<String> varNames) {
        String var = node.getAttrOrDefault("variable", null);
        if (var != null) {
            varNames.add(var);
        }
        // 递归遍历子节点（复合条件 ANY/ALL 的子列表等）
        if (node.type().handler() instanceof ScriptIR.NodeTraverser traverser) {
            for (FlowNode child : traverser.traverseChildren(node)) {
                discoverVariables(child, varNames);
            }
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

    // ======================== 元素提取 ========================

    /**
     * 从 Collection/Array 中提取元素并存入 elementSlot（始终装箱引用 ASTORE）。
     */
    private static void emitExtractElement(MethodVisitor mv, CollectionKind kind,
                                           int sourceSlot1, int sourceSlot2,
                                           int elementSlot, IRType elementType,
                                           Class<?> arrayComponent) {
        if (kind == CollectionKind.ARRAY) {
            mv.visitVarInsn(Opcodes.ALOAD, sourceSlot1);
            mv.visitVarInsn(Opcodes.ILOAD, sourceSlot2);
            if (arrayComponent != null && arrayComponent.isPrimitive()) {
                mv.visitInsn(ASMUtils.arrayLoadOpcode(arrayComponent));
                if (arrayComponent == float.class) {
                    mv.visitInsn(Opcodes.F2D);
                }
                ASMUtils.emitBox(mv, IRType.fromClass(arrayComponent));
            } else {
                mv.visitInsn(Opcodes.AALOAD);
                emitElementCast(mv, elementType);
            }
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, sourceSlot1);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next",
                    "()Ljava/lang/Object;", true);
            emitElementCast(mv, elementType);
        }
        mv.visitVarInsn(Opcodes.ASTORE, elementSlot);
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

    /**
     * 验证 matchFlow 仅包含谓词安全的节点类型。
     * <p>
     * 谓词模式下安全的节点：CHECK（条件判断）、ANY/ALL（复合条件）。
     * 不安全的节点：RETURN（直接退出方法而非跳过元素）、ACTION（每元素副作用）、
     * MATH（变量存储语义冲突）、COLLECT（嵌套循环槽位冲突）、SWITCH（复杂分支语义不匹配）。
     */
    private static void validateMatchFlow(ImmutableList<FlowNode> matchFlow, ParseContext ctx) {
        for (FlowNode node : matchFlow) {
            validateMatchNode(node, ctx);
        }
    }

    /**
     * 递归验证单个节点及其子节点是否为谓词安全类型。
     * ANY/ALL 的子列表也必须满足约束——否则嵌套的 ACTION/RETURN 仍会导致语义错误。
     */
    private static void validateMatchNode(FlowNode node, ParseContext ctx) {
        switch (node.type()) {
            case CHECK -> {} // 谓词安全
            case ANY, ALL -> {
                // 递归验证复合条件的子节点
                if (node.type().handler() instanceof ScriptIR.NodeTraverser traverser) {
                    for (FlowNode child : traverser.traverseChildren(node)) {
                        validateMatchNode(child, ctx);
                    }
                }
            }
            case RETURN -> throw ctx.error(
                    "RETURN node is not allowed inside match — "
                            + "it would exit the entire script, not skip the element.");
            case ACTION -> throw ctx.error(
                    "ACTION node is not allowed inside match — "
                            + "it would execute side effects for every iterated element.");
            default -> throw ctx.error(
                    "Node type '" + node.type() + "' is not supported inside match. "
                            + "Only CHECK, ANY, and ALL nodes are allowed.");
        }
    }

    // ======================== matchFlow 分支重排 ========================

    @Override
    public FlowNode reorderBranches(FlowNode node, CompilationContext ctx) {
        ImmutableList<FlowNode> matchFlow = node.getAttrOrDefault("matchFlow", null);
        if (matchFlow == null || matchFlow.size() <= 1) return node;

        List<FlowNode> sorted = new ArrayList<>(matchFlow);
        sorted.sort(Comparator.comparingInt(CollectNodeHandler::matchNodeCost));

        ImmutableList<FlowNode> sortedFlow = ImmutableList.copyOf(sorted);
        if (sortedFlow.equals(matchFlow)) return node;
        return node.withAttr("matchFlow", sortedFlow);
    }

    /**
     * 估算 matchFlow 节点的比较成本，用于将低成本条件前移以实现更早的短路退出。
     * <p>
     * 成本因子：属性访问 (+10) > 复合条件 (100) > 正则 (10) > 字符串操作 (6) > 数值比较 (2) > null 检查 (1)
     */
    private static int matchNodeCost(FlowNode node) {
        if (node.type() == FlowNodeType.ANY || node.type() == FlowNodeType.ALL) {
            return 100;
        }
        String variable = node.getAttrOrDefault("variable", null);
        int propCost = "$it".equals(variable) ? 0 : 10;

        String opStr = node.getAttrOrDefault("op", "==");
        String rawOp = opStr.startsWith("!") ? opStr.substring(1) : opStr;
        int opCost = switch (rawOp.toLowerCase()) {
            case "null" -> 1;
            case "==", "!=" -> 2;
            case "<", "<=", ">", ">=" -> 2;
            case "between" -> 3;
            case "instanceof" -> 4;
            case "in" -> 5;
            case "contains", "starts_with", "ends_with" -> 6;
            case "matches" -> 10;
            default -> 5;
        };
        return propCost + opCost;
    }

    @Override
    public EnumSet<NodeCapability> capabilities() {
        return EnumSet.of(NodeCapability.SIDE_EFFECT);
    }

    @Override
    public Iterable<FlowNode> traverseChildren(FlowNode node) {
        ArrayList<FlowNode> children = new ArrayList<>();
        ImmutableList<FlowNode> matchFlow = node.getAttrOrDefault("matchFlow", null);
        if (matchFlow != null) {
            children.addAll(matchFlow);
        }
        ImmutableList<FlowNode> onFailNodes = node.getAttrOrDefault("onFailNodes", null);
        if (onFailNodes != null) {
            children.addAll(onFailNodes);
        }
        return children;
    }

    @Override
    public FlowNode mapChildren(FlowNode node, java.util.function.Function<FlowNode, FlowNode> mapper) {
        ImmutableList<FlowNode> matchFlow   = node.getAttrOrDefault("matchFlow", null);
        ImmutableList<FlowNode> onFailNodes = node.getAttrOrDefault("onFailNodes", null);
        FlowNode result = node;
        if (matchFlow != null) {
            ImmutableList<FlowNode> mapped = matchFlow.stream()
                    .map(mapper).collect(ImmutableList.toImmutableList());
            if (!mapped.equals(matchFlow)) {
                result = result.withAttr("matchFlow", mapped);
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
        result = ScriptIR.NodeMutator.filterAttr(result, "matchFlow", keep, mapper);
        result = ScriptIR.NodeMutator.filterAttr(result, "onFailNodes", keep, mapper);
        return result;
    }
}

package gloomlib.script.core.optimizer;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import gloomlib.script.core.CheckOp;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.CompilationContext.ConstantDef;
import gloomlib.script.core.ScriptIR;
import gloomlib.script.core.ScriptIR.FlowNode;
import gloomlib.script.core.ScriptIR.NodeCapability;
import gloomlib.script.core.ScriptIR.ScriptUnit;

import java.util.*;

/**
 * 脚本核心优化器。
 * <p>
 * 优化按序执行：常量折叠 → 值域传播 → 死代码消除 → 分支重排 → 变量下沉与内联预估
 * 此后执行混合型 Pass（常量提取）和分析型 Pass（活跃变量分析）。
 * <p>
 * 使用 {@link FlowNode#flags} 位掩码存储优化标记，实现零装箱分配。
 * <p>
 * 每个 Pass 实现为 {@link OptimizationPass} 接口，支持独立组合和扩展。
 *
 * <h3>Pass 设计规范</h3>
 * <ol>
 *   <li><b>返回值契约</b>：如果 Pass 修改了 IR 结构（包括节点属性），
 *       必须返回修改后的 {@link ScriptUnit}，不得丢弃。
 *       建议优先使用方法引用 {@code this::methodName}，避免手动 lambda 丢失返回值。</li>
 *   <li><b>子节点遍历</b>：引用计数和分析型 Pass 必须通过 {@link ScriptIR.NodeTraverser}
 *       递归遍历子节点（ANY/ALL/COLLECT 等复合结构），与 {@code liveVarAnalysis} 保持一致。
 *       不递归会导致子节点中的变量引用被遗漏。</li>
 *   <li><b>变量收集</b>：统计变量引用次数时，应使用 {@code getAllConsumedVariables()}
 *       而非 {@code getConsumedVariable()}，以覆盖 {@code valueNode} 等次级引用源。</li>
 * </ol>
 */
@SuppressWarnings("null")
public final class ScriptOptimizer {

    // ---- 转换型 Pass 实例（按执行顺序排列） ----

    private final OptimizationPass constantFoldingPass = this::constantFolding;
    private final OptimizationPass valueRangePropagationPass = this::valueRangePropagation;
    private final OptimizationPass deadCodeEliminationPass = this::deadCodeElimination;
    private final OptimizationPass branchReorderingPass = this::branchReordering;
    private final OptimizationPass variableInliningPass = this::variableInlining;

    // ---- 混合型 Pass（修改 IR 结构 + 写入 ctx）——必须返回修改后的 unit ----

    private final OptimizationPass constantHoistingPass = this::constantHoisting;

    // ---- 分析型 Pass 实例（结果写入 ctx，不改变返回的 IR 结构） ----

    private final OptimizationPass liveVarAnalysisPass = (unit, ctx) -> {
        liveVarAnalysis(unit, ctx);
        return unit;
    };

    private final OptimizationPass deadAssignmentEliminationPass = this::deadAssignmentElimination;
    private final OptimizationPass deadProducerEliminationPass = this::deadProducerElimination;

    /**
     * 转换型 Pass 列表（按执行顺序）。外部可通过此列表实现自定义 Pass 注入。
     */
    private final List<OptimizationPass> transformPasses = List.of(
            constantFoldingPass,
            valueRangePropagationPass,
            deadCodeEliminationPass,
            branchReorderingPass,
            variableInliningPass
    );

    /**
     * 后置 Pass 列表（在全部 transformPasses 之后按序执行）。
     * <p>
     * 包含分析型（写入 ctx）和依赖分析结果的后置转换型（修改 IR）。
     * 执行顺序敏感：deadProducerElimination 和 deadAssignmentElimination 依赖 liveVarAnalysis 的结果。
     */
    private final List<OptimizationPass> analysisPasses = List.of(
            constantHoistingPass,
            liveVarAnalysisPass,
            deadProducerEliminationPass,
            deadAssignmentEliminationPass
    );

    /**
     * 判断节点是否为"纯守卫"——无外部副作用、仅作条件分支控制的节点。
     * <p>
     * 用于 Phase A 前瞻扫描：生产者内联可以安全地跳过这些节点，
     * 使得中间的守卫检查仍然正常执行，而动作调用则延迟到消费者位置。
     */
    private static boolean isPureGuardNode(FlowNode node) {
        return node.handler().capabilities().contains(NodeCapability.PURE_GUARD);
    }


    public ScriptUnit optimize(ScriptUnit unit, CompilationContext ctx) {
        for (OptimizationPass pass : transformPasses) {
            unit = pass.apply(unit, ctx);
        }
        for (OptimizationPass pass : analysisPasses) {
            unit = pass.apply(unit, ctx);
        }
        return unit;
    }


    private ScriptUnit constantFolding(ScriptUnit unit, CompilationContext ctx) {
        return unit.withFlow(foldNodes(unit.flow(), ctx));
    }

    /**
     * 递归常量折叠：对节点列表执行折叠，并通过 {@link ScriptIR.NodeMutator}
     * 递归进入 ANY/ALL/COLLECT 等复合节点的子树。
     */
    private ImmutableList<FlowNode> foldNodes(ImmutableList<FlowNode> nodes, CompilationContext ctx) {
        ImmutableList.Builder<FlowNode> optimized = ImmutableList.builder();
        for (FlowNode node : nodes) {
            FlowNode processed = foldNodeRecursive(node, ctx);
            if (processed.handler() instanceof ScriptIR.ConstantFolder folder) {
                Boolean result = folder.evaluateFold(processed, ctx);
                if (result == null) {
                    optimized.add(processed);
                } else if (result) {
                    optimized.add(processed.withFlag(FlowNode.FLAG_FOLDED));
                } else {
                    // 恒假复合节点：保留 onFail 动作（若存在）
                    ImmutableList<FlowNode> onFail = processed.getAttrOrDefault("onFailNodes", null);
                    if (onFail != null) {
                        optimized.addAll(onFail);
                    }
                    optimized.add(FlowNode.earlyReturn());
                    break;
                }
            } else {
                optimized.add(processed);
            }
        }
        return optimized.build();
    }

    private FlowNode foldNodeRecursive(FlowNode node, CompilationContext ctx) {
        if (node.handler() instanceof ScriptIR.NodeMutator mutator) {
            return mutator.mapChildren(node, child -> {
                FlowNode folded = foldNodeRecursive(child, ctx);
                if (folded.handler() instanceof ScriptIR.ConstantFolder folder) {
                    Boolean result = folder.evaluateFold(folded, ctx);
                    if (result != null) {
                        if (result) {
                            return folded.withFlag(FlowNode.FLAG_FOLDED);
                        }
                        // 恒假且无 onFail 时才标记 FLAG_DEAD
                        ImmutableList<FlowNode> onFail = folded.getAttrOrDefault("onFailNodes", null);
                        if (onFail == null || onFail.isEmpty()) {
                            return folded.withFlag(FlowNode.FLAG_DEAD);
                        }
                    }
                }
                return folded;
            });
        }
        return node;
    }


    private ScriptUnit deadCodeElimination(ScriptUnit unit, CompilationContext ctx) {
        return unit.withFlow(eliminateDeadNodes(unit.flow()));
    }

    /**
     * 递归死代码消除：移除 FLAG_FOLDED 节点，并通过 {@link ScriptIR.NodeMutator}
     * 递归清理 ANY/ALL/COLLECT 等复合节点子树中被折叠的节点。
     */
    private ImmutableList<FlowNode> eliminateDeadNodes(ImmutableList<FlowNode> nodes) {
        ImmutableList.Builder<FlowNode> optimized = ImmutableList.builder();
        for (FlowNode node : nodes) {
            if (node.hasFlag(FlowNode.FLAG_FOLDED))
                continue;
            optimized.add(dceNodeRecursive(node));
            if (node.handler().capabilities().contains(NodeCapability.TERMINATES_FLOW)) {
                break;
            }
        }
        return optimized.build();
    }

    private FlowNode dceNodeRecursive(FlowNode node) {
        if (node.handler() instanceof ScriptIR.NodeMutator mutator) {
            return mutator.filterChildren(node, this::isLiveChild, this::dceNodeRecursive);
        }
        return node;
    }

    private boolean isLiveChild(FlowNode child) {
        return !child.hasFlag(FlowNode.FLAG_FOLDED) && !child.hasFlag(FlowNode.FLAG_DEAD);
    }


    /**
     * 值域传播优化 Pass。
     * <p>
     * 只读保证下，每个 check 通过后更新变量值域约束。
     * 后续 check 若在约束下恒真/恒假，直接折叠。
     * <p>
     * 复用 {@link #evaluateBaseOp} 的比较逻辑和 {@link FlowNode#flags} 标记机制。
     */
    private ScriptUnit valueRangePropagation(ScriptUnit unit, CompilationContext ctx) {
        Map<String, ValueRange> ranges = new HashMap<>();
        return unit.withFlow(propagateRanges(unit.flow(), ranges));
    }

    /**
     * 递归值域传播：在节点列表上执行 VRP，并通过 {@link ScriptIR.NodeMutator}
     * 递归进入 ANY/ALL/COLLECT 子树，将外层已知约束传递给内层 CHECK。
     */
    private ImmutableList<FlowNode> propagateRanges(ImmutableList<FlowNode> nodes,
                                                    Map<String, ValueRange> ranges) {
        ImmutableList.Builder<FlowNode> optimized = ImmutableList.builder();

        for (FlowNode node : nodes) {
            // 常量 MATH 产出 → 向后续 CHECK 注入精确值域约束
            if (node.handler() instanceof ScriptIR.VariableProducer producer) {
                String var = producer.getProducedVariable(node);
                Object constVal = producer.getProducedConstantValue(node);
                if (var != null && constVal != null) {
                    double d = constVal instanceof Number n ? n.doubleValue() : 0;
                    ranges.put(var, new ValueRange(d, d, constVal, true));
                }
            }

            // 递归进入复合节点子树，共享当前值域快照
            FlowNode processed = vrpNodeRecursive(node, ranges);

            // 递归 VRP 后，检查复合节点整体是否可折叠（基于子节点 FLAG_DEAD/FLAG_FOLDED 标记）
            if (processed.handler() instanceof ScriptIR.ConstantFolder folder
                    && !(processed.handler() instanceof ScriptIR.RangePropagator)) {
                Boolean foldResult = folder.evaluateFold(processed, null);
                if (foldResult != null) {
                    if (foldResult) {
                        optimized.add(processed.withFlag(FlowNode.FLAG_FOLDED));
                        continue;
                    } else {
                        // 恒假复合节点：保留 onFail 动作（若存在），再插入 earlyReturn
                        ImmutableList<FlowNode> onFail = processed.getAttrOrDefault("onFailNodes", null);
                        if (onFail != null) {
                            optimized.addAll(onFail);
                        }
                        optimized.add(FlowNode.earlyReturn());
                        break;
                    }
                }
            }

            if (processed.handler() instanceof ScriptIR.RangePropagator propagator) {
                String var = propagator.getConstrainedVariable(processed);
                if (var != null) {
                    ValueRange range = ranges.getOrDefault(var, ValueRange.UNCONSTRAINED);

                    // 尝试用现有约束折叠
                    Boolean foldResult = propagator.tryFoldWithRange(processed, range);
                    if (foldResult != null) {
                        if (foldResult) {
                            optimized.add(processed.withFlag(FlowNode.FLAG_FOLDED));
                            continue;
                        } else {
                            optimized.add(FlowNode.earlyReturn());
                            break;
                        }
                    }

                    // 未折叠 → 更新约束
                    ranges.put(var, propagator.updateRange(processed, range));
                }
            }
            optimized.add(processed);
        }
        return optimized.build();
    }

    private FlowNode vrpNodeRecursive(FlowNode node, Map<String, ValueRange> outerRanges) {
        if (node.handler() instanceof ScriptIR.NodeMutator mutator) {
            // 子树使用外层约束的只读快照（子树内的约束不回传到外层）
            Map<String, ValueRange> childRanges = new HashMap<>(outerRanges);
            return mutator.mapChildren(node, child -> {
                FlowNode processed = vrpNodeRecursive(child, childRanges);
                // 复合节点内的常量产出 → 向同级后续 CHECK 注入精确值域约束
                if (processed.handler() instanceof ScriptIR.VariableProducer producer) {
                    String pVar = producer.getProducedVariable(processed);
                    Object constVal = producer.getProducedConstantValue(processed);
                    if (pVar != null && constVal != null) {
                        double d = constVal instanceof Number n ? n.doubleValue() : 0;
                        childRanges.put(pVar, new ValueRange(d, d, constVal, true));
                    }
                }
                if (processed.handler() instanceof ScriptIR.RangePropagator propagator) {
                    String var = propagator.getConstrainedVariable(processed);
                    if (var != null) {
                        ValueRange range = childRanges.getOrDefault(var, ValueRange.UNCONSTRAINED);
                        Boolean foldResult = propagator.tryFoldWithRange(processed, range);
                        if (foldResult != null) {
                            return foldResult ? processed.withFlag(FlowNode.FLAG_FOLDED)
                                              : processed.withFlag(FlowNode.FLAG_DEAD);
                        }
                        childRanges.put(var, propagator.updateRange(processed, range));
                    }
                } else if (processed.handler() instanceof ScriptIR.ConstantFolder folder) {
                    // 内层复合节点（ANY/ALL）子条件被递归标记后，评估整体折叠
                    Boolean foldResult = folder.evaluateFold(processed, null);
                    if (foldResult != null) {
                        if (foldResult) {
                            return processed.withFlag(FlowNode.FLAG_FOLDED);
                        }
                        // 恒假且无 onFail 时才标记 FLAG_DEAD（有 onFail 的复合节点不能在 1:1 mapper 中折叠）
                        ImmutableList<FlowNode> onFail = processed.getAttrOrDefault("onFailNodes", null);
                        if (onFail == null || onFail.isEmpty()) {
                            return processed.withFlag(FlowNode.FLAG_DEAD);
                        }
                    }
                }
                return processed;
            });
        }
        return node;
    }


    private ScriptUnit branchReordering(ScriptUnit unit, CompilationContext ctx) {
        return unit.withFlow(reorderNodesRecursive(unit.flow(), ctx));
    }

    private ImmutableList<FlowNode> reorderNodesRecursive(ImmutableList<FlowNode> nodes, CompilationContext ctx) {
        ImmutableList.Builder<FlowNode> optimized = ImmutableList.builder();
        for (FlowNode node : nodes) {
            FlowNode processed = reorderNodeRecursive(node, ctx);
            if (processed.handler() instanceof ScriptIR.BranchReorderer reorderer) {
                optimized.add(reorderer.reorderBranches(processed, ctx));
            } else {
                optimized.add(processed);
            }
        }
        return optimized.build();
    }

    private FlowNode reorderNodeRecursive(FlowNode node, CompilationContext ctx) {
        if (node.handler() instanceof ScriptIR.NodeMutator mutator) {
            return mutator.mapChildren(node, child -> {
                FlowNode processed = reorderNodeRecursive(child, ctx);
                if (processed.handler() instanceof ScriptIR.BranchReorderer reorderer) {
                    return reorderer.reorderBranches(processed, ctx);
                }
                return processed;
            });
        }
        return node;
    }


    /**
     * 扫描 flow 节点收集需提升为 static final 的常量。
     * <p>
     * 结果存入 {@link CompilationContext#setHoistedConstants}，
     * 由 BytecodeCompiler 生成 {@code <clinit>} 字段。
     */
    private ScriptUnit constantHoisting(ScriptUnit unit, CompilationContext ctx) {
        ArrayList<ConstantDef> defs = new ArrayList<>();
        ImmutableList.Builder<FlowNode> optimized = ImmutableList.builder();
        int[] counter = {0};

        for (FlowNode node : unit.flow()) {
            optimized.add(hoistNode(node, defs, counter));
        }
        ctx.setHoistedConstants(ImmutableList.copyOf(defs));
        return unit.withFlow(optimized.build());
    }

    @SuppressWarnings("unchecked")
    private FlowNode hoistNode(FlowNode node, List<ConstantDef> defs, int[] counter) {
        if (node.handler() instanceof ScriptIR.ConstantHoister hoister) {
            node = hoister.hoistConstants(node, defs, counter);
        }

        if (node.handler() instanceof ScriptIR.NodeMutator mutator) {
            node = mutator.mapChildren(node, child -> hoistNode(child, defs, counter));
        } else {
            // 对于仅实现 NodeTraverser 的容器（CHECK、SWITCH、RETURN、ACTION），
            // 通过直接遍历属性中的子节点列表进行递归替换，确保 _hoistedField 不会被丢弃。
            for (java.util.Map.Entry<String, Object> entry : node.attrs().entrySet()) {
                Object val = entry.getValue();
                if (val instanceof ImmutableList<?> list && !list.isEmpty()
                        && list.get(0) instanceof FlowNode) {
                    ImmutableList<FlowNode> children = (ImmutableList<FlowNode>) val;
                    ImmutableList.Builder<FlowNode> mapped = ImmutableList.builder();
                    boolean changed = false;
                    for (FlowNode child : children) {
                        FlowNode hoisted = hoistNode(child, defs, counter);
                        mapped.add(hoisted);
                        if (hoisted != child) changed = true;
                    }
                    if (changed) {
                        node = node.withAttr(entry.getKey(), mapped.build());
                    }
                } else if (val instanceof FlowNode childNode) {
                    FlowNode hoisted = hoistNode(childNode, defs, counter);
                    if (hoisted != childNode) {
                        node = node.withAttr(entry.getKey(), hoisted);
                    }
                }
            }
        }
        return node;
    }


    /**
     * 扫描 flow 节点引用的变量集合。
     * <p>
     * 结果存入 {@link CompilationContext#setLiveVars}，
     * 由 BytecodeCompiler 跳过死变量的提取。
     * <p>
     * 复用 {@link HashMultiset} 统计模式。
     */

    /**
     * 检查顶层流程中是否存在某个 ACTION 节点将 {@code varName} 作为点链引用参数使用。
     * <p>
     * 例：{@code varName="cause"} 匹配 {@code "{cause.name}"} 这类参数。
     * 用于扩展属性下沉：当变量仅在顶层 ACTION 的点链参数中出现一次时可安全下沉。
     */
    private static boolean hasTopLevelDottedRefTo(ImmutableList<FlowNode> flow, String varName) {
        for (FlowNode node : flow) {
            if (!node.handler().capabilities().contains(NodeCapability.DOTTED_ARG_SINK)) continue;
            ImmutableList<String> args = node.getAttrOrDefault("args", ImmutableList.of());
            for (String arg : args) {
                if (ScriptIR.isDottedSingleRef(arg)) {
                    String inner = arg.substring(1, arg.length() - 1);
                    if (varName.equals(ScriptIR.splitDotted(inner)[0])) return true;
                }
            }
        }
        return false;
    }

    private void liveVarAnalysis(ScriptUnit unit, CompilationContext ctx) {
        Multiset<String> refs = HashMultiset.create();
        for (FlowNode node : unit.flow()) {
            collectLiveVars(node, refs);
        }
        // 传递性：属性路径中通过 {varName} 引用的变量（如 DynamicMapAccessor / DynamicListAccessor 的键/索引）
        // 需要与被引用它的变量一同被提取，否则运行时槽位未初始化会导致 VerifyError。
        // 核心不动点迭代：只要新加入了 live 变量，就重新扫描，直到不再有新增为止。
        boolean changed = true;
        while (changed) {
            changed = false;
            for (gloomlib.script.core.ScriptIR.VarDecl var : unit.vars()) {
                if (!refs.contains(var.name())) continue;
                // 扫描该变量属性路径中的所有 {dynVar} 引用
                String prop = var.property();
                int i = 0;
                while (i < prop.length()) {
                    int start = prop.indexOf('{', i);
                    if (start == -1) break;
                    int end = prop.indexOf('}', start + 1);
                    if (end == -1) break;
                    String dynVar = prop.substring(start + 1, end);
                    if (refs.add(dynVar, 1) == 0) { // add returns previous count; 0 means newly added
                        changed = true;
                    }
                    i = end + 1;
                }
            }
        }
        ctx.setLiveVars(refs.elementSet());
    }

    private ScriptUnit deadAssignmentElimination(ScriptUnit unit, CompilationContext ctx) {
        Set<String> liveVars = ctx.liveVars();
        ImmutableList.Builder<ScriptIR.VarDecl> liveDecls = ImmutableList.builder();
        for (gloomlib.script.core.ScriptIR.VarDecl v : unit.vars()) {
            if (liveVars.contains(v.name())) {
                liveDecls.add(v);
            }
        }
        return unit.withVars(liveDecls.build());
    }

    /**
     * 死生产者消除：产出变量已死 + 无外部副作用 → 安全消除整个节点。
     * <p>
     * 依赖 {@link #liveVarAnalysis} 的结果（{@link CompilationContext#liveVars()}）。
     * MATH 节点（无 {@link NodeCapability#SIDE_EFFECT}）产出死变量时可被整体删除。
     * ACTION/COLLECT（有 SIDE_EFFECT）即使产出死变量也不可删除。
     */
    private ScriptUnit deadProducerElimination(ScriptUnit unit, CompilationContext ctx) {
        Set<String> liveVars = ctx.liveVars();
        ImmutableList.Builder<FlowNode> optimized = ImmutableList.builder();
        for (FlowNode node : unit.flow()) {
            if (node.handler() instanceof ScriptIR.VariableProducer producer
                    && !node.handler().capabilities().contains(NodeCapability.SIDE_EFFECT)) {
                String var = producer.getProducedVariable(node);
                if (var != null && !liveVars.contains(var)) {
                    continue;
                }
            }
            optimized.add(node);
        }
        return unit.withFlow(optimized.build());
    }

    private void collectLiveVars(FlowNode node, Multiset<String> refs) {
        if (node.handler() instanceof ScriptIR.VariableConsumer consumer) {
            for (String var : consumer.getAllConsumedVariables(node)) {
                if (var != null) {
                    refs.add(var);
                }
            }
        }

        if (node.handler() instanceof ScriptIR.NodeTraverser traverser) {
            for (FlowNode child : traverser.traverseChildren(node)) {
                collectLiveVars(child, refs);
            }
        }
    }


    /**
     * 指令下沉与窥孔内联融合优化 (Variable Sinking & Inlining)
     * <p>
     * 全局扫描利用了抽象接口体系 {@link ScriptIR.VariableProducer} /
     * {@link ScriptIR.VariableConsumer}。
     * <p>
     * 1. 生产者内联 (Producer Inlining): 发现局部声明生产的实体及所输出的变量，
     * 若被紧随其后的消费者单次访问，则剥离自身封装作为环境快照供下游消费闭包栈顶处理。
     * 2. 属性下沉 (Property Sinking): 对于 {@code variables} 环境块内声明的独立提取，若全域唯有 1 处使用，
     * 则剔除预装载 (CSE) 清单，转换为仅在判定点即时通过虚拟生产者 (DummyProducer) 闭包发射动作。
     */
    private ScriptUnit variableInlining(ScriptUnit unit, CompilationContext ctx) {
        ImmutableList<FlowNode> oldFlow = unit.flow();
        if (oldFlow.size() < 2) {
            return unit;
        }

        // 1. 全域使用次数分析 (含预声明的 vars 与中间态)
        //    规范：deepRefs 递归覆盖子节点 + getAllConsumedVariables，用于安全判断。
        //    topLevelSinkableRefs 仅统计顶层 getConsumedVariable()，用于判断可下沉性。
        //    属性下沉条件 = deepRefs == 1 && topLevelSinkableRefs == 1（唯一引用且可通过 inlineAction 应用）。
        Multiset<String> deepRefs = HashMultiset.create();
        Multiset<String> topLevelSinkableRefs = HashMultiset.create();
        for (FlowNode node : oldFlow) {
            collectLiveVars(node, deepRefs);
            if (node.handler() instanceof ScriptIR.VariableConsumer consumer) {
                String var = consumer.getConsumedVariable(node);
                if (var != null) {
                    topLevelSinkableRefs.add(var);
                }
            }
        }

        // 2. 环境快照下沉提取池 (Property Sinking Pool)
        Map<String, gloomlib.script.core.ScriptIR.VarDecl> sinkingVars = new HashMap<>();
        ImmutableList.Builder<gloomlib.script.core.ScriptIR.VarDecl> optimizedVars = ImmutableList.builder();

        for (gloomlib.script.core.ScriptIR.VarDecl v : unit.vars()) {
            if (v.isPayloadAlias()) {
                // payload 别名不能被下沉（它就是 slot 1，无需提取也无法虚拟化属性链）
                optimizedVars.add(v);
                continue;
            }
            if (topLevelSinkableRefs.count(v.name()) == 1 && deepRefs.count(v.name()) == 1) {
                // 唯一引用且该引用位于可下沉的顶层消费者 → 从 CSE 数组中踢出，转入待下放池
                sinkingVars.put(v.name(), v);
            } else if (deepRefs.count(v.name()) == 1 && topLevelSinkableRefs.count(v.name()) == 0
                    && hasTopLevelDottedRefTo(oldFlow, v.name())) {
                // 扩展：仅在顶层 ACTION 节点的点链参数（如 {cause.name}）中使用一次
                // 将此变量下沉到该 ACTION 的参数发射点，避免在守卫失败路径上执行无效的 getter
                sinkingVars.put(v.name(), v);
            } else {
                optimizedVars.add(v);
            }
        }

        // 3. 窥孔扫描：寻找 [单测存入 -> 相邻立即消耗] 的 AST 连对，并处理安全下沉
        List<FlowNode> optimized = new ArrayList<>(oldFlow.size());
        for (int i = 0; i < oldFlow.size(); i++) {
            FlowNode current = oldFlow.get(i);

            //
            // 改进：允许跨越纯守卫节点 (CHECK / ANY / ALL) 寻找消费者。
            // 被跳过的守卫节点照常输出，仅生产者与消费者合并。
            // 这也是一个安全的子优化：如果中间的 CHECK 提前终止了脚本，
            // 生产者的动作调用会被完全跳过（减少不必要的副作用执行）。
            if (current.handler() instanceof ScriptIR.VariableProducer producer) {
                String storeTarget = producer.getProducedVariable(current);
                if (storeTarget != null && deepRefs.count(storeTarget) == 1) {
                    // 前瞻扫描：跳过纯守卫节点，寻找唯一的消费者
                    int consumerIdx = -1;
                    for (int j = i + 1; j < oldFlow.size() && j <= i + 8; j++) {
                        FlowNode candidate = oldFlow.get(j);
                        if (candidate.handler() instanceof ScriptIR.VariableConsumer vc) {
                            String cVar = vc.getConsumedVariable(candidate);
                            if (storeTarget.equals(cVar)) {
                                consumerIdx = j;
                                break;
                            }
                        }
                        // 仅允许跳过无副作用的纯守卫节点
                        if (!isPureGuardNode(candidate)) {
                            // 扩展：无外部副作用 + 产出不同变量 → 安全跳过
                            if (!candidate.handler().capabilities().contains(NodeCapability.SIDE_EFFECT)
                                    && candidate.handler() instanceof ScriptIR.VariableProducer vp) {
                                String pVar = vp.getProducedVariable(candidate);
                                if (pVar != null && !pVar.equals(storeTarget)) {
                                    continue;
                                }
                            }
                            break;
                        }
                    }

                    if (consumerIdx > 0) {
                        FlowNode peelAction = producer.stripProducedVariable(current);
                        FlowNode consumerNode = oldFlow.get(consumerIdx);
                        ScriptIR.VariableConsumer consumer =
                                (ScriptIR.VariableConsumer) consumerNode.handler();
                        FlowNode modifiedConsumer = consumer.inlineAction(consumerNode, peelAction);

                        // 输出中间的守卫节点（保持原始执行顺序）
                        for (int k = i + 1; k < consumerIdx; k++) {
                            optimized.add(oldFlow.get(k));
                        }
                        optimized.add(modifiedConsumer);
                        i = consumerIdx; // 跳过已处理的节点
                        continue;
                    }
                }
            }

            if (current.handler() instanceof ScriptIR.VariableConsumer consumer) {
                String reqVar = consumer.getConsumedVariable(current);
                // 扩展：若主 getConsumedVariable 未命中，且当前节点为顶层 ACTION，
                // 则尝试从点链参数中寻找可下沉变量（如 {cause.name} 中的 cause）
                if (reqVar == null && current.handler().capabilities().contains(NodeCapability.DOTTED_ARG_SINK)) {
                    for (String cVar : consumer.getAllConsumedVariables(current)) {
                        if (sinkingVars.containsKey(cVar)) {
                            reqVar = cVar;
                            break;
                        }
                    }
                }
                if (reqVar != null && sinkingVars.containsKey(reqVar)) {
                    gloomlib.script.core.ScriptIR.VarDecl decl = sinkingVars.get(reqVar);

                    // 构建一个匿名 ActionNode 作为模拟获取器，它不会经过标准的 emit 执行分发
                    // 它只会被消费节点 (如 Check) 特判并通过附带的 Accessor 执行内联出栈
                    FlowNode virtualHook = FlowNode.virtualProducer(decl);

                    FlowNode modifiedTarget = consumer.inlineAction(current, virtualHook);

                    optimized.add(modifiedTarget);
                    continue;
                }
            }

            // 常规落空兜底
            optimized.add(current);
        }

        return unit.withFlow(ImmutableList.copyOf(optimized)).withVars(optimizedVars.build());
    }

    /**
     * 编译期已知的变量值域约束。
     * <p>
     * 只读保证下，一个 check 通过后其约束在整个 accept() 内有效。
     */
    public record ValueRange(double min, double max, Object exactValue, boolean nonNull) {

        public static final ValueRange UNCONSTRAINED = new ValueRange(
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, null, false);

        public ValueRange withMin(double newMin) {
            return new ValueRange(Math.max(min, newMin), max, exactValue, nonNull);
        }

        public ValueRange withMax(double newMax) {
            return new ValueRange(min, Math.min(max, newMax), exactValue, nonNull);
        }

        public ValueRange withExact(Object val) {
            double d = val instanceof Number n ? n.doubleValue() : 0;
            return new ValueRange(d, d, val, true);
        }

        public ValueRange withNonNull() {
            return new ValueRange(min, max, exactValue, true);
        }

        /**
         * 判断给定操作是否在当前约束下恒真/恒假。
         *
         * @return Boolean.TRUE=恒真, Boolean.FALSE=恒假, null=不确定
         */
        public Boolean canFold(CheckOp op, double cmpValue) {
            return op.foldRange(min, max, cmpValue, exactValue);
        }

        /**
         * 用 String exactValue 判断互斥（枚举/字符串 ==）
         */
        public Boolean canFoldExact(CheckOp op, Object cmpValue) {
            if (op == CheckOp.EQ && exactValue != null) {
                return exactValue.equals(cmpValue) ? Boolean.TRUE : Boolean.FALSE;
            }
            return null;
        }
    }
}

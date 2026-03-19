package gloomlib.script.core;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import gloomlib.diagnostic.DiagnosticCategory;
import gloomlib.script.api.ScriptCompileException;
import gloomlib.script.core.codegen.BytecodeCompiler;
import gloomlib.script.core.codegen.ScriptConstantBootstrap;
import gloomlib.script.core.optimizer.ScriptOptimizer;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 编译管线，串联解析→优化→代码生成的全流程。
 * <p>
 * 内置编译缓存：同一 {@link ScriptIR.ScriptUnit} 多次提交时直接复用已编译结果，
 * 跳过 Parser→Optimizer→ASM→defineClass 整条管线。
 * <p>
 * 使用方式：
 *
 * <pre>{@code
 * CompilationPipeline pipeline = new CompilationPipeline();
 * CompiledScript script = pipeline.compile(yamlInput);
 * // script.handlerClass() 是编译后的 Consumer<Event>
 * }</pre>
 */
public final class CompilationPipeline {

    /**
     * 结构化编译缓存键。record 的 equals/hashCode 做深度结构比较，消除裸 int 哈希碰撞。
     * <p>
     * 编译缓存使用 {@code softValues()} 确保内存压力时 JVM 自动回收，Hidden Class 可被元空间 GC 卸载。
     */
    private record CacheKey(
            String payloadClass,
            ImmutableList<ScriptIR.VarDecl> vars,
            ImmutableList<ScriptIR.FlowNode> flow,
            Class<?> expectedReturnType
    ) {}

    /**
     * 常量键追踪表：CacheKey → 该编译结果引用的常量键列表。
     * 配合 CACHE 的 RemovalListener，在 CompiledScript 被驱逐时释放 REGISTRY 中的常量引用。
     */
    private static final ConcurrentHashMap<CacheKey, List<String>> CONSTANT_KEY_TRACKER =
            new ConcurrentHashMap<>();

    private static final Cache<CacheKey, CompiledScript> CACHE = CacheBuilder.newBuilder()
            .softValues()
            .removalListener((RemovalNotification<CacheKey, CompiledScript> notification) -> {
                CacheKey evictedKey = notification.getKey();
                if (evictedKey != null) {
                    List<String> keys = CONSTANT_KEY_TRACKER.remove(evictedKey);
                    if (keys != null && !keys.isEmpty()) {
                        ScriptConstantBootstrap.release(keys);
                    }
                }
            })
            .build();

    /**
     * 结构模板缓存：结构化键 → 已优化的 IR 模板。
     * <p>
     * 结构相同但常量值不同的脚本共享同一优化结果，
     * 仅需替换常量后重新运行 BytecodeCompiler（跳过全部验证和优化 Pass）。
     * <p>
     * LRU 淘汰策略，上限 128 条目。
     */
    private static final Cache<Integer, TemplateRecord> TEMPLATE_CACHE = CacheBuilder.newBuilder()
            .maximumSize(128)
            .build();

    private final ScriptOptimizer optimizer;
    private final BytecodeCompiler compiler;

    public CompilationPipeline() {
        this.optimizer = new ScriptOptimizer();
        this.compiler = new BytecodeCompiler();
    }

    /**
     * 清除指定脚本的编译缓存。
     */
    public static void invalidate(ScriptIR.ScriptUnit unit) {
        // 收集匹配键后调用 CACHE.invalidate()，确保 RemovalListener 被触发从而清理 CONSTANT_KEY_TRACKER
        CACHE.asMap().keySet().stream()
                .filter(k -> k.payloadClass().equals(unit.payloadClass())
                        && k.vars().equals(unit.vars())
                        && k.flow().equals(unit.flow()))
                .collect(java.util.stream.Collectors.toList())
                .forEach(CACHE::invalidate);
    }

    /**
     * 清空全部编译缓存和常量注册表（用于配置热重载场景）。
     * <p>
     * 同时调用 {@link ScriptConstantBootstrap#purge()} 释放
     * 旧版脚本的外置常量（Pattern / Set / 数组），避免常量池无限增长。
     */
    public static void clearCache() {
        CACHE.invalidateAll();
        CONSTANT_KEY_TRACKER.clear();
        TEMPLATE_CACHE.invalidateAll();
        ScriptConstantBootstrap.purge();
    }

    /**
     * 返回当前缓存条目数（调试用）。
     */
    public static int cacheSize() {
        return (int) CACHE.size();
    }

    /**
     * 返回结构模板缓存条目数（调试用）。
     */
    public static int templateCacheSize() {
        return (int) TEMPLATE_CACHE.size();
    }


    /**
     * 计算脚本的"结构哈希"——只哈希节点类型、变量名、操作符等结构信息，
     * 忽略字面量值（numericValue、attrs 中的 value/args）。
     * <p>
     * 结构哈希相同的脚本可以通过常量替换复用已优化的 IR。
     */
    public static int structuralHash(ScriptIR.ScriptUnit unit) {
        int h = unit.payloadClass().hashCode();
        // vars 的结构部分：名字 + 属性链 + 类型
        for (ScriptIR.VarDecl v : unit.vars()) {
            h = 31 * h + v.name().hashCode();
            h = 31 * h + v.property().hashCode();
            h = 31 * h + v.type().hashCode();
        }
        // flow 的结构部分
        for (ScriptIR.FlowNode node : unit.flow()) {
            h = 31 * h + structuralHashNode(node);
        }
        return h;
    }

    /**
     * 递归计算单个节点的结构哈希。
     * 忽略: numericValue, attrs["value"], attrs["args"], attrs["valueList"]
     */
    private static int structuralHashNode(ScriptIR.FlowNode node) {
        int h = node.type().hashCode();
        for (Map.Entry<String, Object> entry : node.attrs().entrySet()) {
            String key = entry.getKey();
            // 跳过字面量值——这些不影响结构
            if ("value".equals(key) || "args".equals(key) || "valueList".equals(key)
                    || "valueType".equals(key)) {
                continue;
            }
            h = 31 * h + key.hashCode();
            Object val = entry.getValue();
            // 递归处理子节点列表（如 onFailNodes, children, cases）
            if (val instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ScriptIR.FlowNode) {
                for (Object item : list) {
                    h = 31 * h + structuralHashNode((ScriptIR.FlowNode) item);
                }
            } else if (val instanceof ScriptIR.FlowNode childNode) {
                h = 31 * h + structuralHashNode(childNode);
            } else if (val != null) {
                h = 31 * h + val.hashCode();
            }
        }
        return h;
    }

    /**
     * 将 newUnit 的常量值替换进 templateOptimized 的对应位置。
     * <p>
     * 递归收集原始与新 IR 的常量差异（含嵌套子节点），构建 oldValue→newValue 映射，
     * 然后递归遍历优化后 IR（含嵌套子节点）执行替换。
     * <p>
     * 当检测到映射冲突（同一旧值需替换为不同新值）时，抛出 IllegalStateException，
     * 由 {@link #compileFromTemplate} 的 catch 捕获并安全回退到完整编译路径。
     */
    private static ScriptIR.ScriptUnit substituteConstants(
            ScriptIR.ScriptUnit templateOptimized,
            ScriptIR.ScriptUnit templateOriginal,
            ScriptIR.ScriptUnit newUnit) {

        ImmutableList<ScriptIR.FlowNode> origFlow = templateOriginal.flow();
        ImmutableList<ScriptIR.FlowNode> newFlow = newUnit.flow();

        if (origFlow.size() != newFlow.size()) {
            throw new IllegalStateException("Structural mismatch: flow size differs");
        }

        // 1. 递归收集所有常量差异（含嵌套复合节点子树）
        Map<Double, Double> numericSubs = new java.util.LinkedHashMap<>();
        Map<String, Map<Object, Object>> attrSubs = new java.util.HashMap<>();
        collectConstantDiffs(origFlow, newFlow, numericSubs, attrSubs);

        if (numericSubs.isEmpty() && attrSubs.isEmpty()) {
            return templateOptimized;
        }

        // 2. 递归对优化后 IR 的所有节点（含嵌套）应用替换
        return templateOptimized.withFlow(substituteFlow(templateOptimized.flow(), numericSubs, attrSubs));
    }

    private static final String[] CONSTANT_ATTR_KEYS = {"value", "args", "valueList"};

    /**
     * 递归收集原始节点列表与新节点列表之间的常量值差异。
     * <p>
     * 当发现同一旧值需映射到不同新值（冲突）时，抛出异常触发安全回退。
     */
    @SuppressWarnings("unchecked")
    private static void collectConstantDiffs(
            ImmutableList<ScriptIR.FlowNode> origNodes,
            ImmutableList<ScriptIR.FlowNode> newNodes,
            Map<Double, Double> numericSubs,
            Map<String, Map<Object, Object>> attrSubs) {

        int limit = Math.min(origNodes.size(), newNodes.size());
        for (int i = 0; i < limit; i++) {
            ScriptIR.FlowNode orig = origNodes.get(i);
            ScriptIR.FlowNode repl = newNodes.get(i);

            // 数值差异（仅当节点显式设置了数值时才替换，通过 FLAG 区分默认 0.0 与用户显式 value: 0）
            if (Double.compare(orig.numericValue(), repl.numericValue()) != 0
                    && orig.hasFlag(ScriptIR.FlowNode.FLAG_HAS_EXPLICIT_NUMERIC)) {
                Double existing = numericSubs.get(orig.numericValue());
                if (existing != null && Double.compare(existing, repl.numericValue()) != 0) {
                    throw new IllegalStateException("Numeric constant collision: "
                            + orig.numericValue() + " → " + existing + " vs " + repl.numericValue());
                }
                numericSubs.put(orig.numericValue(), repl.numericValue());
            }

            // 属性值差异
            for (String key : CONSTANT_ATTR_KEYS) {
                Object ov = orig.attrs().get(key);
                Object nv = repl.attrs().get(key);
                if (ov != null && nv != null && !nv.equals(ov)) {
                    Map<Object, Object> sub = attrSubs.computeIfAbsent(key, k -> new java.util.LinkedHashMap<>());
                    Object existing = sub.get(ov);
                    if (existing != null && !existing.equals(nv)) {
                        throw new IllegalStateException("Attr constant collision for key '" + key + "'");
                    }
                    sub.put(ov, nv);
                }
            }

            // 递归进入嵌套子节点列表属性（children、matchFlow、cases、onFailNodes 等）
            for (Map.Entry<String, Object> entry : orig.attrs().entrySet()) {
                Object origVal = entry.getValue();
                Object newVal = repl.attrs().get(entry.getKey());
                if (origVal instanceof List<?> origList && newVal instanceof List<?>
                        && !origList.isEmpty() && origList.get(0) instanceof ScriptIR.FlowNode) {
                    collectConstantDiffs(
                            (ImmutableList<ScriptIR.FlowNode>) origVal,
                            (ImmutableList<ScriptIR.FlowNode>) newVal,
                            numericSubs, attrSubs);
                } else if (origVal instanceof ScriptIR.FlowNode origChild
                        && newVal instanceof ScriptIR.FlowNode newChild) {
                    collectConstantDiffs(
                            ImmutableList.of(origChild), ImmutableList.of(newChild),
                            numericSubs, attrSubs);
                }
            }
        }
    }

    /**
     * 递归替换优化后 IR 节点流中的常量值。
     * 若无任何替换发生，返回原列表引用（供调用方 {@code !=} 判断是否变化）。
     */
    private static ImmutableList<ScriptIR.FlowNode> substituteFlow(
            ImmutableList<ScriptIR.FlowNode> flow,
            Map<Double, Double> numericSubs,
            Map<String, Map<Object, Object>> attrSubs) {
        ImmutableList.Builder<ScriptIR.FlowNode> builder = null;
        for (int i = 0; i < flow.size(); i++) {
            ScriptIR.FlowNode orig = flow.get(i);
            ScriptIR.FlowNode subst = substituteNode(orig, numericSubs, attrSubs);
            if (subst != orig && builder == null) {
                builder = ImmutableList.builder();
                for (int j = 0; j < i; j++) builder.add(flow.get(j));
            }
            if (builder != null) builder.add(subst);
        }
        return builder != null ? builder.build() : flow;
    }

    /**
     * 递归替换单个优化后 IR 节点的常量值（含嵌套子节点属性）。
     */
    @SuppressWarnings("unchecked")
    private static ScriptIR.FlowNode substituteNode(
            ScriptIR.FlowNode node,
            Map<Double, Double> numericSubs,
            Map<String, Map<Object, Object>> attrSubs) {

        if (node.hasFlag(ScriptIR.FlowNode.FLAG_OPTIMIZER_INJECTED)
                || node.hasFlag(ScriptIR.FlowNode.FLAG_FOLDED)) {
            return node;
        }

        // 替换 numericValue
        Double newNum = numericSubs.get(node.numericValue());
        if (newNum != null) {
            node = node.withNumericValue(newNum);
        }

        // 替换属性值
        for (Map.Entry<String, Map<Object, Object>> sub : attrSubs.entrySet()) {
            String attrKey = sub.getKey();
            Object currentVal = node.attrs().get(attrKey);
            if (currentVal != null) {
                Object replacement = sub.getValue().get(currentVal);
                if (replacement != null) {
                    node = node.withAttr(attrKey, replacement);
                }
            }
        }

        // 递归进入嵌套子节点属性
        for (Map.Entry<String, Object> entry : node.attrs().entrySet()) {
            Object val = entry.getValue();
            if (val instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ScriptIR.FlowNode) {
                ImmutableList<ScriptIR.FlowNode> children = (ImmutableList<ScriptIR.FlowNode>) val;
                ImmutableList<ScriptIR.FlowNode> substituted = substituteFlow(children, numericSubs, attrSubs);
                if (substituted != children) {
                    node = node.withAttr(entry.getKey(), substituted);
                }
            } else if (val instanceof ScriptIR.FlowNode childNode) {
                ScriptIR.FlowNode substituted = substituteNode(childNode, numericSubs, attrSubs);
                if (substituted != childNode) {
                    node = node.withAttr(entry.getKey(), substituted);
                }
            }
        }

        return node;
    }


    private static Method findSAM(Class<?> interfaceClass, String scriptId) {
        Method sam = null;
        for (Method m : interfaceClass.getMethods()) {
            if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())
                    && !m.isDefault()
                    && !isObjectMethod(m)) {
                if (sam != null) {
                    throw ScriptCompileException.create(scriptId, null,
                            gloomlib.diagnostic.DiagnosticCategory.SEMANTIC,
                            "Target interface " + interfaceClass.getName()
                            + " is not a single abstract method (SAM) interface.");
                }
                sam = m;
            }
        }
        if (sam == null) {
            throw ScriptCompileException.create(scriptId, null,
                    gloomlib.diagnostic.DiagnosticCategory.SEMANTIC,
                    "Target interface " + interfaceClass.getName() + " has no abstract method.");
        }
        return sam;
    }

    private static boolean isObjectMethod(Method m) {
        try {
            Object.class.getMethod(m.getName(), m.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * 断言变量在编译上下文中存在，否则抛出友好的编译异常。
     */
    private static void assertVarExists(String varName, ScriptIR.FlowNode node,
                                        CompilationContext ctx, String scriptId) {
        if ("payload".equals(varName))
            return;
        if (varName.startsWith("$"))
            return; // 动态变量（如 $it）由处理器在 emit 时注入，跳过静态校验
        try {
            ctx.getSlot(varName);
        } catch (gloomlib.diagnostic.DiagnosticException e) {
            Object nodeValue = node.getAttrOrDefault("value", null);
            String context = (nodeValue instanceof String s && !s.isEmpty())
                    ? " (in: \"" + s + "\")"
                    : "";
            throw ScriptCompileException.create(scriptId, node,
                    String.format("Undefined variable '%s' referenced in %s node%s.",
                            varName, node.type(), context));
        }
    }

    /**
     * 委托 {@link gloomlib.script.core.codegen.generated.GeneratedScriptHost#defineHidden}
     * 在 {@code codegen.generated} 包内定义隐藏类。
     */
    private static Class<?> defineHidden(byte[] bytecode) {
        return gloomlib.script.core.codegen.generated.GeneratedScriptHost.defineHidden(bytecode);
    }

    public CompiledScript compile(ScriptIR.ScriptUnit unit) {
        return compile(unit, Object.class);
    }

    /**
     * 编译一个已构建好的抽象语法树（纯 Java 代码无 YAML 依赖）。
     * <p>
     * 相同内容的 {@link ScriptIR.ScriptUnit} 会命中缓存直接返回，跳过完整编译。
     *
     * @param unit               脚本单元中间层表示
     * @param expectedReturnType 用户外部期望获取的强类型返回对象
     * @return 编译结果，包含生成的强类型高性能处理器。
     */
    public CompiledScript compile(ScriptIR.ScriptUnit unit, Class<?> expectedReturnType) {
        Preconditions.checkNotNull(unit, "unit");

        CacheKey key = new CacheKey(unit.payloadClass(), unit.vars(), unit.flow(), expectedReturnType);
        CompiledScript cached = CACHE.getIfPresent(key);
        if (cached != null) {
            return new CompiledScript(unit, cached.handlerClass());
        }

        try {
            int structKey = structuralHash(unit) * 31 + expectedReturnType.hashCode();
            TemplateRecord template = TEMPLATE_CACHE.getIfPresent(structKey);
            if (template != null) {
                return compileFromTemplate(unit, template, key);
            }

            CompilationContext ctx = buildContext(unit, expectedReturnType);

            primeNarrowings(unit, ctx);
            validateVariableReferences(unit, ctx);
            validateActionParameterTypes(unit, ctx);
            validateReturnType(unit, ctx, expectedReturnType);

            ScriptIR.ScriptUnit optimized = optimizer.optimize(unit, ctx);

            byte[] bytecode = compiler.compile(optimized, ctx);

            Class<?> clazz = defineHidden(bytecode);

            CompiledScript result = new CompiledScript(optimized, clazz);
            CACHE.put(key, result);
            trackConstantKeys(key, ctx);

            // 注册结构模板（首次编译后缓存优化结果供后续快速路径复用）
            TEMPLATE_CACHE.asMap().putIfAbsent(structKey, new TemplateRecord(unit, optimized, ctx, expectedReturnType));

            return result;
        } catch (ScriptCompileException e) {
            throw e;
        } catch (gloomlib.diagnostic.DiagnosticException e) {
            throw new ScriptCompileException(e.diagnostic(), e);
        } catch (Exception e) {
            throw ScriptCompileException.create(unit.id(), null,
                    "Error compiling script [" + unit.id() + "]: " + e.getMessage());
        }
    }

    /**
     * 动态接口自适应编译。根据用户传入的目标 SAM 接口动态生成无装箱字节码。
     */
    public <T> T compileInterface(ScriptIR.ScriptUnit unit, Class<T> expectedInterfaceType) {
        Preconditions.checkNotNull(unit, "unit");
        Preconditions.checkNotNull(expectedInterfaceType, "expectedInterfaceType");
        Preconditions.checkArgument(expectedInterfaceType.isInterface(), "target must be an interface");

        Method sam = findSAM(expectedInterfaceType, unit.id());
        Class<?> expectedReturnType = sam.getReturnType();

        CacheKey key = new CacheKey(unit.payloadClass(), unit.vars(), unit.flow(), expectedInterfaceType);
        CompiledScript cached = CACHE.getIfPresent(key);
        if (cached != null) {
            return cached.newInstance(unit.id());
        }

        try {
            CompilationContext ctx = buildContext(unit, expectedInterfaceType);

            primeNarrowings(unit, ctx);
            validateVariableReferences(unit, ctx);
            validateActionParameterTypes(unit, ctx);
            validateReturnType(unit, ctx, expectedReturnType);

            ScriptIR.ScriptUnit optimized = optimizer.optimize(unit, ctx);
            byte[] bytecode = compiler.compile(optimized, ctx);
            Class<?> clazz = defineHidden(bytecode);

            CompiledScript result = new CompiledScript(optimized, clazz);
            CACHE.put(key, result);
            trackConstantKeys(key, ctx);

            return result.newInstance(unit.id());
        } catch (ScriptCompileException e) {
            throw e;
        } catch (gloomlib.diagnostic.DiagnosticException e) {
            throw new ScriptCompileException(e.diagnostic(), e);
        } catch (Exception e) {
            throw ScriptCompileException.create(unit.id(), null,
                    "Error compiling script [" + unit.id() + "]: " + e.getMessage());
        }
    }

    /**
     * 从模板快速编译：用新脚本的常量值替换模板 IR 中的常量，跳过全部验证和优化。
     */
    private CompiledScript compileFromTemplate(ScriptIR.ScriptUnit newUnit, TemplateRecord template, CacheKey cacheKey) {
        try {
            // 1. 将新脚本的常量值替换进已优化的 IR
            ScriptIR.ScriptUnit substituted = substituteConstants(template.optimizedUnit(), template.originalUnit(),
                    newUnit);

            // 2. 重建编译上下文（使用与模板相同的 expectedReturnType）
            CompilationContext freshCtx = buildContext(newUnit, template.expectedReturnType());
            // 复制活跃变量分析结果（结构相同，活跃变量集合不变）
            freshCtx.setLiveVars(template.ctx().liveVars());

            // 对替换后的 IR 重新运行常量提升，重建正确的 ConstantDef 键和值，
            // 避免复用模板的 hoistedConstants 导致 REGISTRY 中 putIfAbsent 引用过时常量
            substituted = optimizer.constantHoistingOnly(substituted, freshCtx);

            // 3. 直接生成字节码（跳过 8 个优化 Pass + 全部验证）
            byte[] bytecode = compiler.compile(substituted, freshCtx);

            // 4. 加载
            Class<?> clazz = defineHidden(bytecode);

            CompiledScript result = new CompiledScript(substituted, clazz);
            CACHE.put(cacheKey, result);
            trackConstantKeys(cacheKey, freshCtx);
            return result;
        } catch (Exception e) {
            // 模板路径出错时安全回退到完整编译
            TEMPLATE_CACHE.invalidate(structuralHash(newUnit) * 31
                    + (template.expectedReturnType() != null ? template.expectedReturnType().hashCode() : 0));
            return compileFull(newUnit,
                    template.expectedReturnType() != null ? template.expectedReturnType() : Object.class);
        }
    }

    /**
     * 将编译上下文中的常量键列表注册到追踪表，
     * 配合 CACHE RemovalListener 实现增量清理。
     */
    private static void trackConstantKeys(CacheKey key, CompilationContext ctx) {
        List<String> keys = ctx.hoistedConstants().stream()
                .map(CompilationContext.ConstantDef::key)
                .toList();
        if (!keys.isEmpty()) {
            CONSTANT_KEY_TRACKER.put(key, keys);
        }
    }

    /**
     * 完整编译路径（用于模板回退）。
     */
    private CompiledScript compileFull(ScriptIR.ScriptUnit unit, Class<?> expectedReturnType) {
        CompilationContext ctx = buildContext(unit, expectedReturnType);
        primeNarrowings(unit, ctx);
        validateVariableReferences(unit, ctx);
        validateActionParameterTypes(unit, ctx);
        validateReturnType(unit, ctx, expectedReturnType);
        ScriptIR.ScriptUnit optimized = optimizer.optimize(unit, ctx);
        byte[] bytecode = compiler.compile(optimized, ctx);
        Class<?> clazz = defineHidden(bytecode);
        return new CompiledScript(optimized, clazz);
    }

    private CompilationContext buildContext(ScriptIR.ScriptUnit unit, Class<?> expectedInterfaceType) {
        try {
            Class<?> payloadClass = Class.forName(unit.payloadClass());
            CompilationContext.Builder builder = CompilationContext.builder(payloadClass)
                    .scriptId(unit.id());

            if (expectedInterfaceType != null && expectedInterfaceType.isInterface()) {
                Method sam = findSAM(expectedInterfaceType, unit.id());
                builder.targetMethod(
                        org.objectweb.asm.Type.getInternalName(expectedInterfaceType),
                        sam.getName(),
                        org.objectweb.asm.Type.getMethodDescriptor(sam),
                        org.objectweb.asm.Type.getReturnType(sam));
            }

            Set<String> registeredVars = new HashSet<>();
            for (ScriptIR.VarDecl var : unit.vars()) {
                registeredVars.add(var.name());
                if (var.isPayloadAlias()) {
                    // 别名直接映射到 slot 1，类型为 payload 具体类
                    builder.addPayloadAlias(var.name(), ScriptIR.IRType.fromClass(payloadClass));
                } else {
                    builder.addVar(var.name(), var.type());
                }
            }

            // 自动为所有会产生局部变量的节点（如 Action, Math 等 VariableProducer）开辟存储槽位，免去显式声明的麻烦
            Map<String, ScriptIR.IRType> producerTypes = new LinkedHashMap<>();
            for (ScriptIR.FlowNode node : unit.flow()) {
                ScriptIR.FlowNodeHandler handler = node.handler();
                if (handler instanceof ScriptIR.VariableProducer producer) {
                    String store = producer.getProducedVariable(node);
                    if (store != null && !registeredVars.contains(store)) {
                        ScriptIR.IRType type = producer.resolveProducedType(node, payloadClass, unit);
                        // 多路径类型合并：同名变量在不同分支中产出时，取宽类型
                        producerTypes.merge(store, type, ScriptIR.IRType::merge);
                    }
                }
                // COLLECT 的 store 是副作用产出，不可内联，但需要分配槽位
                if (node.type() == ScriptIR.FlowNodeType.COLLECT) {
                    String store = node.getAttrOrDefault("store", null);
                    if (store != null && !registeredVars.contains(store)) {
                        ScriptIR.IRType storeType = node.getAttrOrDefault("returnType",
                                ScriptIR.IRType.INT);
                        producerTypes.merge(store, storeType, ScriptIR.IRType::merge);
                    }
                }
            }
            for (Map.Entry<String, ScriptIR.IRType> entry : producerTypes.entrySet()) {
                registeredVars.add(entry.getKey());
                builder.addVar(entry.getKey(), entry.getValue());
            }

            CompilationContext ctx = builder.build();

            // 静态类型预填：将所有编译期已知静态类型的变量预注册进 narrowedClasses，
            // 使点链模板 {x.y} 无需显式 instanceof check 即可访问静态已知类型的属性。
            // 若后续 instanceof check 指向更具体的子类，primeNarrowings / CheckNodeHandler.emit() 会覆盖。
            //
            // 覆盖三类变量：
            //   1. variables 声明变量：由 PropertyResolver 通过 getter 推断出精确返回类型
            //   2. $self payload 别名：直接对应 payload 具体类（EntityDamageByEntityEvent 等）
            //   3. VariableProducer 产生的变量：action 返回值的精确类型
            for (ScriptIR.VarDecl var : unit.vars()) {
                if (var.isPayloadAlias()) {
                    // $self 别名 → payload 具体类（如 EntityDamageByEntityEvent）
                    ctx.narrowType(var.name(), payloadClass);
                } else {
                    Class<?> rawClass = var.type().getToken().getRawType();
                    if (rawClass != null && rawClass != Object.class && rawClass != Enum.class) {
                        ctx.narrowType(var.name(), rawClass);
                    }
                }
            }
            // VariableProducer 产生的变量（如 Action 返回值 store: result）
            for (Map.Entry<String, ScriptIR.IRType> entry : producerTypes.entrySet()) {
                Class<?> rawClass = entry.getValue().getToken().getRawType();
                if (rawClass != null && rawClass != Object.class && rawClass != Enum.class) {
                    ctx.narrowType(entry.getKey(), rawClass);
                }
            }

            return ctx;
        } catch (ClassNotFoundException e) {
            throw ScriptCompileException.create(unit.id(), null,
                    gloomlib.diagnostic.DiagnosticCategory.SEMANTIC,
                    "Payload class not found: " + unit.payloadClass());
        }
    }

    /**
     * 预扫描所有顶层 check 节点，将 instanceof（非取反）产生的窄化提前注册进 CompilationContext，
     * 使验证阶段（validateActionParameterTypes）可以感知到窄化类型。
     */
    private void primeNarrowings(ScriptIR.ScriptUnit unit, CompilationContext ctx) {
        for (ScriptIR.FlowNode node : unit.flow()) {
            if (node.type() != ScriptIR.FlowNodeType.CHECK)
                continue;
            String rawOp = node.getAttrOrDefault("op", null);
            if (rawOp == null)
                continue;
            CheckOp.Resolved resolved = CheckOp.resolve(rawOp);
            if (resolved.op() != CheckOp.INSTANCEOF || resolved.negate())
                continue;
            String variable = node.getAttrOrDefault("variable", null);
            String rawClass = node.getAttrOrDefault("value", null);
            if (variable == null || rawClass == null)
                continue;
            try {
                ctx.narrowType(variable, Class.forName(rawClass.replace('/', '.')));
            } catch (ClassNotFoundException e) {
                // 未找到类时静默忽略，正式编译阶段会再次校验并报错
            }
        }
    }

    /**
     * AOT 变量引用完整性检查。
     * <p>
     * 在优化前扫描所有节点，验证被消费的变量（VariableConsumer、模板字符串）在编译上下文中存在。
     */
    private void validateVariableReferences(ScriptIR.ScriptUnit unit, CompilationContext ctx) {
        for (ScriptIR.FlowNode node : unit.flow()) {
            validateNodeVarRefs(node, ctx, unit.id());
        }
    }

    private void validateNodeVarRefs(ScriptIR.FlowNode node, CompilationContext ctx, String scriptId) {
        ScriptIR.FlowNodeHandler handler = node.handler();

        // 1. 统一处理所有节点汇报的消费变量引用
        if (handler instanceof ScriptIR.VariableConsumer consumer) {
            for (String var : consumer.getAllConsumedVariables(node)) {
                assertVarExists(var, node, ctx, scriptId);
            }
        }

        // 2. 递归检查子节点
        if (handler instanceof ScriptIR.NodeTraverser traverser) {
            for (ScriptIR.FlowNode child : traverser.traverseChildren(node)) {
                validateNodeVarRefs(child, ctx, scriptId);
            }
        }
    }

    // ======================== 类型穿透推导 (Type Propagation Pass)

    private void validateActionParameterTypes(ScriptIR.ScriptUnit unit, CompilationContext ctx) {
        for (ScriptIR.FlowNode node : unit.flow()) {
            validateActionTypesInNode(node, ctx);
        }
    }

    private void validateActionTypesInNode(ScriptIR.FlowNode node, CompilationContext ctx) {
        ScriptIR.FlowNodeHandler handler = node.handler();

        // 1. 委托节点处理器进行自己的类型匹配校验
        if (handler instanceof ScriptIR.TypeValidator validator) {
            validator.validateTypes(node, ctx);
        }

        // 2. 递归校验子节点（复合条件 / onFail 等）
        if (handler instanceof ScriptIR.NodeTraverser traverser) {
            for (ScriptIR.FlowNode child : traverser.traverseChildren(node)) {
                validateActionTypesInNode(child, ctx);
            }
        }
    }


    private void validateReturnType(ScriptIR.ScriptUnit unit, CompilationContext ctx, Class<?> expectedJavaType) {
        if (expectedJavaType == Object.class || expectedJavaType == void.class || expectedJavaType == Void.class) {
            return; // 不约束返回类型
        }

        ScriptIR.IRType expectedIR = ScriptIR.IRType.fromClass(expectedJavaType);
        boolean hasReturn = false;

        for (ScriptIR.FlowNode node : unit.flow()) {
            hasReturn |= checkReturnNodesRecursive(node, ctx, expectedIR, expectedJavaType, unit.id());
        }

        if (!hasReturn) {
            throw ScriptCompileException.create(unit.id(), null, DiagnosticCategory.SEMANTIC,
                    String.format(
                            "Script intends to return a strongly-typed %s, but no explicit RETURN node was found.",
                            expectedJavaType.getSimpleName()));
        }
    }

    private boolean checkReturnNodesRecursive(ScriptIR.FlowNode node, CompilationContext ctx,
                                              ScriptIR.IRType expectedIR, Class<?> expectedJavaType, String scriptId) {
        boolean found = false;

        if (node.type() == ScriptIR.FlowNodeType.RETURN) {
            found = true;
            String varName = node.getAttrOrDefault("variable", null);
            ScriptIR.IRType actualIR = (varName == null) ? ScriptIR.IRType.OBJECT : ctx.getType(varName);

            // 如果节点指定了返回变量，并且该变量的类型不兼容
            if (varName != null && !expectedIR.isAssignableFrom(actualIR)) {
                throw ScriptCompileException.create(scriptId, node,
                        gloomlib.diagnostic.DiagnosticCategory.TYPE, String.format(
                                "Script compiled for strict return type %s, but RETURN node provides variable '{%s}' of type %s.",
                                expectedJavaType.getSimpleName(), varName, actualIR));
            }
        }

        ScriptIR.FlowNodeHandler handler = node.handler();
        if (handler instanceof ScriptIR.NodeTraverser traverser) {
            for (ScriptIR.FlowNode child : traverser.traverseChildren(node)) {
                found |= checkReturnNodesRecursive(child, ctx, expectedIR, expectedJavaType, scriptId);
            }
        }
        return found;
    }


    /**
     * 结构模板记录。
     * <p>
     * 存储首次完整编译的优化后 IR 和上下文，供后续结构相同的脚本快速复用。
     */
    private record TemplateRecord(
            ScriptIR.ScriptUnit originalUnit,
            ScriptIR.ScriptUnit optimizedUnit,
            CompilationContext ctx,
            Class<?> expectedReturnType) {
    }


    /**
     * 编译结果。
     */
    public record CompiledScript(
            ScriptIR.ScriptUnit ir,
            Class<?> handlerClass) {

        /**
         * 创建 Consumer「副作用型」处理器实例。
         * 内部实际为 Function，包装为 Consumer 以兼容现有 API。
         */
        public Consumer<Object> newHandler() {
            Function<Object, Object> func = newFunction();
            return func::apply; // 方法引用包装，零额外开销
        }

        /**
         * 动态实例化（用于零装箱等纯粹动态匹配）。
         */
        @SuppressWarnings("unchecked")
        public <T> T newInstance(String scriptId) {
            try {
                return (T) handlerClass.getDeclaredConstructor(String.class).newInstance(scriptId);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot instantiate compiled function for script: " + scriptId, e);
            }
        }

        /**
         * 创建计算型处理器实例。
         * 膀本返回 null（void RETURN），有值返回装箱后的变量（RETURN_VALUE）。
         */
        public Function<Object, Object> newFunction() {
            return newInstance(ir.id());
        }
    }
}

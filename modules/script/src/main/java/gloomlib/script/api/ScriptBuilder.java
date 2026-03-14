package gloomlib.script.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import gloomlib.script.api.action.ActionRegistry;
import gloomlib.script.api.action.ActionRegistry.ActionDef;
import gloomlib.math.api.MathNode;
import gloomlib.math.api.MathParser;
import gloomlib.script.core.CompilationPipeline;
import gloomlib.script.core.CompilationPipeline.CompiledScript;
import gloomlib.script.core.ScriptIR.*;
import gloomlib.script.core.parser.ScriptParser;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 纯 Java 环境下的脚本无字面量（YAML）构建器。
 * <p>
 * 为开发者提供脱离 YAML 文本、基于链式调用直接生成内部抽象语法树（AST）和执行回调的超高速 API。
 */
public final class ScriptBuilder {

    private final Class<?> payloadClazz;
    private final ImmutableList.Builder<VarDecl> vars = ImmutableList.builder();
    private final ImmutableList.Builder<FlowNode> flow = ImmutableList.builder();
    private String scriptId;
    private ActionRegistry actionRegistry;

    private ScriptBuilder(Class<?> payloadClass) {
        this.payloadClazz = payloadClass;
        this.scriptId = "Builder-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 创建一个新的脚构建器，并绑定数据载体类。
     *
     * @param payloadClass 该脚本依赖的底层数据源类环境（如 PlayerData.class）
     */
    public static ScriptBuilder on(Class<?> payloadClass) {
        return new ScriptBuilder(payloadClass);
    }

    /**
     * 构建单个 CHECK 节点的核心方法。被顶层 check()、ConditionBuilder、MatchBuilder 共用。
     */
    private static FlowNode buildCheckNodeInternal(String variable, String op, Object value) {
        ValueParsing.validateOperator(op);

        ImmutableMap.Builder<String, Object> attrs = ImmutableMap.builder();
        attrs.put("variable", variable);
        attrs.put("op", op);

        double numericValue = 0.0;
        if (value != null) {
            Object normalizedValue = value;
            if (value instanceof String s) {
                Object parsed = ValueParsing.parseNumber(s);
                if (parsed instanceof Number) {
                    normalizedValue = parsed;
                }
            }
            attrs.put("value", normalizedValue);
            attrs.put("valueType", ValueParsing.inferType(normalizedValue));
            if (normalizedValue instanceof Number n) {
                numericValue = n.doubleValue();
            }
        }
        return new FlowNode(FlowNodeType.CHECK, "check", attrs.build(), numericValue, 0);
    }

    /**
     * 将 Object 可变参数统一转为 String 列表，以对齐 ActionNodeHandler.emit 的期望类型。
     */
    private static ImmutableList<String> toStringArgs(Object... args) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (Object arg : args) {
            builder.add(String.valueOf(arg));
        }
        return builder.build();
    }

    /**
     * 校验参数个数（与 ActionNodeHandler.parse 保持一致）。
     */
    private static void validateActionArgs(String actionName, ActionDef def, ImmutableList<String> args) {
        int expectedArgs = def.consumesPayload()
                ? Math.max(0, def.paramCount() - 1)
                : def.paramCount();
        if (args.size() != expectedArgs) {
            throw gloomlib.script.api.ScriptCompileException.create(
                    null, null,
                    gloomlib.diagnostic.DiagnosticCategory.SEMANTIC,
                    String.format("Action '%s' expects %d %s, but got %d.",
                            actionName, expectedArgs,
                            def.consumesPayload()
                                    ? "user argument(s) (payload is auto-injected as first param)"
                                    : "argument(s)",
                            args.size()));
        }
    }

    /**
     * 手动指定本脚本的来源标识，用于运行期报错追踪。
     */
    public ScriptBuilder id(String id) {
        this.scriptId = id;
        return this;
    }

    /**
     * 绑定动作注册表，以支持 {@link #action} 和 {@link #actionStore} 方法中的编译期校验。
     * <p>
     * 若不调用此方法，使用 {@code action()} / {@code actionStore()} 时将抛出
     * {@link IllegalStateException}。
     *
     * @param registry 已扫描注册过动作的 {@link ActionRegistry}
     */
    public ScriptBuilder withActionRegistry(ActionRegistry registry) {
        this.actionRegistry = registry;
        return this;
    }

    /**
     * 定义一个允许脚本中操作和提取的底层变量。
     *
     * @param varName    暴露给脚本内部计算的变量别名（例如 "hp"）
     * @param property   底层类的真实属性取值链（例如 "health" 会映射为 getHealth()）
     * @param returnType 该属性推定的真实返回类型
     */
    public ScriptBuilder defineVar(String varName, String property, IRType returnType) {
        vars.add(new VarDecl(varName, property, returnType));
        return this;
    }

    /**
     * 定义一个允许脚本操作的变量，并自动通过反射推导其返回的 IR 类型。
     * 完美支持套娃（级联）属性查找，例如 "player.inventory.itemInMainHand.amount"。
     *
     * @param varName  暴露给脚本内部计算的变量别名（例如 "数量"）
     * @param property 底层类的真实属性取值链（会映射为对应的连续 getters）
     */
    public ScriptBuilder defineVar(String varName, String property) {
        IRType inferredType = ScriptParser.PropertyResolver.resolveType(payloadClazz, property);
        return defineVar(varName, property, inferredType);
    }

    /**
     * 追加一个判断限制节点（如果此条件不符合，底层的执行器会在该位置停止，类似 Kotlin 的 takeIf）。
     * <p>
     * 操作符：{@code null, instanceof, ==, !=, >, >=, <, <=, starts_with, ends_with, matches, contains, contains_value, in, empty, between}
     * <br>
     * 所有操作符前均可加 {@code !} 前缀取反。
     *
     * @param variable 比对的变量
     * @param op       操作符（如 ">", "<", "=="，也支持 "!" 前缀反选）
     * @param value    比对的值
     */
    public ScriptBuilder check(String variable, String op, Object value) {
        flow.add(buildCheckNode(variable, op, value, null));
        return this;
    }

    /**
     * 追加一个无值判断节点，适用于 {@code null}、{@code instanceof} 等单目操作符。
     *
     * @param variable 比对的变量
     * @param op       操作符（如 "null", "!null"）
     */
    public ScriptBuilder check(String variable, String op) {
        flow.add(buildCheckNode(variable, op, null, null));
        return this;
    }

    /**
     * 追加一个带失败时回调的判断节点。
     * <p>
     * 当检测不通过时，会执行 {@code onFail} 配置的后续动作，然后结束整个脚本执行。
     *
     * @param variable 比对的变量
     * @param op       操作符
     * @param value    比对的值
     * @param onFail   条件不满足时要执行的动作配置
     */
    public ScriptBuilder check(String variable, String op, Object value, Consumer<ScriptBuilder> onFail) {
        flow.add(buildCheckNode(variable, op, value, onFail));
        return this;
    }


    /**
     * 追加一个 {@code in} 操作的判断节点，检查变量值是否在给定的候选列表中。
     * <p>
     * 编译器自动选择最优策略：≤3 项展开为多路比较，>3 项使用 Set.of() 路径。
     *
     * @param variable 比对的变量
     * @param values   候选值列表
     */
    public ScriptBuilder checkIn(String variable, Object... values) {
        ImmutableList<Object> valueList = ImmutableList.copyOf(values);
        flow.add(new FlowNode(FlowNodeType.CHECK, "check", ImmutableMap.<String, Object>builder()
                .put("variable", variable)
                .put("op", "in")
                .put("value", valueList)
                .put("valueList", valueList)
                .build()));
        return this;
    }

    /**
     * 追加一个 {@code between} 范围判断节点，检查数值变量是否在 [low, high] 闭区间内。
     *
     * @param variable 比对的变量（必须为 INT 或 DOUBLE 类型）
     * @param low      下界（含）
     * @param high     上界（含）
     */
    public ScriptBuilder checkBetween(String variable, Number low, Number high) {
        ImmutableList<Object> range = ImmutableList.of(low, high);
        flow.add(new FlowNode(FlowNodeType.CHECK, "check", ImmutableMap.<String, Object>builder()
                .put("variable", variable)
                .put("op", "between")
                .put("value", range)
                .put("valueList", range)
                .build()));
        return this;
    }

    /**
     * 内部统一构建 CHECK FlowNode 的辅助方法。
     */
    private FlowNode buildCheckNode(String variable, String op, Object value, Consumer<ScriptBuilder> onFail) {
        ImmutableMap.Builder<String, Object> attrs = ImmutableMap.builder();

        FlowNode baseNode = buildCheckNodeInternal(variable, op, value);
        attrs.putAll(baseNode.attrs());
        appendOnFailNodes(attrs, onFail);

        return new FlowNode(FlowNodeType.CHECK, "check", attrs.build(), baseNode.numericValue(), 0);
    }

    /**
     * 追加一个 ANY (OR) 复合判断节点。子条件任一成立即通过。
     */
    public ScriptBuilder checkAny(Consumer<ConditionBuilder> anyBuilder) {
        flow.add(buildCompositeNode(FlowNodeType.ANY, anyBuilder, null));
        return this;
    }

    public ScriptBuilder checkAny(Consumer<ConditionBuilder> anyBuilder, Consumer<ScriptBuilder> onFail) {
        flow.add(buildCompositeNode(FlowNodeType.ANY, anyBuilder, onFail));
        return this;
    }

    /**
     * 追加一个 ALL (AND) 复合判断节点。子条件必须全部成立才通过。
     */
    public ScriptBuilder checkAll(Consumer<ConditionBuilder> allBuilder) {
        flow.add(buildCompositeNode(FlowNodeType.ALL, allBuilder, null));
        return this;
    }


    public ScriptBuilder checkAll(Consumer<ConditionBuilder> allBuilder, Consumer<ScriptBuilder> onFail) {
        flow.add(buildCompositeNode(FlowNodeType.ALL, allBuilder, onFail));
        return this;
    }

    private FlowNode buildCompositeNode(FlowNodeType type, Consumer<ConditionBuilder> builderOpt,
                                        Consumer<ScriptBuilder> onFail) {
        ConditionBuilder cb = new ConditionBuilder();
        builderOpt.accept(cb);

        ImmutableMap.Builder<String, Object> attrs = ImmutableMap.builder();
        attrs.put("children", cb.children.build());
        appendOnFailNodes(attrs, onFail);

        return new FlowNode(type, type.key(), attrs.build());
    }


    /**
     * 追加一个 COLLECT 节点（集合谓词操作），检查集合变量中是否存在满足条件的元素。
     * <p>
     * 等价于 {@code exists} 操作：任一元素满足全部 match → 继续执行，否则 early return。
     *
     * @param variable     集合变量名（必须为 COLLECTION 类型）
     * @param matchBuilder 子条件构建器
     */
    public ScriptBuilder collectExists(String variable, Consumer<MatchBuilder> matchBuilder) {
        flow.add(buildCollectNode(variable, "exists", false, null, matchBuilder, null));
        return this;
    }

    /**
     * 追加一个 COLLECT exists 节点，带 on_fail 回调。
     */
    public ScriptBuilder collectExists(String variable, Consumer<MatchBuilder> matchBuilder,
                                       Consumer<ScriptBuilder> onFail) {
        flow.add(buildCollectNode(variable, "exists", false, null, matchBuilder, onFail));
        return this;
    }

    /**
     * 追加一个 COLLECT !exists 节点：集合中无任何元素满足条件时继续执行。
     */
    public ScriptBuilder collectNotExists(String variable, Consumer<MatchBuilder> matchBuilder) {
        flow.add(buildCollectNode(variable, "exists", true, null, matchBuilder, null));
        return this;
    }

    /**
     * 追加一个 COLLECT count 节点：统计满足条件的元素数量，存入指定变量。
     *
     * @param variable     集合变量名
     * @param store        结果存入的变量名（int 类型）
     * @param matchBuilder 子条件构建器
     */
    public ScriptBuilder collectCount(String variable, String store, Consumer<MatchBuilder> matchBuilder) {
        flow.add(buildCollectNode(variable, "count", false, store, matchBuilder, null));
        return this;
    }

    /**
     * 追加一个 COLLECT index 节点：查找首个匹配元素的索引（-1 表示未找到），存入指定变量。
     *
     * @param variable     集合变量名
     * @param store        结果存入的变量名（int 类型）
     * @param matchBuilder 子条件构建器
     */
    public ScriptBuilder collectIndex(String variable, String store, Consumer<MatchBuilder> matchBuilder) {
        flow.add(buildCollectNode(variable, "index", false, store, matchBuilder, null));
        return this;
    }

    /**
     * 追加一个 COLLECT all 节点：全部元素满足条件时继续执行，否则 early return。
     *
     * @param variable     集合变量名
     * @param matchBuilder 子条件构建器
     */
    public ScriptBuilder collectAll(String variable, Consumer<MatchBuilder> matchBuilder) {
        flow.add(buildCollectNode(variable, "all", false, null, matchBuilder, null));
        return this;
    }

    /**
     * 追加一个 COLLECT all 节点，带 on_fail 回调。
     */
    public ScriptBuilder collectAll(String variable, Consumer<MatchBuilder> matchBuilder,
                                    Consumer<ScriptBuilder> onFail) {
        flow.add(buildCollectNode(variable, "all", false, null, matchBuilder, onFail));
        return this;
    }

    /**
     * 追加一个 COLLECT !all 节点：有任一元素不满足条件时继续执行。
     */
    public ScriptBuilder collectNotAll(String variable, Consumer<MatchBuilder> matchBuilder) {
        flow.add(buildCollectNode(variable, "all", true, null, matchBuilder, null));
        return this;
    }

    /**
     * 追加一个 COLLECT find 节点：返回首个匹配元素本身（未找到则 null），存入指定变量。
     *
     * @param variable     集合变量名
     * @param store        结果存入的变量名（Object 类型）
     * @param matchBuilder 子条件构建器
     */
    public ScriptBuilder collectFind(String variable, String store, Consumer<MatchBuilder> matchBuilder) {
        flow.add(buildCollectNode(variable, "find", false, store, matchBuilder, null));
        return this;
    }

    /**
     * 追加一个 COLLECT filter 节点：收集所有匹配元素为新 List，存入指定变量。
     *
     * @param variable     集合变量名
     * @param store        结果存入的变量名（List 类型）
     * @param matchBuilder 子条件构建器
     */
    public ScriptBuilder collectFilter(String variable, String store, Consumer<MatchBuilder> matchBuilder) {
        flow.add(buildCollectNode(variable, "filter", false, store, matchBuilder, null));
        return this;
    }

    private FlowNode buildCollectNode(String variable, String op, boolean negate,
                                      String store, Consumer<MatchBuilder> matchBuilder,
                                      Consumer<ScriptBuilder> onFail) {
        return buildCollectNode(variable, op, negate, store, null, matchBuilder, onFail);
    }

    private FlowNode buildCollectNode(String variable, String op, boolean negate,
                                      String store, IterateMode iterateMode,
                                      Consumer<MatchBuilder> matchBuilder,
                                      Consumer<ScriptBuilder> onFail) {
        MatchBuilder mb = new MatchBuilder();
        matchBuilder.accept(mb);

        ImmutableMap.Builder<String, Object> attrs = ImmutableMap.builder();
        attrs.put("variable", variable);
        attrs.put("collectOp", op.toUpperCase());
        attrs.put("collectNegate", negate);
        attrs.put("matchFlow", mb.conditions.build());
        if (iterateMode != null) {
            attrs.put("iterateMode", iterateMode.name());
        }
        if (store != null) {
            IRType returnType = switch (op.toUpperCase()) {
                case "FIND" -> IRType.OBJECT;
                case "FILTER" -> IRType.COLLECTION;
                default -> IRType.INT;
            };
            attrs.put("store", store);
            attrs.put("returnType", returnType);
        }
        appendOnFailNodes(attrs, onFail);

        return new FlowNode(FlowNodeType.COLLECT, "collect", attrs.build());
    }


    /**
     * 追加一个要触发的动作节点（Action）。
     *
     * @param actionName 在 ActionRegistry 中已经注册好的 @ScriptAction 的名字（比如
     *                   "sendMessage"）
     * @param args       顺序填入的参数列表（支持 {@code "{变量名}"} 的模板插值法）
     */
    public ScriptBuilder action(String actionName, Object... args) {
        ActionDef def = requireRegistry().lookup(actionName);
        ImmutableList<String> argsList = toStringArgs(args);
        validateActionArgs(actionName, def, argsList);
        flow.add(new FlowNode(FlowNodeType.ACTION, "action", ImmutableMap.<String, Object>builder()
                .put("action", actionName)
                .put("args", argsList)
                .put("def", def)
                .build()));
        return this;
    }

    /**
     * 追加一个要触发的动作节点（Action）并捕获它的返回值。
     * 引擎编译期会自动识别该 action 的返回值类型，并为你开辟这个储值槽。
     *
     * @param store      捕获返回值的局部变量名称
     * @param actionName 在 ActionRegistry 中已经注册好的 @ScriptAction 名字
     * @param args       顺序填入的参数列表
     */
    public ScriptBuilder actionStore(String store, String actionName, Object... args) {
        ActionDef def = requireRegistry().lookup(actionName);
        ImmutableList<String> argsList = toStringArgs(args);
        validateActionArgs(actionName, def, argsList);

        // 验证返回值（与 ActionNodeHandler.parse 对齐）
        if (def.returnType() == void.class || def.returnType() == Void.class) {
            throw gloomlib.script.api.ScriptCompileException.create(
                    null, null,
                    gloomlib.diagnostic.DiagnosticCategory.SEMANTIC,
                    String.format("Action '%s' does not return a value, cannot store to '%s'", actionName, store));
        }

        IRType returnIRType = IRType.fromClass(def.returnType());

        flow.add(new FlowNode(FlowNodeType.ACTION, "action", ImmutableMap.<String, Object>builder()
                .put("store", store)
                .put("action", actionName)
                .put("args", argsList)
                .put("def", def)
                .put("returnType", returnIRType)
                .build()));
        return this;
    }

    /**
     * 流程提前终止，等价于 {@code return null}。
     */
    public ScriptBuilder returnEarly() {
        flow.add(new FlowNode(FlowNodeType.RETURN, "return", ImmutableMap.of()));
        return this;
    }

    /**
     * 返回字符串字面量、变量或模板字符串。
     * <ul>
     * <li>{@code "{dmg}"} → 返回变量值（自动识别）</li>
     * <li>{@code "HP:{hp} 伤:{dmg}"} → invokedynamic 模板拼接</li>
     * <li>{@code "固定文本"} → 字符串字面量</li>
     * </ul>
     */
    public ScriptBuilder returnValue(String value) {
        flow.add(new FlowNode(FlowNodeType.RETURN, "return", ImmutableMap.of("value", value)));
        return this;
    }

    /**
     * 返回整数字面量。
     */
    public ScriptBuilder returnValue(int value) {
        flow.add(new FlowNode(FlowNodeType.RETURN, "return", ImmutableMap.of("value", value)));
        return this;
    }

    /**
     * 返回浮点字面量。
     */
    public ScriptBuilder returnValue(double value) {
        flow.add(new FlowNode(FlowNodeType.RETURN, "return", ImmutableMap.of("value", value)));
        return this;
    }

    /**
     * 返回布尔字面量。
     */
    public ScriptBuilder returnValue(boolean value) {
        flow.add(new FlowNode(FlowNodeType.RETURN, "return", ImmutableMap.of("value", value)));
        return this;
    }


    /**
     * 直接返回变量的原始值（零开销路径）。
     * <p>
     * 与 {@code returnValue("{varName}")} 不同，此方法走 {@code variable} 属性路径，
     * 绕过字符串模板解析，直接加载局部变量并装箱返回。
     *
     * @param varName 已通过 {@link #defineVar} 声明的变量名
     */
    public ScriptBuilder returnVar(String varName) {
        flow.add(new FlowNode(FlowNodeType.RETURN, "return", ImmutableMap.of("variable", varName)));
        return this;
    }

    /**
     * 返回集合字面量。
     * <p>
     * 每个元素支持：字面量、{@code "{变量名}"} 模板插值、或模板字符串。
     * 编译后通过 {@code List.of(Object...)} 构造不可变列表。
     *
     * @param elements 集合元素（支持 String / Number / Boolean 及变量模板）
     */
    public ScriptBuilder returnList(Object... elements) {
        ImmutableList<Object> list = ImmutableList.copyOf(elements);
        flow.add(new FlowNode(FlowNodeType.RETURN, "return", ImmutableMap.of("value", list)));
        return this;
    }


    /**
     * 追加一个多分支选择节点（Switch）。
     *
     * @param variable 比对的变量
     * @param config   分支流程构造器
     */
    public ScriptBuilder switchBranch(String variable, Consumer<SwitchBuilder> config) {
        SwitchBuilder builder = new SwitchBuilder(payloadClazz, actionRegistry);
        config.accept(builder);
        flow.add(new FlowNode(FlowNodeType.SWITCH, "switch", ImmutableMap.<String, Object>builder()
                .put("variable", variable)
                .put("cases", builder.buildCases())
                .build()));
        return this;
    }

    /**
     * 追加一个数学表达式计算节点，将计算结果存入指定变量。
     * <p>
     * 表达式中可通过 {@code {变量名}} 引用已声明的变量，支持四则运算、幂运算、比较运算、
     * 三元运算符、内置函数等。编译器会自动进行常量折叠与代数恒等优化。
     *
     * @param store 计算结果存入的变量名
     * @param expr  数学表达式（如 {@code "{hp} * 0.5 + 10"}）
     */
    public ScriptBuilder math(String store, String expr) {
        MathNode root = MathParser.parse(expr);
        flow.add(new FlowNode(FlowNodeType.MATH, "math", ImmutableMap.of(
                "store", store,
                "expr", expr,
                "mathNode", root)));
        return this;
    }

    /**
     * 追加一个方法调用节点，在 payload 对象上调用指定方法（无返回值捕获）。
     * <p>
     * 与 {@link #action} 不同，{@code invoke} 直接调用 payload 上任意公开方法，
     * 无需预注册 {@code @ScriptAction}。编译后通过字节码直接调用，零反射开销。
     *
     * @param methodName 方法名（如 {@code "setHealth"}）
     * @param args       顺序参数（支持 {@code "{变量名}"} 模板插值）
     */
    public ScriptBuilder invoke(String methodName, Object... args) {
        flow.add(buildInvokeNode(methodName, null, null, args));
        return this;
    }

    /**
     * 在指定目标变量上调用方法（无返回值捕获）。
     *
     * @param methodName 方法名
     * @param target     目标变量引用（如 {@code "{player}"}）
     * @param args       顺序参数
     */
    public ScriptBuilder invokeOn(String methodName, String target, Object... args) {
        flow.add(buildInvokeNode(methodName, target, null, args));
        return this;
    }

    /**
     * 在 payload 对象上调用方法并捕获返回值。
     *
     * @param store      返回值存入的变量名
     * @param methodName 方法名
     * @param args       顺序参数
     */
    public ScriptBuilder invokeStore(String store, String methodName, Object... args) {
        flow.add(buildInvokeNode(methodName, null, store, args));
        return this;
    }

    /**
     * 在指定目标变量上调用方法并捕获返回值。
     *
     * @param store      返回值存入的变量名
     * @param methodName 方法名
     * @param target     目标变量引用
     * @param args       顺序参数
     */
    public ScriptBuilder invokeStoreOn(String store, String methodName, String target, Object... args) {
        flow.add(buildInvokeNode(methodName, target, store, args));
        return this;
    }

    private FlowNode buildInvokeNode(String methodName, String target, String store, Object... args) {
        validateInvokeMethod(methodName);
        ImmutableMap.Builder<String, Object> attrs = ImmutableMap.builder();
        attrs.put("methodName", methodName);
        attrs.put("args", toStringArgs(args));
        if (target != null) attrs.put("target", target);
        if (store != null) attrs.put("store", store);
        return new FlowNode(FlowNodeType.INVOKE, "invoke", attrs.build());
    }

    /**
     * 通用 COLLECT 节点构建器，支持完整配置（含迭代模式、全部操作类型）。
     * <p>
     * 适用于需要精细控制迭代模式（如 Map 的 KEYS / ENTRIES 遍历）的高级场景。
     * 简单场景请直接使用 {@link #collectExists} 等便捷方法。
     *
     * @param variable 集合/Map 变量名
     * @param config   配置回调
     */
    public ScriptBuilder collect(String variable, Consumer<CollectConfig> config) {
        CollectConfig cc = new CollectConfig();
        config.accept(cc);
        if (cc.matchConsumer == null) {
            throw new IllegalStateException("collect() requires match() to be called.");
        }
        flow.add(buildCollectNode(variable, cc.op, cc.negate, cc.store,
                cc.iterateMode, cc.matchConsumer, cc.onFail));
        return this;
    }

    /**
     * 编译为副作用型处理器（无返回值场景）。
     *
     * @return 运行速度等同于原生硬编码 Java 代码的回调函数
     */
    public Consumer<Object> compile() {
        return buildCompiledScript(Object.class).newHandler();
    }

    /**
     * 编译为计算函数（脚本包含 {@link FlowNodeType#RETURN} 节点时使用）。
     *
     * @return Function&lt;Object, Object&gt;，入参为 payload 对象，返回值为 RETURN
     * 指定变量的装箱值
     */
    public Function<Object, Object> compileAsFunction() {
        return buildCompiledScript(Object.class).newFunction();
    }

    /**
     * 编译为强类型计算函数。
     * 包含在运行期间零损耗的绝对类型校验机制（在脚本编译阶段实施类型阻断）。
     *
     * @param expectedReturnType 期待输出的返回类型
     * @param <T>                Payload 参数的具体类型（在构建器实例化时确定）
     * @param <R>                返回结果的具体类型
     * @return 强类型 Function，直接跳过 instanceOf 开销
     */
    @SuppressWarnings("unchecked")
    public <T, R> Function<T, R> compileTypedFunction(Class<R> expectedReturnType) {
        return (Function<T, R>) buildCompiledScript(expectedReturnType).newFunction();
    }

    /**
     * 动态编译并直接实例化为指定的零损耗字节码接口（Zero-Boxing Adaptation）。
     * <p>
     * 在后台直接抛弃通用的 Function&lt;Object, Object&gt;。通过反射分析给定接口方法的真实参数与返回值，
     * 利用 ASM 从字节码根源上自适应消除拆开箱损耗（例如直接通过 {@code IRETURN} 返回 int 给
     * {@code ToIntFunction}）。
     *
     * @param expectedInterfaceType 目标单方法接口的 Class，例如
     *                              {@code java.util.function.ToIntFunction.class}
     * @param <T>                   具体接口类型的泛型
     * @return 编译就绪且无拆装箱性能损耗的代理实例
     */
    public <T> T compileInterface(Class<T> expectedInterfaceType) {
        ScriptUnit unit = new ScriptUnit(scriptId, payloadClazz.getName(), 0, vars.build(), flow.build(), false);
        return new CompilationPipeline().compileInterface(unit, expectedInterfaceType);
    }


    private CompiledScript buildCompiledScript(Class<?> expectedReturnType) {
        ScriptUnit unit = new ScriptUnit(scriptId, payloadClazz.getName(), 0, vars.build(), flow.build(), false);
        return new CompilationPipeline().compile(unit, expectedReturnType);
    }

    /**
     * 将 onFail 回调编译为子脚本并追加到属性中。3 个 build*Node 方法共用。
     */
    private void appendOnFailNodes(ImmutableMap.Builder<String, Object> attrs, Consumer<ScriptBuilder> onFail) {
        if (onFail == null) return;
        ScriptBuilder failBuilder = new ScriptBuilder(payloadClazz);
        failBuilder.id(this.scriptId + "-fail");
        failBuilder.actionRegistry = this.actionRegistry;
        onFail.accept(failBuilder);
        attrs.put("onFailNodes", failBuilder.flow.build());
    }

    private static void validateInvokeMethod(String methodName) {
        ValueParsing.validateInvokeMethod(methodName);
    }

    /**
     * 获取已绑定的 ActionRegistry，未绑定则抛异常。
     */
    private ActionRegistry requireRegistry() {
        if (actionRegistry == null) {
            throw new IllegalStateException(
                    "ActionRegistry not set. Call withActionRegistry() before using action()/actionStore().");
        }
        return actionRegistry;
    }

    /**
     * 用于构建复合条件子项的建造器。
     */
    public static final class ConditionBuilder {
        private final ImmutableList.Builder<FlowNode> children = ImmutableList.builder();

        private ConditionBuilder() {
        }

        public ConditionBuilder check(String variable, String op, Object value) {
            children.add(buildCheckNodeInternal(variable, op, value));
            return this;
        }

        public ConditionBuilder check(String variable, String op) {
            children.add(buildCheckNodeInternal(variable, op, null));
            return this;
        }

        public ConditionBuilder any(Consumer<ConditionBuilder> anyBuilder) {
            ConditionBuilder cb = new ConditionBuilder();
            anyBuilder.accept(cb);
            children.add(new FlowNode(FlowNodeType.ANY, "any", ImmutableMap.of("children", cb.children.build())));
            return this;
        }

        public ConditionBuilder all(Consumer<ConditionBuilder> allBuilder) {
            ConditionBuilder cb = new ConditionBuilder();
            allBuilder.accept(cb);
            children.add(new FlowNode(FlowNodeType.ALL, "all", ImmutableMap.of("children", cb.children.build())));
            return this;
        }
    }

    /**
     * Switch 分支的内部构造器
     */
    public static final class SwitchBuilder {
        private final Class<?> payloadClazz;
        private final ActionRegistry actionRegistry;
        private final ImmutableMap.Builder<String, ImmutableList<FlowNode>> cases = ImmutableMap.builder();

        private SwitchBuilder(Class<?> payloadClazz, ActionRegistry actionRegistry) {
            this.payloadClazz = payloadClazz;
            this.actionRegistry = actionRegistry;
        }

        /**
         * 构造一个 Case 分支区块（支持 String、Enum 或 Number 自动转字符串底层表示）。
         *
         * @param caseKey       匹配的确切键
         * @param branchBuilder 分支内的脚本流程配置
         */
        public SwitchBuilder caseOf(Object caseKey, Consumer<ScriptBuilder> branchBuilder) {
            ScriptBuilder subBuilder = new ScriptBuilder(payloadClazz);
            subBuilder.id("SwitchCase-" + caseKey);
            subBuilder.actionRegistry = this.actionRegistry;
            branchBuilder.accept(subBuilder);
            cases.put(String.valueOf(caseKey), subBuilder.flow.build());
            return this;
        }

        private ImmutableMap<String, ImmutableList<FlowNode>> buildCases() {
            return cases.build();
        }
    }

    /**
     * COLLECT 节点的迭代模式。
     * <p>
     * 仅对 Map 类型的集合变量有效。对 List / Set / Array 自动忽略。
     */
    public enum IterateMode {
        /** 遍历 Map.values()（默认模式，向后兼容） */
        VALUES,
        /** 遍历 Map.keySet() */
        KEYS,
        /** 遍历 Map.entrySet()，在 match 谓词中通过 $key / $value 访问 */
        ENTRIES
    }

    /**
     * 通用 COLLECT 配置构建器，支持全部操作类型与迭代模式。
     */
    public static final class CollectConfig {
        private String op = "exists";
        private boolean negate = false;
        private String store = null;
        private IterateMode iterateMode = null;
        private Consumer<MatchBuilder> matchConsumer;
        private Consumer<ScriptBuilder> onFail;

        private CollectConfig() {
        }

        /** 设置为 exists 操作（默认）。 */
        public CollectConfig exists() { this.op = "exists"; return this; }

        /** 设置为 all 操作。 */
        public CollectConfig all() { this.op = "all"; return this; }

        /** 设置为 count 操作，结果存入指定变量。 */
        public CollectConfig count(String store) { this.op = "count"; this.store = store; return this; }

        /** 设置为 index 操作（首个匹配索引），结果存入指定变量。 */
        public CollectConfig index(String store) { this.op = "index"; this.store = store; return this; }

        /** 设置为 find 操作（首个匹配元素），结果存入指定变量。 */
        public CollectConfig find(String store) { this.op = "find"; this.store = store; return this; }

        /** 设置为 filter 操作（所有匹配元素），结果存入指定变量。 */
        public CollectConfig filter(String store) { this.op = "filter"; this.store = store; return this; }

        /** 取反操作（!exists, !all）。 */
        public CollectConfig negate() { this.negate = !this.negate; return this; }

        /** 设置迭代模式（KEYS / ENTRIES / VALUES），仅对 Map 变量生效。 */
        public CollectConfig iterate(IterateMode mode) { this.iterateMode = mode; return this; }

        /** 设置匹配条件。 */
        public CollectConfig match(Consumer<MatchBuilder> matcher) { this.matchConsumer = matcher; return this; }

        /** 设置条件不满足时的回调。 */
        public CollectConfig onFail(Consumer<ScriptBuilder> fail) { this.onFail = fail; return this; }
    }

    /**
     * COLLECT 节点的 match 子条件构建器。
     */
    public static final class MatchBuilder {
        private final ImmutableList.Builder<FlowNode> conditions = ImmutableList.builder();

        private MatchBuilder() {
        }

        /**
         * 添加一个子条件（对元素自身 $it 进行比对）。
         *
         * @param op    操作符（如 "contains", ">", "=="）
         * @param value 比对值
         */
        public MatchBuilder match(String op, Object value) {
            conditions.add(buildCheckNodeInternal("$it", op, value));
            return this;
        }

        /**
         * 添加一个无值的子条件（如 "null"）。
         */
        public MatchBuilder match(String op) {
            conditions.add(buildCheckNodeInternal("$it", op, null));
            return this;
        }

        /**
         * 添加一个属性级子条件（对元素的属性进行比对）。
         *
         * @param variable 属性路径（如 "type.name"、"amount"），或 "$it" 表示元素自身
         * @param op       操作符
         * @param value    比对值
         */
        public MatchBuilder check(String variable, String op, Object value) {
            conditions.add(buildCheckNodeInternal(variable, op, value));
            return this;
        }

        /**
         * 添加一个属性级无值子条件。
         */
        public MatchBuilder check(String variable, String op) {
            conditions.add(buildCheckNodeInternal(variable, op, null));
            return this;
        }

        /**
         * 追加一个 ANY (OR) 复合子条件。子条件任一成立即视为该 match 项通过。
         */
        public MatchBuilder any(Consumer<ConditionBuilder> anyBuilder) {
            ConditionBuilder cb = new ConditionBuilder();
            anyBuilder.accept(cb);
            conditions.add(new FlowNode(FlowNodeType.ANY, "any",
                    ImmutableMap.of("children", cb.children.build())));
            return this;
        }

        /**
         * 追加一个 ALL (AND) 复合子条件。子条件必须全部成立才视为该 match 项通过。
         */
        public MatchBuilder all(Consumer<ConditionBuilder> allBuilder) {
            ConditionBuilder cb = new ConditionBuilder();
            allBuilder.accept(cb);
            conditions.add(new FlowNode(FlowNodeType.ALL, "all",
                    ImmutableMap.of("children", cb.children.build())));
            return this;
        }
    }
}

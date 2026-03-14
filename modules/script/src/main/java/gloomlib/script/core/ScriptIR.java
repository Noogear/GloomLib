package gloomlib.script.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import gloomlib.script.api.ScriptCompileException;
import gloomlib.script.core.optimizer.ScriptOptimizer;
import org.objectweb.asm.MethodVisitor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本引擎中间表示（IR）体系。
 * <p>
 * 包含全部 IR 节点定义、流程节点类型枚举、处理器接口和节点能力枚举。
 */
@SuppressWarnings("null")
public final class ScriptIR {

    /**
     * 模板字符串占位符正则。
     * 支持普通变量 {@code {hp}}、窄化点链 {@code {entity.name}}、安全访问 {@code {entity?.name}}
     * 和索引访问 {@code {list[0]}}、{@code {map[key]}}、{@code {data[{idx}]}}、{@code {entity.tags[0]}}。
     */
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{([\\w]+(?:[?]?\\.\\w+)*(?:\\[[^\\]]+\\])*)\\}");

    /**
     * 括号内动态变量引用模式（如 {@code [0]}、{@code [key]}、{@code [{idx}]}）。
     * 用于从索引引用中提取动态变量名。
     */
    private static final Pattern DYN_INDEX_VAR = Pattern.compile("\\{(\\w+)\\}");


    private ScriptIR() {
    }

    /**
     * 判断字符串是否为纯单变量引用，如 "{dmg}"（全部内容就是一个占位符，无其他文本）。
     * 注意：点链引用 "{entity.name}" 和安全访问 "{entity?.name}" 不属于单变量。
     */
    public static boolean isSingleVar(String s) {
        if (s == null || s.length() <= 2) return false;
        if (s.charAt(0) != '{' || s.charAt(s.length() - 1) != '}') return false;
        if (s.indexOf('{', 1) != -1) return false;
        // 含点号或问号的是点链引用/安全访问，不是单变量
        String inner = s.substring(1, s.length() - 1);
        return !inner.contains(".") && !inner.contains("?") && !inner.contains("[");
    }

    /**
     * 判断字符串是否为纯单点链引用，如 "{entity.name}" 或安全访问 "{entity?.name}"。
     * <p>
     * 与 {@link #isSingleVar} 互斥：内容包含 {@code .} 的为点链引用，不包含的为单变量。
     */
    public static boolean isDottedSingleRef(String s) {
        if (s == null || s.length() <= 2) return false;
        if (s.charAt(0) != '{' || s.charAt(s.length() - 1) != '}') return false;
        if (s.indexOf('{', 1) != -1) return false;
        String inner = s.substring(1, s.length() - 1);
        return inner.contains(".");
    }


    /**
     * 判断字符串是否包含模板占位符（如 "HP:{hp} 伤害:{dmg}"）。
     */
    public static boolean isTemplate(String s) {
        return s != null && TEMPLATE_PATTERN.matcher(s).find();
    }

    /**
     * 判断模板 part 是否为窄化点链引用，如 {@code "entity.name"} 或 {@code "entity?.name"}。
     */
    public static boolean isDottedPart(String part) {
        return part != null && part.contains(".");
    }

    /**
     * 判断模板占位符是否包含索引访问（如 {@code "list[0]"} 或 {@code "map[key]"}）。
     */
    public static boolean isIndexedRef(String part) {
        return part != null && part.contains("[");
    }

    /**
     * 判断点链引用是否为安全访问模式（含 {@code ?.}）。
     */
    public static boolean isSafeAccess(String part) {
        return part != null && part.contains("?.");
    }

    /**
     * 将安全访问表达式中的 {@code ?.} 规范化为 {@code .}，用于属性解析。
     */
    public static String normalizeDotted(String part) {
        return part.replace("?.", ".");
    }

    /**
     * 拆分窄化点链引用为 [varName, propertyPath]。
     * 支持安全访问语法：{@code "entity?.name"} → {@code ["entity", "name"]}。
     * 例： {@code "entity.name"} → {@code ["entity", "name"]}。
     */
    public static String[] splitDotted(String part) {
        String normalized = normalizeDotted(part);
        int dot = normalized.indexOf('.');
        return new String[]{normalized.substring(0, dot), normalized.substring(dot + 1)};
    }

    /**
     * 从模板字符串中提取所有占位符的基础变量名（点链取头部，去重并保持首次出现顺序）。
     * <p>
     * 例：{@code "HP:{hp} 伤:{entity.dmg} [{hp}]"} → {@code ["hp", "entity"]}
     * <p>
     * 索引引用中的动态变量也会被提取：{@code "值:{list[{idx}]}"} → {@code ["list", "idx"]}
     * <p>
     * 直接复用已编译的 {@link #TEMPLATE_PATTERN}，比调用方自行 {@code Pattern.compile}
     * 性能优一至两个数量级（Pattern.compile 平均耗时约为此方法整体的 10–100x）。
     *
     * @param template 含占位符的字符串
     * @return 基础变量名列表（不含重复项，保持首次出现顺序）
     */
    public static List<String> templateBaseVars(String template) {
        List<String> vars = new ArrayList<>();
        Matcher m = TEMPLATE_PATTERN.matcher(template);
        while (m.find()) {
            String part = m.group(1);
            // 截断索引部分以获取基础变量链
            String baseChain = isIndexedRef(part) ? part.substring(0, part.indexOf('[')) : part;
            String base = isDottedPart(baseChain) ? splitDotted(baseChain)[0] : baseChain;
            if (!vars.contains(base)) {    // 模板变量数量通常 ≤ 4，线性扫描优于 Set（无哈希开销，缓存友好）
                vars.add(base);
            }
            // 提取括号内动态变量引用（如 {idx}）
            if (isIndexedRef(part)) {
                Matcher dynM = DYN_INDEX_VAR.matcher(part);
                while (dynM.find()) {
                    String dynVar = dynM.group(1);
                    if (!vars.contains(dynVar)) {
                        vars.add(dynVar);
                    }
                }
            }
        }
        return vars;
    }

    /**
     * 解析模板字符串，提取交替的字面量和变量名列表。
     * 例如 "HP:{hp}!" → ["HP:", "hp", "!"]
     */
    public static List<String> parseTemplate(String template) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                parts.add(template.substring(last, matcher.start()));
            }
            parts.add(matcher.group(1));
            last = matcher.end();
        }
        if (last < template.length()) {
            parts.add(template.substring(last));
        }
        return parts;
    }

    /**
     * 基础枚举核心，用于支持 switch 查表等干净的原始匹配逻辑。
     */
    public enum BaseType {
        INT, LONG, DOUBLE, STRING, ENUM, OBJECT, BOOLEAN, COLLECTION, MAP
    }

    /**
     * 流程节点类型枚举，纯粹的类型标签。
     * <p>
     * 内置节点使用对应枚举值，运行时注册的自定义节点使用 {@link #CUSTOM}，
     * 通过 {@link FlowNode#nodeKey} 区分具体类型。
     * <p>
     * 所有注册、查询与分发逻辑由 {@link NodeRegistry} 统一管理。
     */
    public enum FlowNodeType {
        ACTION, RETURN, CHECK, SWITCH, ANY, ALL, COLLECT, MATH, INVOKE,
        /** 运行时注册的自定义节点类型。通过 nodeKey 区分具体类型。 */
        CUSTOM;

        /**
         * 返回内置类型的 shorthand key（小写枚举名），CUSTOM 返回 {@code null}。
         */
        public String key() {
            return this == CUSTOM ? null : name().toLowerCase();
        }
    }

    public enum NodeCapability {
        /**
         * 终止流：此节点之后的代码不可达。
         * 查询方：DCE、{@link NodeMutator#filterAttr}。
         */
        TERMINATES_FLOW,

        /**
         * 外部副作用：删除此节点会改变外部可观测行为（如 I/O、集合遍历）。
         * <p>
         * 仅写入本地变量（如 MATH 的 DSTORE）不算外部副作用。
         * 查询方：deadProducerElimination（守卫）、variableInlining 前瞻扩展。
         */
        SIDE_EFFECT,

        /**
         * 纯守卫：无副作用、不产出变量，仅做条件分支控制。
         * 变量内联时可被安全跳过。
         * <p>
         * 与 {@link #SIDE_EFFECT}、{@link #TERMINATES_FLOW} 互斥。
         */
        PURE_GUARD,

        /**
         * 点链参数下沉：args 中可能包含 {var.prop} 引用。
         * 查询方：hasTopLevelDottedRefTo、variableInlining 下沉检测。
         */
        DOTTED_ARG_SINK,

        /**
         * 谓词安全：可在 COLLECT match 块内使用。
         * 查询方：CollectNodeHandler.validateMatchNode。
         */
        PREDICATE_SAFE
    }


    /**
     * 流程节点处理器接口，统一解析与字节码发射。
     */
    public interface FlowNodeHandler {
        FlowNode parse(ParseContext ctx);

        void emit(FlowNode node, MethodVisitor mv, CompilationContext ctx);

        EnumSet<NodeCapability> capabilities();
    }

    /**
     * 允许节点在内联路径中发射代码并将结果留在操作数栈顶（不执行 STORE / POP）。
     * <p>
     * 消除 ArgInliningHelper、ReturnNodeHandler 等消费方对 {@link FlowNodeType} 的硬编码分发，
     * 新节点类型只需实现此接口即可自动参与内联优化。
     */
    public interface InlineEmitter {
        /**
         * 发射当前节点的字节码，将计算结果留在操作数栈顶。
         * 与 {@link FlowNodeHandler#emit} 的区别：后者会 STORE / POP 返回值。
         */
        void emitInline(FlowNode node, MethodVisitor mv, CompilationContext ctx);

        /**
         * 返回 {@link #emitInline} 留在栈顶的原始类型，用于消费方的装箱 / 拆箱适配。
         */
        IRType inlineResultType(FlowNode node, CompilationContext ctx);
    }

    /**
     * 实现该接口的处理器表示其是一个条件判断原语，能够向外统一提供条件比较的底层逻辑方法。
     * 允许复合节点（如 ANY/ALL）多态调用以判定任何条件，而不必强制下转为 CheckNodeHandler。
     */
    public interface ConditionEmitter {
        /**
         * 发射单个条件的比较字节码。
         * 返回该条件成立时控制流应当执行的 Opcodes 跳转指令（例如 Opcodes.IFEQ）。
         */
        int emitCondition(FlowNode node, MethodVisitor mv, CompilationContext ctx);
    }


    /**
     * 允许内部节点暴露自己所包含的所有逻辑上的子流程节点（如条件块产生的子集、any块的 children），
     * 供 ScriptOptimizer 进行生命周期遍历而无需猜想具体变量。
     */
    public interface NodeTraverser {
        Iterable<FlowNode> traverseChildren(FlowNode node);
    }


    /**
     * 允许节点在编译前自身提取编译期常量，代替优化器寻找。
     * 结果需追加至 defs，提取完成后可通过 `withAttr` 返回带标记的新节点以备字节码内消洗。
     */
    public interface ConstantHoister {
        FlowNode hoistConstants(FlowNode node, List<CompilationContext.ConstantDef> defs, int[] counter);
    }

    /**
     * 允许流节点自行判定在没有额外环境约束时能否得出绝对真伪（常量折叠）。
     */
    public interface ConstantFolder {
        Boolean evaluateFold(FlowNode node, CompilationContext ctx);
    }

    /**
     * 允许流节点报告其检查的变量名，并在已有约束下尝试被折叠，或对现有约束进行更新。
     */
    public interface RangePropagator {
        default String getConstrainedVariable(FlowNode node) {
            return node.getAttrOrDefault("variable", null);
        }

        Boolean tryFoldWithRange(FlowNode node, ScriptOptimizer.ValueRange range);

        ScriptOptimizer.ValueRange updateRange(FlowNode node, ScriptOptimizer.ValueRange range);
    }

    /**
     * 允许流节点在其结构中报告读取的特征变量，并提供吸收 Action 的虚拟闭包替换支持（用于按需下沉属性读取）。
     */
    public interface VariableConsumer {
        default String getConsumedVariable(FlowNode node) {
            return node.getAttrOrDefault("variable", null);
        }

        default java.util.List<String> getAllConsumedVariables(FlowNode node) {
            String single = getConsumedVariable(node);
            return single != null ? java.util.List.of(single) : java.util.List.of();
        }

        default FlowNode inlineAction(FlowNode node, FlowNode inlineHook) {
            return node.withoutAttr("variable").withAttr("conditionAction", inlineHook);
        }
    }


    /**
     * 允许节点自行校验参数与上下文变量之间的类型兼容性。
     */
    public interface TypeValidator {
        void validateTypes(FlowNode node, gloomlib.script.core.CompilationContext ctx);
    }


    /**
     * 允许流节点根据上下文的权重表对内部分支进行重新排列重组，以提升短路命中率。
     */
    public interface BranchReorderer {
        FlowNode reorderBranches(FlowNode node, gloomlib.script.core.CompilationContext ctx);
    }

    /**
     * 允许对树形流节点的子级迭代执行映射回调并安全重建节点（主要用于静态常量提升阶段修剪树干）。
     */
    public interface NodeMutator extends NodeTraverser {
        FlowNode mapChildren(FlowNode node, java.util.function.Function<FlowNode, FlowNode> mapper);

        /**
         * 过滤并映射子节点：保留满足 predicate 的子节点，对保留的子节点应用 mapper。
         * <p>
         * 由各 handler 自行根据自身属性布局实现（如 "children"、"matchFlow"、"onFailNodes"），
         * 避免优化器硬编码属性名称。
         */
        FlowNode filterChildren(FlowNode node, java.util.function.Predicate<FlowNode> keep,
                                java.util.function.Function<FlowNode, FlowNode> mapper);

        /**
         * 对节点的指定子列表属性执行过滤+映射。
         * 供 {@link #filterChildren} 实现复用。
         * <p>
         * 遇到 {@link NodeCapability#TERMINATES_FLOW} 的子节点后截断后续不可达节点。
         */
        static FlowNode filterAttr(FlowNode node, String attrKey,
                                   java.util.function.Predicate<FlowNode> keep,
                                   java.util.function.Function<FlowNode, FlowNode> mapper) {
            ImmutableList<FlowNode> list = node.getAttrOrDefault(attrKey, null);
            if (list == null) return node;
            boolean changed = false;
            ImmutableList.Builder<FlowNode> filtered = ImmutableList.builder();
            for (FlowNode child : list) {
                if (!keep.test(child)) { changed = true; continue; }
                FlowNode mapped = mapper.apply(child);
                filtered.add(mapped);
                if (mapped != child) changed = true;
                if (mapped.handler().capabilities().contains(NodeCapability.TERMINATES_FLOW)) {
                    changed = true;
                    break;
                }
            }
            return changed ? node.withAttr(attrKey, filtered.build()) : node;
        }
    }

    /**
     * 允许流节点汇报自身是对某个变量值的产出者，并提供剥离产出标记的能力。
     */
    public interface VariableProducer {
        String getProducedVariable(FlowNode node);

        /**
         * 剥离节点中的“产出变量”标记，返回纯执行节点。由具体 handler 处理自己的 attr 布局。
         */
        FlowNode stripProducedVariable(FlowNode node);

        /**
         * 若该节点产出一个编译期已知的常量值，返回该值；否则返回 {@code null}。
         * <p>
         * 用于 {@link gloomlib.script.core.optimizer.ScriptOptimizer} 值域传播：
         * 常量 MATH 产出可直接注入后续 CHECK 的约束，使其折叠为恒真/恒假。
         */
        default Object getProducedConstantValue(FlowNode node) {
            return null;
        }

        /**
         * 解析该节点产出变量的 IR 类型，用于编译管线的槽位分配。
         * <p>
         * 默认从节点 {@code returnType} 属性读取；handler 可在此处内聚更复杂的推导逻辑
         * （如 INVOKE 的反射解析），避免编译管线对 {@link FlowNodeType} 的硬编码分发。
         *
         * @param node         含 store 属性的流节点
         * @param payloadClass 脚本 payload 具体类
         * @param unit         当前脚本单元（用于变量声明查找）
         */
        default IRType resolveProducedType(FlowNode node, Class<?> payloadClass, ScriptUnit unit) {
            return node.getAttrOrDefault("returnType", IRType.OBJECT);
        }
    }

    /**
     * 顶层脚本单元。
     */
    public record ScriptUnit(
            String id,
            String payloadClass,
            int priority,
            ImmutableList<VarDecl> vars,
            ImmutableList<FlowNode> flow,
            boolean ignoreCancelled) {
        public ScriptUnit withFlow(ImmutableList<FlowNode> newFlow) {
            return new ScriptUnit(id, payloadClass, priority, vars, newFlow, ignoreCancelled);
        }

        public ScriptUnit withVars(ImmutableList<VarDecl> newVars) {
            return new ScriptUnit(id, payloadClass, priority, newVars, flow, ignoreCancelled);
        }
    }

    /**
     * 变量声明。
     * <p>
     * {@code property} 为特殊哨兵值 {@code "$self"} 时，表示该变量是 payload 的别名，
     * 编译期直接复用 slot 1，不做任何属性提取。
     */
    public record VarDecl(String name, String property, IRType type) {
        /**
         * 是否为 payload 别名（{@code variables: event: $self}）。
         */
        public boolean isPayloadAlias() {
            return "$self".equals(property);
        }
    }

    /**
     * 通用流程节点，由 {@link FlowNodeType} 枚举标识类型，{@code nodeKey} 字段标识具体注册键。
     * <p>
     * 性能关键路径使用 {@code numericValue} 和 {@code flags} 字段
     * 存储原生值，避免 attrs Map 的自动装箱。
     */
    public record FlowNode(
            FlowNodeType type,
            String nodeKey,
            ImmutableMap<String, Object> attrs,
            double numericValue,
            int flags,
            int lineNumber) {
        /**
         * 标记：已常量折叠
         */
        public static final int FLAG_FOLDED = 1;
        /**
         * 标记：RETURN 后不可达
         */
        public static final int FLAG_DEAD_AFTER = 1 << 2;
        /**
         * 标记：复合节点子条件恒假（用于递归折叠标记，区别于 FLAG_FOLDED 的恒真）
         */
        public static final int FLAG_DEAD = 1 << 4;
        /**
         * 标记：由优化器自动注入，非用户显式定义
         */
        public static final int FLAG_OPTIMIZER_INJECTED = 1 << 5;

        /**
         * 无行号的 5-arg 构造。
         */
        public FlowNode(FlowNodeType type, String nodeKey, ImmutableMap<String, Object> attrs, double numericValue, int flags) {
            this(type, nodeKey, attrs, numericValue, flags, -1);
        }

        /**
         * 仅 attrs 的简易构造（用于非数值节点）。
         */
        public FlowNode(FlowNodeType type, String nodeKey, ImmutableMap<String, Object> attrs) {
            this(type, nodeKey, attrs, 0.0, 0, -1);
        }

        /**
         * 通过 {@link NodeRegistry} 获取 handler 实例。全局唯一分发入口。
         */
        public FlowNodeHandler handler() {
            return NodeRegistry.handler(nodeKey);
        }

        /**
         * 创建优化器注入的提前终止节点，用于恒假分支截断。
         */
        public static FlowNode earlyReturn() {
            return new FlowNode(FlowNodeType.RETURN, "return", ImmutableMap.of())
                    .withFlag(FLAG_DEAD_AFTER | FLAG_OPTIMIZER_INJECTED);
        }

        // --- Code Slimming 辅助方法 ---

        /**
         * 为属性下沉构建匿名虚拟生产者节点。
         */
        public static FlowNode virtualProducer(VarDecl decl) {
            return new FlowNode(FlowNodeType.ACTION, "action",
                    ImmutableMap.of(
                            "_sinking_property", decl.property(),
                            "_var_name", decl.name(),
                            "returnType", decl.type()));
        }

        /**
         * 提取节点所在的 YAML 行号。
         *
         * @return 1-based 行号，-1 表示未知
         */
        public int getLineNumber() {
            return lineNumber;
        }

        /**
         * 获取属性，如果为空则返回提供的默认值。自带泛型推断。
         */
        @SuppressWarnings("unchecked")
        public <T> T getAttrOrDefault(String key, T def) {
            Object val = attrs.get(key);
            return val != null ? (T) val : def;
        }

        /**
         * 获取并转换为指定的枚举类型。
         * 如果不存在或无法转换则抛出明确的编译异常。
         */
        public <E extends Enum<E>> E getEnumAttr(String key, Class<E> enumClass) {
            String val = getAttrOrDefault(key, null);
            if (val == null)
                return null;
            try {
                return Enum.valueOf(enumClass, val.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ScriptCompileException.create(this,
                        "Invalid value '" + val + "' for attribute '" + key
                                + "'. Expected one of: " + Arrays.toString(enumClass.getEnumConstants()));
            }
        }

        /**
         * 获取必填属性，如果为空则抛出编译异常。
         */
        @SuppressWarnings("unchecked")
        public <T> T getRequiredAttr(String key) {
            Object val = attrs.get(key);
            if (val == null) {
                throw ScriptCompileException.create(this,
                        "Missing required attribute: '" + key + "' in node " + type);
            }
            return (T) val;
        }

        public boolean hasFlag(int flag) {
            return (flags & flag) != 0;
        }

        public FlowNode withFlag(int flag) {
            return new FlowNode(type, nodeKey, attrs, numericValue, flags | flag, lineNumber);
        }

        public FlowNode withNumericValue(double value) {
            return new FlowNode(type, nodeKey, attrs, value, flags, lineNumber);
        }

        public FlowNode withAttr(String key, Object value) {
            return new FlowNode(type, nodeKey, ImmutableMap.<String, Object>builder()
                    .putAll(attrs)
                    .put(key, value)
                    .buildKeepingLast(), numericValue, flags, lineNumber);
        }

        public FlowNode withoutAttr(String key) {
            if (!attrs.containsKey(key)) {
                return this;
            }
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                if (!entry.getKey().equals(key)) {
                    builder.put(entry);
                }
            }
            return new FlowNode(type, nodeKey, builder.build(), numericValue, flags, lineNumber);
        }
    }

    /**
     * IR 值类型，携带泛型基因。
     */
    public static final class IRType {
        public static final IRType INT = new IRType(BaseType.INT,
                com.google.common.reflect.TypeToken.of(Integer.class));
        public static final IRType LONG = new IRType(BaseType.LONG, com.google.common.reflect.TypeToken.of(Long.class));
        public static final IRType DOUBLE = new IRType(BaseType.DOUBLE,
                com.google.common.reflect.TypeToken.of(Double.class));
        public static final IRType STRING = new IRType(BaseType.STRING,
                com.google.common.reflect.TypeToken.of(String.class));
        public static final IRType ENUM = new IRType(BaseType.ENUM, com.google.common.reflect.TypeToken.of(Enum.class));
        public static final IRType OBJECT = new IRType(BaseType.OBJECT,
                com.google.common.reflect.TypeToken.of(Object.class));
        public static final IRType BOOLEAN = new IRType(BaseType.BOOLEAN,
                com.google.common.reflect.TypeToken.of(Boolean.class));
        public static final IRType COLLECTION = new IRType(BaseType.COLLECTION,
                com.google.common.reflect.TypeToken.of(java.util.Collection.class));
        public static final IRType MAP = new IRType(BaseType.MAP,
                com.google.common.reflect.TypeToken.of(java.util.Map.class));
        private static final java.util.Map<Class<?>, IRType> PRIMITIVE_MAP = java.util.Map.of(
                int.class, INT,
                long.class, LONG,
                double.class, DOUBLE,
                float.class, DOUBLE,
                boolean.class, BOOLEAN,
                byte.class, INT,
                short.class, INT,
                char.class, INT);
        private final BaseType baseType;
        private final com.google.common.reflect.TypeToken<?> typeToken;
        private IRType(BaseType baseType, com.google.common.reflect.TypeToken<?> typeToken) {
            this.baseType = baseType;
            this.typeToken = typeToken;
        }

        public static IRType fromToken(com.google.common.reflect.TypeToken<?> token) {
            Class<?> clazz = com.google.common.primitives.Primitives.unwrap(token.getRawType());
            IRType primitiveType = PRIMITIVE_MAP.get(clazz);
            if (primitiveType != null) {
                return primitiveType;
            }
            if (clazz == String.class)
                return STRING;
            if (clazz.isEnum())
                return new IRType(BaseType.ENUM, token);
            if (java.util.Collection.class.isAssignableFrom(clazz) || clazz.isArray())
                return new IRType(BaseType.COLLECTION, token);
            if (java.util.Map.class.isAssignableFrom(clazz))
                return new IRType(BaseType.MAP, token);
            return new IRType(BaseType.OBJECT, token);
        }

        /**
         * 构造一个 {@code List<E>} 的 COLLECTION 类型，保留元素泛型信息。
         * <p>
         * 用于 COLLECT filter 操作的结果类型推导：将 {@code Collection<E>} 过滤后，
         * 结果是 {@code List<E>}，而非裸 {@code Collection}。
         *
         * @param element 元素类型
         * @return 携带 {@code List<element>} 泛型的 COLLECTION IRType
         */
        public static IRType listOf(IRType element) {
            java.lang.reflect.Type elemType = element.getToken().getType();
            java.lang.reflect.ParameterizedType listType = new java.lang.reflect.ParameterizedType() {
                @Override public java.lang.reflect.Type[] getActualTypeArguments() { return new java.lang.reflect.Type[]{elemType}; }
                @Override public java.lang.reflect.Type getRawType() { return java.util.List.class; }
                @Override public java.lang.reflect.Type getOwnerType() { return null; }
            };
            return new IRType(BaseType.COLLECTION, com.google.common.reflect.TypeToken.of(listType));
        }

        public static IRType fromClass(Class<?> rawClass) {
            return fromToken(com.google.common.reflect.TypeToken.of(rawClass));
        }

        public BaseType base() {
            return baseType;
        }

        public boolean isAssignableFrom(IRType actual) {
            if (this == OBJECT)
                return true;
            if (this.getToken().isSupertypeOf(actual.getToken()))
                return true;
            if (this.equals(actual))
                return true;
            return this.isNumeric() && actual.isNumeric();
        }

        public boolean isNumeric() {
            return baseType == BaseType.INT || baseType == BaseType.LONG || baseType == BaseType.DOUBLE;
        }

        public boolean isPrimitive() {
            return baseType == BaseType.INT || baseType == BaseType.LONG || baseType == BaseType.DOUBLE
                    || baseType == BaseType.BOOLEAN;
        }

        public boolean isContainer() {
            return baseType == BaseType.COLLECTION || baseType == BaseType.STRING || baseType == BaseType.MAP;
        }

        /** 此类型在 JVM 局部变量表中占用的槽位数（LONG/DOUBLE 占 2，其余占 1）。 */
        public int slotWidth() {
            return (baseType == BaseType.DOUBLE || baseType == BaseType.LONG) ? 2 : 1;
        }

        /**
         * 解析映射类型的键类型：
         * <ul>
         *   <li>MAP ({@code Map<K, V>}) → K</li>
         *   <li>其他 → OBJECT</li>
         * </ul>
         */
        public IRType keyType() {
            if (baseType != BaseType.MAP) return OBJECT;
            try {
                com.google.common.reflect.TypeToken<?> kToken = typeToken.resolveType(
                        java.util.Map.class.getTypeParameters()[0]);
                if (kToken.getType() instanceof java.lang.reflect.TypeVariable<?>) {
                    return OBJECT;
                }
                return fromToken(kToken);
            } catch (Exception e) {
                return OBJECT;
            }
        }

        /**
         * 解析集合/映射类型的元素类型：
         * <ul>
         *   <li>COLLECTION + 数组 → 组件类型</li>
         *   <li>COLLECTION + {@code Collection<E>} → E</li>
         *   <li>MAP ({@code Map<K, V>}) → V</li>
         *   <li>其他 → OBJECT</li>
         * </ul>
         */
        public IRType elementType() {
            if (baseType == BaseType.COLLECTION) {
                Class<?> raw = typeToken.getRawType();
                if (raw.isArray()) {
                    Class<?> comp = raw.getComponentType();
                    return comp != null ? fromClass(comp) : OBJECT;
                }
                try {
                    com.google.common.reflect.TypeToken<?> elementToken = typeToken.resolveType(
                            java.util.Collection.class.getTypeParameters()[0]);
                    if (elementToken.getType() instanceof java.lang.reflect.TypeVariable<?>) {
                        return OBJECT;
                    }
                    return fromToken(elementToken);
                } catch (Exception e) {
                    return OBJECT;
                }
            } else if (baseType == BaseType.MAP) {
                try {
                    com.google.common.reflect.TypeToken<?> vToken = typeToken.resolveType(
                            java.util.Map.class.getTypeParameters()[1]);
                    if (vToken.getType() instanceof java.lang.reflect.TypeVariable<?>) {
                        return OBJECT;
                    }
                    return fromToken(vToken);
                } catch (Exception e) {
                    return OBJECT;
                }
            }
            return OBJECT;
        }

        /**
         * 合并两个类型，用于多路径（如 SWITCH/ANY）中同名变量的类型统一。
         * <p>
         * 规则：
         * <ul>
         *   <li>相同类型 → 返回自身</li>
         *   <li>数值类型 → 按宽度提升（INT → LONG → DOUBLE）</li>
         *   <li>其他不兼容 → 退化为 OBJECT</li>
         * </ul>
         * 零运行时开销：仅在编译期执行一次。
         */
        public IRType merge(IRType other) {
            if (this.equals(other)) return this;
            if (this.isNumeric() && other.isNumeric()) {
                // 按宽度提升：INT < LONG < DOUBLE
                int thisOrd = numericWidthOrdinal(this.baseType);
                int otherOrd = numericWidthOrdinal(other.baseType);
                return thisOrd >= otherOrd ? this : other;
            }
            return OBJECT;
        }

        private static int numericWidthOrdinal(BaseType base) {
            return switch (base) {
                case INT -> 0;
                case LONG -> 1;
                case DOUBLE -> 2;
                default -> -1;
            };
        }

        public com.google.common.reflect.TypeToken<?> getToken() {
            return typeToken;
        }

        public String name() {
            return baseType.name();
        }

        @Override
        public String toString() {
            if (typeToken.getType() instanceof Class) {
                return baseType.name();
            }
            return baseType.name() + "<" + typeToken.toString().replaceAll("\\b[a-z_][a-z0-9_]*\\.", "") + ">";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof IRType irType))
                return false;
            return baseType == irType.baseType && typeToken.equals(irType.typeToken);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(baseType, typeToken);
        }
    }
}

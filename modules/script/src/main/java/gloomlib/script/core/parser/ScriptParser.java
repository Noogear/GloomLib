package gloomlib.script.core.parser;

import com.google.common.base.CaseFormat;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.reflect.TypeToken;
import gloomlib.diagnostic.Diagnostic;
import gloomlib.diagnostic.DiagnosticCategory;
import gloomlib.diagnostic.DiagnosticException;
import gloomlib.diagnostic.SourceLocation;
import gloomlib.script.api.ScriptCompileException;
import gloomlib.script.api.ScriptErrorHandler;
import gloomlib.script.core.ParseContext;
import gloomlib.script.core.ScriptIR;
import gloomlib.script.core.ScriptIR.*;
import gloomlib.script.core.parser.accessor.PropertyAccessor;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * YAML 脚本解析器，合并入口解析、流程节点分发、值类型推导和属性链解析。
 */
@SuppressWarnings("null")
public final class ScriptParser {

    /**
     * 将预先解析好的 Map 数据结构转换为强类型的 IR {@link ScriptUnit}。
     * 消除对具体序列化格式（如 YAML/JSON）的依赖，数据可由宿主环境（如 Bukkit Configuration）提供。
     *
     * @param root 包含 event、priority、variables、flow 键的核心 Map
     */
    @SuppressWarnings("unchecked")
    public static ScriptUnit parse(Map<String, Object> root) {
        // 顶层字段
        String idStr = String.valueOf(root.getOrDefault("id", "AnonymousScript"));
        String payloadClassStr = (String) root.get("event");
        int priority = ScriptParser.ValueParser.parseInteger(
                String.valueOf(root.getOrDefault("priority", "0")), 0);
        boolean ignoreCancelled = Boolean.parseBoolean(
                String.valueOf(root.getOrDefault("ignore-cancelled", "true")));

        // 变量声明
        Map<String, Object> varMap = (Map<String, Object>) root.getOrDefault("variables", Map.of());
        Class<?> payloadClazz;
        try {
            payloadClazz = Class.forName(payloadClassStr);
        } catch (ClassNotFoundException e) {
            throw new DiagnosticException(
                    Diagnostic.simple(new SourceLocation(idStr, 0, 0), DiagnosticCategory.SEMANTIC,
                            "Payload class not found: " + payloadClassStr), e);
        }

        ImmutableList.Builder<VarDecl> vars = ImmutableList.builder();
        for (Map.Entry<String, Object> entry : varMap.entrySet()) {
            String name = entry.getKey();
            String property = String.valueOf(entry.getValue());
            if ("$self".equals(property)) {
                // payload 别名：跳过属性解析，类型在 buildContext 中用 payload 实际类填充
                vars.add(new VarDecl(name, "$self", ScriptIR.IRType.OBJECT));
            } else {
                IRType type = PropertyResolver.resolveType(payloadClazz, property, idStr);
                vars.add(new VarDecl(name, property, type));
            }
        }

        // 流程列表
        List<Map<String, Object>> flowList = (List<Map<String, Object>>) root.getOrDefault("flow", List.of());

        return new ScriptUnit(idStr, payloadClassStr, priority, vars.build(), parseFlowNodes(flowList, idStr), ignoreCancelled);
    }

    /**
     * 解析单个流程节点，通过 {@link FlowNodeType} 枚举分发到对应 Handler。
     * <p>
     * 支持短语法：
     * <ul>
     * <li>无 {@code type} 且有 {@code action} 字段 → ACTION</li>
     * <li>有 {@code return} 字段 → RETURN_VALUE（即 {@code - return: 42}）</li>
     * </ul>
     */
    /**
     * 向后兼容入口：不携带脚本来源信息，等价于 {@code parseFlowNode(new ParseContext(attrs, null))}。
     */
    public static FlowNode parseFlowNode(Map<String, Object> attrs) {
        return parseFlowNode(new ParseContext(attrs, null));
    }

    /**
     * 携带完整解析上下文的主实现。
     * <p>
     * 节点类型通过 {@link FlowNodeType#fromShorthand(String)} 泛型分发，
     * 新增节点类型只需在 {@link FlowNodeType} 枚举中声明 shorthand 元数据即可，无需修改本方法。
     * <p>
     * {@link ParseContext} 同时承担属性访问与诊断定位：handler 调用 {@link ParseContext#error}
     * 时可直接获得带有文件名与行号的 {@link ScriptCompileException}，无需外层 try-catch。
     */
    public static FlowNode parseFlowNode(ParseContext ctx) {
        Map<String, Object> attrs = ctx.attrs();

        // 提取并移除临时行号标记，然后设入 ParseContext
        int lineNumber = ctx.lineNumber();
        Object lineObj = attrs.get("__line__");
        if (lineObj instanceof Number num) {
            lineNumber = num.intValue();
            attrs = new java.util.HashMap<>(attrs);
            attrs.remove("__line__");
            ctx = new ParseContext(attrs, ctx.scriptId(), lineNumber);
        }

        String typeStr = (String) attrs.get("type");
        FlowNodeType type = null;

        if (typeStr == null) {
            // 遍历 shorthand 注册表，泛型检测触发键（枚举声明顺序即优先级）
            for (String key : FlowNodeType.reservedKeys()) {
                if (attrs.containsKey(key)) {
                    type = FlowNodeType.fromShorthand(key);
                    String alias = type.shorthandAlias();
                    if (alias != null) {
                        Map<String, Object> rebuilt = new java.util.HashMap<>(attrs);
                        Object val = rebuilt.remove(key);
                        if (val != null) rebuilt.put(alias, val);
                        ctx = ctx.withAttrs(rebuilt);
                    }
                    break;
                }
            }

            // 动态 Action 推断：取第一个非保留字段作为 action 名
            if (type == null) {
                String inferredAction = null;
                Object inferredArgs = null;
                for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                    String k = entry.getKey();
                    if (!FlowNodeType.reservedKeys().contains(k)
                            && !k.equals("store") && !k.equals("args")
                            && !k.equals("type")) {
                        inferredAction = k;
                        inferredArgs = entry.getValue();
                        break;
                    }
                }
                if (inferredAction != null) {
                    Map<String, Object> rebuilt = new java.util.HashMap<>(attrs);
                    rebuilt.remove(inferredAction);
                    rebuilt.put("action", inferredAction);
                    if (inferredArgs instanceof List) {
                        rebuilt.put("args", inferredArgs);
                    } else if (inferredArgs != null) {
                        rebuilt.put("args", List.of(inferredArgs));
                    }
                    ctx = ctx.withAttrs(rebuilt);
                    type = FlowNodeType.ACTION;
                }
            }
        }

        if (type == null) {
            type = FlowNodeType.fromYaml(typeStr);
        }
        FlowNode node = type.handler().parse(ctx);
        // 将提取的行号写入 FlowNode
        if (lineNumber > 0 && node.lineNumber() <= 0) {
            node = new FlowNode(node.type(), node.attrs(), node.numericValue(), node.flags(), lineNumber);
        }
        return node;
    }

    /**
     * 内部快捷入口：携带 scriptId 封装为 {@link ParseContext} 后调用主实现。
     * ParseContext 会在 handler 调用 {@link ParseContext#error} 时自动注入文件名与行号，
     * 不再需要外层 try-catch 补充位置信息。
     */
    private static FlowNode parseFlowNode(Map<String, Object> attrs, String scriptId) {
        return parseFlowNode(new ParseContext(attrs, scriptId));
    }

    /**
     * 解析反序列化出的 List 形式的流程节点。
     * 用于非完整 ScriptUnit 场景下的局部逻辑 AST 构建。
     */
    public static ImmutableList<FlowNode> parseFlow(List<?> flowList) {
        if (flowList == null) {
            return ImmutableList.of();
        }
        return parseFlowNodes(flowList, null);
    }

    /**
     * 将 YAML 中反序列化出来的节点列表转换为强类型的 AST 节点列表。
     * <p>
     * 支持 {@code - return} 纯字符串简写（SnakeYAML 将其解析为 String）。
     *
     * @param scriptId 脚本来源标识（文件名等），可为 {@code null}
     */
    @SuppressWarnings("unchecked")
    private static ImmutableList<FlowNode> parseFlowNodes(List<?> flowList, String scriptId) {
        if (flowList == null || flowList.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<FlowNode> flow = ImmutableList.builder();
        for (Object item : flowList) {
            if (item instanceof String s) {
                if (s.equalsIgnoreCase("return")) {
                    // 对应 YAML 中的 "- return"（空返回）
                    flow.add(FlowNodeType.RETURN.handler().parse(new ParseContext(Map.of(), scriptId)));
                } else {
                    // 新增：自动包装纯字符串动作 (例如 "- healAllPlayers")
                    flow.add(parseFlowNode(Map.of("action", s), scriptId));
                }
            } else if (item instanceof Map<?, ?> rawMap) {
                flow.add(parseFlowNode((Map<String, Object>) rawMap, scriptId));
            } else {
                throw new DiagnosticException(
                        Diagnostic.simple(
                                scriptId != null ? new SourceLocation(scriptId, 0, 0) : SourceLocation.UNKNOWN,
                                DiagnosticCategory.PARSE,
                                "Unexpected flow node type: " + item));
            }
        }
        return flow.build();
    }


    /**
     * 值类型解析工具。
     */
    public static final class ValueParser {

        private ValueParser() {
        }

        /**
         * 推导值的 IR 类型。
         */
        public static IRType inferType(Object value) {
            if (value == null)
                return IRType.OBJECT;
            if (value instanceof Integer)
                return IRType.INT;
            if (value instanceof Long)
                return IRType.LONG;
            if (value instanceof Double || value instanceof Float)
                return IRType.DOUBLE;
            if (value instanceof Boolean)
                return IRType.BOOLEAN;
            if (value instanceof String s) {
                // 全大写下划线 → 枚举
                if (s.matches("[A-Z][A-Z0-9_]+"))
                    return IRType.ENUM;
                return IRType.STRING;
            }
            return IRType.OBJECT;
        }

        /**
         * 安全解析数字字符串，使用 Guava tryParse 避免异常驱动。
         */
        public static Object parseNumber(String s) {
            Integer i = Ints.tryParse(s);
            if (i != null)
                return i;
            Long l = Longs.tryParse(s);
            if (l != null)
                return l;
            Double d = Doubles.tryParse(s);
            if (d != null)
                return d;
            return s;
        }

        /**
         * 安全解析整数，带默认值。
         */
        public static int parseInteger(String s, int def) {
            if (s == null)
                return def;
            Integer i = Ints.tryParse(s);
            return i != null ? i : def;
        }
    }


    /**
     * 事件属性路径解析器。
     * <p>
     * 将 YAML 变量映射的属性名（如 {@code "entity"}、{@code "damage"}）
     * 解析为事件类的 getter 方法名（如 {@code "getEntity"}、{@code "getDamage"}），
     * 并推导返回类型。
     */
    public static final class PropertyResolver {

        /** 匹配单个 {@code [content]} 括号块，用于多层索引迭代。 */
        private static final Pattern INDEX_CHUNK = Pattern.compile("\\[([^\\]]+)\\]");

        private PropertyResolver() {
        }

        /**
         * 提取属性链的根基名称。
         * 例如从 "player.inventory[0]" 提取出 "player"。
         * 专门用于给编译器进行同祖先对象的局部变量缓冲优化。
         */
        public static String getRootProperty(String propertyPath) {
            int dotIdx = propertyPath.indexOf('.');
            int bracketIdx = propertyPath.indexOf('[');

            int splitIdx = -1;
            if (dotIdx != -1 && bracketIdx != -1) {
                splitIdx = Math.min(dotIdx, bracketIdx);
            } else if (dotIdx != -1) {
                splitIdx = dotIdx;
            } else if (bracketIdx != -1) {
                splitIdx = bracketIdx;
            }

            return (splitIdx == -1) ? propertyPath : propertyPath.substring(0, splitIdx);
        }

        /**
         * 解析属性的 IR 类型。支持链式属性（以 {@code .} 分隔）和集合/Map索引（如 list[0] 或 map[key]）。
         */
        public static IRType resolveType(Class<?> payloadClass, String property) {
            return resolveType(payloadClass, property, null);
        }

        /**
         * 解析属性的 IR 类型，携带脚本来源标识用于错误定位。
         */
        public static IRType resolveType(Class<?> payloadClass, String property, String scriptId) {
            TypeToken<?> currentType = TypeToken.of(payloadClass);
            List<PropertyAccessor> accessors = resolveAccessors(currentType, property, scriptId);
            if (accessors.isEmpty()) {
                return ScriptIR.IRType.fromClass(payloadClass);
            }
            return ScriptIR.IRType.fromToken(accessors.get(accessors.size() - 1).returnType());
        }

        /**
         * 解析属性为一系列的 PropertyAccessor 指令集，保留了全泛型分析链。
         */
        public static List<PropertyAccessor> resolveAccessors(TypeToken<?> ownerType, String property) {
            return resolveAccessors(ownerType, property, null);
        }

        /**
         * 解析属性为一系列的 PropertyAccessor 指令集，携带脚本来源标识用于错误定位。
         * <p>
         * 支持多层索引访问，例如 {@code nested[k1][k2]}、{@code matrix[0][1]}、
         * {@code table[key][{dynIdx}]}，每层均完整推导泛型类型。
         */
        public static List<PropertyAccessor> resolveAccessors(TypeToken<?> ownerType, String property, String scriptId) {
            List<PropertyAccessor> result = new ArrayList<>();
            Iterable<String> parts = Splitter.on('.').split(property);
            TypeToken<?> currentType = ownerType;

            for (String part : parts) {
                int firstBracket = part.indexOf('[');
                if (firstBracket >= 0) {
                    // 形如: prop[idx] 或 prop[k1][k2] 或 prop[{var}][0]
                    String baseProp = part.substring(0, firstBracket);
                    String bracketChain = part.substring(firstBracket); // "[k1][k2]..."

                    // 1. 解析基础属性 → getter（若无属性前缀则直接对 currentType 做索引）
                    if (!baseProp.isEmpty()) {
                        Method baseGetter = resolveGetter(currentType.getRawType(), baseProp, scriptId);
                        TypeToken<?> baseType = currentType.resolveType(baseGetter.getGenericReturnType());
                        result.add(new gloomlib.script.core.parser.accessor.MethodAccessor(baseGetter, baseType));
                        currentType = baseType;
                    }

                    // 2. 逐层解析每个 [index] 括号块，每层推导一次类型
                    Matcher indexM = INDEX_CHUNK.matcher(bracketChain);
                    while (indexM.find()) {
                        currentType = resolveIndexAccess(result, currentType, indexM.group(1), part, scriptId);
                    }
                } else {
                    // 普通属性
                    Method getter = resolveGetter(currentType.getRawType(), part, scriptId);
                    currentType = currentType.resolveType(getter.getGenericReturnType());
                    result.add(new gloomlib.script.core.parser.accessor.MethodAccessor(getter, currentType));
                }
            }
            return result;
        }

        /**
         * 针对单个 {@code [indexStr]} 解析一层索引访问，追加对应 Accessor 并返回新的元素类型。
         * <p>
         * 支持 List（数字或动态变量索引）、Map（字符串键或动态变量键）、数组（数字或动态变量索引）。
         * 类型不支持（如对 String、int 做索引）在编译期报错。
         */
        private static TypeToken<?> resolveIndexAccess(
                List<PropertyAccessor> result,
                TypeToken<?> currentType,
                String indexStr,
                String fullPart,
                String scriptId) {
            if (List.class.isAssignableFrom(currentType.getRawType())) {
                TypeToken<?> elementType = extractListType(currentType);
                if (ScriptIR.isSingleVar(indexStr)) {
                    result.add(new gloomlib.script.core.parser.accessor.DynamicListAccessor(
                            indexStr.substring(1, indexStr.length() - 1), elementType));
                } else {
                    result.add(new gloomlib.script.core.parser.accessor.ListAccessor(
                            parseIndex(indexStr, fullPart, scriptId), elementType));
                }
                return elementType;
            } else if (Map.class.isAssignableFrom(currentType.getRawType())) {
                TypeToken<?> keyType = extractMapKeyType(currentType);
                TypeToken<?> valueType = extractMapValueType(currentType);
                String key = indexStr;
                if ((key.startsWith("'") && key.endsWith("'"))
                        || (key.startsWith("\"") && key.endsWith("\""))) {
                    key = key.substring(1, key.length() - 1);
                }
                if (ScriptIR.isSingleVar(key)) {
                    result.add(new gloomlib.script.core.parser.accessor.DynamicMapAccessor(
                            key.substring(1, key.length() - 1), keyType, valueType));
                } else {
                    result.add(new gloomlib.script.core.parser.accessor.MapAccessor(key, valueType));
                }
                return valueType;
            } else if (currentType.getRawType().isArray()) {
                TypeToken<?> elementType =
                        com.google.common.reflect.TypeToken.of(currentType.getRawType().getComponentType());
                if (ScriptIR.isSingleVar(indexStr)) {
                    result.add(new gloomlib.script.core.parser.accessor.DynamicArrayAccessor(
                            indexStr.substring(1, indexStr.length() - 1), elementType));
                } else {
                    result.add(new gloomlib.script.core.parser.accessor.ArrayAccessor(
                            parseIndex(indexStr, fullPart, scriptId), elementType));
                }
                return elementType;
            } else {
                String message;
                if (java.util.Set.class.isAssignableFrom(currentType.getRawType())) {
                    message = "Set is an unordered collection and does not support indexed access '[" + indexStr
                            + "'] in '" + fullPart + "'. Use COLLECT to iterate Set elements, or convert to List first.";
                } else {
                    message = "Type " + currentType + " does not support index access '[" + indexStr + "']  in '"
                            + fullPart + "'. Supported: List (numeric index), Map (string key), array.";
                }
                throw new DiagnosticException(
                        Diagnostic.simple(
                                scriptId != null ? new SourceLocation(scriptId, 0, 0) : SourceLocation.UNKNOWN,
                                DiagnosticCategory.TYPE,
                                message));
            }
        }

        private static int parseIndex(String indexStr, String fullPart, String scriptId) {
            try {
                return Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                throw new DiagnosticException(
                        Diagnostic.simple(
                                scriptId != null ? new SourceLocation(scriptId, 0, 0) : SourceLocation.UNKNOWN,
                                DiagnosticCategory.PARSE,
                                "Invalid numeric index '" + indexStr + "' in '" + fullPart
                                        + "'. Expected an integer or a dynamic variable reference like {var}."));
            }
        }

        private static TypeToken<?> extractListType(TypeToken<?> listType) {
            try {
                // List<E> -> 获取 E
                java.lang.reflect.TypeVariable<?> param = List.class.getTypeParameters()[0];
                return listType.resolveType(param);
            } catch (Exception e) {
                ScriptErrorHandler.fine(
                        () -> "Failed to extract List element type from " + listType + ", falling back to Object");
                return TypeToken.of(Object.class);
            }
        }

        private static TypeToken<?> extractMapValueType(TypeToken<?> mapType) {
            try {
                // Map<K, V> -> 获取 V
                java.lang.reflect.TypeVariable<?> param = Map.class.getTypeParameters()[1];
                return mapType.resolveType(param);
            } catch (Exception e) {
                ScriptErrorHandler.fine(
                        () -> "Failed to extract Map value type from " + mapType + ", falling back to Object");
                return TypeToken.of(Object.class);
            }
        }

        private static TypeToken<?> extractMapKeyType(TypeToken<?> mapType) {
            try {
                // Map<K, V> -> 获取 K
                java.lang.reflect.TypeVariable<?> param = Map.class.getTypeParameters()[0];
                return mapType.resolveType(param);
            } catch (Exception e) {
                ScriptErrorHandler.fine(
                        () -> "Failed to extract Map key type from " + mapType + ", falling back to Object");
                return TypeToken.of(Object.class);
            }
        }

        /**
         * 获取 getter 方法名。
         */
        public static String getGetterName(String property) {
            String capitalized = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, property);
            return "get" + capitalized;
        }

        /**
         * 解析 getter 方法。
         */
        public static Method resolveGetter(Class<?> clazz, String property) {
            return resolveGetter(clazz, property, null);
        }

        /**
         * 解析 getter 方法，携带脚本来源标识用于错误定位。
         */
        public static Method resolveGetter(Class<?> clazz, String property, String scriptId) {
            String getterName = getGetterName(property);
            try {
                return clazz.getMethod(getterName);
            } catch (NoSuchMethodException ignored) {
            }

            String isName = "is" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, property);
            try {
                return clazz.getMethod(isName);
            } catch (NoSuchMethodException ignored) {
            }

            try {
                return clazz.getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
            } catch (NoSuchMethodException ignored) {
            }

            // 最后尝试 Record 组件或 Fluent API 风格纯同名访问器 (例如: record.name())
            try {
                Method fallback = clazz.getMethod(property);
                // 必须过滤掉无返回值的普通方法，防止被当成 getter 从而在求值时造成执行副作用（如 clear() 等）
                if (fallback.getReturnType() != void.class) {
                    return fallback;
                }
            } catch (NoSuchMethodException ignored) {
            }

            throw new DiagnosticException(
                    Diagnostic.simple(
                            scriptId != null ? new SourceLocation(scriptId, 0, 0) : SourceLocation.UNKNOWN,
                            DiagnosticCategory.SEMANTIC,
                            "No getter found for '" + property + "' on " + clazz.getName()));
        }

    }

    // ── YAML 解析 ────────────────────────────────────────────────────────────────

    /**
     * 将 YAML 字符串解析为 {@code Map<String, Object>}，自动为流程节点注入 {@code __line__} 行号。
     * <p>
     * 行号仅注入到<b>序列中的映射子节点</b>（即 {@code flow}、{@code on_fail}、{@code any/all}
     * 列表中的流程节点 Map），数据 Map（如 {@code variables}、{@code cases}）不会被注入，
     * 从而保证迭代安全。
     * <p>
     * 使用示例：
     * <pre>
     * Map&lt;String, Object&gt; root = ScriptParser.parseYaml(yamlContent);
     * root.put("id", fileName);
     * injector.inject(root);
     * </pre>
     *
     * @param yaml YAML 文本
     * @return 根映射节点对应的 Map，可直接传入 {@link #parse(Map)} 或 {@link
     *         gloomlib.script.api.injection.ScriptInjector#inject(Map)}
     * @throws IllegalArgumentException 如果根节点不是 MappingNode
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseYaml(String yaml) {
        Node rootNode = new Yaml().compose(new StringReader(yaml));
        if (!(rootNode instanceof MappingNode)) {
            throw new IllegalArgumentException("YAML root element must be a mapping");
        }
        return (Map<String, Object>) convertNode(rootNode, false);
    }

    /**
     * 递归转换 SnakeYAML AST 节点为 Java 对象。
     *
     * @param injectLine 是否为该节点注入 {@code __line__}（仅序列中的映射子节点为 true）
     */
    private static Object convertNode(Node node, boolean injectLine) {
        if (node instanceof ScalarNode scalar) {
            String value = scalar.getValue();
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Boolean.parseBoolean(value);
            }
            return value;
        } else if (node instanceof SequenceNode sequence) {
            List<Object> list = new ArrayList<>(sequence.getValue().size());
            for (Node child : sequence.getValue()) {
                list.add(convertNode(child, child instanceof MappingNode));
            }
            return list;
        } else if (node instanceof MappingNode mapping) {
            Map<String, Object> map = new LinkedHashMap<>(mapping.getValue().size() + 1);
            if (injectLine) {
                map.put("__line__", mapping.getStartMark().getLine() + 1);
            }
            for (NodeTuple tuple : mapping.getValue()) {
                String key = ((ScalarNode) tuple.getKeyNode()).getValue();
                map.put(key, convertNode(tuple.getValueNode(), false));
            }
            return map;
        }
        return null;
    }
}

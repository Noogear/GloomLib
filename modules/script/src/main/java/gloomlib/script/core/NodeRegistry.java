package gloomlib.script.core;

import gloomlib.script.api.ScriptCompileException;
import gloomlib.script.core.ScriptIR.FlowNodeHandler;
import gloomlib.script.core.ScriptIR.FlowNodeType;
import gloomlib.script.core.handler.*;

import java.util.*;
import java.util.function.Supplier;

/**
 * 脚本节点注册表——节点类型、Handler 工厂、能力声明的单一权威来源。
 * <p>
 * 注册操作在 {@link #freeze()} 前完成，freeze 后只读访问。
 */
public final class NodeRegistry {

    private static final Map<String, NodeDescriptor> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, FlowNodeHandler> HANDLER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean frozen = false;

    private NodeRegistry() {}

    // ===================== 注册 API =====================

    /**
     * 注册一个内置节点类型。
     */
    public static void register(FlowNodeType type, String shorthandAlias,
                                Supplier<FlowNodeHandler> factory,
                                String... dependencies) {
        checkNotFrozen();
        String key = type.key();
        if (key == null) throw new IllegalArgumentException("Use registerCustom for CUSTOM types");
        REGISTRY.put(key, new NodeDescriptor(type, key, shorthandAlias, factory, Set.of(dependencies)));
        ActionNodeHandler.registry().addReservedKey(key);
    }

    /**
     * 注册一个自定义节点类型。
     *
     * @param key            YAML shorthand 键名（如 "delay"）
     * @param shorthandAlias shorthand 值的重命名目标属性（可为 null）
     * @param factory        Handler 工厂
     * @param dependencies   依赖的节点 key（如 "check"）
     * @return NodeDescriptor
     */
    public static NodeDescriptor registerCustom(String key, String shorthandAlias,
                                                Supplier<FlowNodeHandler> factory,
                                                String... dependencies) {
        checkNotFrozen();
        if (REGISTRY.containsKey(key)) {
            throw new IllegalStateException("Node type key '" + key + "' already registered");
        }
        NodeDescriptor desc = new NodeDescriptor(
                FlowNodeType.CUSTOM, key, shorthandAlias, factory, Set.of(dependencies));
        REGISTRY.put(key, desc);
        ActionNodeHandler.registry().addReservedKey(key);
        return desc;
    }

    /**
     * 反注册（禁用）一个节点。
     */
    public static void unregister(String key) {
        checkNotFrozen();
        REGISTRY.remove(key);
    }

    // ===================== 查询 API =====================

    public static boolean isEnabled(String key) {
        return REGISTRY.containsKey(key);
    }

    public static FlowNodeHandler handler(String nodeKey) {
        FlowNodeHandler cached = HANDLER_CACHE.get(nodeKey);
        if (cached != null) return cached;
        NodeDescriptor desc = REGISTRY.get(nodeKey);
        if (desc == null) {
            throw ScriptCompileException.parse("Node type '" + nodeKey + "' is not registered.");
        }
        FlowNodeHandler handler = desc.createHandler();
        if (frozen) {
            HANDLER_CACHE.put(nodeKey, handler);
        }
        return handler;
    }

    public static NodeDescriptor descriptor(String nodeKey) {
        return REGISTRY.get(nodeKey);
    }

    /**
     * 按 YAML 显式 type 字段查找。
     */
    public static NodeDescriptor fromYaml(String typeName) {
        if (typeName == null) return REGISTRY.get("action");
        NodeDescriptor desc = REGISTRY.get(typeName.toLowerCase());
        if (desc == null) {
            throw ScriptCompileException.parse("Unknown or disabled node type: " + typeName);
        }
        return desc;
    }

    /**
     * 当前已注册的所有 shorthand key。
     */
    public static Set<String> registeredKeys() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    // ===================== 生命周期 =====================

    /**
     * 冻结注册表，校验依赖，此后不可修改。
     */
    public static void freeze() {
        validateDependencies();
        validateCapabilities();
        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    /**
     * 返回诊断快照。
     */
    public static RegistrySnapshot snapshot() {
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        for (NodeDescriptor d : REGISTRY.values()) {
            if (!d.dependencies().isEmpty()) {
                deps.put(d.key(), d.dependencies());
            }
        }
        return new RegistrySnapshot(new LinkedHashSet<>(REGISTRY.keySet()), deps, frozen);
    }

    private static void validateDependencies() {
        for (NodeDescriptor desc : REGISTRY.values()) {
            for (String dep : desc.dependencies()) {
                if (!REGISTRY.containsKey(dep)) {
                    throw new IllegalStateException(
                            "Node '" + desc.key() + "' depends on '" + dep + "', but it is not registered.");
                }
            }
        }
    }

    /**
     * 校验注册节点的能力互斥约束。
     * <ul>
     *   <li>{@code TERMINATES_FLOW + PURE_GUARD}：终止流的节点不可能是可安全跳过的守卫</li>
     *   <li>{@code SIDE_EFFECT + PURE_GUARD}：有外部副作用的节点不可能是纯守卫</li>
     * </ul>
     */
    private static void validateCapabilities() {
        for (NodeDescriptor desc : REGISTRY.values()) {
            var caps = desc.createHandler().capabilities();
            if (caps.contains(ScriptIR.NodeCapability.TERMINATES_FLOW)
                    && caps.contains(ScriptIR.NodeCapability.PURE_GUARD)) {
                throw new IllegalStateException(
                        "Node '" + desc.key() + "': TERMINATES_FLOW + PURE_GUARD are mutually exclusive");
            }
            if (caps.contains(ScriptIR.NodeCapability.SIDE_EFFECT)
                    && caps.contains(ScriptIR.NodeCapability.PURE_GUARD)) {
                throw new IllegalStateException(
                        "Node '" + desc.key() + "': SIDE_EFFECT + PURE_GUARD are mutually exclusive");
            }
        }
    }

    /**
     * 重置注册表状态（仅限测试使用）。
     */
    static void reset() {
        REGISTRY.clear();
        HANDLER_CACHE.clear();
        frozen = false;
    }

    public record RegistrySnapshot(
            Set<String> registeredKeys,
            Map<String, Set<String>> dependencies,
            boolean frozen
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("NodeRegistry[frozen=").append(frozen).append("]\n");
            for (String key : registeredKeys) {
                sb.append("  ").append(key);
                Set<String> d = dependencies.get(key);
                if (d != null && !d.isEmpty()) {
                    sb.append(" → depends: ").append(String.join(", ", d));
                }
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    private static void checkNotFrozen() {
        if (frozen) throw new IllegalStateException("NodeRegistry is frozen");
    }

    /**
     * 注册所有内置节点类型并调用必要的初始化。
     * 在 freeze 之前调用。
     */
    public static void registerDefaults() {
        register(FlowNodeType.CHECK, "variable", CheckNodeHandler::new);
        register(FlowNodeType.SWITCH, "variable", SwitchNodeHandler::new);
        register(FlowNodeType.RETURN, null, ReturnNodeHandler::new);
        register(FlowNodeType.ACTION, null, ActionNodeHandler::new);
        register(FlowNodeType.MATH, "expr", MathNodeHandler::new);
        register(FlowNodeType.COLLECT, "variable", CollectNodeHandler::new);
        register(FlowNodeType.ANY, null, CompositeCheckHandler::new);
        register(FlowNodeType.ALL, null, CompositeCheckHandler::new);
        register(FlowNodeType.INVOKE, null, InvokeNodeHandler::new);

        ActionNodeHandler.registry().scanAndRegister(
                gloomlib.script.api.action.ScriptBuiltinActions.class);
    }
}

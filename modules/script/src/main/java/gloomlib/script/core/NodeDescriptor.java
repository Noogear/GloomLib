package gloomlib.script.core;

import gloomlib.script.core.ScriptIR.FlowNodeHandler;
import gloomlib.script.core.ScriptIR.FlowNodeType;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 节点类型的完整注册描述。
 * 包含 handler 工厂、shorthand 元数据和依赖声明。
 */
public record NodeDescriptor(
        FlowNodeType type,
        String key,
        String shorthandAlias,
        Supplier<FlowNodeHandler> factory,
        Set<String> dependencies
) {
    public FlowNodeHandler createHandler() {
        return factory.get();
    }
}

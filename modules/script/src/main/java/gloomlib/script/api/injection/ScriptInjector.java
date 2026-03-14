package gloomlib.script.api.injection;

import com.google.common.base.Preconditions;
import gloomlib.script.api.ScriptHost;
import gloomlib.script.core.CompilationPipeline;
import gloomlib.script.core.NodeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 脚本注入器。
 * <p>
 * 将 YAML 脚本编译并通过 {@link ScriptHost} 注入到宿主平台。
 */
public final class ScriptInjector {

    // 注册内置节点并冻结注册表
    static {
        if (!NodeRegistry.isFrozen()) {
            NodeRegistry.registerDefaults();
            NodeRegistry.freeze();
        }
    }

    private final ScriptHost host;
    private final CompilationPipeline pipeline;
    private final List<RegisteredScript> registered = new ArrayList<>();

    public ScriptInjector(ScriptHost host) {
        this.host = Preconditions.checkNotNull(host, "host");
        this.pipeline = new CompilationPipeline();
    }

    /**
     * 编译并注入一个预先解析好的 Map 配置脚本。
     */
    public RegisteredScript inject(Map<String, Object> rootData) {
        gloomlib.script.core.ScriptIR.ScriptUnit unit = gloomlib.script.core.parser.ScriptParser
                .parse(rootData);
        CompilationPipeline.CompiledScript compiled = pipeline.compile(unit);

        // 获取荷载类
        Class<?> payloadClass;
        try {
            payloadClass = Class.forName(compiled.ir().payloadClass());
        } catch (ClassNotFoundException e) {
            throw gloomlib.script.api.ScriptCompileException.create(
                    compiled.ir().id(), null, gloomlib.diagnostic.DiagnosticCategory.SEMANTIC,
                    "Payload class not found: " + compiled.ir().payloadClass());
        }

        Consumer<Object> handler = compiled.newHandler();
        int priority = compiled.ir().priority();
        boolean ignoreCancelled = compiled.ir().ignoreCancelled();

        Object token = host.registerEvent(payloadClass, priority, ignoreCancelled, handler);
        RegisteredScript reg = new RegisteredScript(token, payloadClass, handler);
        registered.add(reg);
        return reg;
    }

    /**
     * 注入通用代码回调。
     */
    public void injectCallback(Runnable compiled) {
        Preconditions.checkNotNull(compiled, "compiled");
        compiled.run();
    }

    /**
     * 卸载单个脚本。
     */
    public void eject(RegisteredScript script) {
        host.unregisterEvent(script.token());
        registered.remove(script);
    }

    /**
     * 卸载所有已注入的脚本，并清理编译缓存与常量注册表。
     */
    public void ejectAll() {
        for (RegisteredScript reg : registered) {
            host.unregisterEvent(reg.token());
        }
        registered.clear();
        CompilationPipeline.clearCache();
    }

    /**
     * 获取已注册脚本数量。
     */
    public int registeredCount() {
        return registered.size();
    }

    /**
     * 已注册脚本记录。
     */
    public record RegisteredScript(
            Object token,
            Class<?> payloadClass,
            Consumer<Object> handler) {
    }
}

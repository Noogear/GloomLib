package gloomlib.script.core.codegen;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 脚本常量引导器（外置常量池）。
 * <p>
 * 替代每个 Hidden Class 内部的 {@code static final} 字段 + {@code <clinit>} 初始化，
 * 将 Pattern / Set / 数组等重量常量外置到本类的全局注册表，通过 {@code invokedynamic} 访问。
 * <p>
 * 元空间优势：
 * <ul>
 *   <li>消除每个脚本类的字段元数据（每个 {@code static final} 字段约 50B）</li>
 *   <li>消除每个脚本类的 {@code <clinit>} 方法体（每个常量约 40–80B）</li>
 *   <li>相同内容的常量跨脚本共享同一 {@link ConstantCallSite}，零重复元空间开销</li>
 * </ul>
 * <p>
 * 运行期性能：{@link ConstantCallSite} 首次链接后 JIT 将调用点折叠为纯常量引用，
 * 与 {@code GETSTATIC static final} 等价，无额外开销。
 */
public final class ScriptConstantBootstrap {

    /**
     * 全局常量注册表：内容哈希键 → 常量实例（Pattern / Set / 数组）
     */
    private static final ConcurrentHashMap<String, Object> REGISTRY = new ConcurrentHashMap<>();

    /**
     * 引用计数：每个 key 被多少个活跃 CompiledScript 引用。
     * 配合 CACHE 的 softValues 回收，计数归零时从 REGISTRY 移除。
     */
    private static final ConcurrentHashMap<String, AtomicInteger> REF_COUNTS = new ConcurrentHashMap<>();

    private ScriptConstantBootstrap() {
    }

    /**
     * 批量注册提升后的常量列表。由 {@link BytecodeCompiler} 在生成字节码前调用。
     * <p>
     * 根据 {@link gloomlib.script.core.CompilationContext.ConstantKind} 构建常量实例
     * 并写入 {@link #REGISTRY}。{@code putIfAbsent} 保证跨脚本相同内容的常量共享同一实例。
     *
     * @param constants 由优化器提升的常量定义列表
     */
    @SuppressWarnings("unchecked")
    static void registerAll(List<gloomlib.script.core.CompilationContext.ConstantDef> constants) {
        for (var def : constants) {
            Object instance = switch (def.kind()) {
                case PATTERN -> Pattern.compile((String) def.value());
                case STRING_SET -> {
                    com.google.common.collect.ImmutableList<Object> vals =
                            (com.google.common.collect.ImmutableList<Object>) def.value();
                    yield Set.of(vals.stream().map(Object::toString).toArray());
                }
                case DOUBLE_ARRAY, INT_ARRAY -> def.value();
            };
            REGISTRY.putIfAbsent(def.key(), instance);
            REF_COUNTS.computeIfAbsent(def.key(), k -> new AtomicInteger()).incrementAndGet();
        }
    }

    /**
     * {@code invokedynamic} 引导方法。
     * <p>
     * JVM 在第一次执行对应 {@code invokedynamic} 指令时调用此方法，
     * 从 {@link #REGISTRY} 取出预注册的常量，返回 {@link ConstantCallSite}。
     * JIT 随后将该调用点折叠为常量引用，后续调用开销与 {@code GETSTATIC static final} 相同。
     *
     * @param lookup 调用点的查找对象（JVM 自动传入）
     * @param name   调用点名称（即 {@code invokedynamic} 的方法名，调试用）
     * @param type   方法类型，返回值为常量的实际类型
     * @param key    常量键，与 {@link #registerAll} 注册的 key 一致
     * @return 绑定到常量的 {@link ConstantCallSite}
     * @throws BootstrapMethodError 若 key 未注册（属于编译器内部错误）
     */
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type, String key) {
        Object constant = REGISTRY.get(key);
        if (constant == null) {
            throw new BootstrapMethodError("Unregistered script constant key: " + key);
        }
        return new ConstantCallSite(MethodHandles.constant(type.returnType(), constant));
    }


    /**
     * 减引用计数，计数归零时从 REGISTRY 移除。
     * 配合编译缓存的 softValues RemovalListener 调用，实现增量清理。
     *
     * @param constantKeys 被驱逐的 CompiledScript 所引用的常量键列表
     */
    public static void release(List<String> constantKeys) {
        for (String key : constantKeys) {
            REF_COUNTS.computeIfPresent(key, (k, count) -> {
                if (count.decrementAndGet() <= 0) {
                    REGISTRY.remove(k);
                    return null; // 从 REF_COUNTS 移除
                }
                return count;
            });
        }
    }


    /**
     * 清除注册表中的所有常量条目。
     * <p>
     * 适用于：
     * <ul>
     *   <li>插件 reload 时释放旧版脚本的常量（旧 Hidden Class 已被 GC）</li>
     *   <li>服务器关闭时主动释放内存</li>
     * </ul>
     * <p>
     * <strong>线程安全</strong>：操作基于 {@link java.util.concurrent.ConcurrentHashMap#clear()}。
     * 正在运行的脚本已通过 {@link java.lang.invoke.ConstantCallSite} 锁住引用，
     * 不受清理影响。仅影响后续新编译脚本的首次 bootstrap 链接。
     */
    public static void purge() {
        REGISTRY.clear();
        REF_COUNTS.clear();
    }

    // ── Test Support ─────────────────────────────────────────────────────────

    /** 单条注册，仅供测试使用。生产代码应使用 {@link #registerAll}。 */
    public static void register(String key, Object value) {
        REGISTRY.putIfAbsent(key, value);
        REF_COUNTS.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    /** 返回当前注册表条目数，仅供测试断言使用。 */
    public static int registrySizeForTest() {
        return REGISTRY.size();
    }
}

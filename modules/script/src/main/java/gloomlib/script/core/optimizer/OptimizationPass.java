package gloomlib.script.core.optimizer;

import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.ScriptIR.ScriptUnit;

/**
 * 独立优化 Pass 接口。
 * <p>
 * 每个 Pass 接收一个 {@link ScriptUnit}，返回转换后的新单元（不可变保证）。
 * Pass 实例应保持无状态或仅读取 {@link CompilationContext}。
 */
@FunctionalInterface
public interface OptimizationPass {

    /**
     * 执行优化 Pass。
     *
     * @param unit 待优化的脚本单元
     * @param ctx  编译上下文（只读或写入分析结果）
     * @return 优化后的脚本单元
     */
    ScriptUnit apply(ScriptUnit unit, CompilationContext ctx);
}

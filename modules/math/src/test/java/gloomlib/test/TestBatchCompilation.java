package gloomlib.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import gloomlib.math.api.MathEngine;

/**
 * MathEngine 批量编译 API 测试。
 * 覆盖：基本功能、共享类优势、常量折叠、缓存、边界条件。
 */
public class TestBatchCompilation {

    @AfterEach
    public void cleanup() {
        MathEngine.clearCache();
    }

    // ======================== 基本功能 ========================

    @Test
    public void testBatchBasic() {
        MathEngine.BatchResult batch = MathEngine.compileBatch(
                new String[]{
                        "{hp} / {max} * 100",
                        "{hp} > 0 ? 1 : 0",
                        "{max} - {hp}"
                },
                "hp", "max");

        assertEquals(3, batch.size(), "批次应包含 3 个表达式");

        // 表达式 0: hp/max*100
        assertEquals(60.0, batch.get(0).evaluate(60, 100), 0.0001, "60/100*100");

        // 表达式 1: hp>0 ? 1 : 0
        assertEquals(1.0, batch.get(1).evaluate(60, 100), 0.0, "hp>0 → 1");
        assertEquals(0.0, batch.get(1).evaluate(-1, 100), 0.0, "hp<0 → 0");

        // 表达式 2: max-hp
        assertEquals(40.0, batch.get(2).evaluate(60, 100), 0.0001, "100-60");
    }

    @Test
    public void testBatchSingleExpression() {
        // 单个表达式应走普通编译路径，仍返回有效结果
        MathEngine.BatchResult batch = MathEngine.compileBatch(
                new String[]{ "{x} * 2" }, "x");

        assertEquals(1, batch.size());
        assertEquals(10.0, batch.get(0).evaluate(5), 0.0001);
    }

    @Test
    public void testBatchPureConstants() {
        // 全常量表达式应在批量编译中被正确折叠
        MathEngine.BatchResult batch = MathEngine.compileBatch(
                new String[]{ "3 * 5", "pi", "2^10" });

        assertEquals(15.0, batch.get(0).evaluate(), 0.0001, "3*5");
        assertEquals(Math.PI, batch.get(1).evaluate(), 1e-10, "pi");
        assertEquals(1024.0, batch.get(2).evaluate(), 0.0001, "2^10");
    }

    @Test
    public void testBatchMixedConstantAndVariable() {
        MathEngine.BatchResult batch = MathEngine.compileBatch(
                new String[]{
                        "100",           // 纯常量
                        "{x} + 1",       // 含变量
                        "sin(0)"         // 函数折叠为常量
                },
                "x");

        assertEquals(100.0, batch.get(0).evaluate(0), 0.0001);
        assertEquals(6.0, batch.get(1).evaluate(5), 0.0001);
        assertEquals(0.0, batch.get(2).evaluate(0), 0.0001);
    }

    // ======================== 缓存 ========================

    @Test
    public void testBatchCacheHit() {
        MathEngine.BatchResult batch1 = MathEngine.compileBatch(
                new String[]{ "{x} + 1", "{x} * 2" }, "x");
        MathEngine.BatchResult batch2 = MathEngine.compileBatch(
                new String[]{ "{x} + 1", "{x} * 2" }, "x");
        // 相同参数应命中缓存，返回同一实例
        assertSame(batch1, batch2, "相同参数应命中缓存");
    }

    @Test
    public void testBatchCacheClear() {
        MathEngine.compileBatch(new String[]{ "{x} + 1" }, "x");
        MathEngine.clearBatchCache();
        // 清除后不应命中
        MathEngine.BatchResult fresh = MathEngine.compileBatch(
                new String[]{ "{x} + 1" }, "x");
        assertNotNull(fresh, "清除缓存后应重新编译");
    }

    // ======================== 边界条件 ========================

    @Test
    public void testBatchEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> MathEngine.compileBatch(new String[0]));
    }

    @Test
    public void testBatchNull() {
        assertThrows(IllegalArgumentException.class,
                () -> MathEngine.compileBatch(null));
    }

    @Test
    public void testBatchNoVariables() {
        // 零变量批量编译
        MathEngine.BatchResult batch = MathEngine.compileBatch(
                new String[]{ "1 + 2", "3 * 4" });
        assertEquals(3.0, batch.get(0).evaluate(), 0.0001);
        assertEquals(12.0, batch.get(1).evaluate(), 0.0001);
    }

    @Test
    public void testBatchWithFunctions() {
        MathEngine.BatchResult batch = MathEngine.compileBatch(
                new String[]{
                        "clamp({x}, 0, 100)",
                        "abs({x})",
                        "max({x}, 0)"
                },
                "x");

        // clamp test
        assertEquals(50.0, batch.get(0).evaluate(50), 0.0);
        assertEquals(0.0, batch.get(0).evaluate(-10), 0.0);
        assertEquals(100.0, batch.get(0).evaluate(200), 0.0);

        // abs test
        assertEquals(5.0, batch.get(1).evaluate(-5), 0.0);

        // max test
        assertEquals(10.0, batch.get(2).evaluate(10), 0.0);
        assertEquals(0.0, batch.get(2).evaluate(-5), 0.0);
    }

    @Test
    public void testBatchLargeExpressionCount() {
        // 编译较多表达式（10 个），验证不会出错
        String[] exprs = new String[10];
        for (int i = 0; i < 10; i++) {
            exprs[i] = "{x} + " + i;
        }
        MathEngine.BatchResult batch = MathEngine.compileBatch(exprs, "x");
        assertEquals(10, batch.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(5.0 + i, batch.get(i).evaluate(5), 0.0001,
                    "expr[" + i + "]: x+" + i + " with x=5");
        }
    }

    @Test
    public void testBatchWithTernary() {
        MathEngine.BatchResult batch = MathEngine.compileBatch(
                new String[]{
                        "{x} > 0 ? {x} : 0",
                        "{x} > 10 ? 10 : {x}"
                },
                "x");

        assertEquals(5.0, batch.get(0).evaluate(5), 0.0);
        assertEquals(0.0, batch.get(0).evaluate(-3), 0.0);

        assertEquals(10.0, batch.get(1).evaluate(15), 0.0);
        assertEquals(5.0, batch.get(1).evaluate(5), 0.0);
    }
}

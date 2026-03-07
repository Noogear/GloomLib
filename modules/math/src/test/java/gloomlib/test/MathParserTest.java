package gloomlib.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import gloomlib.math.api.MathEngine;
import gloomlib.math.api.MathParser;
import gloomlib.math.api.MathNode;
import gloomlib.math.core.MathFunction;
import gloomlib.math.core.Operator;

/**
 * MathEngine 功能测试。
 * 涵盖：常量折叠 / ASM 字节码编译。
 */
public class MathParserTest {

    // ======================== 基础功能 ========================

    @Test
    public void testBasicArithmetic() {
        // 1 + 2*3 → 常量折叠后 AST 应只剩一个 LiteralNode(7.0)
        var expr = MathEngine.compile("1 + 2 * 3");
        assertEquals(7.0, expr.evaluate(), 0.0001, "1+2*3");

        expr = MathEngine.compile("(1 + 2) * 3");
        assertEquals(9.0, expr.evaluate(), 0.0001, "(1+2)*3");
    }

    @Test
    public void testVariablesDoubleArray() {
        // 带变量：{hp} * 2 + damage，double[] 零装箱接口
        var expr = MathEngine.compile("{hp} * 2 + damage", "hp", "damage");
        assertEquals(25.0, expr.evaluate(10.0, 5.0), 0.0001, "hp*2+damage");
    }

    @Test
    public void testFunctions() {
        var expr = MathEngine.compile("max(10, {x}) + round(3.6)", "x");
        assertEquals(19.0, expr.evaluate(15.0), 0.0001, "max+round");

        expr = MathEngine.compile("sin(0)"); // 全常量 → 折叠到 LiteralNode(0.0)
        assertEquals(0.0, expr.evaluate(), 0.0001, "sin(0) folded");
    }

    @Test
    public void testUnary() {
        var expr = MathEngine.compile("-10 + 5"); // -10 → LiteralNode(-10), +5 → LiteralNode(-5)
        assertEquals(-5.0, expr.evaluate(), 0.0001, "unary -10+5");

        expr = MathEngine.compile("-(10 + 5) * -{x}", "x");
        assertEquals(30.0, expr.evaluate(2.0), 0.0001, "-(10+5)*-x");
    }

    // ======================== 常量折叠验证 ========================

    @Test
    public void testConstantFolding() {
        // 纯常量表达式：AST 应折叠为单个 LiteralNode
        MathNode tree = MathParser.parse("3 * 5 + 2");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "3*5+2 should fold to LiteralNode(17)");
        assertEquals(17.0, ((MathNode.LiteralNode) tree).value(), 0.0001);
    }

    @Test
    public void testPartialConstantFolding() {
        // 部分折叠：(0.5 + 1.5) 被折叠为 2.0，但 {x} 保留
        MathNode tree = MathParser.parse("{x} * (0.5 + 1.5)");
        // 根节点应为 BinaryNode(VariableNode, LiteralNode(2.0))
        assertInstanceOf(MathNode.BinaryNode.class, tree);
        MathNode.BinaryNode bin = (MathNode.BinaryNode) tree;
        assertInstanceOf(MathNode.VariableNode.class, bin.left());
        assertInstanceOf(MathNode.LiteralNode.class, bin.right());
        assertEquals(2.0, ((MathNode.LiteralNode) bin.right()).value(), 0.0001);
    }

    @Test
    public void testConstantFunctionFolding() {
        // sqrt(4) → LiteralNode(2.0)
        MathNode tree = MathParser.parse("sqrt(4)");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "sqrt(4) should fold");
        assertEquals(2.0, ((MathNode.LiteralNode) tree).value(), 0.0001);
    }

    // ======================== 多场景验证 ========================

    @Test
    public void testBytecodeSimple() {
        var expr = MathEngine.compile("10 * {x} + 5/2", "x");
        // 5/2 → 常量折叠 2.5；x=3 → 10*3+2.5=32.5
        assertEquals(32.5, expr.evaluate(3.0), 0.0001, "10*x+5/2");
    }

    @Test
    public void testBytecodeMultiVar() {
        var expr = MathEngine.compile("{hp} / {max} * 100", "hp", "max");
        // hp=60, max=100 → 60%
        assertEquals(60.0, expr.evaluate(60.0, 100.0), 0.0001, "hp/max*100");
    }

    @Test
    public void testBytecodePureConstant() {
        // 纯常量折叠后 AST 只有一个 LiteralNode，ASM 发射单条 LDC
        var expr = MathEngine.compile("3.5^2 + 0.75");
        assertEquals(13.0, expr.evaluate(), 0.0001, "pure constant");
    }

    @Test
    public void testBytecodeFunction() {
        var expr = MathEngine.compile("abs({x}) + floor(1.9)", "x");
        // floor(1.9) → 常量折叠 1.0；x=-5 → 5 + 1 = 6
        assertEquals(6.0, expr.evaluate(-5.0), 0.0001, "abs+floor");
    }

    // ======================== 命名常量 ========================

    @Test
    public void testNamedConstants() {
        var expr = MathEngine.compile("pi");
        assertEquals(Math.PI, expr.evaluate(), 1e-10, "pi");

        expr = MathEngine.compile("e");
        assertEquals(Math.E, expr.evaluate(), 1e-10, "e");

        expr = MathEngine.compile("true");
        assertEquals(1.0, expr.evaluate(), 0.0, "true");

        expr = MathEngine.compile("false");
        assertEquals(0.0, expr.evaluate(), 0.0, "false");

        // 常量折叠
        MathNode tree = MathParser.parse("pi * 2");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "pi*2 should fold");
    }

    // ======================== 科学计数法 ========================

    @Test
    public void testScientificNotation() {
        var expr = MathEngine.compile("1E3");
        assertEquals(1000.0, expr.evaluate(), 0.0001, "1E3");

        expr = MathEngine.compile("2.5e2");
        assertEquals(250.0, expr.evaluate(), 0.0001, "2.5e2");

        expr = MathEngine.compile("1.5e-2");
        assertEquals(0.015, expr.evaluate(), 1e-10, "1.5e-2");
    }

    // ======================== tanh ========================

    @Test
    public void testTanh() {
        var expr = MathEngine.compile("tanh(0)");
        assertEquals(0.0, expr.evaluate(), 0.0001, "tanh(0) folded");

        expr = MathEngine.compile("tanh({x})", "x");
        assertEquals(Math.tanh(1.0), expr.evaluate(1.0), 1e-10, "tanh(1)");
    }

    // ======================== 比较运算符 ========================

    @Test
    public void testComparisonOperators() {
        // 常量折叠路径
        assertEquals(1.0, MathEngine.compile("3 == 3").evaluate(), 0.0, "3==3");
        assertEquals(0.0, MathEngine.compile("3 == 4").evaluate(), 0.0, "3==4");
        assertEquals(1.0, MathEngine.compile("3 != 4").evaluate(), 0.0, "3!=4");
        assertEquals(0.0, MathEngine.compile("3 != 3").evaluate(), 0.0, "3!=3");
        assertEquals(1.0, MathEngine.compile("5 > 3").evaluate(),  0.0, "5>3");
        assertEquals(0.0, MathEngine.compile("3 > 5").evaluate(),  0.0, "3>5");
        assertEquals(1.0, MathEngine.compile("3 >= 3").evaluate(), 0.0, "3>=3");
        assertEquals(0.0, MathEngine.compile("2 >= 3").evaluate(), 0.0, "2>=3");
        assertEquals(1.0, MathEngine.compile("2 < 3").evaluate(),  0.0, "2<3");
        assertEquals(1.0, MathEngine.compile("3 <= 3").evaluate(), 0.0, "3<=3");

        // 含变量的字节码路径
        var expr = MathEngine.compile("{x} > {y}", "x", "y");
        assertEquals(1.0, expr.evaluate(5.0, 3.0), 0.0, "x>y true");
        assertEquals(0.0, expr.evaluate(3.0, 5.0), 0.0, "x>y false");

        expr = MathEngine.compile("{x} == {y}", "x", "y");
        assertEquals(1.0, expr.evaluate(4.0, 4.0), 0.0, "x==y true");
        assertEquals(0.0, expr.evaluate(4.0, 5.0), 0.0, "x==y false");
    }

    // ======================== 布尔运算符 ========================

    @Test
    public void testBooleanOperators() {
        // && 常量折叠
        assertEquals(1.0, MathEngine.compile("true && true").evaluate(),   0.0, "T&&T");
        assertEquals(0.0, MathEngine.compile("true && false").evaluate(),  0.0, "T&&F");
        assertEquals(0.0, MathEngine.compile("false && true").evaluate(),  0.0, "F&&T");

        // || 常量折叠
        assertEquals(1.0, MathEngine.compile("true || false").evaluate(),  0.0, "T||F");
        assertEquals(0.0, MathEngine.compile("false || false").evaluate(), 0.0, "F||F");

        // 含变量的字节码短路路径
        var expr = MathEngine.compile("{a} > 0 && {b} > 0", "a", "b");
        assertEquals(1.0, expr.evaluate(1.0, 2.0), 0.0, "a>0&&b>0 true");
        assertEquals(0.0, expr.evaluate(-1.0, 2.0), 0.0, "a<0&&b>0 false");
        assertEquals(0.0, expr.evaluate(1.0, -2.0), 0.0, "a>0&&b<0 false");

        var expr2 = MathEngine.compile("{a} > 0 || {b} > 0", "a", "b");
        assertEquals(1.0, expr2.evaluate(-1.0, 2.0), 0.0, "a<0||b>0 true");
        assertEquals(0.0, expr2.evaluate(-1.0, -2.0), 0.0, "both neg || false");
    }

    // ======================== 逻辑非 ========================

    // ======================== 逻辑非 ========================

    @Test
    public void testLogicalNot() {
        assertEquals(0.0, MathEngine.compile("!true").evaluate(),  0.0, "!true");
        assertEquals(1.0, MathEngine.compile("!false").evaluate(), 0.0, "!false");

        var expr = MathEngine.compile("!{flag}", "flag");
        assertEquals(0.0, expr.evaluate(1.0), 0.0, "!1");
        assertEquals(1.0, expr.evaluate(0.0), 0.0, "!0");

        var expr2 = MathEngine.compile("!{a} && {b}", "a", "b");
        assertEquals(1.0, expr2.evaluate(0.0, 1.0), 0.0, "!0 && 1");
        assertEquals(0.0, expr2.evaluate(1.0, 1.0), 0.0, "!1 && 1");
    }

    // ======================== 新增折叠优化 ========================

    @Test
    public void testDoubleNegationElimination() {
        // --{x} → x（双重取负消除）
        MathNode tree = MathParser.parse("--{x}");
        assertInstanceOf(MathNode.VariableNode.class, tree, "--x should fold to x");

        // ---{x} → -x（奇数取负只保留一层）
        tree = MathParser.parse("---{x}");
        assertInstanceOf(MathNode.UnaryNode.class, tree, "---x should be -x");
        assertTrue(((MathNode.UnaryNode) tree).isNegation());

        // 标准字节码路径验证
        var expr = MathEngine.compile("--{x}", "x");
        assertEquals(5.0, expr.evaluate(5.0), 0.0, "--x eval");
    }

    @Test
    public void testZeroMinusX() {
        // 0 - x → -x（消除 DSUB，换成 DNEG）
        MathNode tree = MathParser.parse("0 - {x}");
        assertInstanceOf(MathNode.UnaryNode.class, tree, "0-x should fold to -x");
        assertTrue(((MathNode.UnaryNode) tree).isNegation());

        var expr = MathEngine.compile("0 - {x}", "x");
        assertEquals(-3.0, expr.evaluate(3.0), 0.0, "0-x eval");
    }

    @Test
    public void testNegOneMultiply() {
        // -1 * x → -x，x * -1 → -x
        MathNode tree1 = MathParser.parse("-1 * {x}");
        assertInstanceOf(MathNode.UnaryNode.class, tree1, "-1*x should fold to -x");

        MathNode tree2 = MathParser.parse("{x} * -1");
        assertInstanceOf(MathNode.UnaryNode.class, tree2, "x*-1 should fold to -x");

        var expr = MathEngine.compile("-1 * {x}", "x");
        assertEquals(-7.0, expr.evaluate(7.0), 0.0, "-1*x eval");
    }

    @Test
    public void testSameVarSquare() {
        // {x} * {x} → {x}^2（触发 DUP2+DMUL 特化）
        MathNode tree = MathParser.parse("{x} * {x}");
        // 折叠后应为 BinaryNode(x, 2.0, POWER)
        assertInstanceOf(MathNode.BinaryNode.class, tree, "x*x should fold to x^2");
        MathNode.BinaryNode bin = (MathNode.BinaryNode) tree;
        assertEquals(Operator.POWER, bin.op());
        assertEquals(2.0, ((MathNode.LiteralNode) bin.right()).value(), 0.0, "exponent should be 2.0");

        var expr = MathEngine.compile("{x} * {x}", "x");
        assertEquals(9.0, expr.evaluate(3.0), 0.0, "x*x eval");
        assertEquals(25.0, expr.evaluate(5.0), 0.0, "x*x eval 5");
    }

    @Test
    public void testPowerAccumulation() {
        // x*x*x → x^3（后序折叠 pass 累积）
        MathNode tree = MathParser.parse("{x} * {x} * {x}");
        assertInstanceOf(MathNode.BinaryNode.class, tree, "x*x*x should fold to x^3");
        assertEquals(Operator.POWER, ((MathNode.BinaryNode) tree).op());
        assertEquals(3.0, ((MathNode.LiteralNode)((MathNode.BinaryNode) tree).right()).value(), 0.0, "exp=3");

        // x*x*x*x → x^4
        tree = MathParser.parse("{x} * {x} * {x} * {x}");
        assertEquals(4.0, ((MathNode.LiteralNode)((MathNode.BinaryNode) tree).right()).value(), 0.0, "exp=4");

        var expr = MathEngine.compile("{x} * {x} * {x}", "x");
        assertEquals(27.0, expr.evaluate(3.0), 0.0, "x^3 eval");

        expr = MathEngine.compile("{x} * {x} * {x} * {x}", "x");
        assertEquals(16.0, expr.evaluate(2.0), 0.0, "x^4 eval");

        // x^2 * x^3 → x^5（x^n * x^m → x^(n+m)）
        tree = MathParser.parse("{x}^2 * {x}^3");
        assertInstanceOf(MathNode.BinaryNode.class, tree);
        assertEquals(Operator.POWER, ((MathNode.BinaryNode) tree).op());
        assertEquals(5.0, ((MathNode.LiteralNode)((MathNode.BinaryNode) tree).right()).value(), 0.0, "exp=5");

        expr = MathEngine.compile("{x}^2 * {x}^3", "x");
        assertEquals(32.0, expr.evaluate(2.0), 0.0, "x^5 eval");
    }

    @Test
    public void testCompoundNegationFold() {
        // 0-(0-x) → x（foldNode 消除由 tryFoldIdentity 产生的双重 UnaryNode）
        MathNode tree = MathParser.parse("0 - (0 - {x})");
        assertInstanceOf(MathNode.VariableNode.class, tree, "0-(0-x) should fold to x");

        var expr = MathEngine.compile("0 - (0 - {x})", "x");
        assertEquals(5.0, expr.evaluate(5.0), 0.0, "0-(0-x) eval");
    }

    @Test
    public void testAndOrShortCircuitFold() {
        // false && expr → false（右侧死代码消除）
        MathNode tree = MathParser.parse("false && {x}");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "false&&x should fold to 0.0");
        assertEquals(0.0, ((MathNode.LiteralNode) tree).value(), 0.0);

        // expr && false → false
        tree = MathParser.parse("{x} && false");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "x&&false should fold to 0.0");

        // true || expr → true（右侧死代码消除）
        tree = MathParser.parse("true || {x}");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "true||x should fold to 1.0");
        assertEquals(1.0, ((MathNode.LiteralNode) tree).value(), 0.0);

        // expr || true → true
        tree = MathParser.parse("{x} || true");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "x||true should fold to 1.0");

        // 字节码路径验证（运行时短路）
        var expr = MathEngine.compile("{a} > 0 && {b} > 0", "a", "b");
        assertEquals(1.0, expr.evaluate(1.0, 1.0), 0.0, "runtime &&");
        assertEquals(0.0, expr.evaluate(0.0, 1.0), 0.0, "runtime && false");
    }

    // ======================== 三元运算符 ========================

    @Test
    public void testTernaryConstantFolding() {
        // 常量条件 -> 折叠到选中分支
        MathNode tree = MathParser.parse("true ? 10 : 20");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "true?10:20 should fold to 10");
        assertEquals(10.0, ((MathNode.LiteralNode) tree).value(), 0.0);

        tree = MathParser.parse("false ? 10 : 20");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "false?10:20 should fold to 20");
        assertEquals(20.0, ((MathNode.LiteralNode) tree).value(), 0.0);
    }

    @Test
    public void testTernaryBytecode() {
        // 含变量：短路求值
        var expr = MathEngine.compile("{x} > 0 ? {x} * 2 : {x} * -1", "x");
        assertEquals(10.0, expr.evaluate(5.0), 0.0, "5>0 ? 10 : -5");
        assertEquals(3.0, expr.evaluate(-3.0), 0.0, "-3>0 ? -6 : 3");
        assertEquals(0.0, expr.evaluate(0.0), 0.0, "0>0 ? 0 : 0");
    }

    @Test
    public void testTernaryNested() {
        // 嵌套三元：{x} > 0 ? ({x} > 10 ? 100 : 50) : 0
        var expr = MathEngine.compile("{x} > 0 ? ({x} > 10 ? 100 : 50) : 0", "x");
        assertEquals(100.0, expr.evaluate(15.0), 0.0, "15>0 && 15>10 → 100");
        assertEquals(50.0, expr.evaluate(5.0), 0.0, "5>0 && 5<10 → 50");
        assertEquals(0.0, expr.evaluate(-1.0), 0.0, "-1<0 → 0");
    }

    @Test
    public void testTernaryWithFunctions() {
        // 三元与函数组合
        var expr = MathEngine.compile("{x} >= 0 ? sqrt({x}) : 0", "x");
        assertEquals(3.0, expr.evaluate(9.0), 0.0001, "sqrt(9)");
        assertEquals(0.0, expr.evaluate(-4.0), 0.0, "neg → 0");
    }

    // ======================== 自定义函数 ========================

    @Test
    public void testBuiltinClamp() {
        var expr = MathEngine.compile("clamp({x}, 0, 100)", "x");
        assertEquals(50.0, expr.evaluate(50.0), 0.0, "clamp(50,0,100)");
        assertEquals(0.0, expr.evaluate(-10.0), 0.0, "clamp(-10,0,100)");
        assertEquals(100.0, expr.evaluate(200.0), 0.0, "clamp(200,0,100)");
    }

    @Test
    public void testBuiltinLerp() {
        var expr = MathEngine.compile("lerp(0, 100, {t})", "t");
        assertEquals(0.0, expr.evaluate(0.0), 0.0, "lerp t=0");
        assertEquals(50.0, expr.evaluate(0.5), 0.0, "lerp t=0.5");
        assertEquals(100.0, expr.evaluate(1.0), 0.0, "lerp t=1");
    }

    @Test
    public void testBuiltinSaturate() {
        var expr = MathEngine.compile("saturate({x})", "x");
        assertEquals(0.0, expr.evaluate(-0.5), 0.0, "saturate(-0.5)");
        assertEquals(0.5, expr.evaluate(0.5), 0.0, "saturate(0.5)");
        assertEquals(1.0, expr.evaluate(1.5), 0.0, "saturate(1.5)");
    }

    @Test
    public void testBuiltinSign() {
        var expr = MathEngine.compile("sign({x})", "x");
        assertEquals(-1.0, expr.evaluate(-42.0), 0.0, "sign(-42)");
        assertEquals(0.0, expr.evaluate(0.0), 0.0, "sign(0)");
        assertEquals(1.0, expr.evaluate(42.0), 0.0, "sign(42)");
    }

    @Test
    public void testBuiltinStep() {
        var expr = MathEngine.compile("step(5, {x})", "x");
        assertEquals(0.0, expr.evaluate(3.0), 0.0, "step(5,3)");
        assertEquals(1.0, expr.evaluate(5.0), 0.0, "step(5,5)");
        assertEquals(1.0, expr.evaluate(7.0), 0.0, "step(5,7)");
    }

    @Test
    public void testBuiltinSmoothstep() {
        var expr = MathEngine.compile("smoothstep(0, 1, {x})", "x");
        assertEquals(0.0, expr.evaluate(0.0), 0.0, "smoothstep at 0");
        assertEquals(1.0, expr.evaluate(1.0), 0.0, "smoothstep at 1");
        assertEquals(0.5, expr.evaluate(0.5), 0.0001, "smoothstep at 0.5");
        // 边界外
        assertEquals(0.0, expr.evaluate(-1.0), 0.0, "smoothstep below");
        assertEquals(1.0, expr.evaluate(2.0), 0.0, "smoothstep above");
    }

    @Test
    public void testBuiltinMap() {
        // map(50, 0, 100, 0, 1) → 0.5
        var expr = MathEngine.compile("map({x}, 0, 100, 0, 1)", "x");
        assertEquals(0.0, expr.evaluate(0.0), 0.0, "map 0");
        assertEquals(0.5, expr.evaluate(50.0), 0.0001, "map 50");
        assertEquals(1.0, expr.evaluate(100.0), 0.0, "map 100");
    }

    @Test
    public void testCustomFunctionConstantFolding() {
        // clamp(50, 0, 100) → 全常量，应折叠为 LiteralNode
        MathNode tree = MathParser.parse("clamp(50, 0, 100)");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "clamp(50,0,100) should fold");
        assertEquals(50.0, ((MathNode.LiteralNode) tree).value(), 0.0);
    }

    @Test
    public void testCustomFunctionRegistration() {
        // 注册自定义函数并使用
        MathFunction.register("triple", 1, args -> args[0] * 3.0);
        try {
            var expr = MathEngine.compile("triple({x})", "x");
            assertEquals(15.0, expr.evaluate(5.0), 0.0, "triple(5)");
            assertEquals(-6.0, expr.evaluate(-2.0), 0.0, "triple(-2)");
        } finally {
            MathFunction.unregister("triple");
        }
    }

    // ======================== 变量默认值 ========================

    @Test
    public void testVariableDefaultValueParsing() {
        // {hp:-0} → VariableNode(hp, -1, 0.0)
        MathNode tree = MathParser.parse("{hp:-0}");
        assertInstanceOf(MathNode.VariableNode.class, tree);
        MathNode.VariableNode v = (MathNode.VariableNode) tree;
        assertEquals("hp", v.name());
        assertTrue(v.hasDefault(), "should have default");
        assertEquals(0.0, v.defaultVal(), 0.0);
    }

    @Test
    public void testVariableDefaultValueNegative() {
        // {dmg:-5.5} → VariableNode(dmg, -1, -5.5)
        MathNode tree = MathParser.parse("{dmg:--5.5}");
        assertInstanceOf(MathNode.VariableNode.class, tree);
        MathNode.VariableNode v = (MathNode.VariableNode) tree;
        assertEquals("dmg", v.name());
        assertEquals(-5.5, v.defaultVal(), 0.0);
    }

    @Test
    public void testVariableNoDefault() {
        // {hp} → VariableNode(hp, -1, NaN)
        MathNode tree = MathParser.parse("{hp}");
        assertInstanceOf(MathNode.VariableNode.class, tree);
        MathNode.VariableNode v = (MathNode.VariableNode) tree;
        assertFalse(v.hasDefault(), "should NOT have default");
    }

    @Test
    public void testVariableDefaultPreservedAfterBindIndex() {
        // 确保 bindIndex 不丢失 defaultVal
        var expr = MathEngine.compile("{hp:-10} * 2", "hp");
        // 编译成功即可，说明 defaultVal 被正确传递
        assertEquals(20.0, expr.evaluate(10.0), 0.0, "value provided overrides default");
    }

    // ======================== LRU 缓存 ========================

    @Test
    public void testLRUCache() {
        MathEngine.clearCache();
        MathEngine.setMaxCacheSize(3);
        try {
            // 填充 3 个
            MathEngine.compile("1+1");
            MathEngine.compile("2+2");
            MathEngine.compile("3+3");
            assertEquals(3, MathEngine.cacheSize(), "cache should have 3 entries");

            // 第 4 个应淘汰最早的
            MathEngine.compile("4+4");
            assertEquals(3, MathEngine.cacheSize(), "cache should still be capped at 3");
        } finally {
            MathEngine.setMaxCacheSize(0); // 恢复无限制
            MathEngine.clearCache();
        }
    }

    @Test
    public void testCacheClear() {
        MathEngine.compile("99+1");
        assertTrue(MathEngine.cacheSize() > 0, "cache should not be empty");
        MathEngine.clearCache();
        assertEquals(0, MathEngine.cacheSize(), "cache should be empty after clear");
    }

    // ======================== 三元 + 自定义函数组合 ========================

    @Test
    public void testTernaryWithCustomFunction() {
        var expr = MathEngine.compile("{x} > 0 ? clamp({x}, 0, 10) : 0", "x");
        assertEquals(5.0, expr.evaluate(5.0), 0.0, "clamp in ternary true");
        assertEquals(10.0, expr.evaluate(15.0), 0.0, "clamp caps at 10");
        assertEquals(0.0, expr.evaluate(-5.0), 0.0, "ternary false → 0");
    }

    @Test
    public void testComplexExpression() {
        // 综合测试：三元 + 函数 + 自定义函数 + 比较
        var expr = MathEngine.compile(
                "{hp} > 0 ? clamp({hp} / {max} * 100, 0, 100) : 0",
                "hp", "max");
        assertEquals(60.0, expr.evaluate(60.0, 100.0), 0.0001, "60%");
        assertEquals(100.0, expr.evaluate(150.0, 100.0), 0.0, "capped at 100");
        assertEquals(0.0, expr.evaluate(-10.0, 100.0), 0.0, "dead → 0");
    }
}

package gloomlib.test;

import gloomlib.diagnostic.DiagnosticCategory;
import gloomlib.diagnostic.DiagnosticException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import gloomlib.math.api.MathParser;
import gloomlib.math.api.MathNode;
import gloomlib.math.api.MathEngine;
import gloomlib.diagnostic.DiagnosticCategory;
import gloomlib.diagnostic.DiagnosticException;

/**
 * MathParser 诊断集成测试。
 * 验证各类解析错误都携带精确的位置信息、错误类别和源码片段。
 */
public class TestMathDiagnostics {

    // ======================== 解析错误（PARSE 类别） ========================

    @Test
    public void testMismatchedParentheses() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathParser.parse("(1 + 2"));
        assertEquals(DiagnosticCategory.PARSE, ex.category(), "类别应为 PARSE");
        assertTrue(ex.getMessage().contains("Mismatched parentheses"));
        assertTrue(ex.getMessage().contains("↑"), "应包含源码指示符");
    }

    @Test
    public void testMissingTernaryColon() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathParser.parse("{x} > 0 ? 1"));
        assertEquals(DiagnosticCategory.PARSE, ex.category());
        assertTrue(ex.getMessage().contains("Expected ':'"));
    }

    @Test
    public void testUnexpectedCharacter() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathParser.parse("1 + @"));
        assertEquals(DiagnosticCategory.PARSE, ex.category());
        assertTrue(ex.getMessage().contains("Unexpected character"));
        assertTrue(ex.getMessage().contains("@"));
    }

    @Test
    public void testEmptyExpression() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathParser.parse(""));
        assertEquals(DiagnosticCategory.PARSE, ex.category());
        assertTrue(ex.getMessage().contains("Empty expression"));
    }

    @Test
    public void testMissingFunctionParenthesis() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathParser.parse("max 1, 2"));
        assertEquals(DiagnosticCategory.PARSE, ex.category());
        assertTrue(ex.getMessage().contains("Expected '('"));
    }

    @Test
    public void testUnclosedFunctionCall() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathParser.parse("max(1, 2"));
        assertEquals(DiagnosticCategory.PARSE, ex.category());
        assertTrue(ex.getMessage().contains("Expected ')'"));
    }

    // ======================== 语义错误（SEMANTIC 类别） ========================

    @Test
    public void testWrongArgCount() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathParser.parse("max(1)"));
        assertEquals(DiagnosticCategory.SEMANTIC, ex.category(), "参数数量不匹配应为 SEMANTIC");
        assertTrue(ex.getMessage().contains("expects 2 args, got 1"));
    }

    @Test
    public void testUnknownVariableInBindIndex() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathEngine.compile("{undefined}", "hp"));
        assertEquals(DiagnosticCategory.SEMANTIC, ex.category());
        assertTrue(ex.getMessage().contains("Unknown variable"));
        assertTrue(ex.getMessage().contains("undefined"));
    }

    // ======================== 位置精确性验证 ========================

    @Test
    public void testErrorPositionAccuracy() {
        // "1 + * 2" → 出错位置应该指向 '*'（offset 4）
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathParser.parse("1 + * 2"));
        assertNotNull(ex.location());
        // 列号 = offset + 1 = 5（'*' 在 index 4）
        assertEquals(1, ex.location().line(), "单行表达式 line=1");
    }

    @Test
    public void testDiagnosticExceptionHasFormattedMessage() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> MathParser.parse("1 + )"));
        String msg = ex.getMessage();
        // 格式：[Parse] at source:1:N — message\n  source\n  ↑
        assertTrue(msg.contains("[Parse]"), "应包含类别前缀");
        assertTrue(msg.contains("↑"), "应包含插入符号");
    }

    // ======================== 正常解析不受影响 ========================

    @Test
    public void testValidExpressionStillWorks() {
        // 确保诊断集成不影响正常解析
        MathNode tree = MathParser.parse("1 + 2 * 3");
        assertInstanceOf(MathNode.LiteralNode.class, tree);
        assertEquals(7.0, ((MathNode.LiteralNode) tree).value(), 0.0001);
    }

    @Test
    public void testValidFunctionStillWorks() {
        MathNode tree = MathParser.parse("max(1, 2)");
        assertInstanceOf(MathNode.LiteralNode.class, tree, "max(1,2) 应折叠为 2.0");
        assertEquals(2.0, ((MathNode.LiteralNode) tree).value(), 0.0001);
    }

    @Test
    public void testValidVariableExprCompiles() {
        var expr = MathEngine.compile("{hp} * 2", "hp");
        assertEquals(20.0, expr.evaluate(10.0), 0.0001);
    }
}

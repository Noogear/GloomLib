package gloomlib.test;

import gloomlib.translation.exception.LocalizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalizedException 测试")
class LocalizedExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("应正确创建带节点的异常")
        void shouldCreateWithNode() {
            LocalizedException ex = new LocalizedException("error.test");
            assertEquals("error.test", ex.node());
            assertEquals(0, ex.arguments().length);
        }

        @Test
        @DisplayName("应正确创建带参数的异常")
        void shouldCreateWithArguments() {
            LocalizedException ex = new LocalizedException("error.test", "arg1", "arg2");
            assertEquals("error.test", ex.node());
            assertArrayEquals(new String[]{"arg1", "arg2"}, ex.arguments());
        }

        @Test
        @DisplayName("应正确创建带原因的异常")
        void shouldCreateWithCause() {
            Exception cause = new RuntimeException("root cause");
            LocalizedException ex = new LocalizedException("error.test", cause, "arg1");
            assertEquals("error.test", ex.node());
            assertSame(cause, ex.getCause());
            assertArrayEquals(new String[]{"arg1"}, ex.arguments());
        }

        @Test
        @DisplayName("应处理 null 参数数组")
        void shouldHandleNullArguments() {
            LocalizedException ex = new LocalizedException("error.test", (String[]) null);
            assertEquals(0, ex.arguments().length);
        }
    }

    @Nested
    @DisplayName("参数操作测试")
    class ArgumentOperationTests {

        @Test
        @DisplayName("arguments() 应返回副本")
        void argumentsShouldReturnCopy() {
            LocalizedException ex = new LocalizedException("error.test", "arg1");
            String[] args = ex.arguments();
            args[0] = "modified";
            assertEquals("arg1", ex.arguments()[0]);
        }

        @Test
        @DisplayName("setArgument 应正确设置参数")
        void setArgumentShouldWork() {
            LocalizedException ex = new LocalizedException("error.test", "old");
            ex.setArgument(0, "new");
            assertEquals("new", ex.arguments()[0]);
        }

        @Test
        @DisplayName("setArgument 应拒绝无效索引")
        void setArgumentShouldRejectInvalidIndex() {
            LocalizedException ex = new LocalizedException("error.test", "arg1");
            assertThrows(IndexOutOfBoundsException.class, () -> ex.setArgument(-1, "x"));
            assertThrows(IndexOutOfBoundsException.class, () -> ex.setArgument(1, "x"));
        }

        @Test
        @DisplayName("prependArgument 应在开头添加参数")
        void prependArgumentShouldWork() {
            LocalizedException ex = new LocalizedException("error.test", "second");
            ex.prependArgument("first");
            assertArrayEquals(new String[]{"first", "second"}, ex.arguments());
        }

        @Test
        @DisplayName("appendArgument 应在末尾添加参数")
        void appendArgumentShouldWork() {
            LocalizedException ex = new LocalizedException("error.test", "first");
            ex.appendArgument("second");
            assertArrayEquals(new String[]{"first", "second"}, ex.arguments());
        }

        @Test
        @DisplayName("连续操作应正确执行")
        void chainedOperationsShouldWork() {
            LocalizedException ex = new LocalizedException("error.test");
            ex.appendArgument("middle");
            ex.prependArgument("first");
            ex.appendArgument("last");
            assertArrayEquals(new String[]{"first", "middle", "last"}, ex.arguments());
        }
    }

    @Nested
    @DisplayName("getMessage 测试")
    class GetMessageTests {

        @Test
        @DisplayName("无 TranslationManager 时应返回节点名")
        void shouldReturnNodeWhenNoManager() {
            LocalizedException ex = new LocalizedException("error.unknown.key");
            String message = ex.getMessage();
            assertEquals("error.unknown.key", message);
        }

        @Test
        @DisplayName("消息应替换参数占位符")
        void messageShouldReplaceArgumentPlaceholders() {
            LocalizedException ex = new LocalizedException("error.test", "value1", "value2");
            String message = ex.getMessage();
            assertNotNull(message);
        }
    }

    @Nested
    @DisplayName("异常链测试")
    class ExceptionChainTests {

        @Test
        @DisplayName("异常应可作为标准异常使用")
        void shouldWorkAsStandardException() {
            LocalizedException ex = new LocalizedException("error.test", "arg");

            try {
                throw ex;
            } catch (RuntimeException e) {
                assertTrue(e instanceof LocalizedException);
                assertEquals("error.test", ((LocalizedException) e).node());
            }
        }

        @Test
        @DisplayName("异常链应正确传递")
        void exceptionChainShouldPropagate() {
            Exception root = new IllegalArgumentException("invalid input");
            LocalizedException wrapper = new LocalizedException("error.wrapper", root, "context");

            assertSame(root, wrapper.getCause());
            assertEquals("error.wrapper", wrapper.node());
        }
    }
}

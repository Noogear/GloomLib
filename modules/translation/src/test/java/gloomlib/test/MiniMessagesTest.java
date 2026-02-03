package gloomlib.test;

import gloomlib.translation.util.MiniMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MiniMessages 工具类测试")
class MiniMessagesTest {

    @Test
    @DisplayName("构造函数应抛出 UnsupportedOperationException")
    void constructorShouldThrowException() {
        assertThrows(Exception.class, () -> {
            var constructor = MiniMessages.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
    }

    @Nested
    @DisplayName("实例获取测试")
    class InstanceTests {

        @Test
        @DisplayName("get() 应返回相同的默认实例")
        void getShouldReturnSameInstance() {
            MiniMessage first = MiniMessages.get();
            MiniMessage second = MiniMessages.get();
            assertSame(first, second);
        }

        @Test
        @DisplayName("strict() 应返回相同的严格实例")
        void strictShouldReturnSameInstance() {
            MiniMessage first = MiniMessages.strict();
            MiniMessage second = MiniMessages.strict();
            assertSame(first, second);
        }

        @Test
        @DisplayName("emptyTags() 应返回相同的空标签实例")
        void emptyTagsShouldReturnSameInstance() {
            MiniMessage first = MiniMessages.emptyTags();
            MiniMessage second = MiniMessages.emptyTags();
            assertSame(first, second);
        }

        @Test
        @DisplayName("三种实例应该互不相同")
        void instancesShouldBeDifferent() {
            MiniMessage defaultInstance = MiniMessages.get();
            MiniMessage strictInstance = MiniMessages.strict();
            MiniMessage emptyTagsInstance = MiniMessages.emptyTags();

            assertNotSame(defaultInstance, strictInstance);
            assertNotSame(defaultInstance, emptyTagsInstance);
            assertNotSame(strictInstance, emptyTagsInstance);
        }
    }

    @Nested
    @DisplayName("MiniMessage 解析测试")
    class ParseTests {

        @Test
        @DisplayName("默认实例应解析 MiniMessage 格式")
        void defaultShouldParseMiniMessage() {
            Component result = MiniMessages.get().deserialize("<red>Hello</red>");
            assertNotNull(result);
        }

        @Test
        @DisplayName("emptyTags() 不应解析标签")
        void emptyTagsShouldNotParseTags() {
            String input = "<red>Hello</red>";
            Component result = MiniMessages.emptyTags().deserialize(input);
            String plainText = MiniMessages.toPlainText(result);
            assertEquals("<red>Hello</red>", plainText);
        }
    }

    @Nested
    @DisplayName("toPlainText 测试")
    class ToPlainTextTests {

        @Test
        @DisplayName("应提取简单文本")
        void shouldExtractSimpleText() {
            Component component = Component.text("Hello World");
            String result = MiniMessages.toPlainText(component);
            assertEquals("Hello World", result);
        }

        @Test
        @DisplayName("应忽略颜色格式")
        void shouldIgnoreFormatting() {
            Component component = Component.text("Hello", NamedTextColor.RED)
                    .append(Component.text(" World", NamedTextColor.BLUE));
            String result = MiniMessages.toPlainText(component);
            assertEquals("Hello World", result);
        }

        @Test
        @DisplayName("应递归提取子组件文本")
        void shouldExtractNestedText() {
            Component nested = Component.text("Parent")
                    .append(Component.text(" - "))
                    .append(Component.text("Child1")
                            .append(Component.text(" + "))
                            .append(Component.text("Child2")));
            String result = MiniMessages.toPlainText(nested);
            assertEquals("Parent - Child1 + Child2", result);
        }

        @Test
        @DisplayName("应处理空组件")
        void shouldHandleEmptyComponent() {
            Component empty = Component.empty();
            String result = MiniMessages.toPlainText(empty);
            assertEquals("", result);
        }

        @Test
        @DisplayName("应处理 MiniMessage 解析的组件")
        void shouldHandleMiniMessageParsed() {
            Component parsed = MiniMessages.get().deserialize("<bold><red>Error:</red></bold> Something went wrong");
            String result = MiniMessages.toPlainText(parsed);
            assertEquals("Error: Something went wrong", result);
        }
    }
}

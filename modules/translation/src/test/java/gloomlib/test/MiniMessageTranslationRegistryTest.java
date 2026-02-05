package gloomlib.test;

import gloomlib.translation.api.MiniMessageTranslationRegistry;
import gloomlib.translation.util.MiniMessages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MiniMessageTranslationRegistry 测试")
class MiniMessageTranslationRegistryTest {

    private MiniMessageTranslationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = MiniMessageTranslationRegistry.create(
                Key.key("test", "registry"),
                MiniMessages.get()
        );
        registry.defaultLocale(Locale.ENGLISH);
    }

    @Nested
    @DisplayName("注册测试")
    class RegisterTests {

        @Test
        @DisplayName("应注册单个翻译")
        void shouldRegisterSingleTranslation() {
            registry.register("test.key", Locale.ENGLISH, "Hello World");
            assertTrue(registry.contains("test.key"));
        }

        @Test
        @DisplayName("应注册多个语言")
        void shouldRegisterMultipleLocales() {
            registry.register("greeting", Locale.ENGLISH, "Hello");
            registry.register("greeting", Locale.CHINESE, "你好");
            assertTrue(registry.contains("greeting"));
        }

        @Test
        @DisplayName("重复注册应抛出异常")
        void duplicateRegistrationShouldThrow() {
            registry.register("test.key", Locale.ENGLISH, "First");
            assertThrows(IllegalArgumentException.class,
                    () -> registry.register("test.key", Locale.ENGLISH, "Second"));
        }

        @Test
        @DisplayName("registerAll 应批量注册")
        void registerAllShouldWork() {
            Map<String, String> bundle = new HashMap<>();
            bundle.put("key1", "Value 1");
            bundle.put("key2", "Value 2");
            bundle.put("key3", "Value 3");

            registry.registerAll(Locale.ENGLISH, bundle);

            assertTrue(registry.contains("key1"));
            assertTrue(registry.contains("key2"));
            assertTrue(registry.contains("key3"));
        }
    }

    @Nested
    @DisplayName("查询测试")
    class QueryTests {

        @BeforeEach
        void registerTestData() {
            registry.register("greeting", Locale.ENGLISH, "Hello");
            registry.register("greeting", Locale.CHINESE, "你好");
            registry.register("english.only", Locale.ENGLISH, "English only");
        }

        @Test
        @DisplayName("contains 应正确检测键存在")
        void containsShouldWork() {
            assertTrue(registry.contains("greeting"));
            assertFalse(registry.contains("nonexistent"));
        }

        @Test
        @DisplayName("miniMessageTranslation 应返回正确的翻译")
        void miniMessageTranslationShouldWork() {
            assertEquals("Hello", registry.miniMessageTranslation("greeting", Locale.ENGLISH));
            assertEquals("你好", registry.miniMessageTranslation("greeting", Locale.CHINESE));
        }

        @Test
        @DisplayName("应回退到默认语言")
        void shouldFallbackToDefaultLocale() {
            String result = registry.miniMessageTranslation("english.only", Locale.JAPANESE);
            assertEquals("English only", result);
        }

        @Test
        @DisplayName("应回退到语言变体")
        void shouldFallbackToLanguageVariant() {
            registry.register("lang.test", Locale.of("zh"), "Language only");

            String result = registry.miniMessageTranslation("lang.test", Locale.of("zh", "TW"));
            assertEquals("Language only", result);
        }

        @Test
        @DisplayName("不存在的键应返回 null")
        void nonExistentKeyShouldReturnNull() {
            assertNull(registry.miniMessageTranslation("nonexistent", Locale.ENGLISH));
        }
    }

    @Nested
    @DisplayName("取消注册测试")
    class UnregisterTests {

        @Test
        @DisplayName("unregister 应移除翻译")
        void unregisterShouldWork() {
            registry.register("test.key", Locale.ENGLISH, "Test");
            assertTrue(registry.contains("test.key"));

            registry.unregister("test.key");
            assertFalse(registry.contains("test.key"));
        }

        @Test
        @DisplayName("unregister 应移除所有语言变体")
        void unregisterShouldRemoveAllLocales() {
            registry.register("multi.lang", Locale.ENGLISH, "English");
            registry.register("multi.lang", Locale.CHINESE, "中文");
            registry.register("multi.lang", Locale.JAPANESE, "日本語");

            registry.unregister("multi.lang");

            assertFalse(registry.contains("multi.lang"));
            assertNull(registry.miniMessageTranslation("multi.lang", Locale.ENGLISH));
            assertNull(registry.miniMessageTranslation("multi.lang", Locale.CHINESE));
        }

        @Test
        @DisplayName("unregister 后可重新注册")
        void canReRegisterAfterUnregister() {
            registry.register("test.key", Locale.ENGLISH, "First");
            registry.unregister("test.key");
            registry.register("test.key", Locale.ENGLISH, "Second");

            assertEquals("Second", registry.miniMessageTranslation("test.key", Locale.ENGLISH));
        }
    }

    @Nested
    @DisplayName("翻译组件测试")
    class TranslateComponentTests {

        @BeforeEach
        void registerTestData() {
            registry.register("simple", Locale.ENGLISH, "Simple text");
            registry.register("formatted", Locale.ENGLISH, "<red>Red text</red>");
            registry.register("with.args", Locale.ENGLISH, "Hello <arg:0>!");
            registry.register("empty", Locale.ENGLISH, "");
        }

        @Test
        @DisplayName("应翻译简单组件")
        void shouldTranslateSimpleComponent() {
            TranslatableComponent translatable = Component.translatable("simple");
            Component result = registry.translate(translatable, Locale.ENGLISH);

            assertNotNull(result);
            String plainText = MiniMessages.toPlainText(result);
            assertEquals("Simple text", plainText);
        }

        @Test
        @DisplayName("应解析 MiniMessage 格式")
        void shouldParseMiniMessageFormat() {
            TranslatableComponent translatable = Component.translatable("formatted");
            Component result = registry.translate(translatable, Locale.ENGLISH);

            assertNotNull(result);
            String plainText = MiniMessages.toPlainText(result);
            assertEquals("Red text", plainText);
        }

        @Test
        @DisplayName("应替换参数")
        void shouldReplaceArguments() {
            TranslatableComponent translatable = Component.translatable(
                    "with.args",
                    Component.text("World")
            );
            Component result = registry.translate(translatable, Locale.ENGLISH);

            assertNotNull(result);
            String plainText = MiniMessages.toPlainText(result);
            assertEquals("Hello World!", plainText);
        }

        @Test
        @DisplayName("空翻译应返回空组件")
        void emptyTranslationShouldReturnEmptyComponent() {
            TranslatableComponent translatable = Component.translatable("empty");
            Component result = registry.translate(translatable, Locale.ENGLISH);

            assertEquals(Component.empty(), result);
        }

        @Test
        @DisplayName("不存在的翻译应返回 null")
        void nonExistentTranslationShouldReturnNull() {
            TranslatableComponent translatable = Component.translatable("nonexistent");
            Component result = registry.translate(translatable, Locale.ENGLISH);

            assertNull(result);
        }

        @Test
        @DisplayName("应保留子组件")
        void shouldPreserveChildren() {
            TranslatableComponent translatable = Component.translatable("simple")
                    .append(Component.text(" - suffix"));
            Component result = registry.translate(translatable, Locale.ENGLISH);

            assertNotNull(result);
            String plainText = MiniMessages.toPlainText(result);
            assertEquals("Simple text - suffix", plainText);
        }
    }

    @Nested
    @DisplayName("hasAnyTranslations 测试")
    class HasAnyTranslationsTests {

        @Test
        @DisplayName("空注册表应返回 FALSE")
        void emptyShouldReturnFalse() {
            assertEquals(net.kyori.adventure.util.TriState.FALSE, registry.hasAnyTranslations());
        }

        @Test
        @DisplayName("有翻译时应返回 TRUE")
        void withTranslationsShouldReturnTrue() {
            registry.register("test", Locale.ENGLISH, "value");
            assertEquals(net.kyori.adventure.util.TriState.TRUE, registry.hasAnyTranslations());
        }
    }

    @Nested
    @DisplayName("缓存测试")
    class CacheTests {

        @Test
        @DisplayName("无参翻译应被缓存")
        void noArgTranslationShouldBeCached() {
            registry.register("cached", Locale.ENGLISH, "Cached value");

            TranslatableComponent translatable = Component.translatable("cached");
            Component first = registry.translate(translatable, Locale.ENGLISH);
            Component second = registry.translate(translatable, Locale.ENGLISH);

            assertSame(first, second);
        }

        @Test
        @DisplayName("带参翻译不应被缓存")
        void argTranslationShouldNotBeCached() {
            registry.register("dynamic", Locale.ENGLISH, "Hello <arg:0>!");

            TranslatableComponent t1 = Component.translatable("dynamic", Component.text("World"));
            TranslatableComponent t2 = Component.translatable("dynamic", Component.text("World"));

            Component first = registry.translate(t1, Locale.ENGLISH);
            Component second = registry.translate(t2, Locale.ENGLISH);

            assertNotSame(first, second);
        }

        @Test
        @DisplayName("unregister 应清除缓存")
        void unregisterShouldClearCache() {
            registry.register("cached", Locale.ENGLISH, "First");
            TranslatableComponent translatable = Component.translatable("cached");
            Component first = registry.translate(translatable, Locale.ENGLISH);

            registry.unregister("cached");
            registry.register("cached", Locale.ENGLISH, "Second");
            Component second = registry.translate(translatable, Locale.ENGLISH);

            assertNotSame(first, second);
            assertEquals("Second", MiniMessages.toPlainText(second));
        }
    }

    @Nested
    @DisplayName("Locale 缓存测试")
    class LocaleCacheTests {

        @Test
        @DisplayName("相同语言的不同地区应复用 Locale 对象")
        void sameLanguageShouldReuseLocale() {
            registry.register("lang.test", Locale.of("en"), "English");

            registry.miniMessageTranslation("lang.test", Locale.of("en", "US"));
            registry.miniMessageTranslation("lang.test", Locale.of("en", "GB"));
            registry.miniMessageTranslation("lang.test", Locale.of("en", "AU"));
        }
    }
}

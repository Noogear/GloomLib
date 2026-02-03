package gloomlib.test;

import gloomlib.translation.api.TranslationManager;
import gloomlib.translation.util.MiniMessages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("翻译资源文件集成测试")
class TranslationResourceTest {

    @TempDir
    static Path tempDir;

    private static Path resourceDir;
    private TranslationManager manager;

    @BeforeAll
    static void setUpResources() throws IOException {
        resourceDir = tempDir.resolve("translations");
        Files.createDirectories(resourceDir);

        copyResource("translations/en_US.yml", resourceDir.resolve("en_US.yml"));
        copyResource("translations/zh_CN.yml", resourceDir.resolve("zh_CN.yml"));
        copyResource("translations/ja_JP.properties", resourceDir.resolve("ja_JP.properties"));
        copyResource("translations/de_DE.yml", resourceDir.resolve("de_DE.yml"));
    }

    private static void copyResource(String resourcePath, Path target) throws IOException {
        try (InputStream is = TranslationResourceTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @BeforeEach
    void setUp() {
        manager = TranslationManager.create(
                Key.key("test", "resources"),
                resourceDir,
                Locale.ENGLISH
        );
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    @Nested
    @DisplayName("多语言加载测试")
    class MultiLanguageLoadTests {

        @Test
        @DisplayName("应加载所有语言文件")
        void shouldLoadAllLanguages() {
            manager.load(List.of("en_US", "zh_CN", "ja_JP", "de_DE"));

            assertNotNull(manager.getRawTranslation("greeting", Locale.of("en", "US")));
            assertNotNull(manager.getRawTranslation("greeting", Locale.of("zh", "CN")));
            assertNotNull(manager.getRawTranslation("greeting", Locale.of("ja", "JP")));
            assertNotNull(manager.getRawTranslation("greeting", Locale.of("de", "DE")));
        }

        @Test
        @DisplayName("应正确返回各语言翻译")
        void shouldReturnCorrectTranslations() {
            manager.load(List.of("en_US", "zh_CN", "ja_JP", "de_DE"));

            assertEquals("Hello World", manager.getRawTranslation("greeting", Locale.of("en", "US")));
            assertEquals("你好世界", manager.getRawTranslation("greeting", Locale.of("zh", "CN")));
            assertEquals("こんにちは世界", manager.getRawTranslation("greeting", Locale.of("ja", "JP")));
            assertEquals("Hallo Welt", manager.getRawTranslation("greeting", Locale.of("de", "DE")));
        }
    }

    @Nested
    @DisplayName("嵌套键测试")
    class NestedKeyTests {

        @BeforeEach
        void loadTranslations() {
            manager.load(List.of("en_US", "zh_CN"));
        }

        @Test
        @DisplayName("应解析嵌套的消息键")
        void shouldResolveNestedKeys() {
            assertEquals("Item not found", manager.getRawTranslation("messages.error.not_found"));
            assertEquals("物品未找到", manager.getRawTranslation("messages.error.not_found", Locale.of("zh", "CN")));
        }

        @Test
        @DisplayName("应解析深层嵌套键")
        void shouldResolveDeeplyNestedKeys() {
            assertEquals("This is deeply nested", manager.getRawTranslation("nested.level1.level2.level3.deep_value"));
            assertEquals("这是深层嵌套", manager.getRawTranslation("nested.level1.level2.level3.deep_value", Locale.of("zh", "CN")));
        }
    }

    @Nested
    @DisplayName("MiniMessage 格式化测试")
    class MiniMessageFormattingTests {

        @BeforeEach
        void loadTranslations() {
            manager.load(List.of("en_US"));
        }

        @Test
        @DisplayName("应解析颜色标签")
        void shouldParseColorTags() {
            Component result = manager.translate("formatted.red_text");
            String plainText = MiniMessages.toPlainText(result);
            assertEquals("This is red", plainText);
        }

        @Test
        @DisplayName("应解析样式标签")
        void shouldParseStyleTags() {
            Component result = manager.translate("formatted.bold_text");
            String plainText = MiniMessages.toPlainText(result);
            assertEquals("This is bold", plainText);
        }

        @Test
        @DisplayName("应解析组合标签")
        void shouldParseCombinedTags() {
            Component result = manager.translate("formatted.combined");
            String plainText = MiniMessages.toPlainText(result);
            assertEquals("Bold Red", plainText);
        }
    }

    @Nested
    @DisplayName("占位符测试")
    class PlaceholderTests {

        @BeforeEach
        void loadTranslations() {
            manager.load(List.of("en_US", "zh_CN"));
        }

        @Test
        @DisplayName("应替换单个占位符")
        void shouldReplaceSinglePlaceholder() {
            Component result = manager.translate("placeholders.single",
                    Placeholder.parsed("name", "Steve"));
            assertEquals("Hello Steve!", MiniMessages.toPlainText(result));
        }

        @Test
        @DisplayName("应替换多个占位符")
        void shouldReplaceMultiplePlaceholders() {
            Component result = manager.translate("placeholders.multiple",
                    Placeholder.parsed("player", "Steve"),
                    Placeholder.parsed("amount", "10"),
                    Placeholder.parsed("receiver", "Alex"));
            assertEquals("Steve gave 10 to Alex", MiniMessages.toPlainText(result));
        }

        @Test
        @DisplayName("中文应正确替换占位符")
        void chineseShouldReplacePlaceholders() {
            Component result = manager.translate("placeholders.single", Locale.of("zh", "CN"),
                    Placeholder.parsed("name", "史蒂夫"));
            assertEquals("你好 史蒂夫!", MiniMessages.toPlainText(result));
        }
    }

    @Nested
    @DisplayName("特殊字符测试")
    class SpecialCharacterTests {

        @BeforeEach
        void loadTranslations() {
            manager.load(List.of("en_US", "zh_CN"));
        }

        @Test
        @DisplayName("应处理空字符串")
        void shouldHandleEmptyString() {
            assertEquals("", manager.getRawTranslation("special.empty"));
        }

        @Test
        @DisplayName("应处理 Unicode 字符")
        void shouldHandleUnicode() {
            assertEquals("🎉 Celebration! 🎊", manager.getRawTranslation("special.unicode"));
            assertEquals("🎉 庆祝! 🎊", manager.getRawTranslation("special.unicode", Locale.of("zh", "CN")));
        }

        @Test
        @DisplayName("应处理引号")
        void shouldHandleQuotes() {
            String raw = manager.getRawTranslation("special.quotes");
            assertTrue(raw.contains("Hello") || raw.contains("你好"));
        }
    }

    @Nested
    @DisplayName("Properties 文件测试")
    class PropertiesFileTests {

        @BeforeEach
        void loadTranslations() {
            manager.load(List.of("ja_JP"));
        }

        @Test
        @DisplayName("应从 Properties 文件加载翻译")
        void shouldLoadFromPropertiesFile() {
            assertEquals("こんにちは世界", manager.getRawTranslation("greeting", Locale.of("ja", "JP")));
            assertEquals("さようなら", manager.getRawTranslation("farewell", Locale.of("ja", "JP")));
        }

        @Test
        @DisplayName("Properties 文件应支持点号分隔的键")
        void propertiesShouldSupportDottedKeys() {
            assertEquals("アイテムが見つかりません",
                    manager.getRawTranslation("messages.error.not_found", Locale.of("ja", "JP")));
        }

        @Test
        @DisplayName("Properties 文件应支持占位符")
        void propertiesShouldSupportPlaceholders() {
            Component result = manager.translate("placeholders.single", Locale.of("ja", "JP"),
                    Placeholder.parsed("name", "太郎"));
            assertEquals("こんにちは 太郎!", MiniMessages.toPlainText(result));
        }
    }

    @Nested
    @DisplayName("语言回退测试")
    class LanguageFallbackTests {

        @BeforeEach
        void loadTranslations() {
            manager.load(List.of("en_US", "de_DE"));
        }

        @Test
        @DisplayName("缺失的键应回退到默认语言")
        void missingKeyShouldFallbackToDefault() {
            Component result = manager.translate("messages.success.deleted", Locale.of("de", "DE"));
            assertEquals("Successfully deleted", MiniMessages.toPlainText(result));
        }

        @Test
        @DisplayName("存在的键应使用目标语言")
        void existingKeyShouldUseTargetLanguage() {
            assertEquals("Erfolgreich erstellt",
                    manager.getRawTranslation("messages.success.created", Locale.of("de", "DE")));
        }
    }

    @Nested
    @DisplayName("component 方法测试")
    class ComponentMethodTests {

        @BeforeEach
        void loadTranslations() {
            manager.load(List.of("en_US"));
        }

        @Test
        @DisplayName("component 应返回格式化组件")
        void componentShouldReturnFormattedComponent() {
            Component result = manager.component("formatted.red_text");
            assertNotNull(result);
            assertEquals("This is red", MiniMessages.toPlainText(result));
        }

        @Test
        @DisplayName("component 对不存在的键应返回键文本")
        void componentShouldReturnKeyForMissing() {
            Component result = manager.component("nonexistent.key.here");
            assertEquals("nonexistent.key.here", MiniMessages.toPlainText(result));
        }
    }
}

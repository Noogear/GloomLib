package gloomlib.test;

import gloomlib.translation.api.TranslationManager;
import gloomlib.translation.config.FileSourceOptions;
import gloomlib.translation.util.MiniMessages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranslationManager 集成测试")
class TranslationManagerTest {

    @TempDir
    Path tempDir;

    private TranslationManager manager;

    @BeforeEach
    void setUp() {
        manager = TranslationManager.create(
                Key.key("test", "translations"),
                tempDir,
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
    @DisplayName("静态方法测试")
    class StaticMethodTests {

        @Test
        @DisplayName("parseLocale 应解析有效的 locale 字符串")
        void parseLocaleShouldWork() {
            assertEquals(Locale.of("en", "US"), TranslationManager.parseLocale("en_US"));
            assertEquals(Locale.of("zh", "CN"), TranslationManager.parseLocale("zh_CN"));
            assertEquals(Locale.of("en"), TranslationManager.parseLocale("en"));
        }

        @Test
        @DisplayName("parseLocale 应处理空值")
        void parseLocaleShouldHandleNull() {
            assertNull(TranslationManager.parseLocale(null));
            assertNull(TranslationManager.parseLocale(""));
        }

        @Test
        @DisplayName("formatLocale 应格式化 locale")
        void formatLocaleShouldWork() {
            assertEquals("en_us", TranslationManager.formatLocale(Locale.of("en", "US")));
            assertEquals("zh_cn", TranslationManager.formatLocale(Locale.of("zh", "CN")));
            assertEquals("en", TranslationManager.formatLocale(Locale.of("en")));
        }

        @Test
        @DisplayName("instance 应返回最新创建的实例")
        void instanceShouldReturnLatest() {
            assertSame(manager, TranslationManager.instance());
        }
    }

    @Nested
    @DisplayName("加载测试")
    class LoadTests {

        @Test
        @DisplayName("应加载 YAML 翻译文件")
        void shouldLoadYamlFile() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), """
                greeting: Hello World
                farewell: Goodbye
                """);

            manager.load(List.of("en_US"));

            assertEquals("Hello World", manager.getRawTranslation("greeting"));
            assertEquals("Goodbye", manager.getRawTranslation("farewell"));
        }

        @Test
        @DisplayName("应加载 Properties 翻译文件")
        void shouldLoadPropertiesFile() throws Exception {
            Files.writeString(tempDir.resolve("en_US.properties"), """
                greeting=Hello World
                farewell=Goodbye
                """);

            manager.load(List.of("en_US"));

            assertEquals("Hello World", manager.getRawTranslation("greeting"));
            assertEquals("Goodbye", manager.getRawTranslation("farewell"));
        }

        @Test
        @DisplayName("应优先使用 YAML 优于 Properties")
        void shouldPreferYamlOverProperties() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "source: yaml");
            Files.writeString(tempDir.resolve("en_US.properties"), "source=properties");

            manager.load(List.of("en_US"));

            assertEquals("yaml", manager.getRawTranslation("source"));
        }

        @Test
        @DisplayName("应加载多个语言")
        void shouldLoadMultipleLanguages() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "greeting: Hello");
            Files.writeString(tempDir.resolve("zh_CN.yml"), "greeting: 你好");

            manager.load(List.of("en_US", "zh_CN"));

            assertEquals("Hello", manager.getRawTranslation("greeting", Locale.of("en", "US")));
            assertEquals("你好", manager.getRawTranslation("greeting", Locale.of("zh", "CN")));
        }

        @Test
        @DisplayName("应忽略无效的 locale 代码")
        void shouldIgnoreInvalidLocaleCodes() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "key: value");

            manager.load(List.of("en_US", "invalid!!!"));

            assertEquals("value", manager.getRawTranslation("key"));
        }

        @Test
        @DisplayName("应创建不存在的翻译目录")
        void shouldCreateTranslationDirectory() throws Exception {
            Path subDir = tempDir.resolve("translations");
            TranslationManager subManager = TranslationManager.create(
                    Key.key("test", "sub"),
                    subDir,
                    Locale.ENGLISH
            );

            subManager.load(List.of("en_US"));
            assertTrue(Files.exists(subDir));

            subManager.close();
        }
    }

    @Nested
    @DisplayName("翻译测试")
    class TranslateTests {

        @BeforeEach
        void loadTranslations() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), """
                simple: Simple text
                formatted: <red>Red text</red>
                with.placeholder: Hello <name>!
                nested:
                  key: Nested value
                """);
            manager.load(List.of("en_US"));
        }

        @Test
        @DisplayName("translate 应返回翻译后的组件")
        void translateShouldWork() {
            Component result = manager.translate("simple");
            assertEquals("Simple text", MiniMessages.toPlainText(result));
        }

        @Test
        @DisplayName("translate 应解析 MiniMessage 格式")
        void translateShouldParseMiniMessage() {
            Component result = manager.translate("formatted");
            assertEquals("Red text", MiniMessages.toPlainText(result));
        }

        @Test
        @DisplayName("translate 应支持占位符")
        void translateShouldSupportPlaceholders() {
            Component result = manager.translate("with.placeholder",
                    Placeholder.parsed("name", "World"));
            assertEquals("Hello World!", MiniMessages.toPlainText(result));
        }

        @Test
        @DisplayName("translate 应处理嵌套键")
        void translateShouldHandleNestedKeys() {
            Component result = manager.translate("nested.key");
            assertEquals("Nested value", MiniMessages.toPlainText(result));
        }

        @Test
        @DisplayName("不存在的键应返回键本身")
        void nonExistentKeyShouldReturnKey() {
            Component result = manager.translate("nonexistent.key");
            assertEquals("nonexistent.key", MiniMessages.toPlainText(result));
        }
    }

    @Nested
    @DisplayName("getRawTranslation 测试")
    class GetRawTranslationTests {

        @BeforeEach
        void loadTranslations() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), """
                test: Test value
                formatted: <red>Red</red>
                """);
            manager.load(List.of("en_US"));
        }

        @Test
        @DisplayName("应返回原始 MiniMessage 字符串")
        void shouldReturnRawString() {
            assertEquals("Test value", manager.getRawTranslation("test"));
            assertEquals("<red>Red</red>", manager.getRawTranslation("formatted"));
        }

        @Test
        @DisplayName("不存在的键应返回 null")
        void nonExistentKeyShouldReturnNull() {
            assertNull(manager.getRawTranslation("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Locale 回退测试")
    class LocaleFallbackTests {

        @BeforeEach
        void loadTranslations() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), """
                english.only: English only
                both: English version
                """);
            Files.writeString(tempDir.resolve("zh_CN.yml"), """
                both: 中文版本
                chinese.only: 仅中文
                """);
            manager.load(List.of("en_US", "zh_CN"));
        }

        @Test
        @DisplayName("应使用请求的 locale")
        void shouldUseRequestedLocale() {
            assertEquals("English version",
                    manager.getRawTranslation("both", Locale.of("en", "US")));
            assertEquals("中文版本",
                    manager.getRawTranslation("both", Locale.of("zh", "CN")));
        }

        @Test
        @DisplayName("应回退到默认 locale")
        void shouldFallbackToDefaultLocale() {
            Component result = manager.translate("english.only", Locale.of("zh", "CN"));
            assertEquals("English only", MiniMessages.toPlainText(result));
        }
    }

    @Nested
    @DisplayName("重新加载测试")
    class ReloadTests {

        @Test
        @DisplayName("reload 应清除文件缓存并重新读取")
        void reloadShouldClearFileCacheAndReread() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "existing: value");
            manager.load(List.of("en_US"));
            assertEquals("value", manager.getRawTranslation("existing"));

            manager.reload();

            assertEquals("value", manager.getRawTranslation("existing"));
        }

        @Test
        @DisplayName("reload 在无已加载语言时应无操作")
        void reloadWithNoLanguagesShouldBeNoop() {
            manager.reload();
        }
    }

    @Nested
    @DisplayName("GlobalTranslator 注册测试")
    class GlobalTranslatorTests {

        @Test
        @DisplayName("registerTranslations 应注册到全局翻译器")
        void registerTranslationsShouldWork() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "test: value");
            manager.load(List.of("en_US"));

            assertFalse(manager.isRegistered());
            manager.registerTranslations();
            assertTrue(manager.isRegistered());
        }

        @Test
        @DisplayName("unregisterTranslations 应从全局翻译器取消注册")
        void unregisterTranslationsShouldWork() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "test: value");
            manager.load(List.of("en_US"));

            manager.registerTranslations();
            assertTrue(manager.isRegistered());

            manager.unregisterTranslations();
            assertFalse(manager.isRegistered());
        }

        @Test
        @DisplayName("重复注册应无害")
        void doubleRegisterShouldBeHarmless() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "test: value");
            manager.load(List.of("en_US"));

            manager.registerTranslations();
            manager.registerTranslations();
            assertTrue(manager.isRegistered());
        }

        @Test
        @DisplayName("重复取消注册应无害")
        void doubleUnregisterShouldBeHarmless() {
            manager.unregisterTranslations();
            manager.unregisterTranslations();
            assertFalse(manager.isRegistered());
        }
    }

    @Nested
    @DisplayName("关闭测试")
    class CloseTests {

        @Test
        @DisplayName("close 应清理资源")
        void closeShouldCleanup() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "test: value");
            manager.load(List.of("en_US"));
            manager.registerTranslations();

            manager.close();

            assertFalse(manager.isRegistered());
        }

        @Test
        @DisplayName("close 应清除单例引用")
        void closeShouldClearInstance() throws Exception {
            TranslationManager current = TranslationManager.instance();
            assertSame(manager, current);

            manager.close();

            assertNull(TranslationManager.instance());
            manager = null;
        }
    }

    @Nested
    @DisplayName("FileSourceOptions 测试")
    class FileSourceOptionsTests {

        @Test
        @DisplayName("应使用自定义选项加载")
        void shouldLoadWithCustomOptions() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "key: value");

            FileSourceOptions options = FileSourceOptions.builder()
                    .verbose(true)
                    .build();

            manager.load(List.of("en_US"), options);

            assertEquals("value", manager.getRawTranslation("key"));
        }
    }

    @Nested
    @DisplayName("component 方法测试")
    class ComponentMethodTests {

        @BeforeEach
        void loadTranslations() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), """
                exists: Existing value
                with.placeholder: Hello <name>!
                """);
            manager.load(List.of("en_US"));
        }

        @Test
        @DisplayName("component 应返回翻译后的组件")
        void componentShouldWork() {
            Component result = manager.component("exists");
            assertEquals("Existing value", MiniMessages.toPlainText(result));
        }

        @Test
        @DisplayName("不存在的键应返回键文本")
        void nonExistentKeyShouldReturnKeyText() {
            Component result = manager.component("nonexistent.key");
            assertEquals("nonexistent.key", MiniMessages.toPlainText(result));
        }
    }

    @Nested
    @DisplayName("getDefaultLocale 测试")
    class GetDefaultLocaleTests {

        @Test
        @DisplayName("应返回构造时设置的默认 locale")
        void shouldReturnDefaultLocale() {
            assertEquals(Locale.ENGLISH, manager.getDefaultLocale());
        }
    }

    @Nested
    @DisplayName("getRegistry 测试")
    class GetRegistryTests {

        @Test
        @DisplayName("应返回内部注册表")
        void shouldReturnRegistry() {
            assertNotNull(manager.getRegistry());
        }
    }

    @Nested
    @DisplayName("文件缓存测试")
    class FileCacheTests {

        @Test
        @DisplayName("相同修改时间不应重新加载")
        void sameModificationTimeShouldNotReload() throws Exception {
            Files.writeString(tempDir.resolve("en_US.yml"), "key: value");
            manager.load(List.of("en_US"));

            manager.load(List.of("en_US"));

            assertEquals("value", manager.getRawTranslation("key"));
        }
    }
}

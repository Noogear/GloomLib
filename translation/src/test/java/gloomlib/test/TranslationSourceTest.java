package gloomlib.test;

import gloomlib.translation.source.PropertiesTranslationSource;
import gloomlib.translation.source.YamlTranslationSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranslationSource 实现测试")
class TranslationSourceTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("YamlTranslationSource 测试")
    class YamlSourceTests {

        @Test
        @DisplayName("应加载简单 YAML 文件")
        void shouldLoadSimpleYaml() throws Exception {
            Path yamlFile = tempDir.resolve("test.yml");
            Files.writeString(yamlFile, """
                greeting: Hello World
                farewell: Goodbye
                """);

            YamlTranslationSource source = new YamlTranslationSource(yamlFile, Locale.ENGLISH);
            source.load();

            assertEquals(Locale.ENGLISH, source.getLocale());
            assertEquals(2, source.getKeys().size());
            assertEquals("Hello World", source.getRaw("greeting"));
            assertEquals("Goodbye", source.getRaw("farewell"));
        }

        @Test
        @DisplayName("应展平嵌套 YAML 结构")
        void shouldFlattenNestedYaml() throws Exception {
            Path yamlFile = tempDir.resolve("nested.yml");
            Files.writeString(yamlFile, """
                messages:
                  error:
                    not_found: Item not found
                    permission: No permission
                  success:
                    created: Created successfully
                """);

            YamlTranslationSource source = new YamlTranslationSource(yamlFile, Locale.ENGLISH);
            source.load();

            assertEquals("Item not found", source.getRaw("messages.error.not_found"));
            assertEquals("No permission", source.getRaw("messages.error.permission"));
            assertEquals("Created successfully", source.getRaw("messages.success.created"));
        }

        @Test
        @DisplayName("应处理多层嵌套")
        void shouldHandleDeeplyNested() throws Exception {
            Path yamlFile = tempDir.resolve("deep.yml");
            Files.writeString(yamlFile, """
                level1:
                  level2:
                    level3:
                      level4:
                        value: Deep value
                """);

            YamlTranslationSource source = new YamlTranslationSource(yamlFile, Locale.ENGLISH);
            source.load();

            assertEquals("Deep value", source.getRaw("level1.level2.level3.level4.value"));
        }

        @Test
        @DisplayName("应处理空文件")
        void shouldHandleEmptyFile() throws Exception {
            Path yamlFile = tempDir.resolve("empty.yml");
            Files.writeString(yamlFile, "");

            YamlTranslationSource source = new YamlTranslationSource(yamlFile, Locale.ENGLISH);
            source.load();

            assertTrue(source.getKeys().isEmpty());
        }

        @Test
        @DisplayName("应返回键本身当键不存在")
        void shouldReturnKeyWhenNotFound() throws Exception {
            Path yamlFile = tempDir.resolve("test.yml");
            Files.writeString(yamlFile, "key: value");

            YamlTranslationSource source = new YamlTranslationSource(yamlFile, Locale.ENGLISH);
            source.load();

            assertEquals("nonexistent.key", source.getRaw("nonexistent.key"));
        }

        @Test
        @DisplayName("reload 应清除旧数据")
        void reloadShouldClearOldData() throws Exception {
            Path yamlFile = tempDir.resolve("reload.yml");
            Files.writeString(yamlFile, "old_key: old_value");

            YamlTranslationSource source = new YamlTranslationSource(yamlFile, Locale.ENGLISH);
            source.load();
            assertEquals("old_value", source.getRaw("old_key"));

            Files.writeString(yamlFile, "new_key: new_value");
            source.load();

            assertFalse(source.getKeys().contains("old_key"));
            assertTrue(source.getKeys().contains("new_key"));
            assertEquals("new_value", source.getRaw("new_key"));
        }

        @Test
        @DisplayName("getKeys() 应返回不可变视图")
        void getKeysShouldReturnUnmodifiableView() throws Exception {
            Path yamlFile = tempDir.resolve("test.yml");
            Files.writeString(yamlFile, "key: value");

            YamlTranslationSource source = new YamlTranslationSource(yamlFile, Locale.ENGLISH);
            source.load();

            Set<String> keys = source.getKeys();
            assertThrows(UnsupportedOperationException.class, () -> keys.add("new"));
        }

        @Test
        @DisplayName("应处理 Unicode 内容")
        void shouldHandleUnicode() throws Exception {
            Path yamlFile = tempDir.resolve("unicode.yml");
            Files.writeString(yamlFile, """
                chinese: 你好世界
                japanese: こんにちは
                emoji: 🎉🎊
                """);

            YamlTranslationSource source = new YamlTranslationSource(yamlFile, Locale.CHINESE);
            source.load();

            assertEquals("你好世界", source.getRaw("chinese"));
            assertEquals("こんにちは", source.getRaw("japanese"));
            assertEquals("🎉🎊", source.getRaw("emoji"));
        }
    }

    @Nested
    @DisplayName("PropertiesTranslationSource 测试")
    class PropertiesSourceTests {

        @Test
        @DisplayName("应加载 properties 文件")
        void shouldLoadProperties() throws Exception {
            Path propsFile = tempDir.resolve("test.properties");
            Files.writeString(propsFile, """
                greeting=Hello World
                farewell=Goodbye
                """);

            PropertiesTranslationSource source = new PropertiesTranslationSource(propsFile, Locale.ENGLISH);
            source.load();

            assertEquals(Locale.ENGLISH, source.getLocale());
            assertEquals(2, source.getKeys().size());
            assertEquals("Hello World", source.getRaw("greeting"));
            assertEquals("Goodbye", source.getRaw("farewell"));
        }

        @Test
        @DisplayName("应处理点号分隔的键")
        void shouldHandleDottedKeys() throws Exception {
            Path propsFile = tempDir.resolve("dotted.properties");
            Files.writeString(propsFile, """
                messages.error.not_found=Item not found
                messages.success.created=Created successfully
                """);

            PropertiesTranslationSource source = new PropertiesTranslationSource(propsFile, Locale.ENGLISH);
            source.load();

            assertEquals("Item not found", source.getRaw("messages.error.not_found"));
            assertEquals("Created successfully", source.getRaw("messages.success.created"));
        }

        @Test
        @DisplayName("应处理空文件")
        void shouldHandleEmptyFile() throws Exception {
            Path propsFile = tempDir.resolve("empty.properties");
            Files.writeString(propsFile, "");

            PropertiesTranslationSource source = new PropertiesTranslationSource(propsFile, Locale.ENGLISH);
            source.load();

            assertTrue(source.getKeys().isEmpty());
        }

        @Test
        @DisplayName("应返回键本身当键不存在")
        void shouldReturnKeyWhenNotFound() throws Exception {
            Path propsFile = tempDir.resolve("test.properties");
            Files.writeString(propsFile, "key=value");

            PropertiesTranslationSource source = new PropertiesTranslationSource(propsFile, Locale.ENGLISH);
            source.load();

            assertEquals("nonexistent.key", source.getRaw("nonexistent.key"));
        }

        @Test
        @DisplayName("getKeys() 应返回不可变视图")
        void getKeysShouldReturnUnmodifiableView() throws Exception {
            Path propsFile = tempDir.resolve("test.properties");
            Files.writeString(propsFile, "key=value");

            PropertiesTranslationSource source = new PropertiesTranslationSource(propsFile, Locale.ENGLISH);
            source.load();

            Set<String> keys = source.getKeys();
            assertThrows(UnsupportedOperationException.class, () -> keys.add("new"));
        }

        @Test
        @DisplayName("应处理 UTF-8 内容")
        void shouldHandleUtf8() throws Exception {
            Path propsFile = tempDir.resolve("unicode.properties");
            Files.writeString(propsFile, """
                chinese=你好世界
                japanese=こんにちは
                """);

            PropertiesTranslationSource source = new PropertiesTranslationSource(propsFile, Locale.CHINESE);
            source.load();

            assertEquals("你好世界", source.getRaw("chinese"));
            assertEquals("こんにちは", source.getRaw("japanese"));
        }
    }

    @Nested
    @DisplayName("TranslationSource 通用行为测试")
    class CommonBehaviorTests {

        @Test
        @DisplayName("两种 source 的 getTranslations() 应返回空 Map")
        void getTranslationsShouldReturnEmptyMap() throws Exception {
            Path yamlFile = tempDir.resolve("test.yml");
            Files.writeString(yamlFile, "key: value");
            Path propsFile = tempDir.resolve("test.properties");
            Files.writeString(propsFile, "key=value");

            YamlTranslationSource yamlSource = new YamlTranslationSource(yamlFile, Locale.ENGLISH);
            yamlSource.load();

            PropertiesTranslationSource propsSource = new PropertiesTranslationSource(propsFile, Locale.ENGLISH);
            propsSource.load();

            assertTrue(yamlSource.getTranslations().isEmpty());
            assertTrue(propsSource.getTranslations().isEmpty());
        }
    }
}

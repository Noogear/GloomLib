package gloomlib.test;

import gloomlib.translation.impl.MapTranslationSource;
import gloomlib.translation.loader.TranslationParsers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranslationSource Tests")
class TranslationSourceTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("YAML Parsing Tests")
    class YamlParsingTests {

        @Test
        @DisplayName("Should parse simple YAML")
        void shouldParseSimpleYaml() throws Exception {
            Path yamlFile = tempDir.resolve("test.yml");
            Files.writeString(yamlFile, """
                    greeting: Hello World
                    farewell: Goodbye
                    """);

            Map<String, String> map = TranslationParsers.parseYaml(yamlFile, java.nio.charset.StandardCharsets.UTF_8);

            assertEquals(2, map.size());
            assertEquals("Hello World", map.get("greeting"));
            assertEquals("Goodbye", map.get("farewell"));
        }

        @Test
        @DisplayName("Should flatten nested YAML")
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

            Map<String, String> map = TranslationParsers.parseYaml(yamlFile, java.nio.charset.StandardCharsets.UTF_8);

            assertEquals("Item not found", map.get("messages.error.not_found"));
            assertEquals("No permission", map.get("messages.error.permission"));
            assertEquals("Created successfully", map.get("messages.success.created"));
        }

        @Test
        @DisplayName("Should handle deeply nested")
        void shouldHandleDeeplyNested() throws Exception {
            Path yamlFile = tempDir.resolve("deep.yml");
            Files.writeString(yamlFile, """
                    level1:
                      level2:
                        level3:
                          level4:
                            value: Deep value
                    """);

            Map<String, String> map = TranslationParsers.parseYaml(yamlFile, java.nio.charset.StandardCharsets.UTF_8);

            assertEquals("Deep value", map.get("level1.level2.level3.level4.value"));
        }

        @Test
        @DisplayName("Should handle empty file")
        void shouldHandleEmptyFile() throws Exception {
            Path yamlFile = tempDir.resolve("empty.yml");
            Files.writeString(yamlFile, "");

            Map<String, String> map = TranslationParsers.parseYaml(yamlFile, java.nio.charset.StandardCharsets.UTF_8);

            assertTrue(map.isEmpty());
        }

        @Test
        @DisplayName("Should handle Unicode")
        void shouldHandleUnicode() throws Exception {
            Path yamlFile = tempDir.resolve("unicode.yml");
            Files.writeString(yamlFile, """
                    chinese: 你好世界
                    japanese: こんにちは
                    emoji: 🎉🎊
                    """);

            Map<String, String> map = TranslationParsers.parseYaml(yamlFile, java.nio.charset.StandardCharsets.UTF_8);

            assertEquals("你好世界", map.get("chinese"));
            assertEquals("こんにちは", map.get("japanese"));
            assertEquals("🎉🎊", map.get("emoji"));
        }
    }

    @Nested
    @DisplayName("Properties Parsing Tests")
    class PropertiesParsingTests {

        @Test
        @DisplayName("Should parse properties file")
        void shouldParseProperties() throws Exception {
            Path propsFile = tempDir.resolve("test.properties");
            Files.writeString(propsFile, """
                    greeting=Hello World
                    farewell=Goodbye
                    """);

            Map<String, String> map = TranslationParsers.parseProperties(propsFile, java.nio.charset.StandardCharsets.UTF_8);

            assertEquals(2, map.size());
            assertEquals("Hello World", map.get("greeting"));
            assertEquals("Goodbye", map.get("farewell"));
        }

        @Test
        @DisplayName("Should handle dotted keys")
        void shouldHandleDottedKeys() throws Exception {
            Path propsFile = tempDir.resolve("dotted.properties");
            Files.writeString(propsFile, """
                    messages.error.not_found=Item not found
                    messages.success.created=Created successfully
                    """);

            Map<String, String> map = TranslationParsers.parseProperties(propsFile, java.nio.charset.StandardCharsets.UTF_8);

            assertEquals("Item not found", map.get("messages.error.not_found"));
            assertEquals("Created successfully", map.get("messages.success.created"));
        }

        @Test
        @DisplayName("Should handle empty file")
        void shouldHandleEmptyFile() throws Exception {
            Path propsFile = tempDir.resolve("empty.properties");
            Files.writeString(propsFile, "");

            Map<String, String> map = TranslationParsers.parseProperties(propsFile, java.nio.charset.StandardCharsets.UTF_8);

            assertTrue(map.isEmpty());
        }

        @Test
        @DisplayName("Should handle UTF-8")
        void shouldHandleUtf8() throws Exception {
            Path propsFile = tempDir.resolve("unicode.properties");
            Files.writeString(propsFile, """
                    chinese=你好世界
                    japanese=こんにちは
                    """);

            Map<String, String> map = TranslationParsers.parseProperties(propsFile, java.nio.charset.StandardCharsets.UTF_8);

            assertEquals("你好世界", map.get("chinese"));
            assertEquals("こんにちは", map.get("japanese"));
        }
    }

    @Nested
    @DisplayName("MapTranslationSource Tests")
    class MapTranslationSourceTests {

        @Test
        @DisplayName("Should return locale")
        void shouldReturnLocale() {
            MapTranslationSource source = new MapTranslationSource(Locale.ENGLISH, Map.of("key", "value"));
            assertEquals(Locale.ENGLISH, source.getLocale());
        }

        @Test
        @DisplayName("Should return keys")
        void shouldReturnKeys() {
            MapTranslationSource source = new MapTranslationSource(Locale.ENGLISH, Map.of("k1", "v1", "k2", "v2"));
            Set<String> keys = source.getKeys();
            assertEquals(2, keys.size());
            assertTrue(keys.contains("k1"));
            assertTrue(keys.contains("k2"));
        }

        @Test
        @DisplayName("Should return key when not found")
        void shouldReturnKeyWhenNotFound() {
            MapTranslationSource source = new MapTranslationSource(Locale.ENGLISH, Map.of("key", "value"));
            assertEquals("nonexistent.key", source.getRaw("nonexistent.key"));
        }

        @Test
        @DisplayName("getTranslations() should return empty map")
        void getTranslationsShouldReturnEmptyMap() {
            MapTranslationSource source = new MapTranslationSource(Locale.ENGLISH, Map.of("key", "value"));
            assertTrue(source.getTranslations().isEmpty());
        }
    }
}

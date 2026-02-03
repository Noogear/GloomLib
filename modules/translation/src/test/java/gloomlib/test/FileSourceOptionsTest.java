package gloomlib.test;

import gloomlib.translation.config.FileSourceOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileSourceOptions 测试")
class FileSourceOptionsTest {

    @Nested
    @DisplayName("默认值测试")
    class DefaultValuesTests {

        @Test
        @DisplayName("defaults() 应返回默认配置")
        void defaultsShouldReturnDefaults() {
            FileSourceOptions defaults = FileSourceOptions.defaults();

            assertNotNull(defaults);
            assertFalse(defaults.verbose());
        }

        @Test
        @DisplayName("多次调用 defaults() 应返回相等的配置")
        void defaultsShouldReturnEqualInstances() {
            FileSourceOptions first = FileSourceOptions.defaults();
            FileSourceOptions second = FileSourceOptions.defaults();

            assertEquals(first.verbose(), second.verbose());
            assertEquals(first.createIfMissing(), second.createIfMissing());
        }
    }

    @Nested
    @DisplayName("Builder 测试")
    class BuilderTests {

        @Test
        @DisplayName("builder 应创建自定义配置")
        void builderShouldCreateCustomOptions() {
            FileSourceOptions options = FileSourceOptions.builder()
                    .verbose(true)
                    .build();

            assertTrue(options.verbose());
        }

        @Test
        @DisplayName("builder 默认值应与 defaults() 一致")
        void builderDefaultsShouldMatchDefaults() {
            FileSourceOptions fromBuilder = FileSourceOptions.builder().build();
            FileSourceOptions defaults = FileSourceOptions.defaults();

            assertEquals(defaults.verbose(), fromBuilder.verbose());
        }
    }

    @Nested
    @DisplayName("Verbose 选项测试")
    class VerboseTests {

        @Test
        @DisplayName("verbose(true) 应启用详细日志")
        void verboseTrueShouldEnable() {
            FileSourceOptions options = FileSourceOptions.builder()
                    .verbose(true)
                    .build();

            assertTrue(options.verbose());
        }

        @Test
        @DisplayName("verbose(false) 应禁用详细日志")
        void verboseFalseShouldDisable() {
            FileSourceOptions options = FileSourceOptions.builder()
                    .verbose(false)
                    .build();

            assertFalse(options.verbose());
        }
    }
}

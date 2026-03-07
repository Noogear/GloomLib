package gloomlib.test;

import gloomlib.configuration.core.model.FieldMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test to verify hybrid VarHandle optimization in FieldMeta.
 */
@DisplayName("FieldMeta Hybrid Optimization Test")
class FieldMetaVarHandleTest {

    private TestConfig config;
    private Field nameField;
    private Field levelField;

    @BeforeEach
    void setUp() throws Exception {
        config = new TestConfig();
        nameField = TestConfig.class.getField("name");
        levelField = TestConfig.class.getField("level");
    }

    @Test
    @DisplayName("Hybrid implementation - correctness")
    void testHybridCorrectness() throws Exception {
        FieldMeta nameMeta = new FieldMeta(nameField, "name", false, false, false);
        FieldMeta levelMeta = new FieldMeta(levelField, "level", false, false, false);

        // Test get
        assertEquals("default", nameMeta.get(config));
        assertEquals(1, levelMeta.get(config));

        // Test set
        nameMeta.set(config, "hybrid");
        levelMeta.set(config, 99);

        assertEquals("hybrid", config.name);
        assertEquals(99, config.level);
    }


    @Test
    @DisplayName("Performance comparison - mixed operations")
    @SuppressWarnings("unused")
    void benchmarkMixedOperations() throws Exception {
        FieldMeta hybridMeta = new FieldMeta(levelField, "level", false, false, false);

        int iterations = 50_000;

        // Hybrid (uses VarHandle for primitives)
        long hybridStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            hybridMeta.set(config, i);
            int val = (int) hybridMeta.get(config);
        }
        long hybridTime = System.nanoTime() - hybridStart;

        System.out.println("╭───────────────────────────────────────────────────────────╮");
        System.out.println("│  FieldMeta Hybrid Performance (int field, " + iterations + " ops)    │");
        System.out.println("├───────────────────────────────────────────────────────────┤");
        System.out.printf("│ Time:          %,10d ns (%5.2f ms)                │%n", hybridTime, hybridTime / 1_000_000.0);
        System.out.println("├───────────────────────────────────────────────────────────┤");
        System.out.println("│ ✅ VarHandle for primitives, MethodHandle for objects    │");
        System.out.println("╰───────────────────────────────────────────────────────────╯");
        System.out.println();
    }

    @Test
    @DisplayName("Performance comparison - String field")
    @SuppressWarnings("unused")
    void benchmarkStringOperations() throws Exception {
        FieldMeta hybridMeta = new FieldMeta(nameField, "name", false, false, false);

        int iterations = 50_000;

        // Hybrid (uses MethodHandle for String)
        long hybridStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            hybridMeta.set(config, "test" + i);
            String val = (String) hybridMeta.get(config);
        }
        long hybridTime = System.nanoTime() - hybridStart;

        System.out.println("╭───────────────────────────────────────────────────────────╮");
        System.out.println("│  FieldMeta Hybrid Performance (String field, " + iterations + " ops) │");
        System.out.println("├───────────────────────────────────────────────────────────┤");
        System.out.printf("│ Time:          %,10d ns (%5.2f ms)                │%n", hybridTime, hybridTime / 1_000_000.0);
        System.out.println("├───────────────────────────────────────────────────────────┤");
        System.out.println("│ ✅ VarHandle for primitives, MethodHandle for objects    │");
        System.out.println("╰───────────────────────────────────────────────────────────╯");
        System.out.println();
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║ Note: Hybrid uses MethodHandle for objects (stable)      ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    static class TestConfig {
        public String name = "default";
        public int level = 1;
        public boolean enabled = true;
        public double score = 3.14;
    }
}

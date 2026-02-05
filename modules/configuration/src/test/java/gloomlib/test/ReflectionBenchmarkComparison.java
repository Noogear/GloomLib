package gloomlib.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Comprehensive benchmark comparing Field API, MethodHandles, and VarHandles.
 * <p>
 * This test demonstrates the performance characteristics of three reflection APIs:
 * <ul>
 *   <li>Field API (Java 1.1+) - Traditional reflection</li>
 *   <li>MethodHandles (Java 7+) - Modern optimized reflection</li>
 *   <li>VarHandles (Java 9+) - Lowest-level, highest-performance API</li>
 * </ul>
 * </p>
 */
@DisplayName("Reflection API Benchmark: Field vs MethodHandle vs VarHandle")
class ReflectionBenchmarkComparison {

    private TestObject testObj;
    // Field API
    private Field nameField;
    private Field valueField;
    // MethodHandles
    private MethodHandle nameGetter;
    private MethodHandle nameSetter;
    private MethodHandle valueGetter;
    private MethodHandle valueSetter;
    // VarHandles
    private VarHandle nameVarHandle;
    private VarHandle valueVarHandle;

    @BeforeEach
    void setUp() throws Exception {
        testObj = new TestObject();

        // Setup Field API
        nameField = TestObject.class.getField("name");
        valueField = TestObject.class.getField("value");
        nameField.setAccessible(true);
        valueField.setAccessible(true);

        // Setup MethodHandles
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        nameGetter = lookup.unreflectGetter(nameField);
        nameSetter = lookup.unreflectSetter(nameField);
        valueGetter = lookup.unreflectGetter(valueField);
        valueSetter = lookup.unreflectSetter(valueField);

        // Setup VarHandles
        nameVarHandle = lookup.unreflectVarHandle(nameField);
        valueVarHandle = lookup.unreflectVarHandle(valueField);
    }

    @Test
    @DisplayName("Correctness: All three APIs produce same results")
    void testCorrectness() throws Throwable {
        // Test String field
        nameField.set(testObj, "field");
        assertEquals("field", nameField.get(testObj));

        nameSetter.invoke(testObj, "method");
        assertEquals("method", nameGetter.invoke(testObj));

        nameVarHandle.set(testObj, "var");
        assertEquals("var", nameVarHandle.get(testObj));

        // Test int field
        valueField.set(testObj, 100);
        assertEquals(100, valueField.get(testObj));

        valueSetter.invoke(testObj, 200);
        assertEquals(200, valueGetter.invoke(testObj));

        valueVarHandle.set(testObj, 300);
        assertEquals(300, valueVarHandle.get(testObj));
    }

    @Test
    @DisplayName("GET Performance: Field vs MethodHandle vs VarHandle")
    void benchmarkGetPerformance() throws Throwable {
        int iterations = 100_000;

        // Warmup all three
        for (int i = 0; i < 1000; i++) {
            nameField.get(testObj);
            nameGetter.invoke(testObj);
            nameVarHandle.get(testObj);
        }

        // Benchmark Field API
        long fieldStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Object val = nameField.get(testObj);
        }
        long fieldTime = System.nanoTime() - fieldStart;

        // Benchmark MethodHandle
        long mhStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Object val = nameGetter.invoke(testObj);
        }
        long mhTime = System.nanoTime() - mhStart;

        // Benchmark VarHandle
        long vhStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Object val = nameVarHandle.get(testObj);
        }
        long vhTime = System.nanoTime() - vhStart;

        // Calculate speedups
        double mhSpeedup = (double) fieldTime / mhTime;
        double vhSpeedup = (double) fieldTime / vhTime;
        double vhVsMh = (double) mhTime / vhTime;

        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║       GET Performance (" + String.format("%,d", iterations) + " iterations)      ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf("║ Field API:      %,10d ns (%6.2f ms)       ║%n", fieldTime, fieldTime / 1_000_000.0);
        System.out.printf("║ MethodHandle:   %,10d ns (%6.2f ms) [%.2fx] ║%n", mhTime, mhTime / 1_000_000.0, mhSpeedup);
        System.out.printf("║ VarHandle:      %,10d ns (%6.2f ms) [%.2fx] ║%n", vhTime, vhTime / 1_000_000.0, vhSpeedup);
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf("║ VarHandle vs MethodHandle: %.2fx faster           ║%n", vhVsMh);
        System.out.println("╚═══════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("SET Performance: Field vs MethodHandle vs VarHandle")
    void benchmarkSetPerformance() throws Throwable {
        int iterations = 100_000;

        // Warmup
        for (int i = 0; i < 1000; i++) {
            valueField.set(testObj, i);
            valueSetter.invoke(testObj, i);
            valueVarHandle.set(testObj, i);
        }

        // Benchmark Field API
        long fieldStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            valueField.set(testObj, i);
        }
        long fieldTime = System.nanoTime() - fieldStart;

        // Benchmark MethodHandle
        long mhStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            valueSetter.invoke(testObj, i);
        }
        long mhTime = System.nanoTime() - mhStart;

        // Benchmark VarHandle
        long vhStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            valueVarHandle.set(testObj, i);
        }
        long vhTime = System.nanoTime() - vhStart;

        double mhSpeedup = (double) fieldTime / mhTime;
        double vhSpeedup = (double) fieldTime / vhTime;
        double vhVsMh = (double) mhTime / vhTime;

        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║       SET Performance (" + String.format("%,d", iterations) + " iterations)      ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf("║ Field API:      %,10d ns (%6.2f ms)       ║%n", fieldTime, fieldTime / 1_000_000.0);
        System.out.printf("║ MethodHandle:   %,10d ns (%6.2f ms) [%.2fx] ║%n", mhTime, mhTime / 1_000_000.0, mhSpeedup);
        System.out.printf("║ VarHandle:      %,10d ns (%6.2f ms) [%.2fx] ║%n", vhTime, vhTime / 1_000_000.0, vhSpeedup);
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf("║ VarHandle vs MethodHandle: %.2fx faster           ║%n", vhVsMh);
        System.out.println("╚═══════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("Mixed Operations: Realistic workload simulation")
    void benchmarkMixedOperations() throws Throwable {
        int iterations = 50_000;

        // Warmup
        for (int i = 0; i < 500; i++) {
            nameField.set(testObj, "test" + i);
            String s = (String) nameField.get(testObj);
            valueField.set(testObj, i);
            int v = (int) valueField.get(testObj);
        }

        // Benchmark Field API
        long fieldStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            nameField.set(testObj, "test" + i);
            String s = (String) nameField.get(testObj);
            valueField.set(testObj, i);
            int v = (int) valueField.get(testObj);
        }
        long fieldTime = System.nanoTime() - fieldStart;

        // Benchmark MethodHandle
        long mhStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            nameSetter.invoke(testObj, "test" + i);
            String s = (String) nameGetter.invoke(testObj);
            valueSetter.invoke(testObj, i);
            int v = (int) valueGetter.invoke(testObj);
        }
        long mhTime = System.nanoTime() - mhStart;

        // Benchmark VarHandle
        long vhStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            nameVarHandle.set(testObj, "test" + i);
            String s = (String) nameVarHandle.get(testObj);
            valueVarHandle.set(testObj, i);
            int v = (int) valueVarHandle.get(testObj);
        }
        long vhTime = System.nanoTime() - vhStart;

        double mhSpeedup = (double) fieldTime / mhTime;
        double vhSpeedup = (double) fieldTime / vhTime;
        double vhVsMh = (double) mhTime / vhTime;

        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║    Mixed Operations (" + String.format("%,d", iterations) + " iterations)      ║");
        System.out.println("║     (2 sets + 2 gets per iteration)               ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf("║ Field API:      %,10d ns (%6.2f ms)       ║%n", fieldTime, fieldTime / 1_000_000.0);
        System.out.printf("║ MethodHandle:   %,10d ns (%6.2f ms) [%.2fx] ║%n", mhTime, mhTime / 1_000_000.0, mhSpeedup);
        System.out.printf("║ VarHandle:      %,10d ns (%6.2f ms) [%.2fx] ║%n", vhTime, vhTime / 1_000_000.0, vhSpeedup);
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf("║ VarHandle vs MethodHandle: %.2fx faster           ║%n", vhVsMh);
        System.out.printf("║ Throughput: %,.0f ops/sec (VarHandle)             ║%n", (iterations * 4.0) / (vhTime / 1_000_000_000.0));
        System.out.println("╚═══════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("Primitive int optimization: VarHandle advantage")
    void benchmarkPrimitiveInt() throws Throwable {
        int iterations = 100_000;

        // Warmup
        for (int i = 0; i < 1000; i++) {
            valueField.set(testObj, i);
            valueSetter.invoke(testObj, i);
            valueVarHandle.set(testObj, i);
        }

        // Field API (boxing overhead)
        long fieldStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            valueField.set(testObj, i);  // Boxes int -> Integer
            int val = (int) valueField.get(testObj);  // Unboxes Integer -> int
        }
        long fieldTime = System.nanoTime() - fieldStart;

        // MethodHandle (boxing overhead)
        long mhStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            valueSetter.invoke(testObj, i);
            int val = (int) valueGetter.invoke(testObj);
        }
        long mhTime = System.nanoTime() - mhStart;

        // VarHandle (no boxing with typed accessors)
        long vhStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            valueVarHandle.set(testObj, i);  // Direct primitive access
            int val = (int) valueVarHandle.get(testObj);
        }
        long vhTime = System.nanoTime() - vhStart;

        double vhSpeedup = (double) fieldTime / vhTime;
        double vhVsMh = (double) mhTime / vhTime;

        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║  Primitive int Operations (100,000 iterations)    ║");
        System.out.println("║    Testing boxing/unboxing overhead               ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf("║ Field API:      %,10d ns (%6.2f ms)       ║%n", fieldTime, fieldTime / 1_000_000.0);
        System.out.printf("║ MethodHandle:   %,10d ns (%6.2f ms)       ║%n", mhTime, mhTime / 1_000_000.0);
        System.out.printf("║ VarHandle:      %,10d ns (%6.2f ms) [%.2fx] ║%n", vhTime, vhTime / 1_000_000.0, vhSpeedup);
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf("║ VarHandle advantage: %.2fx faster than Field     ║%n", vhSpeedup);
        System.out.printf("║                      %.2fx faster than MH        ║%n", vhVsMh);
        System.out.println("╚═══════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("Summary: Recommendations based on use case")
    void printSummary() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                 PERFORMANCE SUMMARY                       ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║                                                           ║");
        System.out.println("║ 🥇 VarHandle      - Fastest, lowest overhead             ║");
        System.out.println("║                    ✓ Best for primitives (no boxing)     ║");
        System.out.println("║                    ✓ Atomic operations support            ║");
        System.out.println("║                    ✓ Memory fence guarantees              ║");
        System.out.println("║                    ✗ Java 9+ only                         ║");
        System.out.println("║                                                           ║");
        System.out.println("║ 🥈 MethodHandle   - Good performance, better than Field  ║");
        System.out.println("║                    ✓ Better JIT optimization              ║");
        System.out.println("║                    ✓ Java 7+ compatible                   ║");
        System.out.println("║                    ✓ Type-safe method invocation          ║");
        System.out.println("║                    ~ Similar to Field in micro-benchmarks ║");
        System.out.println("║                                                           ║");
        System.out.println("║ 🥉 Field API      - Traditional, widely compatible       ║");
        System.out.println("║                    ✓ Java 1.1+ compatible                 ║");
        System.out.println("║                    ✓ Simple, well-understood              ║");
        System.out.println("║                    ✗ Boxing overhead for primitives       ║");
        System.out.println("║                    ✗ Less JIT-friendly                    ║");
        System.out.println("║                                                           ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║ 💡 RECOMMENDATION FOR GLOOMLIB:                          ║");
        System.out.println("║                                                           ║");
        System.out.println("║    VarHandle for new code (Java 9+)                      ║");
        System.out.println("║    MethodHandle for current implementation (Java 7+)     ║");
        System.out.println("║    Field API for maximum compatibility                    ║");
        System.out.println("║                                                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
    }

    static class TestObject {
        public String name = "default";
        public int value = 42;
        public boolean flag = true;
        public double score = 3.14;
    }
}

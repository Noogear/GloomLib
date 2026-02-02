package gloomlib.test;

import gloomlib.translation.api.MiniMessageTranslationRegistry;
import gloomlib.translation.impl.MiniMessageTranslationRegistryImpl;
import gloomlib.translation.util.MiniMessages;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * Performance benchmark for translation key lookup.
 */
class TranslationPerformanceBenchmarkTest {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 100000;
    
    private MiniMessageTranslationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MiniMessageTranslationRegistryImpl(
            Key.key("test", "benchmark"),
            MiniMessages.get()
        );
        registry.defaultLocale(Locale.US);
        
        // Register translations
        registry.register("test.simple", Locale.US, "Hello World");
        registry.register("test.simple", Locale.of("zh", "CN"), "你好世界");
        registry.register("test.simple", Locale.of("zh", "HK"), "你好世界 HK");
        registry.register("test.simple", Locale.of("zh"), "你好世界 ZH");
        
        registry.register("test.complex", Locale.US, "<bold>Hello</bold> <arg:0>!");
        registry.register("test.complex", Locale.of("zh", "CN"), "<bold>你好</bold> <arg:0>!");
        
        // Register many keys to simulate real usage
        for (int i = 0; i < 100; i++) {
            registry.register("messages.key" + i, Locale.US, "Message " + i);
            registry.register("messages.key" + i, Locale.of("zh", "CN"), "消息 " + i);
        }
    }

    @Test
    @DisplayName("Benchmark: Same locale repeated lookup")
    void benchmarkSameLocaleRepeatedLookup() {
        Locale locale = Locale.of("zh", "CN");
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            registry.miniMessageTranslation("test.simple", locale);
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            registry.miniMessageTranslation("test.simple", locale);
        }
        long endTime = System.nanoTime();
        
        double totalMs = (endTime - startTime) / 1_000_000.0;
        double avgNs = (endTime - startTime) / (double) BENCHMARK_ITERATIONS;
        
        System.out.println("=== Same Locale Repeated Lookup ===");
        System.out.println("Iterations: " + BENCHMARK_ITERATIONS);
        System.out.println("Total time: " + String.format("%.2f", totalMs) + " ms");
        System.out.println("Avg per lookup: " + String.format("%.2f", avgNs) + " ns");
        System.out.println();
    }

    @Test
    @DisplayName("Benchmark: Fallback chain lookup (zh_HK -> zh -> en_US)")
    void benchmarkFallbackChainLookup() {
        // zh_TW not registered, should fallback through chain
        Locale locale = Locale.of("zh", "TW");
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            registry.miniMessageTranslation("test.simple", locale);
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            registry.miniMessageTranslation("test.simple", locale);
        }
        long endTime = System.nanoTime();
        
        double totalMs = (endTime - startTime) / 1_000_000.0;
        double avgNs = (endTime - startTime) / (double) BENCHMARK_ITERATIONS;
        
        System.out.println("=== Fallback Chain Lookup (zh_TW -> zh -> en_US) ===");
        System.out.println("Iterations: " + BENCHMARK_ITERATIONS);
        System.out.println("Total time: " + String.format("%.2f", totalMs) + " ms");
        System.out.println("Avg per lookup: " + String.format("%.2f", avgNs) + " ns");
        System.out.println();
    }

    @Test
    @DisplayName("Benchmark: Multiple keys rotation")
    void benchmarkMultipleKeysRotation() {
        Locale locale = Locale.of("zh", "CN");
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            registry.miniMessageTranslation("messages.key" + (i % 100), locale);
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            registry.miniMessageTranslation("messages.key" + (i % 100), locale);
        }
        long endTime = System.nanoTime();
        
        double totalMs = (endTime - startTime) / 1_000_000.0;
        double avgNs = (endTime - startTime) / (double) BENCHMARK_ITERATIONS;
        
        System.out.println("=== Multiple Keys Rotation (100 keys) ===");
        System.out.println("Iterations: " + BENCHMARK_ITERATIONS);
        System.out.println("Total time: " + String.format("%.2f", totalMs) + " ms");
        System.out.println("Avg per lookup: " + String.format("%.2f", avgNs) + " ns");
        System.out.println();
    }

    @Test
    @DisplayName("Benchmark: Mixed locales")
    void benchmarkMixedLocales() {
        Locale[] locales = {
            Locale.US, 
            Locale.of("zh", "CN"), 
            Locale.of("zh", "HK"),
            Locale.of("zh", "TW"),  // fallback
            Locale.of("zh")
        };
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            registry.miniMessageTranslation("test.simple", locales[i % locales.length]);
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            registry.miniMessageTranslation("test.simple", locales[i % locales.length]);
        }
        long endTime = System.nanoTime();
        
        double totalMs = (endTime - startTime) / 1_000_000.0;
        double avgNs = (endTime - startTime) / (double) BENCHMARK_ITERATIONS;
        
        System.out.println("=== Mixed Locales (5 locales) ===");
        System.out.println("Iterations: " + BENCHMARK_ITERATIONS);
        System.out.println("Total time: " + String.format("%.2f", totalMs) + " ms");
        System.out.println("Avg per lookup: " + String.format("%.2f", avgNs) + " ns");
        System.out.println();
    }
}

package gloomlib.test;

import gloomlib.translation.api.TranslationManager;
import gloomlib.translation.impl.TranslationManagerImpl;
import gloomlib.translation.util.MiniMessages;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

class TranslationLoadingBenchmarkTest {

    @TempDir
    Path tempDir;

    private TranslationManagerImpl manager;

    @BeforeEach
    void setUp() throws IOException {
        // Create dummy translation files
        Path enUS = tempDir.resolve("en_US.yml");
        StringBuilder sb = new StringBuilder();
        sb.append("lang-version: '1.0'\n");
        for (int i = 0; i < 1000; i++) {
            sb.append("key.").append(i).append(": \"Value ").append(i).append("\"\n");
        }
        Files.writeString(enUS, sb.toString(), StandardCharsets.UTF_8);

        // Properties file
        Path zhCN = tempDir.resolve("zh_CN.properties");
        sb = new StringBuilder();
        sb.append("lang-version=1.0\n");
        for (int i = 0; i < 1000; i++) {
            sb.append("key.").append(i).append("=值 ").append(i).append("\n");
        }
        Files.writeString(zhCN, sb.toString(), StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    @DisplayName("Benchmark: Load process")
    void benchmarkLoading() {
        // Benchmark
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 100; i++) {
            manager = new TranslationManagerImpl(
                Key.key("test", "bench"),
                tempDir,
                Locale.US
            );
            manager.load(List.of("en_US", "zh_CN"));
            manager.close();
        }
        
        long endTime = System.nanoTime();
        
        double totalMs = (endTime - startTime) / 1_000_000.0;
        double avgMs = totalMs / 100.0;
        
        System.out.println("=== Translation Loading Benchmark (100 iterations) ===");
        System.out.println("Total time: " + String.format("%.2f", totalMs) + " ms");
        System.out.println("Avg per load: " + String.format("%.2f", avgMs) + " ms");
        System.out.println();
    }
}

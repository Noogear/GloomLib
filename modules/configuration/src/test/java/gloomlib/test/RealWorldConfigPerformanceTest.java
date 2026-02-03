package gloomlib.test;

import gloomlib.configuration.ConfigurationFile;
import gloomlib.configuration.ConfigurationManager;
import gloomlib.configuration.ConfigurationPart;
import gloomlib.configuration.annotations.Check;
import gloomlib.configuration.annotations.Comment;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test demonstrating real-world configuration performance with optimized reflection.
 */
@DisplayName("Real-world Configuration Performance")
class RealWorldConfigPerformanceTest {

    private File testDir;

    @BeforeEach
    void setUp() throws Exception {
        testDir = Files.createTempDirectory("gloom-perf-test-").toFile();
        testDir.deleteOnExit();
    }

    @AfterEach
    void tearDown() {
        if (testDir != null && testDir.exists()) {
            File[] files = testDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            testDir.delete();
        }
    }

    // Test configuration classes
    public static class ServerConfig extends ConfigurationFile {
        @Comment("Server name displayed in listings")
        public String serverName = "My Awesome Server";

        @Comment("Maximum number of players")
        @Check(RangeCheck.class)
        public int maxPlayers = 100;

        public boolean whitelist = false;
        public String motd = "Welcome!";
        public Map<String, GameMode> gameModes = new HashMap<>();

        public ServerConfig() {
            gameModes.put("survival", new GameMode());
            gameModes.put("creative", new GameMode());
        }
    }

    public static class GameMode extends ConfigurationPart {
        public String displayName = "Survival";
        public boolean pvpEnabled = true;
        public int difficulty = 1;
        public Map<String, String> rules = new HashMap<>();
    }

    public static class RangeCheck implements Check.Validator<Integer> {
        @Override
        public Integer validate(Integer value) {
            return Math.max(1, Math.min(1000, value));
        }
    }

    @Test
    @DisplayName("Load/Save cycle - realistic config")
    void testRealisticLoadSaveCycle() throws Exception {
        File configFile = new File(testDir, "server.yml");

        // Initial load with defaults
        long startLoad = System.nanoTime();
        ServerConfig config = ConfigurationManager.load(ServerConfig.class, configFile);
        long loadTime = System.nanoTime() - startLoad;

        assertNotNull(config);
        assertEquals("My Awesome Server", config.serverName);
        assertEquals(100, config.maxPlayers);
        assertEquals(2, config.gameModes.size());

        // Modify values
        config.serverName = "Updated Server";
        config.maxPlayers = 200;
        config.gameModes.get("survival").displayName = "Hardcore Survival";
        config.gameModes.get("survival").rules.put("keepInventory", "false");

        // Save
        long startSave = System.nanoTime();
        config.save();
        long saveTime = System.nanoTime() - startSave;

        // Reload and verify
        long startReload = System.nanoTime();
        ServerConfig reloaded = ConfigurationManager.load(ServerConfig.class, configFile);
        long reloadTime = System.nanoTime() - startReload;

        assertEquals("Updated Server", reloaded.serverName);
        assertEquals(200, reloaded.maxPlayers);
        assertEquals("Hardcore Survival", reloaded.gameModes.get("survival").displayName);
        assertEquals("false", reloaded.gameModes.get("survival").rules.get("keepInventory"));

        System.out.println("--- Real-world Config Performance ---");
        System.out.printf("Initial load:  %.2f ms%n", loadTime / 1_000_000.0);
        System.out.printf("Save:          %.2f ms%n", saveTime / 1_000_000.0);
        System.out.printf("Reload:        %.2f ms%n", reloadTime / 1_000_000.0);
        System.out.println();

        // All operations should be fast
        assertTrue(loadTime < 50_000_000, "Load should be under 50ms");
        assertTrue(saveTime < 50_000_000, "Save should be under 50ms");
        assertTrue(reloadTime < 50_000_000, "Reload should be under 50ms");
    }

    @Test
    @DisplayName("Bulk field access - 1000 configs")
    void testBulkFieldAccess() throws Exception {
        File configFile = new File(testDir, "bulk-test.yml");
        ServerConfig config = ConfigurationManager.load(ServerConfig.class, configFile);

        int iterations = 1000;
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // Simulate typical config usage pattern
            config.serverName = "Server #" + i;
            config.maxPlayers = 50 + i;
            config.whitelist = i % 2 == 0;
            config.motd = "MOTD for iteration " + i;

            // Read back
            String name = config.serverName;
            int players = config.maxPlayers;
            boolean wl = config.whitelist;

            assertEquals("Server #" + i, name);
            assertEquals(50 + i, players);
            assertEquals(i % 2 == 0, wl);
        }

        long elapsed = System.nanoTime() - start;

        System.out.printf("--- Bulk Field Access (%,d iterations) ---%n", iterations);
        System.out.printf("Total time:     %.2f ms%n", elapsed / 1_000_000.0);
        System.out.printf("Avg per config: %.2f µs%n", elapsed / (iterations * 1000.0));
        System.out.printf("Throughput:     %,.0f configs/sec%n", (iterations * 1.0) / (elapsed / 1_000_000_000.0));
        System.out.println();

        assertTrue(elapsed < 100_000_000, "Bulk access should complete in under 100ms");
    }

    @Test
    @DisplayName("Validation performance - @Check annotation")
    void testValidationPerformance() throws Exception {
        File configFile = new File(testDir, "validation-test.yml");
        
        int iterations = 500;
        long total = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            ServerConfig config = ConfigurationManager.load(ServerConfig.class, configFile);
            
            // Test validation on invalid values
            config.maxPlayers = -100;  // Should be validated to 1
            config.save();
            
            ServerConfig reloaded = ConfigurationManager.load(ServerConfig.class, configFile);
            assertEquals(1, reloaded.maxPlayers, "Validator should correct negative value");
            
            total += System.nanoTime() - start;
        }

        System.out.printf("--- Validation Performance (%,d iterations) ---%n", iterations);
        System.out.printf("Total time:         %.2f ms%n", total / 1_000_000.0);
        System.out.printf("Avg per load/save:  %.2f ms%n", (total / iterations) / 1_000_000.0);
        System.out.println();

        assertTrue(total < 5_000_000_000L, "Validation should not significantly impact performance");
    }

    @Test
    @DisplayName("Memory stress - 100 config instances")
    void testMemoryStress() throws Exception {
        int configCount = 100;
        ServerConfig[] configs = new ServerConfig[configCount];

        long start = System.nanoTime();
        Runtime runtime = Runtime.getRuntime();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        // Create many config instances
        for (int i = 0; i < configCount; i++) {
            File f = new File(testDir, "config-" + i + ".yml");
            configs[i] = ConfigurationManager.load(ServerConfig.class, f);
            configs[i].serverName = "Server " + i;
            configs[i].save();
        }

        long elapsed = System.nanoTime() - start;
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memUsed = memAfter - memBefore;

        System.out.printf("--- Memory Stress (%d configs) ---%n", configCount);
        System.out.printf("Total time:        %.2f ms%n", elapsed / 1_000_000.0);
        System.out.printf("Memory used:       %.2f MB%n", memUsed / (1024.0 * 1024.0));
        System.out.printf("Per config:        %.2f KB%n", memUsed / (configCount * 1024.0));
        System.out.println();

        // Verify all configs loaded correctly
        for (int i = 0; i < configCount; i++) {
            assertEquals("Server " + i, configs[i].serverName);
        }

        assertTrue(elapsed < 2_000_000_000L, "Should handle 100 configs in under 2 seconds");
    }
}

package gloomlib.test;

import gloomlib.configuration.ConfigBackupManager;
import gloomlib.configuration.ConfigurationManager;
import gloomlib.configuration.SparkConfigIntegration;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for enhanced configuration features:
 * - Version management
 * - Async operations
 * - Backup functionality
 * - Spark integration
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EnhancedConfigTest {

    private static File configFile;
    private static EnhancedTestConfig config;

    @BeforeAll
    static void setup() throws Exception {
        File testDir = new File("build/test-enhanced-config");
        if (!testDir.exists()) {
            testDir.mkdirs();
        }
        configFile = new File(testDir, "enhanced-config.yml");
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. 测试基本配置加载")
    void testBasicLoad() throws Exception {
        System.out.println("\n=== Test 1: Basic Load ===");
        
        config = ConfigurationManager.load(EnhancedTestConfig.class, configFile);
        assertNotNull(config);
        assertEquals(2, config.version);
        assertEquals("MyServer", config.serverName);
        assertEquals(20, config.maxPlayers);
        assertEquals("localhost", config.database.host);
        
        System.out.println("✓ Configuration loaded successfully");
        System.out.println("  - Version: " + config.version);
        System.out.println("  - Server Name: " + config.serverName);
        System.out.println("  - API Key: " + config.apiKey + " (sensitive)");
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试异步重载")
    void testAsyncReload() throws Exception {
        System.out.println("\n=== Test 2: Async Reload ===");
        
        config.serverName = "UpdatedServer";
        config.save();
        
        CompletableFuture<Void> future = config.reloadAsync();
        assertNotNull(future);
        
        // Wait for async operation to complete
        future.get(5, TimeUnit.SECONDS);
        
        assertEquals("UpdatedServer", config.serverName);
        System.out.println("✓ Async reload completed successfully");
        System.out.println("  - Server Name after reload: " + config.serverName);
    }

    @Test
    @Order(3)
    @DisplayName("3. 测试配置备份")
    void testBackup() throws Exception {
        System.out.println("\n=== Test 3: Backup ===");
        
        File backup = ConfigBackupManager.backup(configFile, "manual");
        assertNotNull(backup);
        assertTrue(backup.exists());
        assertTrue(backup.getName().contains("manual"));
        
        System.out.println("✓ Backup created successfully");
        System.out.println("  - Backup file: " + backup.getName());
        System.out.println("  - Backup size: " + backup.length() + " bytes");
        
        // Test async backup
        CompletableFuture<File> asyncBackup = ConfigBackupManager.backupAsync(configFile, "async");
        File asyncBackupFile = asyncBackup.get(5, TimeUnit.SECONDS);
        assertNotNull(asyncBackupFile);
        assertTrue(asyncBackupFile.exists());
        
        System.out.println("✓ Async backup completed");
        System.out.println("  - Async backup file: " + asyncBackupFile.getName());
    }

    @Test
    @Order(4)
    @DisplayName("4. 测试备份列表和清理")
    void testBackupManagement() throws Exception {
        System.out.println("\n=== Test 4: Backup Management ===");
        
        // Create multiple backups
        for (int i = 0; i < 5; i++) {
            ConfigBackupManager.backup(configFile, "test" + i);
            Thread.sleep(100); // Ensure different timestamps
        }
        
        File[] backups = ConfigBackupManager.listBackups(configFile);
        assertTrue(backups.length >= 5);
        System.out.println("✓ Created multiple backups: " + backups.length);
        
        // Test cleanup - keep only 3 most recent
        int deleted = ConfigBackupManager.cleanOldBackups(configFile, 3);
        assertTrue(deleted >= 2);
        System.out.println("✓ Cleaned old backups: " + deleted + " deleted");
        
        File[] remainingBackups = ConfigBackupManager.listBackups(configFile);
        assertTrue(remainingBackups.length <= 5); // May have some from previous tests
        System.out.println("  - Remaining backups: " + remainingBackups.length);
        
        long totalSize = ConfigBackupManager.getBackupSize(configFile);
        System.out.println("  - Total backup size: " + totalSize + " bytes");
    }

    @Test
    @Order(5)
    @DisplayName("5. 测试版本验证")
    void testVersionCheck() throws Exception {
        System.out.println("\n=== Test 5: Version Check ===");
        
        // Read current version
        String content = Files.readString(configFile.toPath());
        assertTrue(content.contains("version: 2"));
        System.out.println("✓ Version field present in YAML");
        
        // Verify version field is annotated
        var versionField = EnhancedTestConfig.class.getField("version");
        assertTrue(versionField.isAnnotationPresent(gloomlib.configuration.annotations.Version.class));
        System.out.println("✓ @Version annotation present");
    }

    @Test
    @Order(6)
    @DisplayName("6. 测试敏感字段标记")
    void testSensitiveFields() throws Exception {
        System.out.println("\n=== Test 6: Sensitive Fields ===");
        
        var apiKeyField = EnhancedTestConfig.class.getField("apiKey");
        var passwordField = EnhancedTestConfig.class.getField("databasePassword");
        var dbPasswordField = EnhancedTestConfig.DatabaseConfig.class.getField("password");
        
        assertTrue(apiKeyField.isAnnotationPresent(gloomlib.configuration.annotations.Sensitive.class));
        assertTrue(passwordField.isAnnotationPresent(gloomlib.configuration.annotations.Sensitive.class));
        assertTrue(dbPasswordField.isAnnotationPresent(gloomlib.configuration.annotations.Sensitive.class));
        
        System.out.println("✓ Sensitive fields properly annotated:");
        System.out.println("  - apiKey");
        System.out.println("  - databasePassword");
        System.out.println("  - database.password");
    }

    @Test
    @Order(7)
    @DisplayName("7. 测试 Spark 集成")
    void testSparkIntegration() throws Exception {
        System.out.println("\n=== Test 7: Spark Integration ===");
        
        // Register config with Spark
        SparkConfigIntegration.register(configFile, EnhancedTestConfig.class);
        
        var registeredConfigs = SparkConfigIntegration.getRegisteredConfigs();
        assertFalse(registeredConfigs.isEmpty());
        System.out.println("✓ Configuration registered with Spark");
        System.out.println("  - Registered configs: " + registeredConfigs.size());
        
        var hiddenPaths = SparkConfigIntegration.getHiddenPaths();
        assertFalse(hiddenPaths.isEmpty());
        System.out.println("✓ Sensitive paths hidden from monitoring:");
        for (String path : hiddenPaths) {
            System.out.println("  - " + path);
        }
        
        // Check if Spark is available (may be false in test environment)
        boolean sparkAvailable = SparkConfigIntegration.isSparkAvailable();
        System.out.println("  - Spark available: " + sparkAvailable);
    }

    @Test
    @Order(8)
    @DisplayName("8. 测试字段验证")
    void testFieldValidation() throws Exception {
        System.out.println("\n=== Test 8: Field Validation ===");
        
        config.maxPlayers = 500; // Exceeds limit
        config.save();
        
        EnhancedTestConfig reloaded = ConfigurationManager.load(EnhancedTestConfig.class, configFile);
        assertEquals(100, reloaded.maxPlayers, "Max players should be clamped to 100");
        
        config.maxPlayers = -10; // Below limit
        config.save();
        
        reloaded = ConfigurationManager.load(EnhancedTestConfig.class, configFile);
        assertEquals(1, reloaded.maxPlayers, "Max players should be clamped to 1");
        
        System.out.println("✓ Field validation working correctly");
        System.out.println("  - 500 → 100 (max limit)");
        System.out.println("  - -10 → 1 (min limit)");
    }

    @AfterAll
    static void cleanup() {
        System.out.println("\n=== Cleanup ===");
        if (configFile != null && configFile.exists()) {
            // Keep the file for manual inspection
            System.out.println("Test configuration saved at: " + configFile.getAbsolutePath());
        }
    }
}

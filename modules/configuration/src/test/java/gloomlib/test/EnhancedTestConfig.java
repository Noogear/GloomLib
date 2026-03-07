package gloomlib.test;

import gloomlib.configuration.api.ConfigurationFile;
import gloomlib.configuration.api.annotation.*;

/**
 * Test configuration demonstrating features:
 * - Version management with @Version
 * - Async operations
 */
@Header({
        "Enhanced Configuration Test",
        "Features: Version Management, Async Reload"
})
public class EnhancedTestConfig extends ConfigurationFile {

    // ============ Version Management ============

    @Version(value = 2, autoBackup = true, migrate = true)
    @Comment("Configuration version (do not modify manually)")
    public int version = 2;

    // ============ API Keys ============

    @Comment("API key")
    public String apiKey = "secret-key-12345";

    @Comment("Database password")
    public String databasePassword = "password123";

    // ============ Regular Configuration ============

    @Comment("Server name")
    public String serverName = "MyServer";

    @Comment("Max players (1-100)")
    @Check(cls = EnhancedTestConfig.class, method = "validateMaxPlayers")
    public int maxPlayers = 20;

    @Comment("Debug mode")
    public boolean debugMode = false;

    // ============ Nested Configuration ============

    @Comment("Database settings")
    public DatabaseConfig database = new DatabaseConfig();

    // ============ Validation Methods ============

    private static int validateMaxPlayers(int value) {
        if (value < 1) return 1;
        if (value > 100) return 100;
        return value;
    }

    // ============ Lifecycle Hooks ============

    @PostLoad
    public void onConfigLoaded() {
        System.out.println("[EnhancedTestConfig] Configuration loaded (version: " + version + ")");
        if (debugMode) {
            System.out.println("[EnhancedTestConfig] Debug mode is ENABLED");
        }
    }

    // ============ Nested Classes ============

    public static class DatabaseConfig extends gloomlib.configuration.api.ConfigurationPart {

        @Comment("Database host")
        public String host = "localhost";

        @Comment("Database port")
        public int port = 3306;

        @Comment("Database username")
        public String username = "root";

        @Comment("Database password")
        public String password = "root";
    }
}

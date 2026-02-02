package gloomlib.test;

import gloomlib.configuration.ConfigurationFile;
import gloomlib.configuration.annotations.*;

/**
 * Test configuration demonstrating new features:
 * - Version management with @Version
 * - Sensitive fields with @Sensitive
 * - Async operations
 */
@Header({
        "Enhanced Configuration Test",
        "Features: Version Management, Sensitive Data, Async Reload"
})
public class EnhancedTestConfig extends ConfigurationFile {

    // ============ Version Management ============

    @Version(value = 2, autoBackup = true, migrate = true)
    @Comment("Configuration version (do not modify manually)")
    public int version = 2;

    // ============ Sensitive Data ============

    @Sensitive(mask = "***")
    @Comment("API key (will be hidden from monitoring tools)")
    public String apiKey = "secret-key-12345";

    @Sensitive(mask = "[REDACTED]")
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

    public static class DatabaseConfig extends gloomlib.configuration.ConfigurationPart {

        @Comment("Database host")
        public String host = "localhost";

        @Comment("Database port")
        public int port = 3306;

        @Sensitive(mask = "***")
        @Comment("Database username")
        public String username = "root";

        @Sensitive(mask = "***")
        @Comment("Database password")
        public String password = "root";
    }
}

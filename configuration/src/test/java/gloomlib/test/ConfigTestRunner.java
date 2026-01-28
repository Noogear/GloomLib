package gloomlib.test;

import gloomlib.configuration.ConfigurationManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigTestRunner {

    private File configFile;

    @BeforeEach
    public void setUp() {
        File buildDir = new File("build/test-outputs-comprehensive");
        if (!buildDir.exists()) {
            buildDir.mkdirs();
        }
        configFile = new File(buildDir, "full_config.yml");
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    public void testComprehensiveFeatures() throws Exception {
        System.out.println("=== 開始全面功能測試 ===");

        // 1. 首次加載與默認值生成
        System.out.println(">>> 1. 測試默認生成與 @Template...");
        TestConfig config = ConfigurationManager.load(TestConfig.class, configFile);

        assertTrue(configFile.exists(), "配置文件應被創建");
        assertEquals("1.0.1", config.version);

        // 驗證 @Template
        assertTrue(config.classes.containsKey("warrior"), "FORCE 策略應生成 'warrior'");
        assertTrue(config.drops.containsKey("gold_coin"), "SMART 策略在為空時應生成 'gold_coin'");
        assertTrue(config.metadata.isEmpty(), "STRICT 策略不應生成任何鍵");

        // 驗證 Record 支持
        assertEquals(1, config.admins.size());
        assertEquals("Console", config.admins.get(0).name());
        assertEquals("System", config.admins.get(0).role());

        // 驗證 @PostLoad
        assertTrue(config.statusMessage.contains("已加載 1 個職業"), "@PostLoad 應正確填充 statusMessage");

        // 驗證嵌套邏輯 (保留原有測試)
        TestConfig.RpgClass warrior = config.classes.get("warrior");
        assertEquals(200, warrior.baseAttributes.health);
        // 新增斷言：驗證 Enum Key Map 是否正確加載
        assertEquals(10, warrior.baseAttributes.resistances.get(TestConfig.Element.FIRE), "Enum Key Map (resistances) 應正確保存並讀取");

        // 2. 測試 @Ignore
        System.out.println(">>> 2. 測試 @Ignore...");
        // 修改內存中的忽略字段
        config.internalSecret = "I_SHOULD_NOT_BE_SAVED";
        config.save();

        // 讀取文件內容檢查是否包含該字段
        String fileContent = Files.readString(configFile.toPath());
        assertFalse(fileContent.contains("internal-secret"), "YAML 文件中不應包含 @Ignore 字段");
        assertFalse(fileContent.contains("I_SHOULD_NOT_BE_SAVED"), "YAML 文件中不應包含忽略字段的值");

        // 重新加載，字段應恢復為類定義中的默認值
        TestConfig reloadedIgnore = ConfigurationManager.load(TestConfig.class, configFile);
        assertEquals("hidden-value", reloadedIgnore.internalSecret, "重新加載後 @Ignore 字段應為 Java 默認值");

        // 3. 測試 @Check 自動驗證與修正
        System.out.println(">>> 3. 測試 @Check 自動修正...");

        // 手動篡改 YAML 文件寫入非法數據
        YamlConfiguration rawYaml = YamlConfiguration.loadConfiguration(configFile);
        rawYaml.set("max-players", 5000); // 非法：超過 100
        rawYaml.set("server-name", "super_admin_user"); // 非法：包含 admin
        rawYaml.save(configFile);

        // 重新加載觸發驗證
        TestConfig validatedConfig = ConfigurationManager.load(TestConfig.class, configFile);

        // 驗證修正結果
        assertEquals(100, validatedConfig.maxPlayers, "超過上限的數值應被修正為 100");
        assertEquals("super_***_user", validatedConfig.serverName, "敏感詞 admin 應被修正為 ***");

        // 4. 測試複雜類型修改與 UUID
        System.out.println(">>> 4. 測試 UUID 與 Record 修改...");
        UUID newUuid = UUID.randomUUID();
        validatedConfig.serverId = newUuid;

        validatedConfig.admins.clear();
        validatedConfig.admins.add(new TestConfig.AdminUser("Player1", "Mod", 1));

        validatedConfig.save();

        TestConfig finalLoad = ConfigurationManager.load(TestConfig.class, configFile);
        assertEquals(newUuid, finalLoad.serverId, "UUID 應正確序列化與反序列化");
        assertEquals("Player1", finalLoad.admins.get(0).name(), "Record 列表應正確保存");
        assertEquals(1, finalLoad.admins.get(0).permissionLevel());

        System.out.println("=== 全面測試全部通過 ===");
        System.out.println("生成文件位於: " + configFile.getAbsolutePath());
    }
}

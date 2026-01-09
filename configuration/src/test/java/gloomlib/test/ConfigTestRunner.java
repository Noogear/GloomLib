package gloomlib.test;

import gloomlib.configuration.ConfigurationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigTestRunner {

    private File configFile;

    @BeforeEach
    public void setUp() {
        // 使用 build 目錄下的文件，方便測試後查看
        File buildDir = new File("build/test-outputs-complex");
        if (!buildDir.exists()) {
            buildDir.mkdirs();
        }
        configFile = new File(buildDir, "complex_config.yml");

        // 確保每次測試前清理舊文件
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    public void testComplexNestedConfiguration() throws Exception {
        System.out.println("=== 開始複雜嵌套配置測試 ===");

        // 1. 首次加載與生成 (Default Generation)
        System.out.println(">>> 測試默認生成...");
        TestConfig config = ConfigurationManager.load(TestConfig.class, configFile);

        // 驗證頂層
        assertTrue(configFile.exists(), "配置文件應被創建");
        assertEquals("1.0.0", config.version);

        // 驗證一層嵌套 (Map -> RpgClass)
        // 'warrior' 是由 @Template 自動生成的
        assertTrue(config.classes.containsKey("warrior"), "應自動生成默認職業 'warrior'");
        TestConfig.RpgClass warrior = config.classes.get("warrior");
        assertEquals("戰士", warrior.displayName);

        // 驗證二層嵌套 (RpgClass -> Attributes)
        // 這些值是在 @PreLoad 中被初始化的
        assertEquals(200, warrior.baseAttributes.health, "生命值應被 PreLoad 修改為 200");
        assertEquals(10, warrior.baseAttributes.resistances.get(TestConfig.Element.FIRE), "抗性 Map 應正確");

        // 驗證三層嵌套 (RpgClass -> List<Skill>)
        assertFalse(warrior.skills.isEmpty(), "技能列表不應為空");
        TestConfig.Skill skill = warrior.skills.get(0);
        assertEquals("火焰斬", skill.name);

        // 驗證四層嵌套 (Skill -> List<Effect>)
        assertFalse(skill.effects.isEmpty(), "效果列表不應為空");
        TestConfig.Effect effect = skill.effects.get(0);
        assertEquals("FIRE_DAMAGE", effect.type);
        assertEquals(3, effect.params.get("burn_time"), "任意參數 Map 讀取應正確");

        // 驗證 Map<String, List> 結構
        assertTrue(warrior.equipmentLimits.containsKey("WEAPON"), "裝備限制 Map 應包含 WEAPON");
        assertTrue(warrior.equipmentLimits.get("WEAPON").contains("AXE"), "裝備列表應包含 AXE");

        System.out.println(">>> 默認值驗證通過");

        // 2. 修改深層數據並保存 (Modification & Save)
        System.out.println(">>> 測試深層修改保存...");

        // 修改屬性
        warrior.baseAttributes.mana = 999;

        // 添加新技能
        TestConfig.Skill newSkill = new TestConfig.Skill();
        newSkill.name = "冰風暴";
        newSkill.levelReq = 5;

        TestConfig.Effect iceEffect = new TestConfig.Effect();
        iceEffect.type = "FREEZE";
        iceEffect.value = 5.0;
        newSkill.effects.add(iceEffect);

        warrior.skills.add(newSkill);

        // 添加一個全新的職業
        TestConfig.RpgClass mage = new TestConfig.RpgClass();
        mage.displayName = "法師";
        mage.baseAttributes.health = 80;
        config.classes.put("mage", mage);

        config.save();

        // 3. 重新加載驗證 (Reload & Verification)
        System.out.println(">>> 測試重新加載...");
        TestConfig reloaded = ConfigurationManager.load(TestConfig.class, configFile);
        TestConfig.RpgClass reloadedWarrior = reloaded.classes.get("warrior");
        TestConfig.RpgClass reloadedMage = reloaded.classes.get("mage");

        // 驗證修改是否生效
        assertEquals(999, reloadedWarrior.baseAttributes.mana, "修改的 Mana 應被保存");
        assertEquals(2, reloadedWarrior.skills.size(), "新添加的技能應被保存");
        assertEquals("冰風暴", reloadedWarrior.skills.get(1).name);
        assertEquals("FREEZE", reloadedWarrior.skills.get(1).effects.get(0).type, "深層嵌套對象屬性應正確");

        // 驗證新職業是否保存
        assertNotNull(reloadedMage, "新添加的職業 'mage' 應被保存");
        assertEquals("法師", reloadedMage.displayName);
        assertEquals(80, reloadedMage.baseAttributes.health);

        System.out.println("=== 複雜嵌套測試全部通過 ===");
        System.out.println("生成文件位於: " + configFile.getAbsolutePath());
    }
}
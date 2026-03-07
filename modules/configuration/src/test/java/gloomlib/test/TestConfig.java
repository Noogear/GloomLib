package gloomlib.test;

import gloomlib.configuration.api.ConfigurationFile;
import gloomlib.configuration.api.ConfigurationPart;
import gloomlib.configuration.api.annotation.*;

import java.util.*;

@Header({
        "==== GloomLib 全面功能測試配置 ====",
        "此文件展示了所有註解的用法以及複雜類型的支持"
})
public class TestConfig extends ConfigurationFile {

    // --- 基礎類型與註釋 ---
    @Comment("插件版本號")
    @Inline("核心版本") // 測試行內註釋
    public String version = "1.0.1";

    @Comment("調試模式開關")
    public boolean debug = false;

    // --- @Ignore 測試 ---
    @Ignore
    public String internalSecret = "hidden-value"; // 此字段不應出現在文件中

    // --- @Check 測試 (反射方法模式) ---
    @Comment("最大玩家數 (限制 1-100，超過自動修正)")
    @Check(cls = TestConfig.class, method = "validatePlayers")
    public int maxPlayers = 20;

    // --- @Check 測試 (接口類模式) ---
    @Comment("服務器名稱 (自動屏蔽敏感詞 'admin')")
    @Check(ServerNameValidator.class)
    public String serverName = "MyServer";

    // --- @PostLoad 測試 ---
    // 此字段不由 YAML 直接加載，而是由 PostLoad 計算填充
    @Ignore
    public String statusMessage = "";

    // --- 特殊類型測試 (Record & UUID) ---
    @Comment("服務器唯一標識符 (UUID)")
    public UUID serverId = UUID.randomUUID();

    @Comment("管理員列表 (Java Record 類型)")
    public List<AdminUser> admins = new ArrayList<>();

    // --- @Template 測試 (不同策略) ---

    @Comment("職業系統 (Template: FORCE)")
    public Map<String, RpgClass> classes = new HashMap<>();

    @Comment("掉落表 (Template: SMART)")
    public Map<String, DropItem> drops = new HashMap<>();

    @Comment("元數據 (Template: STRICT)")
    public Map<String, StrictMetadata> metadata = new HashMap<>();


    // 反射驗證方法
    private static int validatePlayers(int val) {
        if (val < 1) return 1;
        if (val > 100) return 100;
        return val;
    }

    @PreLoad
    public void initDefaults() {
        // 初始化複雜嵌套數據
        if (classes.containsKey("warrior")) {
            RpgClass warrior = classes.get("warrior");
            // 模擬業務邏輯修正默認值
            if (warrior.baseAttributes.health == 100) {
                warrior.baseAttributes.health = 200;
            }
            if (warrior.baseAttributes.resistances.isEmpty()) {
                warrior.baseAttributes.resistances.put(Element.FIRE, 10);
            }
            if (warrior.skills.isEmpty()) {
                Skill slash = new Skill();
                slash.name = "火焰斬";
                Effect dmg = new Effect();
                dmg.type = "FIRE_DAMAGE";
                dmg.value = 50.0;
                dmg.params.put("burn_time", 3);
                slash.effects.add(dmg);
                warrior.skills.add(slash);
                warrior.equipmentLimits.put("WEAPON", List.of("SWORD", "AXE"));
            }
        }

        // 初始化 Record 列表
        if (admins.isEmpty()) {
            admins.add(new AdminUser("Console", "System", 999));
        }
    }

    @PostLoad
    public void updateStatus() {
        // 根據加載的數據生成運行時狀態
        this.statusMessage = String.format("已加載 %d 個職業, %d 個管理員", classes.size(), admins.size());
    }

    public enum Element {FIRE, ICE, THUNDER, POISON}

    @Template(value = Template.Strategy.FORCE, name = "warrior")
    public static class RpgClass extends ConfigurationPart {
        @Comment("顯示名稱")
        public String displayName = "戰士";

        @Comment("基礎屬性")
        public Attributes baseAttributes = new Attributes();

        @Comment("技能列表")
        public List<Skill> skills = new ArrayList<>();

        public Map<String, List<String>> equipmentLimits = new HashMap<>();
    }

    public static class Attributes extends ConfigurationPart {
        public int health = 100;
        public int mana = 50;
        @Comment("元素抗性 (Enum Key)")
        public Map<Element, Integer> resistances = new HashMap<>();
    }

    public static class Skill extends ConfigurationPart {
        public String name = "重斬";
        public List<Effect> effects = new ArrayList<>();
    }

    public static class Effect extends ConfigurationPart {
        public String type = "DAMAGE";
        public double value = 15.0;
        public Map<String, Object> params = new HashMap<>();
    }


    @Template(value = Template.Strategy.SMART, name = "gold_coin")
    public static class DropItem extends ConfigurationPart {
        public double chance = 0.5;
        public int amount = 1;
    }

    @Template(value = Template.Strategy.STRICT)
    public static class StrictMetadata extends ConfigurationPart {
        public String tag = "none";
    }


    // Java Record 類型支持
    public record AdminUser(String name, String role, int permissionLevel) {
    }

    // 接口驗證器
    public static class ServerNameValidator implements Check.Validator<String> {
        @Override
        public String validate(String value) {
            if (value.toLowerCase().contains("admin")) {
                return value.replaceAll("(?i)admin", "***");
            }
            return value;
        }
    }
}


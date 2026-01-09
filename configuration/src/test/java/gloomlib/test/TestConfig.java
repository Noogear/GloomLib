package gloomlib.test;

import gloomlib.configuration.ConfigurationFile;
import gloomlib.configuration.ConfigurationPart;
import gloomlib.configuration.annotations.*;
import java.util.*;

@Header({
        "==== GloomLib 複雜嵌套測試 ====",
        "此文件展示了深層嵌套、泛型集合與混合類型的序列化能力"
})
public class TestConfig extends ConfigurationFile {

    @Comment("插件版本")
    public String version = "1.0.0";

    @Comment("調試模式")
    public boolean debug = false;

    @Comment("職業系統配置")
    public Map<String, RpgClass> classes = new HashMap<>();

    @Template(value = Template.Strategy.FORCE, name = "warrior")
    public static class RpgClass extends ConfigurationPart {
        @Comment("職業顯示名稱")
        public String displayName = "戰士";

        @Comment("基礎屬性")
        public Attributes baseAttributes = new Attributes();

        @Comment("技能樹 (List<ConfigurationPart>)")
        public List<Skill> skills = new ArrayList<>();

        @Comment("裝備限制 (Map<String, List<String>>)")
        public Map<String, List<String>> equipmentLimits = new HashMap<>();
    }

    public static class Attributes extends ConfigurationPart {
        public int health = 100;
        public int mana = 50;
        public double damage = 10.0;

        @Comment("元素抗性 (Map<Enum, Integer>)")
        public Map<Element, Integer> resistances = new HashMap<>();
    }

    public enum Element { FIRE, ICE, THUNDER, POISON }

    public static class Skill extends ConfigurationPart {
        public String name = "重斬";
        public int levelReq = 1;
        public double cooldown = 5.0;
        @Comment("技能效果")
        public List<Effect> effects = new ArrayList<>();
    }

    public static class Effect extends ConfigurationPart {
        public String type = "DAMAGE";
        public double value = 15.0;
        public Map<String, Object> params = new HashMap<>();
    }

    @PreLoad
    public void initDefaults() {
        System.out.println("[TestConfig] 執行 @PreLoad - initDefaults");
        if (classes.containsKey("warrior")) {
            System.out.println("[TestConfig] 找到 'warrior' 鍵");
            RpgClass warrior = classes.get("warrior");

            // 只要發現默認值 100，就修改為 200
            if (warrior.baseAttributes.health == 100) {
                System.out.println("[TestConfig] 修改 warrior 血量 100 -> 200");
                warrior.baseAttributes.health = 200;
            } else {
                System.out.println("[TestConfig] warrior 血量已是: " + warrior.baseAttributes.health);
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
        } else {
            System.out.println("[TestConfig] 未找到 'warrior' 鍵! (Template 未生效?)");
        }
    }
}
package gloomlib.gui.window.type;

import org.bukkit.event.inventory.InventoryType;
import org.jetbrains.annotations.NotNull;

/**
 * GUI 窗口类型枚举
 * <p>
 * 定义所有支持的 Minecraft 容器类型及其属性。
 * <p>
 * 参考实现：
 * <ul>
 *   <li>InvUI: window 包中的各种窗口实现</li>
 *   <li>Minecraft: 原版容器类型</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 2.0
 * @see <a href=\"https://github.com/NichtStudioCode/InvUI/tree/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/window\">InvUI window package</a>
 */
public enum GuiWindowType {
    
    /** 箱子（9x1 ~ 9x6，可自定义行数） */
    CHEST(InventoryType.CHEST, 9, true, 6),
    
    /** 漏斗（5 槽位，固定） */
    HOPPER(InventoryType.HOPPER, 5, false, 1),
    
    /** 发射器/投掷器（3x3，固定） */
    DISPENSER(InventoryType.DISPENSER, 9, false, 1),
    
    /** 潜影盒（3x3，固定） */
    SHULKER_BOX(InventoryType.SHULKER_BOX, 27, false, 1),
    
    /** 熔炉（3 槽位：输入、燃料、输出） */
    FURNACE(InventoryType.FURNACE, 3, false, 1),
    
    /** 高炉（3 槽位） */
    BLAST_FURNACE(InventoryType.BLAST_FURNACE, 3, false, 1),
    
    /** 烟熏炉（3 槽位） */
    SMOKER(InventoryType.SMOKER, 3, false, 1),
    
    /** 酿造台（5 槽位：3 瓶子 + 1 材料 + 1 燃料） */
    BREWING_STAND(InventoryType.BREWING, 5, false, 1),
    
    /** 附魔台（2 槽位：物品 + 青金石） */
    ENCHANTING(InventoryType.ENCHANTING, 2, false, 1),
    
    /** 铁砧（3 槽位：物品 + 材料 + 结果） */
    ANVIL(InventoryType.ANVIL, 3, false, 1),
    
    /** 锻造台（3 槽位：装备 + 材料 + 结果） */
    SMITHING(InventoryType.SMITHING, 3, false, 1),
    
    /** 信标（1 槽位：支付物品） */
    BEACON(InventoryType.BEACON, 1, false, 1),
    
    /** 切石机（2 槽位：输入 + 输出） */
    STONECUTTER(InventoryType.STONECUTTER, 2, false, 1),
    
    /** 织布机（4 槽位：旗帜 + 染料 + 图案 + 结果） */
    LOOM(InventoryType.LOOM, 4, false, 1),
    
    /** 制图台（3 槽位：地图 + 纸 + 结果） */
    CARTOGRAPHY(InventoryType.CARTOGRAPHY, 3, false, 1),
    
    /** 磨石（3 槽位：物品1 + 物品2 + 结果） */
    GRINDSTONE(InventoryType.GRINDSTONE, 3, false, 1),
    
    /** 讲台（1 槽位：书） */
    LECTERN(InventoryType.LECTERN, 1, false, 1),
    
    /** 商人/村民交易（3 槽位：输入1 + 输入2 + 结果） */
    MERCHANT(InventoryType.MERCHANT, 3, false, 1);
    
    private final InventoryType bukkitType;
    private final int defaultSlots;
    private final boolean resizable;
    private final int maxRows;
    
    GuiWindowType(InventoryType bukkitType, int defaultSlots, boolean resizable, int maxRows) {
        this.bukkitType = bukkitType;
        this.defaultSlots = defaultSlots;
        this.resizable = resizable;
        this.maxRows = maxRows;
    }
    
    /**
     * 获取 Bukkit 库存类型
     */
    @NotNull
    public InventoryType getBukkitType() {
        return bukkitType;
    }
    
    /**
     * 获取默认槽位数量
     */
    public int getDefaultSlots() {
        return defaultSlots;
    }
    
    /**
     * 是否可调整大小（仅箱子类型）
     */
    public boolean isResizable() {
        return resizable;
    }
    
    /**
     * 获取最大行数（仅箱子类型）
     */
    public int getMaxRows() {
        return maxRows;
    }
    
    /**
     * 计算指定行数的槽位数量（仅箱子类型）
     */
    public int getSlotsForRows(int rows) {
        if (!resizable) {
            throw new UnsupportedOperationException(this + " is not resizable");
        }
        if (rows < 1 || rows > maxRows) {
            throw new IllegalArgumentException("Invalid rows: " + rows + " (must be 1-" + maxRows + ")");
        }
        return 9 * rows;
    }
    
    /**
     * 是否为特殊容器（有特定功能槽位）
     */
    public boolean isSpecialContainer() {
        return switch (this) {
            case FURNACE, BLAST_FURNACE, SMOKER, 
                 BREWING_STAND, ENCHANTING, ANVIL, 
                 SMITHING, BEACON, STONECUTTER, 
                 LOOM, CARTOGRAPHY, GRINDSTONE -> true;
            default -> false;
        };
    }
    
    /**
     * 是否为存储容器（通用槽位）
     */
    public boolean isStorageContainer() {
        return switch (this) {
            case CHEST, HOPPER, DISPENSER, SHULKER_BOX -> true;
            default -> false;
        };
    }
    
    /**
     * 获取显示名称
     */
    @NotNull
    public String getDisplayName() {
        return switch (this) {
            case CHEST -> "箱子";
            case HOPPER -> "漏斗";
            case DISPENSER -> "发射器";
            case SHULKER_BOX -> "潜影盒";
            case FURNACE -> "熔炉";
            case BLAST_FURNACE -> "高炉";
            case SMOKER -> "烟熏炉";
            case BREWING_STAND -> "酿造台";
            case ENCHANTING -> "附魔台";
            case ANVIL -> "铁砧";
            case SMITHING -> "锻造台";
            case BEACON -> "信标";
            case STONECUTTER -> "切石机";
            case LOOM -> "织布机";
            case CARTOGRAPHY -> "制图台";
            case GRINDSTONE -> "磨石";
            case LECTERN -> "讲台";
            case MERCHANT -> "交易";
        };
    }
    
    /**
     * 从 Bukkit 类型获取窗口类型
     */
    @NotNull
    public static GuiWindowType fromBukkitType(@NotNull InventoryType bukkitType) {
        for (GuiWindowType type : values()) {
            if (type.bukkitType == bukkitType) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported inventory type: " + bukkitType);
    }
    
    /**
     * 是否支持该 Bukkit 类型
     */
    public static boolean isSupported(@NotNull InventoryType bukkitType) {
        for (GuiWindowType type : values()) {
            if (type.bukkitType == bukkitType) {
                return true;
            }
        }
        return false;
    }
}

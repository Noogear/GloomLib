package gloomlib.gui.click;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

/**
 * GUI 点击上下文记录
 * <p>
 * 包含完整的点击信息，包括点击类型、槽位、物品等。
 * 支持 Java 21 的 record 模式匹配。
 * <p>
 * 参考实现：
 * <ul>
 *   <li>InvUI: Click.java 和 ClickEvent.java</li>
 *   <li>Triumph-GUI: ClickContext.java</li>
 * </ul>
 * 
 * @param player       触发点击的玩家
 * @param clickType    点击类型
 * @param slot         被点击的槽位
 * @param item         槽位中的物品
 * @param cursor       光标上的物品
 * @param hotbarButton 快捷栏按钮（0-8），仅对 NUMBER_KEY 有效，其他情况为 -1
 * @param action       背包动作类型
 * @author GloomLib
 * @since 2.0
 * @see <a href=\"https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/Click.java\">InvUI Click.java</a>
 * @see <a href=\"https://github.com/triumphteam/triumph-gui/blob/update/v4/core/src/main/java/dev/triumphteam/gui/click/ClickContext.java\">Triumph-GUI ClickContext.java</a>
 */
public record GuiClickContext(
        @NotNull Player player,
        @NotNull GuiClickType clickType,
        int slot,
        @Nullable ItemStack item,
        @Nullable ItemStack cursor,
        @Range(from = -1, to = 8) int hotbarButton,
        @NotNull InventoryAction action
) {
    
    /**
     * 创建基础点击上下文（无快捷栏按钮）
     */
    public GuiClickContext(
            @NotNull Player player,
            @NotNull GuiClickType clickType,
            int slot,
            @Nullable ItemStack item,
            @Nullable ItemStack cursor,
            @NotNull InventoryAction action
    ) {
        this(player, clickType, slot, item, cursor, -1, action);
    }
    
    /**
     * 从 Bukkit 事件创建上下文
     */
    public static GuiClickContext fromBukkitClick(
            @NotNull Player player,
            @NotNull ClickType bukkitClick,
            int slot,
            @Nullable ItemStack item,
            @Nullable ItemStack cursor,
            int hotbarButton,
            @NotNull InventoryAction action
    ) {
        GuiClickType guiClickType = mapClickType(bukkitClick, action);
        return new GuiClickContext(player, guiClickType, slot, item, cursor, hotbarButton, action);
    }
    
    /**
     * 映射 Bukkit 点击类型到 GUI 点击类型
     */
    private static GuiClickType mapClickType(ClickType bukkitType, InventoryAction action) {
        return switch (bukkitType) {
            case LEFT -> GuiClickType.LEFT;
            case RIGHT -> GuiClickType.RIGHT;
            case SHIFT_LEFT -> GuiClickType.SHIFT_LEFT;
            case SHIFT_RIGHT -> GuiClickType.SHIFT_RIGHT;
            case MIDDLE -> GuiClickType.MIDDLE;
            case NUMBER_KEY -> GuiClickType.NUMBER_KEY;
            case DROP -> GuiClickType.DROP;
            case CONTROL_DROP -> GuiClickType.CONTROL_DROP;
            case DOUBLE_CLICK -> GuiClickType.DOUBLE_CLICK;
            case SWAP_OFFHAND -> GuiClickType.SWAP_OFFHAND;
            case WINDOW_BORDER_LEFT -> GuiClickType.WINDOW_BORDER_LEFT;
            case WINDOW_BORDER_RIGHT -> GuiClickType.WINDOW_BORDER_RIGHT;
            case CREATIVE -> GuiClickType.CREATIVE;
            default -> GuiClickType.UNKNOWN;
        };
    }
    
    public boolean isLeftClick() {
        return clickType.isLeft();
    }
    
    public boolean isRightClick() {
        return clickType.isRight();
    }
    
    public boolean isShiftClick() {
        return clickType.isShift();
    }
    
    public boolean isNumberKey() {
        return clickType == GuiClickType.NUMBER_KEY;
    }
    
    public boolean isOffhandSwap() {
        return clickType == GuiClickType.SWAP_OFFHAND;
    }
    
    public boolean isDrop() {
        return clickType.isDrop();
    }
    
    public boolean isDrag() {
        return clickType.isDrag();
    }
    
    public boolean isDoubleClick() {
        return clickType == GuiClickType.DOUBLE_CLICK;
    }
    
    public boolean isMiddleClick() {
        return clickType == GuiClickType.MIDDLE;
    }
    
    public boolean hasCursor() {
        return cursor != null && !cursor.isEmpty();
    }
    
    public boolean hasItem() {
        return item != null && !item.isEmpty();
    }
    
    public int getHotbarKey() {
        return hotbarButton + 1;
    }
    
    @NotNull
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("GuiClickContext{");
        sb.append("player=").append(player.getName());
        sb.append(", type=").append(clickType);
        sb.append(", slot=").append(slot);
        
        if (hotbarButton >= 0) {
            sb.append(", hotbar=").append(getHotbarKey());
        }
        
        if (hasItem()) {
            sb.append(", item=").append(item.getType());
        }
        
        if (hasCursor()) {
            sb.append(", cursor=").append(cursor.getType());
        }
        
        sb.append(", action=").append(action);
        sb.append("}");
        
        return sb.toString();
    }
    
    @NotNull
    public String getActionDescription() {
        return switch (clickType) {
            case LEFT -> "左键点击槽位 " + slot;
            case RIGHT -> "右键点击槽位 " + slot;
            case SHIFT_LEFT -> "快速移动槽位 " + slot + " 的物品";
            case SHIFT_RIGHT -> "快速移动槽位 " + slot + " 的半堆物品";
            case NUMBER_KEY -> "将槽位 " + slot + " 与快捷栏 " + getHotbarKey() + " 交换";
            case SWAP_OFFHAND -> "将槽位 " + slot + " 与副手交换";
            case DROP -> "丢弃槽位 " + slot + " 的一个物品";
            case CONTROL_DROP -> "丢弃槽位 " + slot + " 的全部物品";
            case DOUBLE_CLICK -> "收集相同物品到光标";
            case DRAG_LEFT -> "平均分配拖拽";
            case DRAG_RIGHT -> "单个物品拖拽";
            case DRAG_MIDDLE -> "完整堆叠拖拽（创造模式）";
            default -> "未知操作";
        };
    }
}

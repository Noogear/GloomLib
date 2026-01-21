package gloomlib.gui.interaction;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 交互上下文记录 - 包含完整的点击交互信息
 * <p>
 * 此类使用 Java 16+ 的 record 特性，提供不可变的数据载体。
 * 支持完整的交互类型检测，包括 MC 1.21+ 的新特性。
 * <p>
 * 参考：InvUI AbstractGui 的点击处理逻辑
 * {@link <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/gui/AbstractGui.java#L100-L550">InvUI AbstractGui.java</a>}
 * 
 * @param player         触发交互的玩家
 * @param clickType      点击类型
 * @param action         背包动作
 * @param slot           被点击的槽位
 * @param item           当前物品
 * @param componentIndex 组件索引
 * @author GloomLib
 * @since 2.0
 */
public record InteractionContext(
        @NotNull Player player,
        @NotNull ClickType clickType,
        @NotNull InventoryAction action,
        int slot,
        @Nullable ItemStack item,
        int componentIndex
) {

    /**
     * 是否为左键点击
     * 
     * @return 如果是左键点击返回 true
     */
    public boolean isLeftClick() {
        return clickType.isLeftClick();
    }

    /**
     * 是否为右键点击
     * 
     * @return 如果是右键点击返回 true
     */
    public boolean isRightClick() {
        return clickType.isRightClick();
    }

    /**
     * 是否为 Shift+点击
     * 
     * @return 如果是 Shift 点击返回 true
     */
    public boolean isShiftClick() {
        return clickType.isShiftClick();
    }

    /**
     * 是否为数字键点击（1-9 切换物品到快捷栏）
     * 
     * @return 如果是数字键点击返回 true
     */
    public boolean isNumberKey() {
        return clickType == ClickType.NUMBER_KEY;
    }

    /**
     * 是否为丢弃物品（Q 键或 Ctrl+Q）
     * 
     * @return 如果是丢弃操作返回 true
     */
    public boolean isDrop() {
        return clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP;
    }

    /**
     * 是否为全部丢弃（Ctrl+Q）
     * 
     * @return 如果是全部丢弃返回 true
     */
    public boolean isControlDrop() {
        return clickType == ClickType.CONTROL_DROP;
    }

    /**
     * 是否为双击收集相同物品
     * 
     * @return 如果是双击返回 true
     */
    public boolean isDoubleClick() {
        return clickType == ClickType.DOUBLE_CLICK;
    }

    /**
     * 是否为创造模式中键复制
     * 
     * @return 如果是中键点击返回 true
     */
    public boolean isMiddleClick() {
        return clickType == ClickType.MIDDLE;
    }

    /**
     * 是否为副手交换（F 键）
     * 
     * @return 如果是副手交换返回 true
     */
    public boolean isOffhandSwap() {
        return clickType == ClickType.SWAP_OFFHAND;
    }

    /**
     * 是否为窗口边界外点击
     * 
     * @return 如果是窗口外点击返回 true
     */
    public boolean isOutsideClick() {
        return clickType == ClickType.WINDOW_BORDER_LEFT || clickType == ClickType.WINDOW_BORDER_RIGHT;
    }

    /**
     * 检查是否为特定动作类型
     * 
     * @param actions 要检查的动作类型
     * @return 如果动作匹配返回 true
     */
    public boolean isAction(InventoryAction... actions) {
        for (InventoryAction a : actions) {
            if (action == a) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为放置物品动作
     * 
     * @return 如果是放置物品返回 true
     */
    public boolean isPlaceAction() {
        return isAction(
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME
        );
    }

    /**
     * 是否为拾取物品动作
     * 
     * @return 如果是拾取物品返回 true
     */
    public boolean isPickupAction() {
        return isAction(
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME
        );
    }

    /**
     * 是否为移动物品到其他背包（Shift+点击）
     * 
     * @return 如果是移动到其他背包返回 true
     */
    public boolean isMoveToOtherInventory() {
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY;
    }

    /**
     * 是否为交换槽位（数字键或副手键）
     * 
     * @return 如果是槽位交换返回 true
     */
    public boolean isSwapAction() {
        return isAction(
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.SWAP_WITH_CURSOR
        );
    }

    /**
     * 是否为克隆物品（创造模式中键）
     * 
     * @return 如果是克隆操作返回 true
     */
    public boolean isCloneAction() {
        return action == InventoryAction.CLONE_STACK;
    }

    /**
     * 是否涉及光标上的物品
     * 
     * @return 如果涉及光标物品返回 true
     */
    public boolean involvesCursor() {
        return isAction(
            InventoryAction.SWAP_WITH_CURSOR,
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME,
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME
        );
    }

    /**
     * 获取交互描述（用于调试）
     * 
     * @return 交互描述字符串
     */
    public String getDescription() {
        return String.format("InteractionContext{player=%s, click=%s, action=%s, slot=%d, index=%d}",
            player.getName(), clickType, action, slot, componentIndex);
    }
}
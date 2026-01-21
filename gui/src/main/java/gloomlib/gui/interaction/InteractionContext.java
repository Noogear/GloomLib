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
 * 支持完整的交互类型检测，包括 MC 1.21+ 的新特性（Bundle 交互等）。
 * <p>
 * 设计参考：InvUI 2.x 的完整点击处理逻辑
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

    // ==================== MC 1.21+ 新特性 ====================

    /**
     * 检查是否为 Bundle 相关交互
     * <p>
     * MC 1.21+ 引入了 Bundle（包裹）物品，支持特殊的交互方式。
     * 
     * @return 如果是 Bundle 交互返回 true
     */
    public boolean isBundleInteraction() {
        // Bundle 交互通常表现为右键点击
        // 具体检测需要根据物品类型判断
        if (item != null && isRightClick()) {
            return item.getType().name().contains("BUNDLE");
        }
        return false;
    }

    /**
     * 检查是否为跨 GUI 双击收集
     * <p>
     * 当玩家双击时，应该收集所有相同类型的物品。
     * InvUI 实现了跨 GUI 的智能收集逻辑。
     * 
     * @return 如果是双击且涉及物品堆叠返回 true
     */
    public boolean isDoubleClickCollect() {
        return isDoubleClick() && item != null && !item.getType().isAir();
    }

    /**
     * 检查是否为拖拽分配操作
     * <p>
     * 拖拽可以将光标上的物品均匀分配到多个槽位。
     * 
     * @return 如果是拖拽操作返回 true
     */
    public boolean isDragOperation() {
        return clickType == ClickType.LEFT || clickType == ClickType.RIGHT;
    }

    /**
     * 检查是否需要阻止物品移动
     * <p>
     * 某些操作（如 Shift+点击、数字键交换）会移动物品，
     * GUI 通常需要阻止这些操作以保持界面状态。
     * 
     * @return 如果需要阻止返回 true
     */
    public boolean shouldPreventItemMovement() {
        return isMoveToOtherInventory() || isSwapAction() || isOffhandSwap();
    }

    /**
     * 检查是否为有效的 GUI 交互
     * <p>
     * 排除一些不应该在 GUI 中处理的特殊点击类型。
     * 
     * @return 如果是有效交互返回 true
     */
    public boolean isValidGuiInteraction() {
        return !isOutsideClick() && action != InventoryAction.NOTHING;
    }
}

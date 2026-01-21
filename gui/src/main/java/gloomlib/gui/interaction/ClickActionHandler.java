package gloomlib.gui.interaction;

import gloomlib.gui.util.BundleUtils;
import gloomlib.gui.util.GuiItemUtils;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 点击动作处理器 - 实现完整的 Minecraft 原版点击交互逻辑
 * <p>
 * 参考 InvUI 2.x 的 GuiImpl 实现，支持：
 * - 左键/右键点击（拾取、放置、堆叠、交换）
 * - Shift+点击跨背包移动（支持优先级）
 * - 数字键快捷栏交换
 * - 双击收集相似物品
 * - 中键克隆（创造模式）
 * - 副手交换（F键）
 * - 丢弃物品（Q键）
 * - Bundle 支持（MC 1.21+）
 * 
 * @author GloomLib
 * @since 2.0
 */
public final class ClickActionHandler {

    private ClickActionHandler() {
    }

    /**
     * 优先级槽位（内部使用）
     */
    private record PrioritizedSlot(int slot, int priority) {
    }

    /**
     * 处理左键点击
     * <p>
     * 逻辑：
     * - 光标空 + 槽位有物品 → 拾取全部
     * - 光标有物品 + 槽位空 → 放置全部
     * - 相似物品 → 尝试堆叠
     * - 不同物品 → 交换
     * - Bundle 特殊处理（MC 1.21+）：插入/取出物品
     * 
     * @param player      玩家
     * @param slotItem    槽位物品
     * @param cursorItem  光标物品
     * @return 点击结果
     */
    @NotNull
    public static ClickResult handleLeftClick(
            @NotNull Player player,
            @Nullable ItemStack slotItem,
            @Nullable ItemStack cursorItem
    ) {
        // Bundle 特殊处理：光标有物品 + 槽位是 Bundle → 插入到 Bundle
        if (BundleUtils.isBundleSupported() && BundleUtils.isBundle(slotItem) && !GuiItemUtils.isEmpty(cursorItem)) {
            BundleUtils.InsertResult bundleResult = BundleUtils.insertIntoBundle(slotItem, cursorItem);
            return new ClickResult(bundleResult.newBundle(), bundleResult.remaining(), true);
        }

        // Bundle 特殊处理：光标空 + 槽位是 Bundle → 从 Bundle 取出
        if (BundleUtils.isBundleSupported() && BundleUtils.isBundle(slotItem) && GuiItemUtils.isEmpty(cursorItem)) {
            BundleUtils.ExtractResult extractResult = BundleUtils.extractFromBundle(slotItem);
            if (extractResult.wasExtracted()) {
                return new ClickResult(extractResult.newBundle(), extractResult.extracted(), true);
            }
            // Bundle 为空，拾取整个 Bundle
            return new ClickResult(null, slotItem.clone(), true);
        }

        // 光标为空，拾取槽位物品
        if (GuiItemUtils.isEmpty(cursorItem)) {
            if (!GuiItemUtils.isEmpty(slotItem)) {
                return new ClickResult(null, slotItem.clone(), true);
            }
            return ClickResult.noChange();
        }

        // 光标有物品，槽位为空，放置全部
        if (GuiItemUtils.isEmpty(slotItem)) {
            return new ClickResult(cursorItem.clone(), null, true);
        }

        // 两者都有物品
        if (GuiItemUtils.canStackWith(slotItem, cursorItem)) {
            // 相似物品，尝试堆叠
            GuiItemUtils.AddResult result = GuiItemUtils.addItem(slotItem, cursorItem);
            return new ClickResult(result.newSlotItem(), result.remaining(), true);
        } else {
            // 不同物品，交换
            GuiItemUtils.SwapResult swap = GuiItemUtils.swap(slotItem, cursorItem);
            return new ClickResult(swap.newA(), swap.newB(), true);
        }
    }

    /**
     * 处理右键点击
     * <p>
     * 逻辑：
     * - 光标空 + 槽位有物品 → 拾取一半（向上取整）
     * - 光标有物品 + 槽位空 → 放置1个
     * - 相似物品 → 增加1个到槽位
     * - 不同物品 → 交换
     * - Bundle 特殊处理（MC 1.21+）：取出第一个物品
     * 
     * @param player      玩家
     * @param slotItem    槽位物品
     * @param cursorItem  光标物品
     * @return 点击结果
     */
    @NotNull
    public static ClickResult handleRightClick(
            @NotNull Player player,
            @Nullable ItemStack slotItem,
            @Nullable ItemStack cursorItem
    ) {
        // Bundle 特殊处理：光标空 + 槽位是 Bundle → 取出第一个物品
        if (BundleUtils.isBundleSupported() && BundleUtils.isBundle(slotItem) && GuiItemUtils.isEmpty(cursorItem)) {
            BundleUtils.ExtractResult extractResult = BundleUtils.extractFromBundle(slotItem);
            if (extractResult.wasExtracted()) {
                return new ClickResult(extractResult.newBundle(), extractResult.extracted(), true);
            }
            // Bundle 为空，拾取一半（实际上只有1个Bundle）
            return new ClickResult(null, slotItem.clone(), true);
        }

        // 光标为空，拾取一半
        if (GuiItemUtils.isEmpty(cursorItem)) {
            if (!GuiItemUtils.isEmpty(slotItem)) {
                GuiItemUtils.RemoveResult result = GuiItemUtils.pickupHalf(slotItem);
                return new ClickResult(result.newSlotItem(), result.removed(), true);
            }
            return ClickResult.noChange();
        }

        // 光标有物品，槽位为空，放置1个
        if (GuiItemUtils.isEmpty(slotItem)) {
            ItemStack newSlot = cursorItem.clone();
            newSlot.setAmount(1);
            ItemStack newCursor = cursorItem.clone();
            newCursor.setAmount(cursorItem.getAmount() - 1);
            return new ClickResult(newSlot, GuiItemUtils.isEmpty(newCursor) ? null : newCursor, true);
        }

        // 两者都有物品
        if (GuiItemUtils.canStackWith(slotItem, cursorItem)) {
            // 相似物品，增加1个到槽位
            int currentAmount = slotItem.getAmount();
            int maxStack = slotItem.getMaxStackSize();

            if (currentAmount < maxStack) {
                ItemStack newSlot = slotItem.clone();
                newSlot.setAmount(currentAmount + 1);
                ItemStack newCursor = cursorItem.clone();
                newCursor.setAmount(cursorItem.getAmount() - 1);
                return new ClickResult(newSlot, GuiItemUtils.isEmpty(newCursor) ? null : newCursor, true);
            } else {
                // 已满，不变
                return ClickResult.noChange();
            }
        } else {
            // 不同物品，交换
            GuiItemUtils.SwapResult swap = GuiItemUtils.swap(slotItem, cursorItem);
            return new ClickResult(swap.newA(), swap.newB(), true);
        }
    }

    /**
     * 处理 Shift+点击（快速移动到另一个背包）- 带优先级支持
     * <p>
     * 逻辑：
     * 1. 按优先级排序槽位
     * 2. 优先填充高优先级槽位的已有堆叠
     * 3. 然后填充高优先级的空槽位
     * 
     * @param slotItem          槽位物品
     * @param targetInventory   目标背包
     * @param startSlot         起始槽位
     * @param endSlot           结束槽位（不包含）
     * @param priority          优先级策略（可选，null 则使用默认策略）
     * @return Shift点击结果
     */
    @NotNull
    public static ShiftClickResult handleShiftClickWithPriority(
            @Nullable ItemStack slotItem,
            @NotNull Inventory targetInventory,
            int startSlot,
            int endSlot,
            @Nullable SlotPriority priority
    ) {
        if (GuiItemUtils.isEmpty(slotItem)) {
            return new ShiftClickResult(null, false);
        }

        // 如果没有优先级策略，使用普通模式
        if (priority == null) {
            return handleShiftClick(slotItem, targetInventory, startSlot, endSlot);
        }

        ItemStack remaining = slotItem.clone();

        // 构建优先级排序的槽位列表
        java.util.List<PrioritizedSlot> slots = new java.util.ArrayList<>();
        for (int i = startSlot; i < endSlot; i++) {
            int slotPriority = priority.getPriority(i, remaining);
            if (slotPriority > SlotPriority.PRIORITY_NONE) {
                slots.add(new PrioritizedSlot(i, slotPriority));
            }
        }

        // 按优先级降序排序
        slots.sort((a, b) -> Integer.compare(b.priority, a.priority));

        // 第一阶段：填充已有的相似堆叠（按优先级）
        for (PrioritizedSlot ps : slots) {
            if (GuiItemUtils.isEmpty(remaining)) break;
            
            ItemStack targetItem = targetInventory.getItem(ps.slot);
            if (!GuiItemUtils.isEmpty(targetItem) && GuiItemUtils.canStackWith(targetItem, remaining)) {
                GuiItemUtils.AddResult result = GuiItemUtils.addItem(targetItem, remaining);
                targetInventory.setItem(ps.slot, result.newSlotItem());
                remaining = result.remaining();
            }
        }

        // 第二阶段：填充空槽位（按优先级）
        for (PrioritizedSlot ps : slots) {
            if (GuiItemUtils.isEmpty(remaining)) break;
            
            if (GuiItemUtils.isEmpty(targetInventory.getItem(ps.slot))) {
                targetInventory.setItem(ps.slot, remaining.clone());
                remaining = null;
                break;
            }
        }

        boolean moved = remaining == null || remaining.getAmount() < slotItem.getAmount();
        return new ShiftClickResult(remaining, moved);
    }

    /**
     * 处理 Shift+点击（快速移动到另一个背包）- 简化版本
     * <p>
     * 逻辑：
     * 1. 优先填充已有的相似堆叠
     * 2. 然后寻找空槽位
     * 
     * @param slotItem          槽位物品
     * @param targetInventory   目标背包
     * @param startSlot         起始槽位
     * @param endSlot           结束槽位（不包含）
     * @return Shift点击结果
     */
    @NotNull
    public static ShiftClickResult handleShiftClick(
            @Nullable ItemStack slotItem,
            @NotNull Inventory targetInventory,
            int startSlot,
            int endSlot
    ) {
        if (GuiItemUtils.isEmpty(slotItem)) {
            return new ShiftClickResult(null, false);
        }

        ItemStack remaining = slotItem.clone();

        // 第一阶段：填充已有的相似堆叠
        for (int i = startSlot; i < endSlot && !GuiItemUtils.isEmpty(remaining); i++) {
            ItemStack targetItem = targetInventory.getItem(i);
            if (!GuiItemUtils.isEmpty(targetItem) && GuiItemUtils.canStackWith(targetItem, remaining)) {
                GuiItemUtils.AddResult result = GuiItemUtils.addItem(targetItem, remaining);
                targetInventory.setItem(i, result.newSlotItem());
                remaining = result.remaining();
            }
        }

        // 第二阶段：寻找空槽位
        for (int i = startSlot; i < endSlot && !GuiItemUtils.isEmpty(remaining); i++) {
            if (GuiItemUtils.isEmpty(targetInventory.getItem(i))) {
                targetInventory.setItem(i, remaining.clone());
                remaining = null;
                break;
            }
        }

        boolean moved = remaining == null || remaining.getAmount() < slotItem.getAmount();
        return new ShiftClickResult(remaining, moved);
    }

    /**
     * 处理数字键（1-9）快捷栏交换
     * <p>
     * 交换点击的槽位与快捷栏对应槽位的物品
     * 
     * @param slotItem        槽位物品
     * @param hotbarSlot      快捷栏槽位（0-8）
     * @param playerInventory 玩家背包
     * @return 数字键结果
     */
    @NotNull
    public static HotbarSwapResult handleHotbarSwap(
            @Nullable ItemStack slotItem,
            int hotbarSlot,
            @NotNull PlayerInventory playerInventory
    ) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return new HotbarSwapResult(slotItem, false);
        }

        ItemStack hotbarItem = playerInventory.getItem(hotbarSlot);
        GuiItemUtils.SwapResult swap = GuiItemUtils.swap(slotItem, hotbarItem);

        playerInventory.setItem(hotbarSlot, swap.newB());
        return new HotbarSwapResult(swap.newA(), true);
    }

    /**
     * 处理双击收集相似物品
     * <p>
     * 从所有可访问的背包中收集与光标相似的物品到光标
     * 
     * @param cursorItem      光标物品（作为模板）
     * @param sourceInventory 源背包（GUI 或玩家背包）
     * @param startSlot       起始槽位
     * @param endSlot         结束槽位（不包含）
     * @return 双击结果
     */
    @NotNull
    public static DoubleClickResult handleDoubleClick(
            @Nullable ItemStack cursorItem,
            @NotNull Inventory sourceInventory,
            int startSlot,
            int endSlot
    ) {
        if (GuiItemUtils.isEmpty(cursorItem)) {
            return new DoubleClickResult(null, false);
        }

        ItemStack collected = cursorItem.clone();
        int maxStack = collected.getMaxStackSize();
        boolean changed = false;

        // 遍历背包收集相似物品
        for (int i = startSlot; i < endSlot && collected.getAmount() < maxStack; i++) {
            ItemStack sourceItem = sourceInventory.getItem(i);
            if (!GuiItemUtils.isEmpty(sourceItem) && GuiItemUtils.canStackWith(sourceItem, collected)) {
                int needed = maxStack - collected.getAmount();
                int available = sourceItem.getAmount();
                int toTake = Math.min(needed, available);

                collected.setAmount(collected.getAmount() + toTake);

                if (toTake >= available) {
                    sourceInventory.setItem(i, null);
                } else {
                    sourceItem.setAmount(available - toTake);
                    sourceInventory.setItem(i, sourceItem);
                }

                changed = true;
            }
        }

        return new DoubleClickResult(collected, changed);
    }

    /**
     * 处理中键点击（创造模式克隆）
     * <p>
     * 仅在创造模式下，克隆槽位物品到光标（满堆叠）
     * 
     * @param player   玩家
     * @param slotItem 槽位物品
     * @return 中键点击结果
     */
    @NotNull
    public static MiddleClickResult handleMiddleClick(
            @NotNull Player player,
            @Nullable ItemStack slotItem
    ) {
        if (player.getGameMode() != GameMode.CREATIVE || GuiItemUtils.isEmpty(slotItem)) {
            return new MiddleClickResult(null, false);
        }

        ItemStack cloned = slotItem.clone();
        cloned.setAmount(cloned.getMaxStackSize());
        return new MiddleClickResult(cloned, true);
    }

    /**
     * 处理副手交换（F键）
     * <p>
     * 交换槽位物品与玩家副手物品
     * 
     * @param slotItem        槽位物品
     * @param playerInventory 玩家背包
     * @return 副手交换结果
     */
    @NotNull
    public static OffhandSwapResult handleOffhandSwap(
            @Nullable ItemStack slotItem,
            @NotNull PlayerInventory playerInventory
    ) {
        ItemStack offhandItem = playerInventory.getItemInOffHand();
        GuiItemUtils.SwapResult swap = GuiItemUtils.swap(slotItem, offhandItem);

        playerInventory.setItemInOffHand(swap.newB());
        return new OffhandSwapResult(swap.newA(), true);
    }

    /**
     * 处理丢弃物品（Q键或Ctrl+Q）
     * <p>
     * Q键丢弃1个，Ctrl+Q丢弃全部
     * 
     * @param slotItem   槽位物品
     * @param dropAll    是否丢弃全部
     * @param player     玩家
     * @return 丢弃结果
     */
    @NotNull
    public static DropResult handleDrop(
            @Nullable ItemStack slotItem,
            boolean dropAll,
            @NotNull Player player
    ) {
        if (GuiItemUtils.isEmpty(slotItem)) {
            return new DropResult(null, false);
        }

        int amount = dropAll ? slotItem.getAmount() : 1;
        GuiItemUtils.RemoveResult result = GuiItemUtils.removeItem(slotItem, amount);

        if (result.wasRemoved()) {
            // 在玩家位置丢弃物品
            player.getWorld().dropItemNaturally(player.getLocation(), result.removed());
            return new DropResult(result.newSlotItem(), true);
        }

        return new DropResult(slotItem, false);
    }

    // ==================== 结果类 ====================

    /**
     * 点击结果（左键/右键）
     */
    public record ClickResult(
            @Nullable ItemStack newSlotItem,
            @Nullable ItemStack newCursorItem,
            boolean changed
    ) {
        public static ClickResult noChange() {
            return new ClickResult(null, null, false);
        }
    }

    /**
     * Shift点击结果
     */
    public record ShiftClickResult(
            @Nullable ItemStack remaining,
            boolean moved
    ) {
    }

    /**
     * 数字键结果
     */
    public record HotbarSwapResult(
            @Nullable ItemStack newSlotItem,
            boolean swapped
    ) {
    }

    /**
     * 双击结果
     */
    public record DoubleClickResult(
            @Nullable ItemStack newCursorItem,
            boolean collected
    ) {
    }

    /**
     * 中键点击结果
     */
    public record MiddleClickResult(
            @Nullable ItemStack newCursorItem,
            boolean cloned
    ) {
    }

    /**
     * 副手交换结果
     */
    public record OffhandSwapResult(
            @Nullable ItemStack newSlotItem,
            boolean swapped
    ) {
    }

    /**
     * 丢弃结果
     */
    public record DropResult(
            @Nullable ItemStack newSlotItem,
            boolean dropped
    ) {
    }
}

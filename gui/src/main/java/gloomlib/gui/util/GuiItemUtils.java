package gloomlib.gui.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GUI 物品操作工具类
 * <p>
 * 提供物品堆叠、添加、移除等常用操作，用于交互处理器复用
 * 
 * @author GloomLib
 * @since 2.0
 */
public final class GuiItemUtils {

    private GuiItemUtils() {
    }

    /**
     * 判断物品是否为空
     */
    public static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    /**
     * 判断两个物品是否可以堆叠
     */
    public static boolean canStackWith(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (isEmpty(a) || isEmpty(b)) {
            return false;
        }
        return a.isSimilar(b);
    }

    /**
     * 获取物品的最大堆叠数量
     */
    public static int getMaxStackSize(@NotNull ItemStack item) {
        return item.getMaxStackSize();
    }

    /**
     * 创建空物品（用于清空槽位）
     */
    @NotNull
    public static ItemStack createEmpty() {
        return new ItemStack(Material.AIR);
    }

    /**
     * 安全克隆物品（处理 null）
     */
    @Nullable
    public static ItemStack cloneSafe(@Nullable ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    /**
     * 尝试向槽位添加物品
     * 
     * @param slotItem 槽位当前物品
     * @param toAdd    要添加的物品
     * @return 添加结果 [新的槽位物品, 剩余物品]
     */
    @NotNull
    public static AddResult addItem(@Nullable ItemStack slotItem, @NotNull ItemStack toAdd) {
        if (isEmpty(toAdd)) {
            return new AddResult(slotItem, null);
        }

        // 槽位为空，直接放置
        if (isEmpty(slotItem)) {
            return new AddResult(toAdd.clone(), null);
        }

        // 物品不可堆叠，无法添加
        if (!canStackWith(slotItem, toAdd)) {
            return new AddResult(slotItem, toAdd);
        }

        int maxStack = getMaxStackSize(slotItem);
        int currentAmount = slotItem.getAmount();
        int toAddAmount = toAdd.getAmount();
        int totalAmount = currentAmount + toAddAmount;

        if (totalAmount <= maxStack) {
            // 可以完全堆叠
            ItemStack newSlot = slotItem.clone();
            newSlot.setAmount(totalAmount);
            return new AddResult(newSlot, null);
        } else {
            // 部分堆叠
            ItemStack newSlot = slotItem.clone();
            newSlot.setAmount(maxStack);
            ItemStack remaining = toAdd.clone();
            remaining.setAmount(totalAmount - maxStack);
            return new AddResult(newSlot, remaining);
        }
    }

    /**
     * 从槽位移除物品
     * 
     * @param slotItem 槽位当前物品
     * @param amount   要移除的数量
     * @return 移除结果 [新的槽位物品, 移除的物品]
     */
    @NotNull
    public static RemoveResult removeItem(@Nullable ItemStack slotItem, int amount) {
        if (isEmpty(slotItem) || amount <= 0) {
            return new RemoveResult(slotItem, null);
        }

        int currentAmount = slotItem.getAmount();

        if (amount >= currentAmount) {
            // 全部移除
            ItemStack removed = slotItem.clone();
            return new RemoveResult(null, removed);
        } else {
            // 部分移除
            ItemStack newSlot = slotItem.clone();
            newSlot.setAmount(currentAmount - amount);
            ItemStack removed = slotItem.clone();
            removed.setAmount(amount);
            return new RemoveResult(newSlot, removed);
        }
    }

    /**
     * 拾取一半物品（向上取整）
     */
    @NotNull
    public static RemoveResult pickupHalf(@Nullable ItemStack slotItem) {
        if (isEmpty(slotItem)) {
            return new RemoveResult(null, null);
        }

        int amount = slotItem.getAmount();
        int half = (amount + 1) / 2; // 向上取整

        return removeItem(slotItem, half);
    }

    /**
     * 交换两个物品
     */
    @NotNull
    public static SwapResult swap(@Nullable ItemStack a, @Nullable ItemStack b) {
        return new SwapResult(cloneSafe(b), cloneSafe(a));
    }

    /**
     * 尝试在背包中找到第一个可以堆叠的槽位
     * 
     * @param inventory 背包
     * @param item      物品
     * @param start     起始槽位
     * @param end       结束槽位（不包含）
     * @return 槽位索引，未找到返回 -1
     */
    public static int findFirstStackableSlot(@NotNull Inventory inventory, @NotNull ItemStack item, int start, int end) {
        if (isEmpty(item)) {
            return -1;
        }

        for (int i = start; i < end; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!isEmpty(slotItem) && canStackWith(slotItem, item) && slotItem.getAmount() < slotItem.getMaxStackSize()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 尝试在背包中找到第一个空槽位
     * 
     * @param inventory 背包
     * @param start     起始槽位
     * @param end       结束槽位（不包含）
     * @return 槽位索引，未找到返回 -1
     */
    public static int findFirstEmptySlot(@NotNull Inventory inventory, int start, int end) {
        for (int i = start; i < end; i++) {
            if (isEmpty(inventory.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 添加结果
     */
    public record AddResult(@Nullable ItemStack newSlotItem, @Nullable ItemStack remaining) {
        public boolean hasRemaining() {
            return !isEmpty(remaining);
        }
    }

    /**
     * 移除结果
     */
    public record RemoveResult(@Nullable ItemStack newSlotItem, @Nullable ItemStack removed) {
        public boolean wasRemoved() {
            return !isEmpty(removed);
        }
    }

    /**
     * 交换结果
     */
    public record SwapResult(@Nullable ItemStack newA, @Nullable ItemStack newB) {
    }
}

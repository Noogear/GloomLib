package gloomlib.gui.click;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * GUI 拖拽处理器
 * <p>
 * 实现三种拖拽模式：
 * <ul>
 *   <li><b>LEFT</b>: 平均分配物品到所有槽位</li>
 *   <li><b>RIGHT</b>: 每个槽位放置一个物品</li>
 *   <li><b>MIDDLE</b>: 每个槽位放置完整堆叠（仅创造模式）</li>
 * </ul>
 * <p>
 * 参考实现：
 * <ul>
 *   <li>InvUI: AbstractWindow.java#handleDrag 和 distributeItems 方法</li>
 *   <li>Minecraft 原版拖拽行为</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 2.0
 * @see <a href=\"https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/window/AbstractWindow.java#L380-L550\">InvUI AbstractWindow.java</a>
 */
public final class DragHandler {
    
    private DragHandler() {}
    
    /**
     * 拖拽结果
     */
    public record DragResult(
            @NotNull ItemStack remaining,
            @NotNull Map<Integer, ItemStack> updatedSlots
    ) {}
    
    /**
     * 处理拖拽操作
     * 
     * @param dragType   拖拽类型（LEFT/RIGHT/MIDDLE）
     * @param draggedItem 被拖拽的物品
     * @param slots       被拖拽到的槽位列表
     * @param getSlotItem 获取槽位物品的函数
     * @return 拖拽结果
     */
    @NotNull
    public static DragResult handleDrag(
            @NotNull GuiClickType dragType,
            @NotNull ItemStack draggedItem,
            @NotNull Set<Integer> slots,
            @NotNull java.util.function.IntFunction<ItemStack> getSlotItem
    ) {
        if (!dragType.isDrag()) {
            throw new IllegalArgumentException("Not a drag type: " + dragType);
        }
        
        if (draggedItem.isEmpty() || slots.isEmpty()) {
            return new DragResult(draggedItem, Map.of());
        }
        
        return switch (dragType) {
            case DRAG_LEFT -> distributeLeft(draggedItem, slots, getSlotItem);
            case DRAG_RIGHT -> distributeRight(draggedItem, slots, getSlotItem);
            case DRAG_MIDDLE -> distributeMiddle(draggedItem, slots, getSlotItem);
            default -> new DragResult(draggedItem, Map.of());
        };
    }
    
    /**
     * 左键拖拽：平均分配
     * <p>
     * 算法：
     * 1. 计算每个槽位应该分配的物品数量（总数 / 槽位数）
     * 2. 按顺序填充每个槽位，直到达到目标数量或物品耗尽
     * 3. 剩余物品返回光标
     */
    private static DragResult distributeLeft(
            ItemStack draggedItem,
            Set<Integer> slots,
            java.util.function.IntFunction<ItemStack> getSlotItem
    ) {
        Map<Integer, ItemStack> updated = new HashMap<>();
        int totalAmount = draggedItem.getAmount();
        int slotCount = slots.size();
        int perSlot = Math.max(1, totalAmount / slotCount);
        int remaining = totalAmount;
        
        for (int slot : slots) {
            if (remaining <= 0) break;
            
            ItemStack current = getSlotItem.apply(slot);
            int toAdd = Math.min(perSlot, remaining);
            
            if (current == null || current.getType() == Material.AIR) {
                // 空槽位：放置新物品
                ItemStack newStack = draggedItem.clone();
                newStack.setAmount(toAdd);
                updated.put(slot, newStack);
                remaining -= toAdd;
            } else if (current.isSimilar(draggedItem)) {
                // 已有相同物品：尝试堆叠
                int maxStack = current.getMaxStackSize();
                int currentAmount = current.getAmount();
                int canAdd = Math.min(toAdd, maxStack - currentAmount);
                
                if (canAdd > 0) {
                    ItemStack newStack = current.clone();
                    newStack.setAmount(currentAmount + canAdd);
                    updated.put(slot, newStack);
                    remaining -= canAdd;
                }
            }
        }
        
        ItemStack remainingStack = draggedItem.clone();
        remainingStack.setAmount(remaining);
        
        return new DragResult(remainingStack, updated);
    }
    
    /**
     * 右键拖拽：每个槽位放一个
     * <p>
     * 算法：
     * 1. 遍历所有槽位
     * 2. 每个槽位尝试放置一个物品
     * 3. 直到物品耗尽
     */
    private static DragResult distributeRight(
            ItemStack draggedItem,
            Set<Integer> slots,
            java.util.function.IntFunction<ItemStack> getSlotItem
    ) {
        Map<Integer, ItemStack> updated = new HashMap<>();
        int remaining = draggedItem.getAmount();
        
        for (int slot : slots) {
            if (remaining <= 0) break;
            
            ItemStack current = getSlotItem.apply(slot);
            
            if (current == null || current.getType() == Material.AIR) {
                // 空槽位：放置一个物品
                ItemStack newStack = draggedItem.clone();
                newStack.setAmount(1);
                updated.put(slot, newStack);
                remaining--;
            } else if (current.isSimilar(draggedItem)) {
                // 已有相同物品：尝试堆叠一个
                int maxStack = current.getMaxStackSize();
                int currentAmount = current.getAmount();
                
                if (currentAmount < maxStack) {
                    ItemStack newStack = current.clone();
                    newStack.setAmount(currentAmount + 1);
                    updated.put(slot, newStack);
                    remaining--;
                }
            }
        }
        
        ItemStack remainingStack = draggedItem.clone();
        remainingStack.setAmount(remaining);
        
        return new DragResult(remainingStack, updated);
    }
    
    /**
     * 中键拖拽：完整堆叠（创造模式）
     * <p>
     * 算法：
     * 1. 遍历所有槽位
     * 2. 每个槽位放置一个完整堆叠（64 或物品最大堆叠数）
     * 3. 创造模式下物品不会减少
     */
    private static DragResult distributeMiddle(
            ItemStack draggedItem,
            Set<Integer> slots,
            java.util.function.IntFunction<ItemStack> getSlotItem
    ) {
        Map<Integer, ItemStack> updated = new HashMap<>();
        int maxStack = draggedItem.getMaxStackSize();
        
        for (int slot : slots) {
            ItemStack current = getSlotItem.apply(slot);
            
            if (current == null || current.getType() == Material.AIR) {
                // 空槽位：放置完整堆叠
                ItemStack newStack = draggedItem.clone();
                newStack.setAmount(maxStack);
                updated.put(slot, newStack);
            } else if (current.isSimilar(draggedItem)) {
                // 已有相同物品：设置为最大堆叠
                ItemStack newStack = current.clone();
                newStack.setAmount(maxStack);
                updated.put(slot, newStack);
            }
        }
        
        // 创造模式拖拽不消耗物品
        return new DragResult(draggedItem, updated);
    }
    
    /**
     * 验证拖拽是否合法
     */
    public static boolean isValidDrag(
            @NotNull GuiClickType dragType,
            @NotNull ItemStack draggedItem,
            @NotNull Set<Integer> slots
    ) {
        if (!dragType.isDrag()) {
            return false;
        }
        
        if (draggedItem.isEmpty()) {
            return false;
        }
        
        if (slots.isEmpty()) {
            return false;
        }
        
        // 中键拖拽仅在创造模式有效（由调用方检查）
        return dragType != GuiClickType.DRAG_MIDDLE;
    }
    
    /**
     * 计算拖拽预览（不实际修改）
     */
    @NotNull
    public static Map<Integer, Integer> calculateDragPreview(
            @NotNull GuiClickType dragType,
            int totalAmount,
            @NotNull Set<Integer> slots
    ) {
        if (!dragType.isDrag() || totalAmount <= 0 || slots.isEmpty()) {
            return Map.of();
        }
        
        Map<Integer, Integer> preview = new HashMap<>();
        
        return switch (dragType) {
            case DRAG_LEFT -> {
                int perSlot = Math.max(1, totalAmount / slots.size());
                int remaining = totalAmount;
                for (int slot : slots) {
                    int amount = Math.min(perSlot, remaining);
                    preview.put(slot, amount);
                    remaining -= amount;
                    if (remaining <= 0) break;
                }
                yield preview;
            }
            case DRAG_RIGHT -> {
                int remaining = totalAmount;
                for (int slot : slots) {
                    preview.put(slot, 1);
                    remaining--;
                    if (remaining <= 0) break;
                }
                yield preview;
            }
            case DRAG_MIDDLE -> {
                // 创造模式：每个槽位都是满堆叠
                for (int slot : slots) {
                    preview.put(slot, 64); // 简化为 64
                }
                yield preview;
            }
            default -> Map.of();
        };
    }
}

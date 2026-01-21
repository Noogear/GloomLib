package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 背包链接组件 - 将真实背包的槽位映射到 GUI 中
 * <p>
 * 此组件允许将玩家背包、箱子或其他容器的槽位直接嵌入到 GUI 中，
 * 玩家可以像操作普通背包一样与这些槽位交互。
 * <p>
 * 参考：InvUI SlotElement.InventoryLink 实现
 * {@link <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/gui/SlotElement.java">InvUI SlotElement.java</a>}
 * 
 * @author GloomLib
 * @since 2.0
 */
public class InventoryLinkComponent implements GloomComponent {

    private final Inventory linkedInventory;
    private final int linkedSlot;
    private final boolean allowInteraction;

    /**
     * 创建背包链接组件
     * 
     * @param linkedInventory  要链接的背包
     * @param linkedSlot       要链接的槽位索引
     * @param allowInteraction 是否允许交互（如果为 false，则只读）
     */
    public InventoryLinkComponent(Inventory linkedInventory, int linkedSlot, boolean allowInteraction) {
        if (linkedInventory == null) {
            throw new IllegalArgumentException("链接的背包不能为 null");
        }
        if (linkedSlot < 0 || linkedSlot >= linkedInventory.getSize()) {
            throw new IllegalArgumentException("槽位索引超出范围: " + linkedSlot);
        }
        
        this.linkedInventory = linkedInventory;
        this.linkedSlot = linkedSlot;
        this.allowInteraction = allowInteraction;
    }

    /**
     * 创建可交互的背包链接组件
     * 
     * @param linkedInventory 要链接的背包
     * @param linkedSlot      要链接的槽位索引
     */
    public InventoryLinkComponent(Inventory linkedInventory, int linkedSlot) {
        this(linkedInventory, linkedSlot, true);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        ItemStack item = linkedInventory.getItem(linkedSlot);
        return item != null ? item : new ItemStack(Material.AIR);
    }

    @Override
    public void onClick(InteractionContext context) {
        // 背包链接组件的点击处理由 GUI 系统的特殊逻辑处理
        // 这里保持空实现，实际的物品交换由 Bukkit 的背包系统处理
        // 如果不允许交互，会在 GUI 层面被阻止
    }

    @Override
    public boolean onTick() {
        // 背包链接组件需要每 tick 检查物品是否变化
        // 返回 true 表示需要重新渲染
        return true;
    }

    @Override
    public int getTickRate() {
        // 每 5 ticks 更新一次以减少性能开销
        // 如果需要实时更新，可以改为 1
        return 5;
    }

    /**
     * 获取链接的背包
     * 
     * @return 链接的背包实例
     */
    public Inventory getLinkedInventory() {
        return linkedInventory;
    }

    /**
     * 获取链接的槽位索引
     * 
     * @return 槽位索引
     */
    public int getLinkedSlot() {
        return linkedSlot;
    }

    /**
     * 是否允许交互
     * 
     * @return 如果允许交互返回 true
     */
    public boolean isAllowInteraction() {
        return allowInteraction;
    }

    /**
     * 检查此组件是否为背包链接组件
     * 
     * @param component 要检查的组件
     * @return 如果是背包链接组件返回 true
     */
    public static boolean isInventoryLink(GloomComponent component) {
        return component instanceof InventoryLinkComponent;
    }

    @Override
    public void dispose() {
        // 背包链接不需要特殊清理
    }

    @Override
    public GloomComponent clone() {
        // 克隆时保持对同一背包和槽位的引用
        return new InventoryLinkComponent(linkedInventory, linkedSlot, allowInteraction);
    }
}

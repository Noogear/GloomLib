package gloomlib.gui.slot;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 槽位元素封装 - 统一的槽位内容抽象
 * <p>
 * SlotElement 使用 Java 17+ 的 sealed 接口特性，提供三种槽位内容类型：
 * <ul>
 *   <li>{@link ComponentSlot} - 普通组件槽位</li>
 *   <li>{@link GuiLink} - GUI 嵌套链接</li>
 *   <li>{@link InventoryLink} - 背包槽位链接</li>
 * </ul>
 * 
 * 设计参考：InvUI 2.x 的 SlotElement
 * {@link <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/gui/SlotElement.java">InvUI SlotElement.java</a>}
 * 
 * <h3>典型用法</h3>
 * <pre>{@code
 * // 组件槽位
 * SlotElement component = new ComponentSlot(myComponent, 0);
 * 
 * // GUI 嵌套
 * SlotElement nestedGui = new GuiLink(childGui, 5);
 * 
 * // 背包链接（带背景）
 * SlotElement inventory = new InventoryLink(
 *     playerInventory, 
 *     9, 
 *     new ItemStack(Material.GRAY_STAINED_GLASS_PANE)
 * );
 * }</pre>
 * 
 * @author GloomLib
 * @since 3.0
 */
public sealed interface SlotElement permits 
    SlotElement.ComponentSlot, 
    SlotElement.GuiLink, 
    SlotElement.InventoryLink {

    /**
     * 渲染此槽位元素为物品
     * 
     * @return 渲染后的物品，null 表示空槽位
     */
    @Nullable
    ItemStack render();

    /**
     * 普通组件槽位
     * 
     * @param component 组件实例
     * @param index     组件内部索引
     */
    record ComponentSlot(
            @NotNull GloomComponent component,
            int index
    ) implements SlotElement {

        @Override
        public @Nullable ItemStack render() {
            return component.render(index);
        }
    }

    /**
     * GUI 嵌套链接
     * <p>
     * 允许将一个 GUI 的槽位显示在另一个 GUI 中，
     * 实现子菜单、弹出窗口等功能。
     * 
     * @param gui  被链接的 GUI
     * @param slot 被链接的槽位索引
     */
    record GuiLink(
            @NotNull GloomGui gui,
            int slot
    ) implements SlotElement {

        @Override
        public @Nullable ItemStack render() {
            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                return component.render(gui.getComponentIndex(slot));
            }
            // 如果没有组件，使用 GUI 的背景物品
            return gui.getBackground().get();
        }
    }

    /**
     * 背包槽位链接
     * <p>
     * 直接映射到真实背包的槽位，允许玩家与其交互。
     * 如果槽位为空，显示背景物品。
     * 
     * @param inventory  被链接的背包
     * @param slot       背包槽位索引
     * @param background 背景物品（槽位为空时显示），null 表示不显示背景
     */
    record InventoryLink(
            @NotNull Inventory inventory,
            int slot,
            @Nullable ItemStack background
    ) implements SlotElement {

        /**
         * 无背景的背包链接
         */
        public InventoryLink(@NotNull Inventory inventory, int slot) {
            this(inventory, slot, null);
        }

        @Override
        public @Nullable ItemStack render() {
            ItemStack item = inventory.getItem(slot);
            return (item != null && !item.getType().isAir()) ? item : background;
        }
    }
}

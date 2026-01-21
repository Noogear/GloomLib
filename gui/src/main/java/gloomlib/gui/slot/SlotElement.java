package gloomlib.gui.slot;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.observable.Observable;
import gloomlib.gui.observable.Observer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 槽位元素抽象
 * <p>
 * 定义了三种槽位元素类型：组件、GUI 链接、库存链接。
 * 使用 Java 21 的 sealed interface + records 确保类型安全。
 * <p>
 * <b>参考实现</b>：
 * <ul>
 *   <li>InvUI: {@code invui/src/main/java/xyz/xenondevs/invui/gui/SlotElement.java}</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 2.0
 * @see <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/gui/SlotElement.java">InvUI SlotElement.java</a>
 */
public sealed interface SlotElement permits
        SlotElement.ComponentSlot,
        SlotElement.GuiLink,
        SlotElement.InventoryLink {

    /**
     * 渲染槽位内容为 ItemStack
     * 
     * @param player 查看的玩家（支持玩家特定的渲染）
     * @return 渲染的物品，null 表示空槽位
     */
    @Nullable
    ItemStack render(@Nullable Player player);
    
    /**
     * 获取该槽位元素的持有者（最终的 SlotElement）
     * <p>
     * 对于嵌套的 GuiLink，会递归查找到最终的持有元素。
     * 
     * @return 持有该槽位的元素
     */
    @NotNull
    default SlotElement getHoldingElement() {
        return this;
    }
    
    /**
     * 遍历元素链（用于嵌套 GUI）
     * <p>
     * 返回从当前元素到最终持有元素的完整路径。
     * 
     * @return 元素链列表
     */
    @NotNull
    default List<SlotElement> traverse() {
        List<SlotElement> path = new ArrayList<>();
        path.add(this);
        return path;
    }

    /**
     * 组件槽位
     * <p>
     * 包装一个 GloomComponent，支持其 Observable 特性。
     * 
     * @param component 组件实例
     * @param index     组件内部索引
     */
    record ComponentSlot(
            @NotNull GloomComponent component,
            int index
    ) implements SlotElement {

        @Override
        public @Nullable ItemStack render(@Nullable Player player) {
            return component.render(index);
        }
        
        /**
         * 如果组件实现了 Observable，可以注册观察者
         */
        public void observe(@NotNull Observer observer, int how) {
            if (component instanceof Observable observable) {
                observable.addObserver(observer, index, how);
            }
        }
        
        /**
         * 移除观察者
         */
        public void unobserve(@NotNull Observer observer, int how) {
            if (component instanceof Observable observable) {
                observable.removeObserver(observer, index, how);
            }
        }
    }

    /**
     * GUI 链接槽位
     * <p>
     * 指向另一个 GUI 的特定槽位，支持无限层级的嵌套。
     * 这是实现嵌套 GUI 的关键机制。
     * 
     * @param gui  目标 GUI
     * @param slot 目标槽位索引
     */
    record GuiLink(
            @NotNull GloomGui gui,
            int slot
    ) implements SlotElement {

        @Override
        public @Nullable ItemStack render(@Nullable Player player) {
            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                return component.render(gui.getComponentIndex(slot));
            }
            // 回退到背景物品
            ItemStack background = gui.getBackground();
            return background != null ? background : null;
        }
        
        @Override
        public @NotNull SlotElement getHoldingElement() {
            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                // 递归获取持有元素
                SlotElement element = new ComponentSlot(component, gui.getComponentIndex(slot));
                return element.getHoldingElement();
            }
            return this;
        }
        
        @Override
        public @NotNull List<SlotElement> traverse() {
            List<SlotElement> path = new ArrayList<>();
            path.add(this);
            
            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                SlotElement element = new ComponentSlot(component, gui.getComponentIndex(slot));
                path.addAll(element.traverse());
            }
            
            return path;
        }
        
        /**
         * 注册观察者到整个链
         */
        public void observeChain(@NotNull Observer observer, int how) {
            List<SlotElement> chain = traverse();
            for (SlotElement element : chain) {
                if (element instanceof ComponentSlot componentSlot) {
                    componentSlot.observe(observer, how);
                }
            }
        }
        
        /**
         * 从整个链移除观察者
         */
        public void unobserveChain(@NotNull Observer observer, int how) {
            List<SlotElement> chain = traverse();
            for (SlotElement element : chain) {
                if (element instanceof ComponentSlot componentSlot) {
                    componentSlot.unobserve(observer, how);
                }
            }
        }
    }

    /**
     * 库存链接槽位
     * <p>
     * 直接链接到一个 Bukkit Inventory 的特定槽位。
     * 当库存槽位为空时，显示背景物品。
     * <p>
     * 用途：
     * <ul>
     *   <li>嵌入玩家库存</li>
     *   <li>显示箱子内容</li>
     *   <li>虚拟库存连接</li>
     * </ul>
     * 
     * @param inventory  目标库存
     * @param slot       槽位索引
     * @param background 背景物品（槽位为空时显示）
     */
    record InventoryLink(
            @NotNull Inventory inventory,
            int slot,
            @Nullable ItemStack background
    ) implements SlotElement {

        /**
         * 创建不带背景的库存链接
         */
        public InventoryLink(@NotNull Inventory inventory, int slot) {
            this(inventory, slot, null);
        }

        @Override
        public @Nullable ItemStack render(@Nullable Player player) {
            ItemStack item = inventory.getItem(slot);
            
            // 如果槽位有物品，返回物品
            if (item != null && !item.getType().isAir()) {
                return item;
            }
            
            // 否则返回背景物品
            return background;
        }
        
        /**
         * 检查槽位是否为空
         */
        public boolean isEmpty() {
            ItemStack item = inventory.getItem(slot);
            return item == null || item.getType().isAir();
        }
        
        /**
         * 设置槽位内容
         */
        public void setItem(@Nullable ItemStack item) {
            inventory.setItem(slot, item);
        }
        
        /**
         * 获取槽位内容（不含背景）
         */
        @Nullable
        public ItemStack getItem() {
            return inventory.getItem(slot);
        }
    }
}

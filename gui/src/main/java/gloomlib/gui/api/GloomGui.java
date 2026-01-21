package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.component.builtin.InventoryLinkComponent;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.observable.Observable;
import gloomlib.gui.observable.Observer;
import gloomlib.gui.slot.SlotElement;
import gloomlib.gui.state.MutableProperty;
import gloomlib.gui.state.Property;
import gloomlib.gui.window.AbstractWindow;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 核心 GUI 类 - 支持多观察者模式
 * <p>
 * 从 3.0 版本开始，GloomGui 支持多个窗口（玩家）同时观察同一个 GUI 实例。
 * 这允许创建共享的商店、拍卖行等多人可见的界面。
 * 
 * @author GloomLib
 * @since 2.0
 */
public class GloomGui implements Observable {

    private final Player player;
    private final Component title;
    private final int size;
    private final GuiConfiguration configuration;
    private final Consumer<InventoryCloseEvent> closeAction;

    private final Map<Integer, GloomComponent> components = new HashMap<>();
    private final Map<Integer, Integer> componentIndices = new HashMap<>();

    // SlotElement 系统：槽位索引 -> 槽位元素
    private final Map<Integer, SlotElement> slotElements = new HashMap<>();

    private final List<Integer> tickingSlots = new ArrayList<>();

    // 多观察者支持：槽位索引 -> 观察者集合
    private final Map<Integer, Set<ObserverEntry>> observers = new ConcurrentHashMap<>();

    // 冻结状态：当 GUI 被冻结时，所有交互都会被阻止
    private final MutableProperty<Boolean> frozen = MutableProperty.of(false);
    
    // 背景物品：在没有组件的槽位显示
    private final MutableProperty<ItemStack> background = MutableProperty.of(null);

    private Inventory inventory;

    /**
     * 观察者条目记录 - 存储观察者和通知方式
     */
    private record ObserverEntry(@NotNull Observer observer, int how) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ObserverEntry entry)) return false;
            return observer == entry.observer;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(observer);
        }
    }

    public GloomGui(Player player,
                    Component title,
                    int rows,
                    InventoryType type,
                    GuiConfiguration configuration,
                    Consumer<InventoryCloseEvent> closeAction,
                    String[] structure,
                    Map<Character, GloomComponent> charComponents,
                    Map<Integer, GloomComponent> slotComponents) {
        this.player = player;
        this.title = title;
        this.configuration = configuration;
        this.closeAction = closeAction;
        this.size = (type == InventoryType.CHEST) ? rows * 9 : type.getDefaultSize();

        calculateLayout(type, structure, charComponents, slotComponents);
    }

    private void calculateLayout(InventoryType type,
                                 String[] structure,
                                 Map<Character, GloomComponent> charComponents,
                                 Map<Integer, GloomComponent> slotComponents) {
        int width = 9;
        if (type == InventoryType.HOPPER) width = 5;
        else if (type == InventoryType.DISPENSER || type == InventoryType.DROPPER || type == InventoryType.WORKBENCH)
            width = 3;

        Map<GloomComponent, Integer> counters = new HashMap<>();

        if (structure != null) {
            for (int r = 0; r < structure.length; r++) {
                String rowStr = structure[r].replace(" ", "");
                for (int c = 0; c < rowStr.length() && c < width; c++) {
                    char key = rowStr.charAt(c);
                    if (key == '.') continue;

                    GloomComponent comp = charComponents.get(key);
                    if (comp != null) {
                        int slot = r * width + c;
                        int idx = counters.getOrDefault(comp, 0);

                        this.components.put(slot, comp);
                        this.componentIndices.put(slot, idx);

                        counters.put(comp, idx + 1);
                    }
                }
            }
        }

        slotComponents.forEach((slot, comp) -> {
            this.components.put(slot, comp);
            this.componentIndices.put(slot, 0);
        });

        this.tickingSlots.clear();
        this.components.forEach((slot, component) -> {
            if (component.getTickRate() > 0) {
                this.tickingSlots.add(slot);
            }
        });
    }

    public void bindToWindow(AbstractWindow window) {
        this.inventory = window.getInventory();
        
        // 设置背景物品监听器
        background.observeWeak(bg -> {
            if (inventory != null) {
                applyBackground();
            }
        });
        
        redraw();
    }

    /**
     * 应用背景物品到所有空槽位
     */
    private void applyBackground() {
        ItemStack bg = background.get();
        if (bg == null) {
            return;
        }
        
        for (int i = 0; i < size; i++) {
            // 如果槽位既没有 SlotElement 也没有传统组件，则应用背景
            if (!slotElements.containsKey(i) && !components.containsKey(i)) {
                inventory.setItem(i, bg.clone());
                // 通知观察者背景槽位已更新
                notifySlotObservers(i);
            }
        }
    }

    public void redraw() {
        if (inventory == null) return;
        
        // 先应用背景
        applyBackground();
        
        // 渲染使用 SlotElement 的槽位
        slotElements.forEach((slot, element) -> {
            updateInventoryItem(slot, element.render());
        });
        
        // 渲染传统组件槽位
        components.forEach((slot, component) -> {
            // 如果该槽位已经使用 SlotElement，跳过
            if (!slotElements.containsKey(slot)) {
                int idx = componentIndices.get(slot);
                updateInventoryItem(slot, component.render(idx));
            }
        });
    }

    public void tick() {
        if (inventory == null) return;

        for (Integer slot : tickingSlots) {
            GloomComponent component = components.get(slot);
            if (component.onTick()) {
                int idx = componentIndices.get(slot);
                updateInventoryItem(slot, component.render(idx));
            }
        }
    }

    private void updateInventoryItem(int slot, ItemStack newItem) {
        ItemStack currentItem = inventory.getItem(slot);

        if (currentItem == null && newItem == null) {
            return;
        }
        if (currentItem != null && newItem != null && currentItem.isSimilar(newItem) && currentItem.getAmount() == newItem.getAmount()) {
            return;
        }

        inventory.setItem(slot, newItem);
        // 通知观察者槽位已更新
        notifySlotObservers(slot);
    }

    public void handleClose(InventoryCloseEvent event) {
        if (closeAction != null) {
            closeAction.accept(event);
        }
        dispose();
    }

    private void dispose() {
        Set<GloomComponent> distinctComponents = new HashSet<>(components.values());
        for (GloomComponent component : distinctComponents) {
            try {
                component.dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == event.getInventory()) {
            // 如果 GUI 被冻结，阻止所有交互
            if (frozen.get()) {
                event.setCancelled(true);
                return;
            }
            
            int slot = event.getSlot();
            GloomComponent component = getComponent(slot);

            // 检查是否为 InventoryLink 组件
            if (component instanceof InventoryLinkComponent link) {
                // 对于 InventoryLink，如果允许交互则不取消事件
                if (!link.isAllowInteraction()) {
                    event.setCancelled(true);
                }
                // 否则让 Bukkit 的背包系统处理交互
                return;
            }
            
            // 对于普通组件，取消事件并调用 onClick
            event.setCancelled(true);

            if (component != null) {
                InteractionContext context = new InteractionContext(
                        event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : player,
                        event.getClick(),
                        event.getAction(),
                        slot,
                        event.getCurrentItem(),
                        getComponentIndex(slot)
                );
                try {
                    component.onClick(context);
                    if (component.onTick()) {
                        updateInventoryItem(slot, component.render(getComponentIndex(slot)));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            // 只在 shift-click 的目标槽位是 GUI 时才阻止
            if (event.isShiftClick() && event.getClickedInventory() != null) {
                int rawSlot = event.getRawSlot();
                // rawSlot < size 表示点击的是上方 GUI，需要阻止
                if (rawSlot >= size && event.getAction().toString().contains("MOVE_TO")) {
                    // 从玩家背包 shift-click 可能移动到 GUI，检查是否会影响 GUI
                    event.setCancelled(true);
                }
            }
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        boolean involvesGui = event.getRawSlots().stream()
                .anyMatch(slot -> slot < size);
        if (involvesGui) {
            event.setCancelled(true);
        }
    }

    public Player getPlayer() {
        return player;
    }

    public Component getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public GuiConfiguration getConfiguration() {
        return configuration;
    }

    public GloomComponent getComponent(int slot) {
        return components.get(slot);
    }

    public int getComponentIndex(int slot) {
        return componentIndices.getOrDefault(slot, 0);
    }

    public Map<Integer, GloomComponent> getLayout() {
        return components;
    }

    /**
     * 获取 frozen 状态（只读视图）
     * 
     * @return frozen 属性的只读视图
     */
    public Property<Boolean> getFrozen() {
        return frozen;
    }

    /**
     * 设置 GUI 是否冻结
     * 
     * @param frozen 是否冻结
     */
    public void setFrozen(boolean frozen) {
        this.frozen.set(frozen);
    }

    /**
     * 检查 GUI 是否冻结
     * 
     * @return 如果冻结返回 true
     */
    public boolean isFrozen() {
        return frozen.get();
    }

    /**
     * 获取背景物品状态（只读视图）
     * 
     * @return 背景物品属性的只读视图
     */
    public Property<ItemStack> getBackground() {
        return background;
    }

    /**
     * 设置背景物品
     * 
     * @param background 背景物品，null 表示无背景
     */
    public void setBackground(ItemStack background) {
        this.background.set(background);
    }

    // ==================== SlotElement 系统 ====================

    /**
     * 设置槽位元素（新API）
     * <p>
     * 使用 SlotElement 系统可以实现更灵活的槽位内容，
     * 包括组件、GUI 嵌套和背包链接。
     * 
     * @param slot    槽位索引
     * @param element 槽位元素
     */
    public void setSlotElement(int slot, @NotNull SlotElement element) {
        slotElements.put(slot, element);
        if (inventory != null) {
            updateInventoryItem(slot, element.render());
        }
    }

    /**
     * 获取槽位元素
     * 
     * @param slot 槽位索引
     * @return 槽位元素，null 表示该槽位没有使用 SlotElement
     */
    public SlotElement getSlotElement(int slot) {
        return slotElements.get(slot);
    }

    /**
     * 移除槽位元素
     * 
     * @param slot 槽位索引
     */
    public void removeSlotElement(int slot) {
        slotElements.remove(slot);
        if (inventory != null) {
            updateInventoryItem(slot, null);
        }
    }

    /**
     * 渲染单个槽位（支持 SlotElement 和传统组件）
     * 
     * @param slot 槽位索引
     * @return 渲染后的物品
     */
    public ItemStack renderSlot(int slot) {
        // 优先使用 SlotElement
        SlotElement element = slotElements.get(slot);
        if (element != null) {
            return element.render();
        }

        // 回退到传统组件系统
        GloomComponent component = components.get(slot);
        if (component != null) {
            return component.render(componentIndices.get(slot));
        }

        // 使用背景物品
        return background.get();
    }

    // ==================== Observable 接口实现 ====================

    @Override
    public void addObserver(@NotNull Observer who, int what, int how) {
        observers.computeIfAbsent(what, k -> ConcurrentHashMap.newKeySet())
                .add(new ObserverEntry(who, how));
    }

    @Override
    public void removeObserver(@NotNull Observer who, int what, int how) {
        Set<ObserverEntry> slotObservers = observers.get(what);
        if (slotObservers != null) {
            slotObservers.removeIf(entry -> entry.observer() == who);
            if (slotObservers.isEmpty()) {
                observers.remove(what);
            }
        }
    }

    @Override
    public void removeAllObservers(@NotNull Observer who) {
        observers.values().forEach(set -> 
            set.removeIf(entry -> entry.observer() == who)
        );
        observers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * 通知所有观察特定槽位的观察者
     * 
     * @param slot 槽位索引
     */
    public void notifySlotObservers(int slot) {
        Set<ObserverEntry> slotObservers = observers.get(slot);
        if (slotObservers != null) {
            slotObservers.forEach(entry -> entry.observer().notifyUpdate(entry.how()));
        }
    }

    /**
     * 通知所有观察者所有槽位都已更新
     */
    public void notifyAllObservers() {
        for (int slot = 0; slot < size; slot++) {
            notifySlotObservers(slot);
        }
    }
}
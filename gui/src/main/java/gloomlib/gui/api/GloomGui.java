package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.component.builtin.InventoryLinkComponent;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import gloomlib.gui.window.AbstractWindow;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;

public class GloomGui {

    private final Player player;
    private final Component title;
    private final int size;
    private final GuiConfiguration configuration;
    private final Consumer<InventoryCloseEvent> closeAction;

    private final Map<Integer, GloomComponent> components = new HashMap<>();
    private final Map<Integer, Integer> componentIndices = new HashMap<>();

    private final List<Integer> tickingSlots = new ArrayList<>();

    // 冻结状态：当 GUI 被冻结时，所有交互都会被阻止
    private final ReactiveState<Boolean> frozen = ReactiveState.of(false);
    
    // 背景物品：在没有组件的槽位显示
    private final ReactiveState<ItemStack> background = ReactiveState.of(null);

    private Inventory inventory;

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
        background.subscribe(bg -> {
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
            if (!components.containsKey(i)) {
                inventory.setItem(i, bg.clone());
            }
        }
    }

    public void redraw() {
        if (inventory == null) return;
        
        // 先应用背景
        applyBackground();
        
        // 然后渲染组件
        components.forEach((slot, component) -> {
            int idx = componentIndices.get(slot);
            updateInventoryItem(slot, component.render(idx));
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
     * 获取 frozen 状态（响应式）
     * 
     * @return frozen 响应式状态
     */
    public ReactiveState<Boolean> getFrozen() {
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
     * 获取背景物品状态（响应式）
     * 
     * @return 背景物品响应式状态
     */
    public ReactiveState<ItemStack> getBackground() {
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
}
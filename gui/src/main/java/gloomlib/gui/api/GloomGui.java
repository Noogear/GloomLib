package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.window.AbstractWindow;
import net.kyori.adventure.text.Component;
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
        redraw();
    }

    public void redraw() {
        if (inventory == null) return;
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
            event.setCancelled(true);

            int slot = event.getSlot();
            GloomComponent component = getComponent(slot);

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
            if (event.isShiftClick()) {
                event.setCancelled(true);
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
}
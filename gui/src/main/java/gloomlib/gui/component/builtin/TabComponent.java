package gloomlib.gui.component.builtin;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A container that switches visible content based on an active tab index.
 */
public class TabComponent implements GloomComponent {

    private final Map<Integer, GloomComponent> tabs;
    private final ReactiveState<Integer> activeTab;
    private GloomGui parent;

    public TabComponent() {
        this.tabs = new HashMap<>();
        this.activeTab = new ReactiveState<>(0);

        this.activeTab.subscribe(tab -> {
            if (parent != null) parent.redraw();
        });
    }

    public void setTab(int index, GloomComponent component) {
        tabs.put(index, component);
        // If parent exists, set it now
        if (parent != null) component.setParent(parent);
    }

    public void switchTo(int index) {
        if (tabs.containsKey(index)) {
            activeTab.set(index);
        }
    }

    public ReactiveState<Integer> getState() {
        return activeTab;
    }

    /**
     * Gets the component for the currently active tab.
     */
    public @Nullable GloomComponent getCurrentComponent() {
        return tabs.get(activeTab.get());
    }

    @Override
    public @NotNull ItemStack render() {
        GloomComponent current = getCurrentComponent();
        return current != null ? current.render() : new ItemStack(Material.AIR);
    }

    @Override
    public void tick() {
        GloomComponent current = getCurrentComponent();
        if (current != null) current.tick();
    }

    @Override
    public void handleClick(@NotNull InteractionContext context) {
        GloomComponent current = getCurrentComponent();
        if (current != null) {
            current.handleClick(context);
        }
    }

    @Override
    public void setParent(@Nullable GloomGui gui) {
        this.parent = gui;
        for (GloomComponent child : tabs.values()) {
            child.setParent(gui);
        }
    }

    @Override
    public @NotNull TabComponent clone() {
        try {
            TabComponent cloned = (TabComponent) super.clone();

            // Deep copy tabs
            Map<Integer, GloomComponent> clonedTabs = new HashMap<>();
            this.tabs.forEach((id, comp) -> clonedTabs.put(id, comp.clone()));

            var tabsField = TabComponent.class.getDeclaredField("tabs");
            tabsField.setAccessible(true);
            tabsField.set(cloned, clonedTabs);

            // Reset state
            var stateField = TabComponent.class.getDeclaredField("activeTab");
            stateField.setAccessible(true);
            stateField.set(cloned, new ReactiveState<>(0)); // Default to tab 0

            cloned.parent = null;

            cloned.getState().subscribe(val -> {
                if (cloned.parent != null) cloned.parent.redraw();
            });

            return cloned;
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone TabComponent", e);
        }
    }
}
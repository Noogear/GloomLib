package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Component that switches between different sub-components based on a tab ID.
 */
public class TabComponent implements GloomComponent {

    private final ReactiveState<String> activeTabState;
    private final Map<String, GloomComponent> tabs = new HashMap<>();
    private final GloomComponent fallback;

    /**
     * Constructs a tab component.
     *
     * @param defaultTab the initial active tab ID
     */
    public TabComponent(String defaultTab) {
        this.activeTabState = new ReactiveState<>(defaultTab);
        this.fallback = GloomComponent.builder().icon(new ItemStack(Material.AIR)).build();

        this.activeTabState.subscribe(val -> {
        });
    }

    public void addTab(String id, GloomComponent component) {
        tabs.put(id, component);
    }

    public void setTab(String id) {
        if (tabs.containsKey(id)) {
            activeTabState.set(id);
        }
    }

    private GloomComponent getActiveComponent() {
        return tabs.getOrDefault(activeTabState.get(), fallback);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        return getActiveComponent().render(index);
    }

    @Override
    public void onClick(InteractionContext context) {
        getActiveComponent().onClick(context);
    }

    @Override
    public boolean onTick() {
        return getActiveComponent().onTick();
    }

    @Override
    public int getTickRate() {
        GloomComponent active = getActiveComponent();
        return active.getTickRate();
    }

    @Override
    public void dispose() {
        for (GloomComponent comp : tabs.values()) {
            comp.dispose();
        }
    }

    @Override
    public TabComponent clone() {
        TabComponent clone = new TabComponent(activeTabState.get());
        this.tabs.forEach((k, v) -> clone.addTab(k, v.clone()));
        return clone;
    }
}

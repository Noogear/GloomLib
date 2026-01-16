package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TabComponent implements GloomComponent {

    private final ReactiveState<String> tabState;
    private final Map<String, GloomComponent> tabs = new HashMap<>();
    private final GloomComponent fallback;

    private final Consumer<String> listener;

    public TabComponent(String initialTab) {
        this.tabState = new ReactiveState<>(initialTab);
        this.fallback = GloomComponent.builder().icon(new ItemStack(Material.AIR)).build();

        this.listener = (val) -> {
        };
        this.tabState.subscribe(this.listener);
    }

    public void addTab(String id, GloomComponent component) {
        tabs.put(id, component);
    }

    public void setTab(String id) {
        tabState.set(id);
    }

    private GloomComponent getCurrent() {
        return tabs.getOrDefault(tabState.get(), fallback);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        return getCurrent().render(index);
    }

    @Override
    public void onClick(InteractionContext context) {
        getCurrent().onClick(context);
    }

    @Override
    public boolean onTick() {
        return getCurrent().onTick();
    }

    @Override
    public void dispose() {
        tabState.unsubscribe(listener);
        tabs.values().forEach(GloomComponent::dispose);
    }

    @Override
    public GloomComponent clone() {
        try {
            TabComponent clone = (TabComponent) super.clone();
            clone.tabs.clear();
            this.tabs.forEach((k, v) -> clone.tabs.put(k, v.clone()));
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
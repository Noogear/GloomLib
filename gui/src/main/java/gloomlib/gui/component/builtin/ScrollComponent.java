package gloomlib.gui.component.builtin;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A component that handles scrolling through a list of items.
 * Can be used for vertical or horizontal scrolling.
 */
public class ScrollComponent implements GloomComponent {

    private final List<GloomComponent> content;
    private final ReactiveState<Integer> scrollOffset;
    private final int viewSize; // How many items are visible at once
    private final int stepSize; // How many columns/rows usually (e.g. 9 for vertical list)
    private GloomGui parent;

    public ScrollComponent(List<GloomComponent> content, int viewSize, int stepSize) {
        this.content = new ArrayList<>(content);
        this.viewSize = viewSize;
        this.stepSize = stepSize;
        this.scrollOffset = new ReactiveState<>(0);

        // Auto-redraw on scroll
        this.scrollOffset.subscribe(offset -> {
            if (parent != null) parent.redraw();
        });
    }

    /**
     * Adds an element dynamically.
     */
    public void addElement(GloomComponent element) {
        content.add(element);
        if (parent != null) parent.redraw();
    }

    public void scroll(int delta) {
        int next = scrollOffset.get() + delta;
        int maxOffset = Math.max(0, (int) Math.ceil((double) (content.size() - viewSize) / stepSize) * stepSize);

        // Simple bounds check (can be refined based on strict row/column logic)
        if (next < 0) next = 0;
        if (next >= content.size()) next = content.size() - 1;
        // Ideally we clamp to max scrollable rows

        scrollOffset.set(next);
    }

    public void scrollUp() {
        scroll(-stepSize);
    }

    public void scrollDown() {
        scroll(stepSize);
    }

    public ReactiveState<Integer> getState() {
        return scrollOffset;
    }

    public List<GloomComponent> getVisibleComponents() {
        int start = scrollOffset.get();
        int end = Math.min(start + viewSize, content.size());

        if (start >= content.size()) return new ArrayList<>();
        return content.subList(start, end);
    }

    @Override
    public @NotNull ItemStack render() {
        // Virtual container, returns AIR or a placeholder
        return new ItemStack(Material.AIR);
    }

    @Override
    public void tick() {
        getVisibleComponents().forEach(GloomComponent::tick);
    }

    @Override
    public void handleClick(@NotNull InteractionContext context) {
        // Delegation logic handled by GuiLayout or Parent
    }

    @Override
    public void setParent(@Nullable GloomGui gui) {
        this.parent = gui;
        for (GloomComponent child : content) {
            child.setParent(gui);
        }
    }

    @Override
    public @NotNull ScrollComponent clone() {
        try {
            ScrollComponent cloned = (ScrollComponent) super.clone();

            // Deep copy content
            List<GloomComponent> clonedContent = new ArrayList<>();
            for (GloomComponent comp : this.content) {
                clonedContent.add(comp.clone());
            }

            // Reset state
            var offsetField = ScrollComponent.class.getDeclaredField("scrollOffset");
            offsetField.setAccessible(true);
            offsetField.set(cloned, new ReactiveState<>(0));

            var contentField = ScrollComponent.class.getDeclaredField("content");
            contentField.setAccessible(true);
            contentField.set(cloned, clonedContent);

            cloned.parent = null;

            cloned.getState().subscribe(val -> {
                if (cloned.parent != null) cloned.parent.redraw();
            });

            return cloned;
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone ScrollComponent", e);
        }
    }

    public enum Orientation {
        VERTICAL,
        HORIZONTAL
    }
}
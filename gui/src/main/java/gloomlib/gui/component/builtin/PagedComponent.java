package gloomlib.gui.component.builtin;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A container component that handles pagination of other Components.
 * Supports full composition (nested components).
 */
public class PagedComponent implements GloomComponent {

    private final List<GloomComponent> content;
    private final ReactiveState<Integer> currentPage;
    private final int pageSize;
    private GloomGui parent;

    public PagedComponent(List<GloomComponent> content, int pageSize) {
        this.content = new ArrayList<>(content); // Defensive copy
        this.pageSize = pageSize;
        this.currentPage = new ReactiveState<>(0);

        // Listen to page changes to trigger redraw
        this.currentPage.subscribe(page -> {
            if (parent != null) parent.redraw();
        });
    }

    /**
     * Adds a component to the pagination list.
     */
    public void addElement(GloomComponent component) {
        this.content.add(component);
        if (parent != null) parent.redraw();
    }

    public void nextPage() {
        if (hasNext()) {
            currentPage.set(currentPage.get() + 1);
        }
    }

    public void previousPage() {
        if (hasPrevious()) {
            currentPage.set(currentPage.get() - 1);
        }
    }

    public boolean hasNext() {
        return (currentPage.get() + 1) * pageSize < content.size();
    }

    public boolean hasPrevious() {
        return currentPage.get() > 0;
    }

    public ReactiveState<Integer> getState() {
        return currentPage;
    }

    /**
     * Calculates which components are visible on the current page.
     */
    public List<GloomComponent> getVisibleComponents() {
        int start = currentPage.get() * pageSize;
        int end = Math.min(start + pageSize, content.size());

        if (start >= content.size()) return new ArrayList<>();
        return content.subList(start, end);
    }

    @Override
    public @NotNull ItemStack render() {
        // PagedComponent is a virtual container.
        // It returns AIR as it doesn't represent a single item slot itself.
        // The GUI logic handles rendering its children.
        return new ItemStack(Material.AIR);
    }

    /**
     * Delegate tick to visible children.
     */
    @Override
    public void tick() {
        getVisibleComponents().forEach(GloomComponent::tick);
    }

    @Override
    public void handleClick(@NotNull InteractionContext context) {
        // GloomGui handles the delegation to specific child components based on slot.
        // This method serves as a fallback or for container-wide clicks.
    }

    @Override
    public void setParent(GloomGui gui) {
        this.parent = gui;
        // Propagate parent to children so they can access the GUI too
        for (GloomComponent child : content) {
            child.setParent(gui);
        }
    }

    @Override
    public @NotNull PagedComponent clone() {
        try {
            PagedComponent cloned = (PagedComponent) super.clone();

            // Deep copy the content list to prevent shared state between players
            List<GloomComponent> clonedContent = new ArrayList<>();
            for (GloomComponent comp : this.content) {
                clonedContent.add(comp.clone());
            }

            // Use reflection to reset final fields if necessary, or rely on mutable internal state.
            // Here we re-initialize the ReactiveState for the new instance.
            var stateField = PagedComponent.class.getDeclaredField("currentPage");
            stateField.setAccessible(true);
            stateField.set(cloned, new ReactiveState<>(0));

            var contentField = PagedComponent.class.getDeclaredField("content");
            contentField.setAccessible(true);
            contentField.set(cloned, clonedContent);

            cloned.parent = null; // Reset parent

            // Re-subscribe listener to the NEW parent (once assigned)
            cloned.getState().subscribe(page -> {
                if (cloned.parent != null) cloned.parent.redraw();
            });

            return cloned;
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone PagedComponent", e);
        }
    }
}
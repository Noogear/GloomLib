package gloomlib.gui.component;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.util.ItemBuilder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class SimpleGloomComponent implements GloomComponent {

    // Transient reference to avoid memory leaks during cloning.
    // This will be re-assigned when the component is added to a new GUI.
    protected GloomGui parent;
    private ItemStack item;
    private Consumer<InteractionContext> action;

    public SimpleGloomComponent(@NotNull ItemStack item) {
        this.item = item;
    }

    public SimpleGloomComponent(@NotNull ItemBuilder builder) {
        this.item = builder.build();
    }

    public void setAction(@NotNull Consumer<InteractionContext> action) {
        this.action = action;
    }

    @Override
    public @NotNull ItemStack render() {
        return item;
    }

    @Override
    public void handleClick(@NotNull InteractionContext context) {
        if (action != null) {
            action.accept(context);
        }
    }

    @Override
    public void setParent(@Nullable GloomGui gui) {
        this.parent = gui;
    }

    @Override
    @NotNull
    public SimpleGloomComponent clone() {
        try {
            SimpleGloomComponent cloned = (SimpleGloomComponent) super.clone();
            // ItemStack is mutable in Bukkit, make sure we have a fresh copy
            if (this.item != null) {
                cloned.item = this.item.clone();
            }
            // Actions are functional interfaces (usually stateless logic), so shallow copy is acceptable.
            // Reset parent, as the cloned component belongs to a new context until assigned.
            cloned.parent = null;
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Failed to clone component", e);
        }
    }
}
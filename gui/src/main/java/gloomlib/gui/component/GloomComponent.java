package gloomlib.gui.component;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.interaction.InteractionContext;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a UI element in the GUI.
 * Updated to support Composition, Animation, and Deep Cloning.
 */
public interface GloomComponent extends Cloneable {

    /**
     * Renders the component to an ItemStack.
     *
     * @return The display item.
     */
    @NotNull
    ItemStack render();

    /**
     * Called when a player clicks on this component.
     *
     * @param context The interaction context.
     */
    void handleClick(@NotNull InteractionContext context);

    /**
     * Called every tick (if the GUI supports ticking).
     * Used for animations or dynamic updates.
     */
    default void tick() {
    }

    /**
     * Sets the parent GUI holder.
     * Essential for scheduling tasks or checking GUI state.
     *
     * @param gui The parent GUI.
     */
    default void setParent(@Nullable GloomGui gui) {
    }

    /**
     * Deep clone contract.
     * Components MUST implement deep copying for mutable fields (Lists, States).
     */
    @NotNull
    GloomComponent clone();
}
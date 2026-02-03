package gloomlib.gui.slot;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.window.Observable;
import gloomlib.gui.window.Observer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface representing an element in a GUI slot.
 */
public sealed interface SlotElement permits
        SlotElement.ComponentSlot,
        SlotElement.GuiLink,
        SlotElement.InventoryLink {

    /**
     * Renders the element for a player.
     *
     * @param player the player
     * @return the rendered item stack
     */
    @Nullable
    ItemStack render(@Nullable Player player);

    /**
     * Gets the holding element.
     *
     * @return the holding element
     */
    @NotNull
    default SlotElement getHoldingElement() {
        return this;
    }

    /**
     * Traverses the element hierarchy.
     *
     * @return the list of elements
     */
    @NotNull
    default List<SlotElement> traverse() {
        List<SlotElement> path = new ArrayList<>();
        path.add(this);
        return path;
    }

    /**
     * Slot element representing a component at an index.
     *
     * @param component the component
     * @param index     the component index
     */
    record ComponentSlot(
            @NotNull GloomComponent component,
            int index
    ) implements SlotElement {

        @Override
        public @Nullable ItemStack render(@Nullable Player player) {
            return component.render(index);
        }

        /**
         * Observes the component.
         *
         * @param observer the observer
         * @param how      notification hint
         */
        public void observe(@NotNull Observer observer, int how) {
            if (component instanceof Observable observable) {
                observable.addObserver(observer, index, how);
            }
        }

        /**
         * Unobserves the component.
         *
         * @param observer the observer to remove
         * @param how      notification hint
         */
        public void unobserve(@NotNull Observer observer, int how) {
            if (component instanceof Observable observable) {
                observable.removeObserver(observer, index, how);
            }
        }
    }

    /**
     * Slot element linking to another GUI slot.
     *
     * @param gui  the target GUI
     * @param slot the target slot
     */
    record GuiLink(
            @NotNull GloomGui gui,
            int slot
    ) implements SlotElement {

        @Override
        public @Nullable ItemStack render(@Nullable Player player) {
            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                return component.render(gui.getComponentIndex(slot));
            }
            return gui.getBackground();
        }

        @Override
        public @NotNull SlotElement getHoldingElement() {
            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                return new ComponentSlot(component, gui.getComponentIndex(slot)).getHoldingElement();
            }
            return this;
        }

        @Override
        public @NotNull List<SlotElement> traverse() {
            List<SlotElement> path = new ArrayList<>();
            path.add(this);

            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                path.addAll(new ComponentSlot(component, gui.getComponentIndex(slot)).traverse());
            }

            return path;
        }

        /**
         * Observes the entire link chain.
         *
         * @param observer the observer
         * @param how      notification hint
         */
        public void observeChain(@NotNull Observer observer, int how) {
            for (SlotElement element : traverse()) {
                if (element instanceof ComponentSlot componentSlot) {
                    componentSlot.observe(observer, how);
                }
            }
        }

        /**
         * Unobserves the entire link chain.
         *
         * @param observer the observer to remove
         * @param how      notification hint
         */
        public void unobserveChain(@NotNull Observer observer, int how) {
            for (SlotElement element : traverse()) {
                if (element instanceof ComponentSlot componentSlot) {
                    componentSlot.unobserve(observer, how);
                }
            }
        }
    }

    /**
     * Slot element linking to a Bukkit inventory slot.
     *
     * @param inventory  the inventory
     * @param slot       the slot index
     * @param background the background item
     */
    record InventoryLink(
            @NotNull Inventory inventory,
            int slot,
            @Nullable ItemStack background
    ) implements SlotElement {

        /**
         * Constructs an inventory link.
         *
         * @param inventory the inventory
         * @param slot      the slot index
         */
        public InventoryLink(@NotNull Inventory inventory, int slot) {
            this(inventory, slot, null);
        }

        @Override
        public @Nullable ItemStack render(@Nullable Player player) {
            ItemStack item = inventory.getItem(slot);
            return (item != null && !item.getType().isAir()) ? item : background;
        }

        /**
         * Checks if the slot is empty.
         *
         * @return true if empty
         */
        public boolean isEmpty() {
            ItemStack item = inventory.getItem(slot);
            return item == null || item.getType().isAir();
        }

        /**
         * Gets the item in the slot.
         *
         * @return the item
         */
        @Nullable
        public ItemStack getItem() {
            return inventory.getItem(slot);
        }

        /**
         * Sets the item in the slot.
         *
         * @param item the item
         */
        public void setItem(@Nullable ItemStack item) {
            inventory.setItem(slot, item);
        }
    }
}

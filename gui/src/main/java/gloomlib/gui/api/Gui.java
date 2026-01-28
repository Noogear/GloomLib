package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.window.Observable;
import gloomlib.gui.slot.SlotElement;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Core GUI interface representing an inventory-based interface.
 */
public sealed interface Gui extends Observable permits Gui.Normal, Gui.Paged {

    /**
     * Gets the player viewing this GUI.
     *
     * @return the player
     */
    @NotNull Player getPlayer();

    /**
     * Gets the GUI title.
     *
     * @return the title component
     */
    @NotNull Component getTitle();

    /**
     * Gets the inventory size.
     *
     * @return the number of slots
     */
    int getSize();

    /**
     * Checks if the GUI is frozen (interactions disabled).
     *
     * @return {@code true} if frozen, {@code false} otherwise
     */
    boolean isFrozen();

    /**
     * Sets the frozen state.
     *
     * @param frozen {@code true} to freeze interactions
     */
    void setFrozen(boolean frozen);

    /**
     * Gets the background item.
     *
     * @return the background item, or {@code null} if none
     */
    @Nullable ItemStack getBackground();

    /**
     * Sets the background item for empty slots.
     *
     * @param background the background item (nullable)
     */
    void setBackground(@Nullable ItemStack background);

    /**
     * Gets the slot element at an index.
     *
     * @param slot the slot index
     * @return the slot element, or {@code null} if empty
     */
    @Nullable SlotElement getSlotElement(int slot);

    /**
     * Sets a slot element at an index.
     *
     * @param slot the slot index
     * @param element the slot element
     */
    void setSlotElement(int slot, @NotNull SlotElement element);

    /**
     * Removes a slot element.
     *
     * @param slot the slot index
     */
    void removeSlotElement(int slot);

    /**
     * Renders a slot to an item stack.
     *
     * @param slot the slot index
     * @return the rendered item, or {@code null} if empty
     */
    @Nullable ItemStack renderSlot(int slot);

    /**
     * Redraws all slots.
     */
    void redraw();

    /**
     * Ticks the GUI for periodic updates.
     */
    void tick();

    /**
     * Binds this GUI to a window.
     *
     * @param window the window to bind to
     */
    void bindToWindow(@NotNull gloomlib.gui.window.AbstractWindow window);

    /**
     * Gets the Bukkit inventory.
     *
     * @return the inventory, or {@code null} if not yet created
     */
    @Nullable Inventory getInventory();

    /**
     * Normal GUI interface with direct component access.
     */
    sealed interface Normal extends Gui permits GloomGui {

        /**
         * Gets the component at a slot.
         *
         * @param slot the slot index
         * @return the component, or {@code null} if none
         */
        @Nullable GloomComponent getComponent(int slot);

        /**
         * Gets the component index for a slot.
         *
         * @param slot the slot index
         * @return the component index
         */
        int getComponentIndex(int slot);

        /**
         * Gets the full component layout.
         *
         * @return a map of slot indices to components
         */
        @NotNull Map<Integer, GloomComponent> getLayout();
    }

    non-sealed interface Paged extends Gui {

        int getCurrentPage();

        void setCurrentPage(int page);

        int getPageCount();

        boolean hasNextPage();

        boolean hasPreviousPage();

        boolean nextPage();

        boolean previousPage();

        @NotNull List<?> getAllContent();
    }
}

package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.observable.Observable;
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
 * The core GUI interface in GloomLib, inspired by InvUI's {@link xyz.xenondevs.invui.gui.Gui}.
 * <p>
 * This sealed interface defines the contract for all GUI types in the framework.
 * It provides a unified abstraction for managing inventory-based user interfaces.
 * <p>
 * There are two main variants:
 * <ul>
 *     <li>{@link Normal} - A standard fixed-size GUI with direct slot access</li>
 *     <li>{@link Paged} - A GUI that supports pagination through multiple pages of content</li>
 * </ul>
 * <p>
 * All GUIs are {@link Observable}, meaning they can notify observers when their state changes.
 * This enables automatic UI updates when underlying data changes.
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * Gui.Normal gui = new GloomGui.Builder(player)
 *     .title(Component.text("My GUI"))
 *     .rows(3)
 *     .setSlot(13, SlotElement.ComponentSlot.of(myComponent))
 *     .build();
 * }</pre>
 * 
 * @see SlotElement
 * @see Observable
 * @see <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui-core/src/main/java/xyz/xenondevs/invui/gui/Gui.java">InvUI Gui.java</a>
 */
public sealed interface Gui extends Observable permits Gui.Normal, Gui.Paged {

    /**
     * Gets the player associated with this GUI.
     * 
     * @return the player viewing this GUI
     */
    @NotNull Player getPlayer();

    /**
     * Gets the title of this GUI.
     * 
     * @return the GUI title component
     */
    @NotNull Component getTitle();

    /**
     * Gets the total number of slots in this GUI.
     * 
     * @return the slot count
     */
    int getSize();

    /**
     * Checks if this GUI is currently frozen.
     * A frozen GUI will not respond to player interactions.
     * 
     * @return {@code true} if the GUI is frozen, {@code false} otherwise
     */
    boolean isFrozen();

    /**
     * Sets whether this GUI is frozen.
     * 
     * @param frozen {@code true} to freeze the GUI, {@code false} to unfreeze
     */
    void setFrozen(boolean frozen);

    /**
     * Gets the background item stack that fills empty slots.
     * 
     * @return the background item, or {@code null} if no background is set
     */
    @Nullable ItemStack getBackground();

    /**
     * Sets the background item stack for empty slots.
     * 
     * @param background the background item, or {@code null} to clear
     */
    void setBackground(@Nullable ItemStack background);

    /**
     * Gets the {@link SlotElement} at the specified slot index.
     * 
     * @param slot the slot index (0-based)
     * @return the SlotElement at that slot, or {@code null} if the slot is empty
     */
    @Nullable SlotElement getSlotElement(int slot);

    /**
     * Sets the {@link SlotElement} at the specified slot index.
     * This is the primary method for populating GUI slots.
     * 
     * @param slot the slot index (0-based)
     * @param element the SlotElement to set
     * @throws IllegalArgumentException if slot is out of bounds
     */
    void setSlotElement(int slot, @NotNull SlotElement element);

    /**
     * Removes the {@link SlotElement} at the specified slot, leaving it empty.
     * 
     * @param slot the slot index (0-based)
     */
    void removeSlotElement(int slot);

    /**
     * Renders the item stack for the specified slot, taking into account
     * the player viewing this GUI (for player-specific rendering).
     * 
     * @param slot the slot index (0-based)
     * @return the rendered ItemStack, or the background item if the slot is empty
     */
    @Nullable ItemStack renderSlot(int slot);

    /**
     * Forces a complete redraw of the GUI, updating all slots in the inventory.
     */
    void redraw();

    /**
     * Ticks the GUI, updating any animated or time-dependent components.
     * Called periodically by {@link gloomlib.gui.GloomGuiManager}.
     */
    void tick();

    /**
     * Binds this GUI to a specific inventory window for display.
     * 
     * @param window the window to bind to
     */
    void bindToWindow(@NotNull gloomlib.gui.window.AbstractWindow window);

    /**
     * Gets the Bukkit {@link Inventory} associated with this GUI.
     * 
     * @return the inventory, or {@code null} if not bound to a window yet
     */
    @Nullable Inventory getInventory();

    /**
     * A normal (non-paged) GUI with a fixed layout.
     * <p>
     * This is the standard GUI type, providing direct access to all slots.
     * It implements {@link Observable} to support reactive updates.
     * <p>
     * Implementations should support setting components or slot elements at specific positions,
     * and handle ticking for animated components.
     * 
     * @see GloomGui
     */
    sealed interface Normal extends Gui permits GloomGui {

        /**
         * Gets the component at the specified slot index.
         * 
         * @param slot the slot index (0-based)
         * @return the component at that slot, or {@code null} if empty
         */
        @Nullable GloomComponent getComponent(int slot);

        /**
         * Gets the component index for a given slot.
         * Components can occupy multiple slots; this returns which component instance
         * is displayed at this slot.
         * 
         * @param slot the slot index (0-based)
         * @return the component index, or -1 if no component is at this slot
         */
        int getComponentIndex(int slot);

        /**
         * Gets a map of all slot-to-component assignments.
         * 
         * @return an immutable map of slot indices to components
         */
        @NotNull Map<Integer, GloomComponent> getLayout();
    }

    /**
     * A paged GUI that supports navigation through multiple pages of content.
     * <p>
     * This GUI type manages multiple pages of items or components, allowing
     * players to navigate forward and backward through the pages.
     * <p>
     * Example use cases include:
     * <ul>
     *     <li>Player lists with pagination</li>
     *     <li>Item shops with many products</li>
     *     <li>Achievement or statistics displays</li>
     * </ul>
     * <p>
     * <b>Note:</b> This is a placeholder interface for future implementation.
     * 
     * @see gloomlib.gui.component.builtin.PagedComponent
     */
    non-sealed interface Paged extends Gui {

        /**
         * Gets the current page number (0-based).
         * 
         * @return the current page index
         */
        int getCurrentPage();

        /**
         * Sets the current page number.
         * 
         * @param page the page index to navigate to (0-based)
         * @throws IllegalArgumentException if page is out of bounds
         */
        void setCurrentPage(int page);

        /**
         * Gets the total number of pages.
         * 
         * @return the page count
         */
        int getPageCount();

        /**
         * Checks if there is a next page available.
         * 
         * @return {@code true} if there is a next page, {@code false} otherwise
         */
        boolean hasNextPage();

        /**
         * Checks if there is a previous page available.
         * 
         * @return {@code true} if there is a previous page, {@code false} otherwise
         */
        boolean hasPreviousPage();

        /**
         * Navigates to the next page if available.
         * 
         * @return {@code true} if navigation was successful, {@code false} if already on last page
         */
        boolean nextPage();

        /**
         * Navigates to the previous page if available.
         * 
         * @return {@code true} if navigation was successful, {@code false} if already on first page
         */
        boolean previousPage();

        /**
         * Gets all content items across all pages.
         * 
         * @return a list of all items/components in this paged GUI
         */
        @NotNull List<?> getAllContent();
    }
}

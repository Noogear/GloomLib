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

public sealed interface Gui extends Observable permits Gui.Normal, Gui.Paged {

    @NotNull Player getPlayer();

    @NotNull Component getTitle();

    int getSize();

    boolean isFrozen();

    void setFrozen(boolean frozen);

    @Nullable ItemStack getBackground();

    void setBackground(@Nullable ItemStack background);

    @Nullable SlotElement getSlotElement(int slot);

    void setSlotElement(int slot, @NotNull SlotElement element);

    void removeSlotElement(int slot);

    @Nullable ItemStack renderSlot(int slot);

    void redraw();

    void tick();

    void bindToWindow(@NotNull gloomlib.gui.window.AbstractWindow window);

    @Nullable Inventory getInventory();

    sealed interface Normal extends Gui permits GloomGui {

        @Nullable GloomComponent getComponent(int slot);

        int getComponentIndex(int slot);

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

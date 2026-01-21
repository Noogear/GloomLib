package gloomlib.gui.slot;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface SlotElement permits
        SlotElement.ComponentSlot,
        SlotElement.GuiLink,
        SlotElement.InventoryLink {

    @Nullable
    ItemStack render();

    record ComponentSlot(
            @NotNull GloomComponent component,
            int index
    ) implements SlotElement {

        @Override
        public @Nullable ItemStack render() {
            return component.render(index);
        }
    }

    record GuiLink(
            @NotNull GloomGui gui,
            int slot
    ) implements SlotElement {

        @Override
        public @Nullable ItemStack render() {
            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                return component.render(gui.getComponentIndex(slot));
            }
            return gui.getBackground().get();
        }
    }

    record InventoryLink(
            @NotNull Inventory inventory,
            int slot,
            @Nullable ItemStack background
    ) implements SlotElement {

        public InventoryLink(@NotNull Inventory inventory, int slot) {
            this(inventory, slot, null);
        }

        @Override
        public @Nullable ItemStack render() {
            ItemStack item = inventory.getItem(slot);
            return (item != null && !item.getType().isAir()) ? item : background;
        }
    }
}

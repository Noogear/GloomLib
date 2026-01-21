package gloomlib.gui.slot;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.observable.Observable;
import gloomlib.gui.observable.Observer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public sealed interface SlotElement permits
        SlotElement.ComponentSlot,
        SlotElement.GuiLink,
        SlotElement.InventoryLink {

    @Nullable
    ItemStack render(@Nullable Player player);

    @NotNull
    default SlotElement getHoldingElement() {
        return this;
    }

    @NotNull
    default List<SlotElement> traverse() {
        List<SlotElement> path = new ArrayList<>();
        path.add(this);
        return path;
    }

    record ComponentSlot(
            @NotNull GloomComponent component,
            int index
    ) implements SlotElement {

        @Override
        public @Nullable ItemStack render(@Nullable Player player) {
            return component.render(index);
        }

        public void observe(@NotNull Observer observer, int how) {
            if (component instanceof Observable observable) {
                observable.addObserver(observer, index, how);
            }
        }

        public void unobserve(@NotNull Observer observer, int how) {
            if (component instanceof Observable observable) {
                observable.removeObserver(observer, index, how);
            }
        }
    }

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
            ItemStack background = gui.getBackground();
            return background;
        }

        @Override
        public @NotNull SlotElement getHoldingElement() {
            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                SlotElement element = new ComponentSlot(component, gui.getComponentIndex(slot));
                return element.getHoldingElement();
            }
            return this;
        }

        @Override
        public @NotNull List<SlotElement> traverse() {
            List<SlotElement> path = new ArrayList<>();
            path.add(this);

            GloomComponent component = gui.getComponent(slot);
            if (component != null) {
                SlotElement element = new ComponentSlot(component, gui.getComponentIndex(slot));
                path.addAll(element.traverse());
            }

            return path;
        }

        public void observeChain(@NotNull Observer observer, int how) {
            List<SlotElement> chain = traverse();
            for (SlotElement element : chain) {
                if (element instanceof ComponentSlot componentSlot) {
                    componentSlot.observe(observer, how);
                }
            }
        }

        public void unobserveChain(@NotNull Observer observer, int how) {
            List<SlotElement> chain = traverse();
            for (SlotElement element : chain) {
                if (element instanceof ComponentSlot componentSlot) {
                    componentSlot.unobserve(observer, how);
                }
            }
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
        public @Nullable ItemStack render(@Nullable Player player) {
            ItemStack item = inventory.getItem(slot);

            if (item != null && !item.getType().isAir()) {
                return item;
            }

            return background;
        }

        public boolean isEmpty() {
            ItemStack item = inventory.getItem(slot);
            return item == null || item.getType().isAir();
        }

        @Nullable
        public ItemStack getItem() {
            return inventory.getItem(slot);
        }

        public void setItem(@Nullable ItemStack item) {
            inventory.setItem(slot, item);
        }
    }
}

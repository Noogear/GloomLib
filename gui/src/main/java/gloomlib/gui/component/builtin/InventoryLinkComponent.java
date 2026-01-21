package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public record InventoryLinkComponent(Inventory linkedInventory, int linkedSlot,
                                     boolean allowInteraction) implements GloomComponent {

    public InventoryLinkComponent {
        if (linkedInventory == null) {
            throw new IllegalArgumentException("链接的背包不能为 null");
        }
        if (linkedSlot < 0 || linkedSlot >= linkedInventory.getSize()) {
            throw new IllegalArgumentException("槽位索引超出范围: " + linkedSlot);
        }

    }

    public InventoryLinkComponent(Inventory linkedInventory, int linkedSlot) {
        this(linkedInventory, linkedSlot, true);
    }

    public static boolean isInventoryLink(GloomComponent component) {
        return component instanceof InventoryLinkComponent;
    }

    @Override
    public @NotNull ItemStack render(int index) {
        ItemStack item = linkedInventory.getItem(linkedSlot);
        return item != null ? item : new ItemStack(Material.AIR);
    }

    @Override
    public void onClick(InteractionContext context) {
    }

    @Override
    public boolean onTick() {
        return true;
    }

    @Override
    public int getTickRate() {
        return 5;
    }

    @Override
    public void dispose() {
    }

    @Override
    public GloomComponent clone() {
        return new InventoryLinkComponent(linkedInventory, linkedSlot, allowInteraction);
    }
}

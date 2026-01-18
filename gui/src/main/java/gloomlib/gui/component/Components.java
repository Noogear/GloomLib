package gloomlib.gui.component;

import gloomlib.gui.component.builtin.PagedComponent;
import gloomlib.gui.component.builtin.ScrollComponent;
import gloomlib.gui.state.ReactiveState;
import gloomlib.gui.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class Components {

    public static GloomComponent button(Material material, String name, Consumer<gloomlib.gui.interaction.InteractionContext> action) {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(material).name(Component.text(name)).build())
                .onClick(action)
                .build();
    }

    public static GloomComponent filler(Material material) {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(material).name(Component.empty()).build())
                .build();
    }

    public static GloomComponent toggle(ReactiveState<Boolean> state, Material onMat, String onText, Material offMat, String offText) {
        return GloomComponent.builder()
                .onRender((s) -> {
                    boolean active = s;
                    return ItemBuilder.from(active ? onMat : offMat)
                            .name(Component.text(active ? onText : offText, active ? NamedTextColor.GREEN : NamedTextColor.RED))
                            .glow(active)
                            .build();
                }, state)
                .onClick(ctx -> state.set(!state.get()))
                .build();
    }

    public static GloomComponent pageNext(PagedComponent<?> pager, Material mat, String name) {
        return GloomComponent.builder()
                .onRender((page) -> {
                    if (!pager.hasNext()) return new ItemStack(Material.AIR);
                    return ItemBuilder.from(mat).name(Component.text(name)).build();
                }, pager.getPageState())
                .onClick(ctx -> pager.nextPage())
                .build();
    }

    public static GloomComponent pagePrev(PagedComponent<?> pager, Material mat, String name) {
        return GloomComponent.builder()
                .onRender((page) -> {
                    if (!pager.hasPrev()) return new ItemStack(Material.AIR);
                    return ItemBuilder.from(mat).name(Component.text(name)).build();
                }, pager.getPageState())
                .onClick(ctx -> pager.prevPage())
                .build();
    }

    public static GloomComponent scrollUp(ScrollComponent<?> scroller, Material mat, String name) {
        return GloomComponent.builder()
                .onRender((offset) -> {
                    if (!scroller.canScrollUp()) return new ItemStack(Material.AIR);
                    return ItemBuilder.from(mat).name(Component.text(name)).build();
                }, scroller.getScrollState())
                .onClick(ctx -> scroller.scrollUp(1))
                .build();
    }

    public static GloomComponent scrollDown(ScrollComponent<?> scroller, Material mat, String name) {
        return GloomComponent.builder()
                .onRender((offset) -> {
                    if (!scroller.canScrollDown()) return new ItemStack(Material.AIR);
                    return ItemBuilder.from(mat).name(Component.text(name)).build();
                }, scroller.getScrollState())
                .onClick(ctx -> scroller.scrollDown(1))
                .build();
    }
}
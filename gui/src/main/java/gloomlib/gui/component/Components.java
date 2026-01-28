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

/**
 * Utility class providing factory methods for common GUI components.
 */
public class Components {

    /**
     * Creates a simple button component.
     *
     * @param material the button material
     * @param name     the display name
     * @param action   the click handler
     * @return a button component
     */
    public static GloomComponent button(Material material, String name, Consumer<gloomlib.gui.interaction.InteractionContext> action) {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(material).name(Component.text(name)).build())
                .onClick(action)
                .build();
    }

    /**
     * Creates a filler component (decorative, non-interactive).
     *
     * @param material the filler material
     * @return a filler component
     */
    public static GloomComponent filler(Material material) {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(material).name(Component.empty()).build())
                .build();
    }

    /**
     * Creates a reactive toggle component that switches between two states.
     *
     * @param state   the reactive boolean state
     * @param onMat   the material when active
     * @param onText  the text when active
     * @param offMat  the material when inactive
     * @param offText the text when inactive
     * @return a toggle component
     */
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

    /**
     * Creates a next-page button for paged components.
     *
     * @param pager the paged component to control
     * @param mat   the button material
     * @param name  the button display name
     * @return a next-page button component
     */
    public static GloomComponent pageNext(PagedComponent<?> pager, Material mat, String name) {
        return GloomComponent.builder()
                .onRender((page) -> {
                    if (!pager.hasNext()) return new ItemStack(Material.AIR);
                    return ItemBuilder.from(mat).name(Component.text(name)).build();
                }, pager.getPageState())
                .onClick(ctx -> pager.nextPage())
                .build();
    }

    /**
     * Creates a previous-page button for paged components.
     *
     * @param pager the paged component to control
     * @param mat   the button material
     * @param name  the button display name
     * @return a previous-page button component
     */
    public static GloomComponent pagePrev(PagedComponent<?> pager, Material mat, String name) {
        return GloomComponent.builder()
                .onRender((page) -> {
                    if (!pager.hasPrev()) return new ItemStack(Material.AIR);
                    return ItemBuilder.from(mat).name(Component.text(name)).build();
                }, pager.getPageState())
                .onClick(ctx -> pager.prevPage())
                .build();
    }

    /**
     * Creates a scroll-up button for scrollable components.
     *
     * @param scroller the scrollable component to control
     * @param mat      the button material
     * @param name     the button display name
     * @return a scroll-up button component
     */
    public static GloomComponent scrollUp(ScrollComponent<?> scroller, Material mat, String name) {
        return GloomComponent.builder()
                .onRender((offset) -> {
                    if (!scroller.canScrollUp()) return new ItemStack(Material.AIR);
                    return ItemBuilder.from(mat).name(Component.text(name)).build();
                }, scroller.getScrollState())
                .onClick(ctx -> scroller.scrollUp(1))
                .build();
    }

    /**
     * Creates a scroll-down button for scrollable components.
     *
     * @param scroller the scrollable component to control
     * @param mat      the button material
     * @param name     the button display name
     * @return a scroll-down button component
     */
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
package gloomlib.gui.component;

import gloomlib.gui.state.ReactiveState;
import gloomlib.gui.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.function.Consumer;

public class Components {

    public static GloomComponent button(Material material, String name, Consumer<gloomlib.gui.interaction.InteractionContext> action) {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(material).name(Component.text(name)).build())
                .onClick(action)
                .build();
    }

    public static GloomComponent toggle(ReactiveState<Boolean> state,
                                        Material onMat, String onText,
                                        Material offMat, String offText) {
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

    public static <T> GloomComponent selector(ReactiveState<T> state, T targetValue,
                                              Material activeMat, Material inactiveMat,
                                              String name) {
        return GloomComponent.builder()
                .onRender((s) -> {
                    boolean isActive = s.equals(targetValue);
                    return ItemBuilder.from(isActive ? activeMat : inactiveMat)
                            .name(Component.text(name, isActive ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                            .glow(isActive)
                            .build();
                }, state)
                .onClick(ctx -> state.set(targetValue))
                .build();
    }

    public static GloomComponent filler(Material material) {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(material).name(Component.empty()).build())
                .build();
    }
}
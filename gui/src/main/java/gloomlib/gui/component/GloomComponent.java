package gloomlib.gui.component;

import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

public interface GloomComponent extends Cloneable {

    static Builder builder() {
        return new Builder();
    }

    @NotNull ItemStack render(int index);

    void onClick(InteractionContext context);

    boolean onTick();

    default int getTickRate() {
        return -1;
    }

    default void dispose() {
    }

    GloomComponent clone();

    class Builder {
        private ItemStack icon;
        private Consumer<InteractionContext> onClick;
        private Function<Object, ItemStack> renderer;
        private ReactiveState<?> state;
        private int tickRate = -1;

        public Builder icon(ItemStack icon) {
            this.icon = icon;
            return this;
        }

        public Builder onClick(Consumer<InteractionContext> onClick) {
            this.onClick = onClick;
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> Builder onRender(Function<T, ItemStack> renderer, ReactiveState<T> state) {
            this.renderer = (Function<Object, ItemStack>) renderer;
            this.state = state;
            return this;
        }

        public Builder tickRate(int tickRate) {
            this.tickRate = tickRate;
            return this;
        }

        public GloomComponent build() {
            return new Impl(icon, onClick, renderer, state, tickRate);
        }

        private static final class Impl implements GloomComponent {
            private final ItemStack icon;
            private final Consumer<InteractionContext> onClick;
            private final Function<Object, ItemStack> renderer;
            private final ReactiveState<?> state;
            private final int tickRate;

            // 性能优化：静态组件渲染缓存
            private final ItemStack cachedIcon;
            private boolean dirty = true;
            private ItemStack cachedRender = null;

            private Impl(ItemStack icon, Consumer<InteractionContext> onClick, Function<Object, ItemStack> renderer, ReactiveState<?> state, int tickRate) {
                this.icon = icon != null ? icon : new ItemStack(Material.AIR);
                this.onClick = onClick;
                this.renderer = renderer;
                this.state = state;
                this.tickRate = tickRate;

                // 性能优化：预克隆静态图标（没有 renderer 和 state 的情况）
                if (renderer == null && state == null) {
                    this.cachedIcon = this.icon.clone();
                } else {
                    this.cachedIcon = null;
                }

                if (this.state != null) {
                    this.state.subscribe(v -> this.dirty = true);
                }
            }

            @Override
            public @NotNull ItemStack render(int index) {
                // 性能优化：静态组件直接返回缓存的图标
                if (cachedIcon != null) {
                    return cachedIcon;
                }

                // 响应式组件：使用脏标记缓存
                if (renderer != null && state != null) {
                    if (dirty || cachedRender == null) {
                        cachedRender = renderer.apply(state.get());
                        dirty = false;
                    }
                    return cachedRender;
                }

                // 其他情况返回 icon
                return icon;
            }

            @Override
            public void onClick(InteractionContext context) {
                if (onClick != null) {
                    onClick.accept(context);
                }
            }

            @Override
            public boolean onTick() {
                return state != null && dirty;
            }

            @Override
            public int getTickRate() {
                return tickRate;
            }

            @Override
            public GloomComponent clone() {
                try {
                    return (GloomComponent) super.clone();
                } catch (CloneNotSupportedException e) {
                    return new Impl(icon, onClick, renderer, state, tickRate);
                }
            }
        }
    }
}
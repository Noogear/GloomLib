package gloomlib.gui.component;

import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 可復用組件接口。
 * 支持多槽位渲染、克隆與響應式更新。
 */
public interface GloomComponent extends Cloneable {

    /**
     * 渲染組件。
     * @param index 組件內部的相對索引 (用於多格組件)。
     */
    @NotNull
    ItemStack render(int index);

    void onClick(InteractionContext context);

    boolean onTick();

    default void dispose() {}

    GloomComponent clone();

    static Builder builder() { return new Builder(); }

    class Builder {
        private Function<ReactiveState<?>, ItemStack> renderer;
        private ReactiveState<?> bindState;
        private Consumer<InteractionContext> clickHandler;
        private Consumer<GloomComponent> tickHandler;

        public <T> Builder onRender(Function<T, ItemStack> renderer, ReactiveState<T> state) {
            this.renderer = (Function<ReactiveState<?>, ItemStack>) (Object) renderer;
            this.bindState = state;
            return this;
        }

        public Builder icon(ItemStack item) {
            this.renderer = (ignored) -> item;
            return this;
        }

        public Builder onClick(Consumer<InteractionContext> handler) {
            this.clickHandler = handler;
            return this;
        }

        public Builder onTick(Consumer<GloomComponent> handler) {
            this.tickHandler = handler;
            return this;
        }

        public GloomComponent build() {
            return new SimpleGloomComponent(
                    (state, idx) -> renderer != null ? renderer.apply(state) : null,
                    bindState, clickHandler, tickHandler
            );
        }
    }
}
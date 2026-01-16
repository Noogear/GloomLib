package gloomlib.gui.component;

import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

public interface GloomComponent extends Cloneable {

    static Builder builder() {
        return new Builder();
    }

    @NotNull
    ItemStack render(int index);

    void onClick(InteractionContext context);

    boolean onTick();

    default boolean canInteract() {
        return false;
    }

    default int getTickRate() {
        return 0;
    }

    default void dispose() {
    }

    GloomComponent clone();

    class Builder {
        private Function<ReactiveState<?>, ItemStack> renderer;
        private ReactiveState<?> bindState;
        private Consumer<InteractionContext> clickHandler;
        private Consumer<GloomComponent> tickHandler;
        private boolean editable = false;
        private int tickRate = 0;

        public <T> Builder onRender(Function<T, ItemStack> renderer, ReactiveState<T> state) {
            this.renderer = (Function<ReactiveState<?>, ItemStack>) renderer;
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

        public Builder editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        public Builder tickRate(int rate) {
            this.tickRate = rate;
            return this;
        }

        public GloomComponent build() {
            return new SimpleGloomComponent(
                    (state, idx) -> renderer != null ? renderer.apply(state) : null,
                    bindState, clickHandler, tickHandler
            ) {
                @Override
                public boolean canInteract() {
                    return editable;
                }

                @Override
                public int getTickRate() {
                    return tickRate;
                }
            };
        }
    }
}
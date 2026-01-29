package gloomlib.gui.component;

import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Core component interface for creating interactive GUI elements.
 */
public interface GloomComponent extends Cloneable {

    /**
     * Creates a new builder for constructing a component.
     *
     * @return a new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Renders the component to an item stack.
     *
     * @param index the slot index
     * @return the rendered item stack
     */
    @NotNull ItemStack render(int index);

    /**
     * Handles click interactions on this component.
     *
     * @param context the interaction context
     */
    void onClick(InteractionContext context);

    /**
     * Called every tick to check for updates.
     *
     * @return true if the component changed
     */
    boolean onTick();

    /**
     * Gets the tick rate for this component.
     *
     * @return the tick interval
     */
    default int getTickRate() {
        return -1;
    }

    /**
     * Disposes resources held by this component.
     */
    default void dispose() {
    }

    /**
     * Creates a shallow copy of this component.
     *
     * @return a cloned component
     */
    GloomComponent clone();

    /**
     * Fluent builder for creating {@link GloomComponent} instances.
     */
    class Builder {
        private ItemStack icon;
        private Consumer<InteractionContext> onClick;
        private Function<Object, ItemStack> renderer;
        private ReactiveState<?> state;
        private int tickRate = -1;

        /**
         * Sets the static icon for this component.
         *
         * @param icon the item stack to display
         * @return this builder instance
         */
        public Builder icon(ItemStack icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Sets the click handler for this component.
         *
         * @param onClick the click handler
         * @return this builder instance
         */
        public Builder onClick(Consumer<InteractionContext> onClick) {
            this.onClick = onClick;
            return this;
        }

        /**
         * Sets a reactive renderer.
         *
         * @param renderer the rendering function
         * @param state the reactive state to observe
         * @param <T> the state type
         * @return this builder instance
         */
        @SuppressWarnings("unchecked")
        public <T> Builder onRender(Function<T, ItemStack> renderer, ReactiveState<T> state) {
            this.renderer = (Function<Object, ItemStack>) renderer;
            this.state = state;
            return this;
        }

        /**
         * Sets the tick rate for periodic updates.
         *
         * @param tickRate the tick interval
         * @return this builder instance
         */
        public Builder tickRate(int tickRate) {
            this.tickRate = tickRate;
            return this;
        }

        /**
         * Builds the component.
         *
         * @return a new component instance
         */
        public GloomComponent build() {
            return new Impl(icon, onClick, renderer, state, tickRate);
        }

        private static final class Impl implements GloomComponent {
            private final ItemStack icon;
            private final Consumer<InteractionContext> onClick;
            private final Function<Object, ItemStack> renderer;
            private final ReactiveState<?> state;
            private final int tickRate;

            private final ItemStack cachedIcon;
            private boolean dirty = true;
            private ItemStack cachedRender = null;

            private Impl(ItemStack icon, Consumer<InteractionContext> onClick, Function<Object, ItemStack> renderer, ReactiveState<?> state, int tickRate) {
                this.icon = icon != null ? icon : new ItemStack(Material.AIR);
                this.onClick = onClick;
                this.renderer = renderer;
                this.state = state;
                this.tickRate = tickRate;

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
                if (cachedIcon != null) {
                    return cachedIcon;
                }

                if (renderer != null && state != null) {
                    if (dirty || cachedRender == null) {
                        cachedRender = renderer.apply(state.get());
                        dirty = false;
                    }
                    return cachedRender;
                }

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

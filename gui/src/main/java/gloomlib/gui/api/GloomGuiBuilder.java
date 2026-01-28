package gloomlib.gui.api;

import gloomlib.gui.GloomGuiManager;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.navigation.NavigationManager;
import gloomlib.gui.template.GuiStructure;
import gloomlib.gui.window.AbstractWindow;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for creating {@link GloomGui} instances.
 * <p>
 * Supports structure-based layouts, component mapping, and navigation features.
 */
public class GloomGuiBuilder {

    private final Map<Character, GloomComponent> charComponents = new HashMap<>();
    private final Map<Integer, GloomComponent> slotComponents = new HashMap<>();
    private Component title;
    private int rows = 3;
    private InventoryType type = InventoryType.CHEST;
    private String[] structure;
    private Consumer<InventoryCloseEvent> closeAction;
    private Boolean manualAnimationEnable = null;
    private int manualTickRate = -1;
    private boolean navigationEnabled = false; // New: Navigation tracking

    private GloomGuiBuilder() {
    }

    public static GloomGuiBuilder create() {
        return new GloomGuiBuilder();
    }

    /**
     * Creates a new chest-type GUI builder (default size 3 rows).
     *
     * @return a new builder instance
     */
    public static GloomGuiBuilder chest() {
        return create().type(InventoryType.CHEST);
    }

    public static GloomGuiBuilder hopper() {
        return create().type(InventoryType.HOPPER).rows(1);
    }

    public static GloomGuiBuilder dispenser() {
        return create().type(InventoryType.DISPENSER).rows(3);
    }

    public GloomGuiBuilder title(Component title) {
        this.title = title;
        return this;
    }

    public GloomGuiBuilder rows(int rows) {
        this.rows = rows;
        return this;
    }

    public GloomGuiBuilder type(InventoryType type) {
        this.type = type;
        return this;
    }

    public GloomGuiBuilder enableAnimations(int tickRate) {
        this.manualAnimationEnable = true;
        this.manualTickRate = tickRate;
        return this;
    }

    public GloomGuiBuilder enableAnimations() {
        return enableAnimations(1);
    }

    public GloomGuiBuilder structure(String... pattern) {
        this.structure = pattern;
        this.rows = Math.max(this.rows, pattern.length);
        return this;
    }

    /**
     * Applies a predefined structure template to this builder.
     *
     * @param guiStructure the structure template
     * @return this builder for chaining
     */
    public GloomGuiBuilder applyStructure(GuiStructure guiStructure) {
        guiStructure.apply(this);
        return this;
    }

    public GloomGuiBuilder setComponent(char key, GloomComponent component) {
        this.charComponents.put(key, component);
        return this;
    }

    public GloomGuiBuilder setIngredient(char key, ItemStack item) {
        return setComponent(key, GloomComponent.builder().icon(item).build());
    }

    public GloomGuiBuilder setItem(int slot, GloomComponent component) {
        this.slotComponents.put(slot, component);
        return this;
    }

    public GloomGuiBuilder setItem(int slot, ItemStack item) {
        return setItem(slot, GloomComponent.builder().icon(item).build());
    }

    public GloomGuiBuilder define(char key, Consumer<GloomComponent.Builder> config) {
        GloomComponent.Builder builder = GloomComponent.builder();
        config.accept(builder);
        return setComponent(key, builder.build());
    }

    public GloomGuiBuilder define(char key, GloomComponent component) {
        return setComponent(key, component);
    }

    public GloomGuiBuilder onClose(Consumer<InventoryCloseEvent> closeAction) {
        this.closeAction = closeAction;
        return this;
    }

    /**
     * Enables navigation tracking for this GUI.
     * <p>
     * When enabled, this window will be pushed to the player's navigation stack when opened,
     * allowing them to use back buttons to return to previous windows.
     * <p>
     * Reference: Inspired by InvUI's parent window system, adapted for GloomLib's builder pattern.
     * 
     * @param enabled true to enable navigation tracking
     * @return this builder for chaining
     * @see <a href="https://github.com/NichtStudioCode/InvUI">InvUI Navigation Pattern</a>
     * @since 2.0
     */
    public GloomGuiBuilder navigationEnabled(boolean enabled) {
        this.navigationEnabled = enabled;
        return this;
    }

    /**
     * Enables navigation tracking (shorthand for navigationEnabled(true)).
     * <p>
     * Convenience method for the most common case.
     * 
     * @return this builder for chaining
     * @since 2.0
     */
    public GloomGuiBuilder withNavigation() {
        return navigationEnabled(true);
    }

    /**
     * Enables navigation and automatically adds back button behavior on close.
     * <p>
     * When the player closes this GUI (by pressing ESC), it will automatically
     * navigate back to the previous window if available.
     * <p>
     * This is the most convenient way to enable navigation with automatic back-on-close behavior.
     * 
     * @return this builder for chaining
     * @since 2.0
     */
    public GloomGuiBuilder withAutoBack() {
        this.navigationEnabled = true;
        
        // Chain the existing close action if any
        Consumer<InventoryCloseEvent> existingAction = this.closeAction;
        
        this.closeAction = event -> {
            // Execute existing action first
            if (existingAction != null) {
                existingAction.accept(event);
            }
            
            // Then handle auto-back
            Player player = (Player) event.getPlayer();
            player.getScheduler().runDelayed(
                GloomGuiManager.getPlugin(),
                task -> {
                    if (NavigationManager.getInstance().hasHistory(player)) {
                        NavigationManager.getInstance().back(player);
                    }
                },
                null,
                2L  // Delay 2 ticks to avoid conflicts
            );
        };
        
        return this;
    }

    public void open(Player player) {
        GloomGui gui = build(player);
        int size = (type == InventoryType.CHEST) ? rows * 9 : type.getDefaultSize();
        AbstractWindow window = new AbstractWindow(player, title, gui, type, size);
        
        // Push to navigation stack if enabled
        if (navigationEnabled) {
            NavigationManager.getInstance().push(player, window);
        }
        
        window.open();
    }

    /**
     * Builds the GUI for a specific player.
     *
     * @param player the player to build for
     * @return a new GloomGui instance
     */
    public GloomGui build(Player player) {
        if (title == null) {
            title = Component.text("GloomGui");
        }

        GuiConfiguration config = resolveConfiguration();

        return new GloomGui(
                player,
                title,
                rows,
                type,
                config,
                closeAction,
                structure,
                charComponents,
                slotComponents
        );
    }

    private GuiConfiguration resolveConfiguration() {
        if (manualAnimationEnable != null && manualAnimationEnable) {
            return new GuiConfiguration(GuiConfiguration.UpdateStrategy.PERIODIC, manualTickRate, true);
        }

        int minDetectedRate = Integer.MAX_VALUE;
        boolean hasAnimatedComponents = false;

        for (GloomComponent comp : charComponents.values()) {
            int r = comp.getTickRate();
            if (r > 0) {
                hasAnimatedComponents = true;
                minDetectedRate = Math.min(minDetectedRate, r);
            }
        }
        for (GloomComponent comp : slotComponents.values()) {
            int r = comp.getTickRate();
            if (r > 0) {
                hasAnimatedComponents = true;
                minDetectedRate = Math.min(minDetectedRate, r);
            }
        }

        if (hasAnimatedComponents) {
            return new GuiConfiguration(GuiConfiguration.UpdateStrategy.PERIODIC, minDetectedRate, true);
        } else {
            return GuiConfiguration.REACTIVE;
        }
    }
}
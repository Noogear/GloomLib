package gloomlib.gui.window;

import gloomlib.gui.GloomGuiManager;
import gloomlib.gui.api.GloomGui;
import gloomlib.gui.config.GuiConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Base implementation of {@link Window} with state management and lifecycle handling.
 * <p>
 * Tracks window state (open/closed/closing), manages component observers,
 * and handles periodic updates with desync detection.
 */
public class AbstractWindow implements Window, InventoryHolder, Observer {

    /**
     * The player viewing this window.
     */
    protected final Player viewer;

    /**
     * The window title component.
     */
    protected final Component title;

    /**
     * The GUI managing components for this window.
     */
    protected final GloomGui gui;
    /**
     * Whether the window is closed.
     */
    protected final AtomicBoolean isClosed = new AtomicBoolean(false);
    /**
     * Tick counters for each slot.
     */
    protected final Map<Integer, Integer> slotTickCounters = new ConcurrentHashMap<>();
    /**
     * State change handlers.
     */
    protected final Map<String, BiConsumer<WindowState, WindowState>> stateChangeHandlers = new ConcurrentHashMap<>();
    /**
     * The inventory type.
     */
    private final InventoryType type;
    /**
     * The inventory size (for chest types).
     */
    private final int size;
    /**
     * The Bukkit inventory instance.
     */
    protected Inventory inventory;

    /**
     * Server-side window state.
     */
    protected volatile WindowState serverWindowState = WindowState.CLOSED;

    /**
     * Client-side window state.
     */
    protected volatile WindowState clientWindowState = WindowState.CLOSED;

    /**
     * Last ping time for desync detection.
     */
    protected volatile long lastPingTime = 0;

    /**
     * Last periodic update check time.
     */
    protected volatile long lastUpdatePeriodCheck = 0;

    /**
     * Constructs a new abstract window.
     *
     * @param viewer the player viewing the window
     * @param title  the window title
     * @param gui    the GUI instance
     * @param type   the inventory type
     * @param size   the inventory size (for chest types)
     */
    public AbstractWindow(Player viewer, Component title, GloomGui gui, InventoryType type, int size) {
        this.viewer = viewer;
        this.title = title;
        this.gui = gui;
        this.type = type;
        this.size = size;
    }

    /**
     * Creates the Bukkit inventory for this window.
     *
     * @return the created inventory
     * @throws IllegalArgumentException if chest size is invalid
     */
    protected Inventory createInventory() {
        if (type == InventoryType.CHEST) {
            if (size <= 0 || size > 54 || size % 9 != 0) {
                throw new IllegalArgumentException("Chest inventory size must be a multiple of 9 and between 9 and 54. Given: " + size);
            }
            return Bukkit.createInventory(this, size, title);
        } else {
            return Bukkit.createInventory(this, type, title);
        }
    }

    @Override
    public void open() {
        this.inventory = createInventory();
        gui.bindToWindow(this);

        for (int slot = 0; slot < gui.getSize(); slot++) {
            gui.addObserver(this, slot, slot);
        }

        GuiConfiguration config = gui.getConfiguration();
        if (config.updateStrategy() == GuiConfiguration.UpdateStrategy.PERIODIC) {
            GloomGuiManager.register(this, config.tickRate());
        }

        changeWindowState(WindowState.OPEN);

        viewer.openInventory(inventory);

        clientWindowState = WindowState.OPEN;
        lastPingTime = System.currentTimeMillis();
    }

    @Override
    public void close() {
        if (!isClosed.get()) {
            changeWindowState(WindowState.CLOSING);
            viewer.closeInventory();
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        isClosed.set(true);
        changeWindowState(WindowState.CLOSED);
        clientWindowState = WindowState.CLOSED;

        GloomGuiManager.unregister(this);
        gui.removeAllObservers(this);
        gui.handleClose(event);
    }

    @Override
    public void tick() {
        if (isClosed.get()) return;

        checkWindowStateSync();

        checkPeriodicUpdates();

        gui.tick();
    }

    /**
     * Checks for window state desync between client and server.
     * <p>
     * Pings every 1 second to detect if the player closed the inventory client-side.
     */
    protected void checkWindowStateSync() {
        long now = System.currentTimeMillis();
        if (now - lastPingTime > 1000) {
            lastPingTime = now;

            if (serverWindowState == WindowState.OPEN && viewer.getOpenInventory().getTopInventory() != inventory) {
                clientWindowState = WindowState.CLOSED;
                handleDesync();
            }
        }
    }

    /**
     * Checks for periodic component updates based on tick counters.
     * <p>
     * Increments per-slot counters and marks dirty when update period reached.
     */
    protected void checkPeriodicUpdates() {
        for (int slot = 0; slot < gui.getSize(); slot++) {
            int updatePeriod = gui.getUpdatePeriod(slot);

            if (updatePeriod > 0) {
                int ticks = slotTickCounters.getOrDefault(slot, 0) + 1;

                if (ticks >= updatePeriod) {
                    gui.markDirty(slot);
                    slotTickCounters.put(slot, 0);
                } else {
                    slotTickCounters.put(slot, ticks);
                }
            }
        }
    }

    /**
     * Handles client-server desync detection.
     * <p>
     * Called when the client closed but server state is still open.
     * Forces window closure and cleanup.
     */
    protected void handleDesync() {
        if (clientWindowState == WindowState.CLOSED && serverWindowState != WindowState.CLOSED) {
            changeWindowState(WindowState.CLOSED);
            isClosed.set(true);
            GloomGuiManager.unregister(this);
            gui.removeAllObservers(this);
        }
    }

    /**
     * Changes the window state and notifies registered handlers.
     *
     * @param newState the new window state
     */
    protected void changeWindowState(WindowState newState) {
        WindowState oldState = serverWindowState;
        if (oldState == newState) return;

        serverWindowState = newState;

        String key = oldState + "_to_" + newState;
        BiConsumer<WindowState, WindowState> handler = stateChangeHandlers.get(key);
        if (handler != null) {
            handler.accept(oldState, newState);
        }

        BiConsumer<WindowState, WindowState> wildcardHandler = stateChangeHandlers.get("*");
        if (wildcardHandler != null) {
            wildcardHandler.accept(oldState, newState);
        }
    }

    /**
     * Registers a handler for a specific state transition.
     *
     * @param fromState the starting state
     * @param toState   the ending state
     * @param handler   the handler to invoke
     */
    public void onStateChange(WindowState fromState, WindowState toState, BiConsumer<WindowState, WindowState> handler) {
        String key = fromState + "_to_" + toState;
        stateChangeHandlers.put(key, handler);
    }

    /**
     * Registers a handler for any state change.
     *
     * @param handler the handler to invoke on any transition
     */
    public void onAnyStateChange(BiConsumer<WindowState, WindowState> handler) {
        stateChangeHandlers.put("*", handler);
    }

    /**
     * Gets the server-side window state.
     *
     * @return the server window state
     */
    public WindowState getServerWindowState() {
        return serverWindowState;
    }

    /**
     * Gets the client-side window state.
     *
     * @return the client window state
     */
    public WindowState getClientWindowState() {
        return clientWindowState;
    }

    @Override
    public GloomGui getGui() {
        return gui;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    @Override
    public Player getViewer() {
        return viewer;
    }

    @Override
    public boolean isClosed() {
        return isClosed.get();
    }

    /**
     * Updates the window title for all viewers.
     *
     * @param title the new title component
     */
    @SuppressWarnings("deprecation")
    public void updateTitle(final Component title) {
        final List<HumanEntity> viewers = getInventory().getViewers();
        if (!viewers.isEmpty()) {
            final String legacyTitle = LegacyComponentSerializer.legacySection().serialize(title);
            for (HumanEntity humanEntity : viewers) {
                humanEntity.getOpenInventory().setTitle(legacyTitle);
            }
        }
    }

    /**
     * Updates the window title for a specific viewer.
     *
     * @param viewer the viewer entity
     * @param title  the new title component
     */
    @SuppressWarnings("deprecation")
    public void updateTitle(@NotNull final HumanEntity viewer, final Component title) {
        final InventoryView openView = viewer.getOpenInventory();
        if (openView.getTopInventory().equals(getInventory())) {
            openView.setTitle(LegacyComponentSerializer.legacySection().serialize(title));
        }
    }

    @Override
    public void notifyUpdate(int slot) {
        if (isClosed.get() || inventory == null) {
            return;
        }

        ItemStack newItem = gui.renderSlot(slot);
        inventory.setItem(slot, newItem);
    }

    /**
     * Window lifecycle states.
     */
    public enum WindowState {
        /**
         * Window is open and active.
         */
        OPEN,
        /**
         * Window is in the process of closing.
         */
        CLOSING,
        /**
         * Window is fully closed.
         */
        CLOSED
    }
}
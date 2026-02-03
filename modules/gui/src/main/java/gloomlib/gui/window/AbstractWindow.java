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
 * Base implementation of Window with state management and lifecycle handling.
 */
public class AbstractWindow implements Window, InventoryHolder, Observer {

    protected final Player viewer;
    protected final Component title;
    protected final GloomGui gui;
    protected final AtomicBoolean isClosed = new AtomicBoolean(false);
    protected final Map<Integer, Integer> slotTickCounters = new ConcurrentHashMap<>();
    protected final Map<String, BiConsumer<WindowState, WindowState>> stateChangeHandlers = new ConcurrentHashMap<>();
    private final InventoryType type;
    private final int size;
    protected Inventory inventory;
    protected volatile WindowState serverWindowState = WindowState.CLOSED;
    protected volatile WindowState clientWindowState = WindowState.CLOSED;
    protected volatile long lastPingTime = 0;
    protected volatile long lastUpdatePeriodCheck = 0;

    /**
     * Constructs a new abstract window.
     *
     * @param viewer the viewer player
     * @param title  the window title
     * @param gui    the GUI instance
     * @param type   the inventory type
     * @param size   the inventory size
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
     * Checks for periodic component updates.
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
     * Changes the window state and notifies handlers.
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
     * @param handler   the handler
     */
    public void onStateChange(WindowState fromState, WindowState toState, BiConsumer<WindowState, WindowState> handler) {
        String key = fromState + "_to_" + toState;
        stateChangeHandlers.put(key, handler);
    }

    /**
     * Registers a handler for any state change.
     *
     * @param handler the handler
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
     * @param viewer the viewer player
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

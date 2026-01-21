package gloomlib.gui.window;

import gloomlib.gui.GloomGuiManager;
import gloomlib.gui.api.GloomGui;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.observable.Observer;
import gloomlib.gui.util.GuiSecurity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
 * Abstract base class for all window types, providing window state tracking.
 * <p>
 * Window state tracking follows InvUI's ping/pong synchronization mechanism:
 * <ul>
 *     <li>Server maintains its own view of window state (serverWindowState)</li>
 *     <li>Client's actual state is tracked (clientWindowState)</li>
 *     <li>Periodic ping checks detect desynchronization</li>
 *     <li>State change handlers are notified of transitions</li>
 * </ul>
 * 
 * @see <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui-core/src/main/java/xyz/xenondevs/invui/window/AbstractWindow.java#L200-250">InvUI AbstractWindow.java#L200-250</a>
 */
public abstract class AbstractWindow implements Window, InventoryHolder, Observer {

    /**
     * Represents the state of a window.
     */
    public enum WindowState {
        /** Window is open and active */
        OPEN,
        /** Window is closing */
        CLOSING,
        /** Window is closed */
        CLOSED
    }

    protected final Player viewer;
    protected final Component title;
    protected final GloomGui gui;
    protected final AtomicBoolean isClosed = new AtomicBoolean(false);
    protected Inventory inventory;

    /**
     * Server's view of the window state.
     * This is what the server thinks the window state should be.
     */
    protected volatile WindowState serverWindowState = WindowState.CLOSED;

    /**
     * Client's actual window state, as detected by server.
     * May differ from serverWindowState due to network lag or client actions.
     */
    protected volatile WindowState clientWindowState = WindowState.CLOSED;

    /**
     * Last ping time for state synchronization check (in milliseconds).
     */
    protected volatile long lastPingTime = 0;

    /**
     * Last time update periods were checked (in milliseconds).
     */
    protected volatile long lastUpdatePeriodCheck = 0;

    /**
     * Tick counters for each slot's update period.
     */
    protected final Map<Integer, Integer> slotTickCounters = new ConcurrentHashMap<>();

    /**
     * Handlers invoked when window state changes.
     * Key: (oldState, newState) -> handler
     */
    protected final Map<String, BiConsumer<WindowState, WindowState>> stateChangeHandlers = new ConcurrentHashMap<>();

    public AbstractWindow(Player viewer, Component title, GloomGui gui) {
        this.viewer = viewer;
        this.title = title;
        this.gui = gui;
    }

    protected abstract Inventory createInventory();

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

        // Update window state
        changeWindowState(WindowState.OPEN);

        viewer.openInventory(inventory);
        
        // Mark client state as open after opening
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
        GuiSecurity.cleanInventory((Player) event.getPlayer());
    }

    @Override
    public void tick() {
        if (isClosed.get()) return;
        
        // Ping/pong state synchronization check
        checkWindowStateSync();
        
        // Check for automatic periodic updates based on Observable.getUpdatePeriod()
        checkPeriodicUpdates();
        
        gui.tick();
    }

    /**
     * Checks if window state is synchronized between server and client.
     * Performs periodic "ping" to detect if client closed the window without notification.
     * Inspired by InvUI's state synchronization mechanism.
     */
    protected void checkWindowStateSync() {
        long now = System.currentTimeMillis();
        if (now - lastPingTime > 1000) { // Check every second
            lastPingTime = now;
            
            // Detect desynchronization: server thinks window is open, but client closed it
            if (serverWindowState == WindowState.OPEN && viewer.getOpenInventory().getTopInventory() != inventory) {
                // Client closed window without server knowing
                clientWindowState = WindowState.CLOSED;
                handleDesync();
            }
        }
    }

    /**
     * Checks for slots that need periodic updates based on their Observable.getUpdatePeriod().
     * <p>
     * For each slot, if the observable has a positive update period, this method tracks
     * the number of ticks elapsed and marks the slot as dirty when the period expires.
     * <p>
     * This enables automatic UI updates for time-dependent displays like:
     * <ul>
     *     <li>Real-time clocks</li>
     *     <li>Countdown timers</li>
     *     <li>Loading animations</li>
     *     <li>Status indicators that refresh periodically</li>
     * </ul>
     * <p>
     * Inspired by InvUI's automatic update period mechanism.
     * 
     * @see Observable#getUpdatePeriod(int)
     * @see <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui-core/src/main/java/xyz/xenondevs/invui/window/AbstractWindow.java#L100-120">InvUI AbstractWindow.java#L100-120</a>
     */
    protected void checkPeriodicUpdates() {
        for (int slot = 0; slot < gui.getSize(); slot++) {
            int updatePeriod = gui.getUpdatePeriod(slot);
            
            if (updatePeriod > 0) {
                // Get or initialize tick counter for this slot
                int ticks = slotTickCounters.getOrDefault(slot, 0) + 1;
                
                if (ticks >= updatePeriod) {
                    // Period elapsed, mark slot as dirty and reset counter
                    gui.markDirty(slot);
                    slotTickCounters.put(slot, 0);
                } else {
                    // Increment counter
                    slotTickCounters.put(slot, ticks);
                }
            }
        }
    }

    /**
     * Handles desynchronization between server and client window state.
     * Called when client's actual state differs from server's expected state.
     */
    protected void handleDesync() {
        // Force server state to match client state
        if (clientWindowState == WindowState.CLOSED && serverWindowState != WindowState.CLOSED) {
            changeWindowState(WindowState.CLOSED);
            isClosed.set(true);
            GloomGuiManager.unregister(this);
            gui.removeAllObservers(this);
        }
    }

    /**
     * Changes the server's view of window state and notifies handlers.
     * 
     * @param newState the new window state
     */
    protected void changeWindowState(WindowState newState) {
        WindowState oldState = serverWindowState;
        if (oldState == newState) return;
        
        serverWindowState = newState;
        
        // Notify state change handlers
        String key = oldState + "_to_" + newState;
        BiConsumer<WindowState, WindowState> handler = stateChangeHandlers.get(key);
        if (handler != null) {
            handler.accept(oldState, newState);
        }
        
        // Also notify wildcard handler
        BiConsumer<WindowState, WindowState> wildcardHandler = stateChangeHandlers.get("*");
        if (wildcardHandler != null) {
            wildcardHandler.accept(oldState, newState);
        }
    }

    /**
     * Registers a handler for specific window state transitions.
     * 
     * @param fromState the source state
     * @param toState the target state
     * @param handler the handler to invoke
     */
    public void onStateChange(WindowState fromState, WindowState toState, BiConsumer<WindowState, WindowState> handler) {
        String key = fromState + "_to_" + toState;
        stateChangeHandlers.put(key, handler);
    }

    /**
     * Registers a handler for all window state transitions.
     * 
     * @param handler the handler to invoke on any state change
     */
    public void onAnyStateChange(BiConsumer<WindowState, WindowState> handler) {
        stateChangeHandlers.put("*", handler);
    }

    /**
     * Gets the current server-side window state.
     * 
     * @return the server window state
     */
    public WindowState getServerWindowState() {
        return serverWindowState;
    }

    /**
     * Gets the current client-side window state (as detected by server).
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
}
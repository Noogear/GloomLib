package gloomlib.gui.navigation;

import gloomlib.gui.window.Window;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Maintains navigation history for a single player.
 */
public final class NavigationHistory {

    private static final long CLEANUP_INTERVAL_MS = 1000;
    private static final int MAX_SEQUENCE_LENGTH = 10;
    private final Player player;
    private final Deque<WeakReference<Window>> stack = new ArrayDeque<>();
    private final Object lock = new Object();
    private volatile int maxDepth = 50;
    private volatile long lastCleanupTime = 0;

    NavigationHistory(@NotNull Player player) {
        this.player = player;
    }

    /**
     * Pushes a window onto the navigation stack.
     *
     * @param window the window to push
     */
    public void push(@NotNull Window window) {
        synchronized (lock) {
            if (window.isClosed()) {
                return;
            }

            cleanDeadReferencesThrottled();

            if (!stack.isEmpty()) {
                WeakReference<Window> topRef = stack.peek();
                if (topRef != null) {
                    Window topWindow = topRef.get();
                    if (topWindow == window) {
                        return;
                    }
                }
            }

            stack.push(new WeakReference<>(window));

            deduplicateRepeatingSequences();

            if (stack.size() >= maxDepth) {
                stack.removeFirst();
            }
        }
    }

    /**
     * Navigates back to the previous window.
     *
     * @return true if navigation was successful
     */
    public boolean back() {
        return backInternal(0);
    }

    private boolean backInternal(int recursionDepth) {
        if (recursionDepth > 10) {
            return false;
        }

        synchronized (lock) {
            cleanDeadReferencesThrottled();

            if (stack.isEmpty()) {
                return false;
            }

            WeakReference<Window> previousRef = stack.poll();
            if (previousRef == null) {
                return false;
            }

            Window previous = previousRef.get();

            if (previous == null || previous.isClosed()) {
                return backInternal(recursionDepth + 1);
            }

            Window currentWindow = getCurrentOpenWindow();
            if (currentWindow == previous) {
                return backInternal(recursionDepth + 1);
            }

            if (currentWindow != null && !currentWindow.isClosed()) {
                currentWindow.close();
            }

            previous.open();
            return true;
        }
    }

    /**
     * Peeks at the previous window without removing it from the stack.
     *
     * @return the previous window
     */
    public @Nullable Window peek() {
        synchronized (lock) {
            cleanDeadReferencesThrottled();

            if (stack.isEmpty()) {
                return null;
            }

            WeakReference<Window> ref = stack.peek();
            return ref != null ? ref.get() : null;
        }
    }

    /**
     * Clears the entire navigation history.
     */
    public void clear() {
        synchronized (lock) {
            stack.clear();
            lastCleanupTime = 0;
        }
    }

    /**
     * Checks if there is any navigation history.
     *
     * @return true if history exists
     */
    public boolean hasHistory() {
        synchronized (lock) {
            cleanDeadReferencesThrottled();
            return !stack.isEmpty();
        }
    }

    /**
     * Gets the current depth of the navigation stack.
     *
     * @return the stack size
     */
    public int getDepth() {
        synchronized (lock) {
            cleanDeadReferencesThrottled();
            return stack.size();
        }
    }

    /**
     * Sets the maximum depth of the navigation stack.
     *
     * @param maxDepth the maximum depth
     */
    public void setMaxDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("Max depth must be positive");
        }
        this.maxDepth = maxDepth;
    }

    private void cleanDeadReferencesThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }

        lastCleanupTime = now;
        cleanDeadReferences();
    }

    private void cleanDeadReferences() {
        stack.removeIf(ref -> {
            Window window = ref.get();
            return window == null || window.isClosed();
        });
    }

    private void deduplicateRepeatingSequences() {
        int size = stack.size();
        if (size < 4) return;

        @SuppressWarnings("unchecked")
        WeakReference<Window>[] stackArray = new WeakReference[size];
        Window[] windows = new Window[size];
        int[] hashes = new int[size];

        int index = size - 1;
        for (WeakReference<Window> ref : stack) {
            stackArray[index] = ref;
            windows[index] = ref.get();
            if (windows[index] == null) {
                return;
            }
            hashes[index] = System.identityHashCode(windows[index]);
            index--;
        }

        int maxLen = Math.min(size / 2, MAX_SEQUENCE_LENGTH);

        for (int seqLen = maxLen; seqLen >= 2; seqLen--) {
            int seq1Start = size - seqLen;

            int seq2Start = seq1Start - seqLen;

            if (seq2Start < 0) continue;

            if (seq2Start == 0) continue;

            if (hashes[seq1Start] != hashes[seq2Start] ||
                    hashes[size - 1] != hashes[seq1Start - 1]) {
                continue;
            }

            boolean isRepeating = true;
            for (int i = 0; i < seqLen; i++) {
                if (hashes[seq1Start + i] != hashes[seq2Start + i] ||
                        windows[seq1Start + i] != windows[seq2Start + i]) {
                    isRepeating = false;
                    break;
                }
            }

            if (isRepeating) {
                for (int i = 0; i < seqLen; i++) {
                    stack.removeLast();
                }
                return;
            }
        }
    }

    private @Nullable Window getCurrentOpenWindow() {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Window window) {
            return window;
        }
        return null;
    }

    /**
     * Gets the player associated with this history.
     *
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }
}

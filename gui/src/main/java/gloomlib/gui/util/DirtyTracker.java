package gloomlib.gui.util;

import java.util.BitSet;

public class DirtyTracker {

    private final BitSet dirtySlots;
    private boolean globalDirty = false;

    public DirtyTracker(int size) {
        this.dirtySlots = new BitSet(size);
    }

    public void markGlobal() {
        this.globalDirty = true;
    }

    public void mark(int slot) {
        synchronized (dirtySlots) {
            dirtySlots.set(slot);
        }
    }

    public boolean isDirty() {
        return globalDirty || !dirtySlots.isEmpty();
    }

    public boolean isGlobalDirty() {
        return globalDirty;
    }

    public BitSet popDirtySlots() {
        synchronized (dirtySlots) {
            BitSet clone = (BitSet) dirtySlots.clone();
            dirtySlots.clear();
            if (globalDirty) {
                globalDirty = false;
            }
            return clone;
        }
    }
}
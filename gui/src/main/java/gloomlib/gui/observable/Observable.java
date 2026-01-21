package gloomlib.gui.observable;

import org.jetbrains.annotations.NotNull;

public interface Observable {

    void addObserver(@NotNull Observer who, int what, int how);

    void removeObserver(@NotNull Observer who, int what, int how);

    void removeAllObservers(@NotNull Observer who);

    default int getUpdatePeriod(int what) {
        return -1;
    }
}

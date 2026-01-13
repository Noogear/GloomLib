package gloomlib.gui.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Paginator<T> {

    private final List<T> allItems;
    private final int pageSize;

    public Paginator(List<T> items, int pageSize) {
        this.allItems = new ArrayList<>(items);
        this.pageSize = Math.max(1, pageSize);
    }

    public int getTotalPages() {
        return (int) Math.ceil((double) allItems.size() / pageSize);
    }

    public List<T> getPage(int page) {
        if (page < 0 || page >= getTotalPages()) {
            return Collections.emptyList();
        }

        int start = page * pageSize;
        int end = Math.min(start + pageSize, allItems.size());

        return allItems.subList(start, end);
    }

    public boolean hasNext(int currentPage) {
        return currentPage < getTotalPages() - 1;
    }

    public boolean hasPrev(int currentPage) {
        return currentPage > 0;
    }

    public int size() {
        return allItems.size();
    }
}
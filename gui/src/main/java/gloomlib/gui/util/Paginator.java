package gloomlib.gui.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for paginating collections.
 *
 * @param <T> the item type
 */
public class Paginator<T> {

    private final List<T> allItems;
    private final int pageSize;

    /**
     * Constructs a paginator with items and page size.
     *
     * @param items    the items
     * @param pageSize the page size
     */
    public Paginator(List<T> items, int pageSize) {
        this.allItems = new ArrayList<>(items);
        this.pageSize = Math.max(1, pageSize);
    }

    /**
     * Gets the total number of pages.
     *
     * @return the total pages
     */
    public int getTotalPages() {
        return (int) Math.ceil((double) allItems.size() / pageSize);
    }

    /**
     * Gets items for a specific page.
     *
     * @param page the page index
     * @return the items for the page
     */
    public List<T> getPage(int page) {
        if (page < 0 || page >= getTotalPages()) {
            return Collections.emptyList();
        }

        int start = page * pageSize;
        int end = Math.min(start + pageSize, allItems.size());

        return allItems.subList(start, end);
    }

    /**
     * Checks if there is a next page.
     *
     * @param currentPage the current page index
     * @return true if a next page exists
     */
    public boolean hasNext(int currentPage) {
        return currentPage < getTotalPages() - 1;
    }

    /**
     * Checks if there is a previous page.
     *
     * @param currentPage the current page index
     * @return true if a previous page exists
     */
    public boolean hasPrev(int currentPage) {
        return currentPage > 0;
    }

    /**
     * Gets the total number of items.
     *
     * @return the size
     */
    public int size() {
        return allItems.size();
    }
}
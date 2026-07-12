package fr.ayoub.pvp.domain.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The geometry of a chest menu: how many slots it has, which ones hold content,
 * and how a long list is split into pages.
 *
 * Pure maths — no Bukkit. Reused by every menu in the engine
 * (mode select, leaderboard, class select, talent tree).
 */
public final class MenuLayout {

    private static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;

    private final int rows;
    private final List<Integer> contentSlots;

    private MenuLayout(int rows, List<Integer> contentSlots) {
        this.rows = rows;
        this.contentSlots = List.copyOf(contentSlots);
    }

    /** Every slot holds content. */
    public static MenuLayout borderless(int rows) {
        checkRows(rows);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < rows * COLUMNS; slot++) {
            slots.add(slot);
        }
        return new MenuLayout(rows, slots);
    }

    /**
     * Content lives in the inner area; the outer ring is left for decoration and
     * for the previous / next page buttons.
     */
    public static MenuLayout bordered(int rows) {
        checkRows(rows);
        if (rows < 3) {
            throw new IllegalArgumentException("a bordered menu needs at least 3 rows, got " + rows);
        }
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int column = 1; column < COLUMNS - 1; column++) {
                slots.add(row * COLUMNS + column);
            }
        }
        return new MenuLayout(rows, slots);
    }

    private static void checkRows(int rows) {
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("a chest has between 1 and " + MAX_ROWS + " rows, got " + rows);
        }
    }

    public int rows() {
        return rows;
    }

    /** Total slots in the inventory. */
    public int size() {
        return rows * COLUMNS;
    }

    public int itemsPerPage() {
        return contentSlots.size();
    }

    /** The inventory slot that holds the item at {@code index} on a page. */
    public int slotAt(int index) {
        return contentSlots.get(index);
    }

    public List<Integer> contentSlots() {
        return contentSlots;
    }

    /** At least 1, so an empty menu still renders. */
    public int pageCount(int totalItems) {
        if (totalItems <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalItems / itemsPerPage());
    }

    /** The slice of {@code items} shown on a 0-based page. Empty if the page is past the end. */
    public <T> List<T> pageItems(List<T> items, int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got " + page);
        }
        int from = page * itemsPerPage();
        if (from >= items.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(from + itemsPerPage(), items.size());
        return List.copyOf(items.subList(from, to));
    }
}

package com.betterlist.data;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Holds the set of items the player is looking for, and answers which tracked chests
 * contain them.
 *
 * This is deliberately separate from {@link ChestHighlightManager}: that one is a manual
 * per-chest toggle (the 💡 button), this one is derived from item ids and changes whenever
 * a chest is rescanned. Keeping them apart lets the world renderer draw them differently,
 * so "I asked for this chest" and "this chest has what you want" never look alike.
 *
 * Item ids are registry keys ({@code BuiltInRegistries.ITEM.getKey(...).toString()}) — the
 * same form {@code AbstractContainerScreenMixin} writes into {@link ContainerDataManager},
 * so matching is a direct lookup with no translation layer to drift.
 *
 * Memory-only, like the highlight set: cleared on disconnect.
 */
@Environment(EnvType.CLIENT)
public final class ChestSearchManager {

    // Insertion-ordered so the newest search is easy to reason about when debugging.
    private static final Set<String> searchedItems = new LinkedHashSet<>();

    private ChestSearchManager() {}

    public static boolean isSearched(String itemId) {
        return itemId != null && searchedItems.contains(itemId);
    }

    /** Left-click on a material-list row. Returns the new state (true = now searched). */
    public static boolean toggle(String itemId) {
        if (itemId == null) return false;
        if (searchedItems.remove(itemId)) return false;
        searchedItems.add(itemId);
        return true;
    }

    public static boolean isEmpty() {
        return searchedItems.isEmpty();
    }

    public static Set<String> all() {
        return new LinkedHashSet<>(searchedItems);
    }

    public static void clear() {
        searchedItems.clear();
    }

    /** Tracked chests holding a specific item, regardless of whether it is being searched. */
    public static Set<String> containersHolding(String itemId) {
        if (itemId == null) return Set.of();

        Set<String> hits = new HashSet<>();
        for (String containerId : ContainerDataManager.getMarkedContainers()) {
            Integer count = ContainerDataManager.getContainerContents(containerId).get(itemId);
            if (count != null && count > 0) hits.add(containerId);
        }
        return hits;
    }

    /** How many of an item sit across all tracked chests. */
    public static int totalStored(String itemId) {
        if (itemId == null) return 0;

        int total = 0;
        for (String containerId : ContainerDataManager.getMarkedContainers()) {
            Integer count = ContainerDataManager.getContainerContents(containerId).get(itemId);
            if (count != null) total += count;
        }
        return total;
    }

    /**
     * Tracked chests holding at least one of the searched items.
     *
     * Computed on demand rather than cached: the answer depends on chest contents, which
     * change on every rescan, and a stale cache would point the player at a chest they
     * already emptied. The set of tracked chests is small and this early-outs to nothing
     * in the common case where no search is active.
     */
    public static Set<String> matchingContainers() {
        if (searchedItems.isEmpty()) return Set.of();

        Set<String> hits = new HashSet<>();
        for (String containerId : ContainerDataManager.getMarkedContainers()) {
            Map<String, Integer> contents = ContainerDataManager.getContainerContents(containerId);
            for (String itemId : searchedItems) {
                Integer count = contents.get(itemId);
                if (count != null && count > 0) {
                    hits.add(containerId);
                    break;
                }
            }
        }
        return hits;
    }
}

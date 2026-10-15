package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Seating plan from one death - not a copy of the items.
 *
 * <p>A full serialised inventory in player data is tens of kilobytes rewritten on every
 * autosave and, worse, a second instance of truth about what the player owned.
 * The grave is the only instance of truth; this is solely the seating of the guests.
 */
public final class SlotSnapshot {

    /** The main inventory keeps its own indices 0-35. */
    public static final int ARMOR_BASE = 100;
    public static final int OFFHAND_SLOT = 150;
    /** Reserved for Curios. Unused in v1. */
    public static final int CURIOS_BASE = 200;

    private final long capturedAt;
    private final List<SlotEntry> entries;

    public SlotSnapshot(long capturedAt) {
        this(capturedAt, new ArrayList<SlotEntry>());
    }

    public SlotSnapshot(long capturedAt, List<SlotEntry> entries) {
        this.capturedAt = capturedAt;
        this.entries = new ArrayList<SlotEntry>(entries);
    }

    public void add(int slot, ItemKey key, int count) {
        entries.add(new SlotEntry(slot, key, count));
    }

    /** Wall clock in milliseconds - the same scale as the grave's deathDate. */
    public long capturedAt() {
        return capturedAt;
    }

    public List<SlotEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}

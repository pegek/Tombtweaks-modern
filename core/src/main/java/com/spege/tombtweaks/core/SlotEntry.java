package com.spege.tombtweaks.core;

/** One remembered seat: what sat where at the moment of death. */
public final class SlotEntry {

    private final int slot;
    private final ItemKey key;
    private final int count;

    public SlotEntry(int slot, ItemKey key, int count) {
        this.slot = slot;
        this.key = key;
        this.count = count;
    }

    public int slot() {
        return slot;
    }

    public ItemKey key() {
        return key;
    }

    /**
     * Quantity at the moment of death. Stored for diagnostics, NEVER a matching criterion:
     * percentLossOnDeath shrinks stacks inside the grave before anyone sees them.
     */
    public int count() {
        return count;
    }
}

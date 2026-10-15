package com.spege.tombtweaks.core;

/**
 * Stack identity: just enough to recognise it on its way back from the grave, and not a gram more.
 *
 * <p>On 1.12.2 the key was (name, metadata, NBT hash). On 1.20.1 there is no metadata,
 * and durability lives in NBT - hence the (name, NBT hash) pair, and hence also the fact that
 * matching must have a fallback tier: serialising a stack into the grave and back can
 * normalise the tag, and then the hash changes. See {@link SlotPlan}.
 *
 * <p>{@code nbtHash} must be stable across JVM restarts, because snapshots are persisted
 * between sessions. {@code CompoundTag.hashCode()} is content-based and stable;
 * an identity hash would not be.
 */
public final class ItemKey {

    private final String id;
    private final int nbtHash;

    public ItemKey(String id, int nbtHash) {
        if (id == null) {
            throw new IllegalArgumentException("id");
        }
        this.id = id;
        this.nbtHash = nbtHash;
    }

    public String id() {
        return id;
    }

    public int nbtHash() {
        return nbtHash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemKey)) {
            return false;
        }
        ItemKey that = (ItemKey) other;
        return this.nbtHash == that.nbtHash && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + nbtHash;
    }

    @Override
    public String toString() {
        return id + "#" + nbtHash;
    }
}

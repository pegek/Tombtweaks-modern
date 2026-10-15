package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshots awaiting one player.
 *
 * <p>A snapshot is created at death, i.e. before the grave exists - so it can only be associated
 * with a specific grave at recovery time. Binding goes by wall-clock time:
 * the snapshot's {@code capturedAt} against the grave's {@code getOwnerDeathTime()}, which
 * Tombstone sets from {@code System.currentTimeMillis()} - the same scale.
 *
 * <p>Tombstone MERGES graves: a second death within 20 blocks of an uncollected grave
 * adds items to it and moves its death date to the newest one. Such a grave therefore binds
 * only to the snapshot of the last death. The older snapshot stays pending until
 * {@link #prune} removes it, and the items it described go through Tombstone's standard
 * path.
 *
 * <p>A matched snapshot is CONSUMED, so that a player with several unvisited graves gets
 * each grave's own seating from it, and not the seating of the last death.
 */
public final class SnapshotStore {

    private final List<SlotSnapshot> pending = new ArrayList<SlotSnapshot>();

    public void add(SlotSnapshot snapshot) {
        pending.add(snapshot);
    }

    public List<SlotSnapshot> all() {
        return Collections.unmodifiableList(pending);
    }

    /**
     * The snapshot nearest in time, removed from the store. On a tie the snapshot added
     * earlier wins.
     *
     * @return null when nothing is within tolerance - then the standard path
     */
    public SlotSnapshot claimNearest(long graveDeathTime, long toleranceMillis) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            long distance = Math.abs(pending.get(i).capturedAt() - graveDeathTime);
            if (distance < 0) {
                continue; // overflow at extreme values (corrupted NBT)
            }
            if (distance <= toleranceMillis && distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best < 0 ? null : pending.remove(best);
    }

    /**
     * Prunes from two sides: count and age. Without this, a single death with no return to
     * the grave would leave an entry behind forever.
     */
    public void prune(long nowMillis, int maxEntries, long maxAgeMillis) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            if (nowMillis - pending.get(i).capturedAt() > maxAgeMillis) {
                pending.remove(i);
            }
        }
        while (!pending.isEmpty() && pending.size() > maxEntries) {
            int oldest = 0;
            for (int i = 1; i < pending.size(); i++) {
                if (pending.get(i).capturedAt() < pending.get(oldest).capturedAt()) {
                    oldest = i;
                }
            }
            pending.remove(oldest);
        }
    }
}

package com.spege.tombtweaks.core;

/**
 * When a grave loses its next item.
 *
 * <p>A pure function of the grave's own {@code countTicks} counter, which Tombstone writes to NBT
 * and reads back when the chunk loads. This way the schedule needs no state of its own
 * and survives both a server restart and a chunk unload.
 *
 * <p>Mind the semantics: {@code countTicks} only grows while the grave is loaded,
 * so {@code startTicks} counts LOADED time, not wall-clock time.
 */
public final class DecaySchedule {

    private DecaySchedule() {
    }

    public static boolean shouldDecay(long countTicks, long startTicks, long intervalTicks) {
        if (intervalTicks <= 0L) {
            return false;
        }
        long start = Math.max(0L, startTicks);
        if (countTicks < start) {
            return false;
        }
        return (countTicks - start) % intervalTicks == 0L;
    }
}

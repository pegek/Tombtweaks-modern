package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SnapshotStoreTest {

    private static final long TOLERANCE = 5_000L;

    private static SlotSnapshot at(long capturedAt) {
        SlotSnapshot snapshot = new SlotSnapshot(capturedAt);
        snapshot.add(0, new ItemKey("minecraft:stone", 0), 1);
        return snapshot;
    }

    @Test
    void bindsByNearestTime() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(50_000L));

        SlotSnapshot claimed = store.claimNearest(50_200L, TOLERANCE);

        assertNotNull(claimed);
        assertEquals(50_000L, claimed.capturedAt());
    }

    @Test
    void consumesSnapshotSoSecondGraveGetsItsOwn() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(50_000L));

        assertEquals(50_000L, store.claimNearest(50_100L, TOLERANCE).capturedAt());
        assertEquals(1_000L, store.claimNearest(1_100L, TOLERANCE).capturedAt());
        assertNull(store.claimNearest(1_100L, TOLERANCE));
    }

    @Test
    void outsideToleranceMeansNoMatch() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));

        assertNull(store.claimNearest(1_000L + TOLERANCE + 1L, TOLERANCE));
    }

    @Test
    void toleranceWorksBothWays() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(10_000L));

        assertNotNull(store.claimNearest(10_000L - TOLERANCE, TOLERANCE));
    }

    @Test
    void prunesByCount() {
        SnapshotStore store = new SnapshotStore();
        for (int i = 1; i <= 6; i++) {
            store.add(at(i * 1_000L));
        }

        store.prune(100_000L, 3, Long.MAX_VALUE);

        assertEquals(3, store.all().size());
        // the youngest remain
        assertEquals(4_000L, store.all().get(0).capturedAt());
        assertEquals(6_000L, store.all().get(2).capturedAt());
    }

    @Test
    void prunesByAge() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(90_000L));

        store.prune(100_000L, 10, 20_000L);

        assertEquals(1, store.all().size());
        assertEquals(90_000L, store.all().get(0).capturedAt());
    }

    @Test
    void negativeLimitClearsEverythingWithoutThrowing() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(2_000L));

        store.prune(100_000L, -1, Long.MAX_VALUE);

        assertEquals(0, store.all().size());
    }

    @Test
    void distanceOverflowDoesNotBindSnapshot() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(Long.MAX_VALUE));

        assertNull(store.claimNearest(-1L, TOLERANCE));
    }

    @Test
    void tieGoesToEarlierAdded() {
        SnapshotStore store = new SnapshotStore();
        SlotSnapshot first = new SlotSnapshot(1_000L);
        first.add(0, new ItemKey("minecraft:stone", 0), 1);
        SlotSnapshot second = new SlotSnapshot(3_000L);
        second.add(1, new ItemKey("minecraft:stone", 0), 1);
        store.add(first);
        store.add(second);

        SlotSnapshot claimed = store.claimNearest(2_000L, TOLERANCE);

        assertNotNull(claimed);
        assertEquals(0, claimed.entries().get(0).slot());
    }

    @Test
    void emptyStoreDoesNotBlowUp() {
        SnapshotStore store = new SnapshotStore();
        store.prune(100_000L, 3, 1_000L);
        assertNull(store.claimNearest(0L, TOLERANCE));
        assertEquals(0, store.all().size());
    }
}

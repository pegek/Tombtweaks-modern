package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SlotPlanTest {

    private static final IntPredicate ALL_FREE = slot -> true;
    private static final int NO = SlotPlan.NO_SEAT;

    private static SlotSnapshot snapshotWith(SlotEntry... items) {
        SlotSnapshot snapshot = new SlotSnapshot(0L);
        for (SlotEntry item : items) {
            snapshot.add(item.slot(), item.key(), item.count());
        }
        return snapshot;
    }

    private static ItemKey sword(int hash) {
        return new ItemKey("minecraft:diamond_sword", hash);
    }

    private static ItemKey bread() {
        return new ItemKey("minecraft:bread", 0);
    }

    @Test
    void exactHit() {
        SlotSnapshot s = snapshotWith(new SlotEntry(3, sword(42), 1));
        assertArrayEquals(new int[] { 3 }, SlotPlan.assign(s, Arrays.asList(sword(42)), ALL_FREE));
    }

    @Test
    void fallsBackToNameMatchWhenNbtDrifted() {
        SlotSnapshot s = snapshotWith(new SlotEntry(7, sword(42), 1));
        assertArrayEquals(new int[] { 7 }, SlotPlan.assign(s, Arrays.asList(sword(999)), ALL_FREE));
    }

    @Test
    void exactTakesPrecedenceOverName() {
        SlotSnapshot s = snapshotWith(new SlotEntry(1, sword(11), 1), new SlotEntry(2, sword(22), 1));
        assertArrayEquals(new int[] { 2, 1 },
                SlotPlan.assign(s, Arrays.asList(sword(22), sword(11)), ALL_FREE));
    }

    @Test
    void twoIdenticalStacksGetTwoDifferentSeats() {
        SlotSnapshot s = snapshotWith(new SlotEntry(4, bread(), 8), new SlotEntry(9, bread(), 8));
        int[] seats = SlotPlan.assign(s, Arrays.asList(bread(), bread()), ALL_FREE);
        assertNotEquals(seats[0], seats[1]);
        assertEquals(13, seats[0] + seats[1]);
    }

    @Test
    void noMatchMeansGivingUp() {
        SlotSnapshot s = snapshotWith(new SlotEntry(4, bread(), 8));
        assertArrayEquals(new int[] { NO },
                SlotPlan.assign(s, Arrays.asList(new ItemKey("minecraft:stone", 0)), ALL_FREE));
    }

    @Test
    void seatIsConsumedOnce() {
        SlotSnapshot s = snapshotWith(new SlotEntry(4, bread(), 8));
        assertArrayEquals(new int[] { 4, NO }, SlotPlan.assign(s, Arrays.asList(bread(), bread()), ALL_FREE));
    }

    @Test
    void driftDoesNotStealSeatFromExactMatch() {
        // The sword from seat 2 has a drifted hash (2 -> 99) and sits in the grave BEFORE the sword
        // from seat 1. Greedy matching would take seat 1 and the two swords would swap slots.
        SlotSnapshot s = snapshotWith(new SlotEntry(1, sword(1), 1), new SlotEntry(2, sword(2), 1));
        assertArrayEquals(new int[] { 2, 1 },
                SlotPlan.assign(s, Arrays.asList(sword(99), sword(1)), ALL_FREE));
    }

    @Test
    void mergedGraveDoesNotSeatOldItem() {
        // A grave merged from two deaths: the first sword (hash 77) comes from a death whose
        // snapshot was not bound to this grave. It is to stay in the grave, while both swords
        // from the bound snapshot are to land on their own seats.
        SlotSnapshot s = snapshotWith(new SlotEntry(1, sword(1), 1), new SlotEntry(2, sword(2), 1));
        assertArrayEquals(new int[] { NO, 1, 2 },
                SlotPlan.assign(s, Arrays.asList(sword(77), sword(1), sword(2)), ALL_FREE));
    }

    @Test
    void occupiedSeatTakesNoPartInMatching() {
        // Seat 1 is occupied (e.g. a soulbound item never made it into the grave).
        // The sword with the drifted hash is to get seat 2, not the useless seat 1.
        SlotSnapshot s = snapshotWith(new SlotEntry(1, sword(1), 1), new SlotEntry(2, sword(2), 1));
        assertArrayEquals(new int[] { 2 },
                SlotPlan.assign(s, Arrays.asList(sword(99)), slot -> slot != 1));
    }

    @Test
    void emptyGraveSlotGetsNoSeat() {
        SlotSnapshot s = snapshotWith(new SlotEntry(4, bread(), 8));
        assertArrayEquals(new int[] { NO, 4 }, SlotPlan.assign(s, Arrays.asList(null, bread()), ALL_FREE));
    }
}

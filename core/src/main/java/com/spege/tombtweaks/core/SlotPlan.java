package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Seats the contents of one grave according to a snapshot - for the whole grave at once.
 *
 * <p>Matching makes two passes over the WHOLE grave, not one pass per item:
 * <ol>
 *   <li>exact - name and NBT hash,</li>
 *   <li>fallback - name only, just for items that did not hit in the first pass.</li>
 * </ol>
 * The key is brittle (a round trip of a stack through the grave's NBT can normalise the tag and
 * change the hash), hence the second pass. The order of the passes matters: greedy matching,
 * item by item, let an item with a drifted hash take a seat that a later item hit exactly.
 *
 * <p>This matters in practice, because Tombstone MERGES graves: a second death within 20 blocks
 * of an uncollected grave adds items to it and moves its death date to the newest one. Such a
 * grave binds only to the snapshot of the last death, yet it also contains items from the
 * earlier one, which that snapshot does not describe.
 *
 * <p>Seats that are occupied on the player take no part in matching at all - otherwise an item
 * would get a seat that cannot be used, and would not try any other.
 *
 * <p>{@link #NO_SEAT} guarantees nothing by itself. The guarantee "worst case = standard
 * behaviour, never a lost or duplicated item" comes from the CALLER: it must
 * insert only into an empty slot and re-check that right before inserting.
 */
public final class SlotPlan {

    public static final int NO_SEAT = -1;

    private SlotPlan() {
    }

    /**
     * @param snapshot  the seating arrangement from the moment of death
     * @param graveKeys the key of each grave slot, in grave order; null = empty slot
     * @param seatFree  whether the seat is free on the player; occupied ones take no part in matching
     * @return seats[i] = the seat for graveKeys.get(i), or {@link #NO_SEAT}
     */
    public static int[] assign(SlotSnapshot snapshot, List<ItemKey> graveKeys, IntPredicate seatFree) {
        List<SlotEntry> free = new ArrayList<SlotEntry>();
        for (SlotEntry entry : snapshot.entries()) {
            if (seatFree.test(entry.slot())) {
                free.add(entry);
            }
        }

        int[] seats = new int[graveKeys.size()];
        Arrays.fill(seats, NO_SEAT);

        for (int i = 0; i < seats.length; i++) {
            ItemKey key = graveKeys.get(i);
            if (key == null) {
                continue;
            }
            for (int j = 0; j < free.size(); j++) {
                if (free.get(j).key().equals(key)) {
                    seats[i] = free.remove(j).slot();
                    break;
                }
            }
        }

        for (int i = 0; i < seats.length; i++) {
            ItemKey key = graveKeys.get(i);
            if (key == null || seats[i] != NO_SEAT) {
                continue;
            }
            for (int j = 0; j < free.size(); j++) {
                if (free.get(j).key().id().equals(key.id())) {
                    seats[i] = free.remove(j).slot();
                    break;
                }
            }
        }

        return seats;
    }
}

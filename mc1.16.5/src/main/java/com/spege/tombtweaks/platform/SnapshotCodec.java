package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.SlotEntry;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * SnapshotStore both ways through the player's NBT.
 *
 * <p>The conversion is separated from player access ({@link #write}/{@link #read}), so that
 * the round trip can be tested without a PlayerEntity instance - a mistake in a key would silently
 * break slot restoration, with no trace in the logs.
 */
public final class SnapshotCodec {

    private static final String ROOT = "tombtweaks_slot_snapshots";

    private SnapshotCodec() {
    }

    public static SnapshotStore load(PlayerEntity player) {
        CompoundNBT persisted = PlayerData.persisted(player);
        if (!persisted.contains(ROOT, Constants.NBT.TAG_LIST)) {
            return new SnapshotStore();
        }
        return read(persisted.getList(ROOT, Constants.NBT.TAG_COMPOUND));
    }

    public static void save(PlayerEntity player, SnapshotStore store) {
        PlayerData.persisted(player).put(ROOT, write(store));
    }

    static SnapshotStore read(ListNBT snapshots) {
        SnapshotStore store = new SnapshotStore();
        for (int i = 0; i < snapshots.size(); i++) {
            CompoundNBT tag = snapshots.getCompound(i);
            List<SlotEntry> entries = new ArrayList<>();
            ListNBT seats = tag.getList("e", Constants.NBT.TAG_COMPOUND);
            for (int s = 0; s < seats.size(); s++) {
                CompoundNBT seat = seats.getCompound(s);
                entries.add(new SlotEntry(
                        seat.getInt("s"),
                        new ItemKey(seat.getString("i"), seat.getInt("h")),
                        seat.getInt("n")));
            }
            store.add(new SlotSnapshot(tag.getLong("c"), entries));
        }
        return store;
    }

    static ListNBT write(SnapshotStore store) {
        ListNBT snapshots = new ListNBT();
        for (SlotSnapshot snapshot : store.all()) {
            CompoundNBT tag = new CompoundNBT();
            tag.putLong("c", snapshot.capturedAt());
            ListNBT seats = new ListNBT();
            for (SlotEntry entry : snapshot.entries()) {
                CompoundNBT seat = new CompoundNBT();
                seat.putInt("s", entry.slot());
                seat.putString("i", entry.key().id());
                seat.putInt("h", entry.key().nbtHash());
                seat.putInt("n", entry.count());
                seats.add(seat);
            }
            tag.put("e", seats);
            snapshots.add(tag);
        }
        return snapshots;
    }
}

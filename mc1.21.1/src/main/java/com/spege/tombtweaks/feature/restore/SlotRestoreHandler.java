package com.spege.tombtweaks.feature.restore;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.SlotPlan;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.SnapshotCodec;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.items.IItemHandler;
import ovh.corail.tombstone.api.event.RestoreInventoryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts the contents of a recovered grave back where they came from.
 *
 * <p>No mixin. Tombstone fires {@code RestoreInventoryEvent} at the only useful moment:
 * after chanceLossOnDeath has taken its share, and before auto-equip and the rest of the
 * distribution. We get the grave's live ItemStackHandler, so an item taken out here simply
 * does not exist for the standard path.
 *
 * <p>The seating is computed by {@link SlotPlan#assign} for the whole grave at once (two passes),
 * because Tombstone merges graves: a second death within 20 blocks adds items to the old
 * grave, and that grave binds only to the snapshot of the last death.
 *
 * <p>Every insertion is conditional on the target slot being empty, checked again
 * right before inserting. It is that condition - not {@code NO_SEAT} - that guarantees the worst
 * case is standard behaviour, never a lost or duplicated item.
 */
@EventBusSubscriber(modid = TombTweaks.MODID)
public final class SlotRestoreHandler {

    /** How many ms may separate the snapshot's capturedAt from the grave's deathDate. */
    private static final long TOLERANCE_MILLIS = 10_000L;

    private SlotRestoreHandler() {
    }

    @SubscribeEvent
    public static void onRestoreInventory(RestoreInventoryEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.restoreEnabled.get()) {
            return;
        }
        // Tombstone calls giveInventory also for a player with a grave key and during tomb raiding,
        // and getPlayer() is the one who OPENS the grave. The snapshot is per player, so for someone
        // else's grave we would take someone else's snapshot (e.g. two players from one explosion,
        // within the 10 s tolerance). Compared by name, not through BlockEntityWritableGrave.isOwner:
        // isOwner returns true for a grave without an ownerId, and the /tbrestoreinventory path has
        // no grave in this dimension.
        if (!player.getGameProfile().getName().equals(event.getOwnerName())) {
            return;
        }

        SnapshotStore store = SnapshotCodec.load(player);
        SlotSnapshot snapshot = store.claimNearest(event.getOwnerDeathTime(), TOLERANCE_MILLIS);
        if (snapshot == null) {
            if (Config.INSTANCE.restoreDebugLogging.get()) {
                TombTweaks.LOGGER.info("[TombTweaks] no snapshot within {} ms of grave death time {}",
                        TOLERANCE_MILLIS, event.getOwnerDeathTime());
            }
            return;
        }
        SnapshotCodec.save(player, store);

        IItemHandler grave = event.getInventory();
        Inventory inventory = player.getInventory();
        HolderLookup.Provider registries = player.registryAccess();

        List<ItemKey> graveKeys = new ArrayList<>(grave.getSlots());
        for (int i = 0; i < grave.getSlots(); i++) {
            ItemStack inGrave = grave.getStackInSlot(i);
            graveKeys.add(inGrave.isEmpty() ? null : StackViews.keyOf(inGrave, registries));
        }
        int[] seats = SlotPlan.assign(snapshot, graveKeys, seat -> isSeatFree(inventory, seat));

        int seated = 0;
        for (int i = 0; i < seats.length; i++) {
            int seat = seats[i];
            // The re-check is a condition of the invariant, not extra caution.
            if (seat == SlotPlan.NO_SEAT || !isSeatFree(inventory, seat)) {
                continue;
            }
            ItemStack inGrave = grave.getStackInSlot(i);
            if (inGrave.isEmpty()) {
                continue;
            }
            ItemStack taken = grave.extractItem(i, inGrave.getCount(), false);
            if (taken.isEmpty()) {
                continue;
            }
            place(inventory, seat, taken);
            seated++;
        }

        if (Config.INSTANCE.restoreDebugLogging.get()) {
            TombTweaks.LOGGER.info("[TombTweaks] seated {} of {} recorded stacks for {}",
                    seated, snapshot.entries().size(), player.getGameProfile().getName());
        }
    }

    private static boolean isSeatFree(Inventory inventory, int seat) {
        if (seat == SlotSnapshot.OFFHAND_SLOT) {
            return inventory.offhand.get(0).isEmpty();
        }
        if (seat >= SlotSnapshot.ARMOR_BASE && seat < SlotSnapshot.ARMOR_BASE + inventory.armor.size()) {
            return inventory.armor.get(seat - SlotSnapshot.ARMOR_BASE).isEmpty();
        }
        if (seat >= 0 && seat < inventory.items.size()) {
            return inventory.items.get(seat).isEmpty();
        }
        return false; // e.g. the reserved Curios space - unsupported in v1
    }

    private static void place(Inventory inventory, int seat, ItemStack stack) {
        if (seat == SlotSnapshot.OFFHAND_SLOT) {
            inventory.offhand.set(0, stack);
        } else if (seat >= SlotSnapshot.ARMOR_BASE && seat < SlotSnapshot.ARMOR_BASE + inventory.armor.size()) {
            inventory.armor.set(seat - SlotSnapshot.ARMOR_BASE, stack);
        } else {
            inventory.items.set(seat, stack);
        }
    }
}

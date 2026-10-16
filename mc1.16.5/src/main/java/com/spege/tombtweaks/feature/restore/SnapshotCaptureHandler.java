package com.spege.tombtweaks.feature.restore;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.SnapshotCodec;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Records the inventory layout at the last moment at which it is still the truth.
 *
 * <p>{@code PlayerEntity.die()} empties the inventory right after this event, and everything that
 * reaches {@code LivingDropsEvent} - where Tombstone builds the grave - is already a flat pile
 * with no indices. Tombstone's own {@code LivingDeathEvent} listener has priority LOWEST
 * (verified on 7.0.0), so at HIGHEST we always see the inventory untouched.
 *
 * <p>Priority HIGHEST also so that a handler cancelling the death does not get in ahead of us.
 * A snapshot for a death that did not happen costs one unused entry, which the
 * store prunes.
 */
@Mod.EventBusSubscriber(modid = TombTweaks.MODID)
public final class SnapshotCaptureHandler {

    private static final int MAX_PENDING = 5;
    private static final long MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L; // 30 days

    private SnapshotCaptureHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof PlayerEntity)) {
            return;
        }
        PlayerEntity player = (PlayerEntity) event.getEntity();
        if (player.level.isClientSide) {
            return;
        }
        if (!Config.INSTANCE.restoreEnabled.get()) {
            return;
        }
        if (player.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        long now = System.currentTimeMillis();
        SlotSnapshot snapshot = new SlotSnapshot(now);
        PlayerInventory inventory = player.inventory;

        for (int i = 0; i < inventory.items.size(); i++) {
            record(snapshot, i, inventory.items.get(i));
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            record(snapshot, SlotSnapshot.ARMOR_BASE + i, inventory.armor.get(i));
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            record(snapshot, SlotSnapshot.OFFHAND_SLOT + i, inventory.offhand.get(i));
        }

        if (snapshot.isEmpty()) {
            return;
        }

        SnapshotStore store = SnapshotCodec.load(player);
        store.add(snapshot);
        store.prune(now, MAX_PENDING, MAX_AGE_MILLIS);
        SnapshotCodec.save(player, store);

        if (Config.INSTANCE.restoreDebugLogging.get()) {
            TombTweaks.LOGGER.info("[TombTweaks] recorded {} seats for {} at {}",
                    snapshot.entries().size(), player.getGameProfile().getName(), now);
        }
    }

    private static void record(SlotSnapshot snapshot, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        snapshot.add(slot, StackViews.keyOf(stack), stack.getCount());
    }
}

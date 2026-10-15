package com.spege.tombtweaks.feature.restore;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.SnapshotCodec;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Records the inventory layout at the last moment at which it is still the truth.
 *
 * <p>{@code Player.die()} empties the inventory right after this event, and everything that
 * reaches {@code LivingDropsEvent} - where Tombstone builds the grave - is already a flat pile
 * with no indices. Tombstone's own {@code LivingDeathEvent} listener has priority LOWEST
 * (9.5.6: {@code EventHandler.onPlayerDeath}), so at HIGHEST we always see the inventory untouched.
 *
 * <p>Priority HIGHEST also so that a handler cancelling the death does not get in ahead of us.
 * A snapshot for a death that did not happen costs one unused entry, which the
 * store prunes.
 */
@EventBusSubscriber(modid = TombTweaks.MODID)
public final class SnapshotCaptureHandler {

    private static final int MAX_PENDING = 5;
    private static final long MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L; // 30 days

    private SnapshotCaptureHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.restoreEnabled.get()) {
            return;
        }
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        long now = System.currentTimeMillis();
        SlotSnapshot snapshot = new SlotSnapshot(now);
        Inventory inventory = player.getInventory();
        HolderLookup.Provider registries = player.registryAccess();

        for (int i = 0; i < inventory.items.size(); i++) {
            record(snapshot, i, inventory.items.get(i), registries);
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            record(snapshot, SlotSnapshot.ARMOR_BASE + i, inventory.armor.get(i), registries);
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            record(snapshot, SlotSnapshot.OFFHAND_SLOT + i, inventory.offhand.get(i), registries);
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

    private static void record(SlotSnapshot snapshot, int slot, ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return;
        }
        snapshot.add(slot, StackViews.keyOf(stack, registries), stack.getCount());
    }
}

package com.spege.tombtweaks.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * The only player-data sub-tag that NeoForge carries across death.
 * Everything that has to survive a respawn must live here.
 *
 * <p>NeoForge 21.1.262 {@code ServerPlayer.restoreFrom} copies the {@code "PlayerPersisted"}
 * sub-tag of the old player's persistent data unconditionally - also on a death respawn, not
 * only on returning from the End. Tombstone 9.5.6 keeps its own death-surviving data under the
 * same literal key.
 */
public final class PlayerData {

    private static final String PERSISTED = "PlayerPersisted";

    private PlayerData() {
    }

    public static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(PERSISTED, Tag.TAG_COMPOUND)) {
            root.put(PERSISTED, new CompoundTag());
        }
        return root.getCompound(PERSISTED);
    }
}

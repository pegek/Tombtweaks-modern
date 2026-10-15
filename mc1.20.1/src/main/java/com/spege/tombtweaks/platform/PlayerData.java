package com.spege.tombtweaks.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * The only player-data sub-tag that Forge carries across death
 * ({@code ServerPlayer.restoreFrom} copies {@link Player#PERSISTED_NBT_TAG}).
 * Everything that has to survive a respawn must live here.
 */
public final class PlayerData {

    private PlayerData() {
    }

    public static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}

package com.spege.tombtweaks.platform;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.Constants;

/**
 * The only player-data sub-tag that Forge carries across death
 * ({@code ServerPlayerEntity.restoreFrom} copies {@link PlayerEntity#PERSISTED_NBT_TAG}).
 * Everything that has to survive a respawn must live here.
 */
public final class PlayerData {

    private PlayerData() {
    }

    public static CompoundNBT persisted(PlayerEntity player) {
        CompoundNBT root = player.getPersistentData();
        if (!root.contains(PlayerEntity.PERSISTED_NBT_TAG, Constants.NBT.TAG_COMPOUND)) {
            root.put(PlayerEntity.PERSISTED_NBT_TAG, new CompoundNBT());
        }
        return root.getCompound(PlayerEntity.PERSISTED_NBT_TAG);
    }
}

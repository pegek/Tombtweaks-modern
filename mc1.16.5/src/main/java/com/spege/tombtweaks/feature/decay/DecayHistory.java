package com.spege.tombtweaks.feature.decay;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.PlayerData;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.UUID;

/**
 * What the grave has already lost.
 *
 * <p>The 1.12.2 version recorded history by looking the player up BY NAME, so the grave
 * of an offline player decayed without a trace - exactly the scenario the feature
 * was made for. Here the key is the UUID; for an offline player the entry goes only to the
 * log for now (at DEBUG level - at the default interval that is one line per minute per grave).
 *
 * <p>An entry holds only the id and count, not the full stack NBT: in v1 the history has no
 * consumer, and a shulker box in every entry could inflate playerdata to megabytes.
 */
public final class DecayHistory {

    private static final String KEY = "tombtweaks_decay_history";

    private DecayHistory() {
    }

    public static void record(World level, BlockPos pos, UUID ownerId, ItemStack lost) {
        MinecraftServer server = level.getServer();
        if (server == null || ownerId == null) {
            return;
        }
        ServerPlayerEntity owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            TombTweaks.LOGGER.debug("[TombTweaks] grave at {} lost {} x{} (owner {} offline)",
                    pos, StackViews.idOf(lost), lost.getCount(), ownerId);
            return;
        }

        CompoundNBT persisted = PlayerData.persisted(owner);
        ListNBT history = persisted.getList(KEY, Constants.NBT.TAG_COMPOUND);

        CompoundNBT entry = new CompoundNBT();
        entry.putLong("at", System.currentTimeMillis());
        entry.putString("pos", pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + "," + level.dimension().location());
        entry.putString("item", StackViews.idOf(lost));
        entry.putInt("count", lost.getCount());
        history.add(entry);

        int max = Config.INSTANCE.decayMaxHistory.get();
        while (history.size() > max) {
            history.remove(0);
        }
        persisted.put(KEY, history);
    }
}

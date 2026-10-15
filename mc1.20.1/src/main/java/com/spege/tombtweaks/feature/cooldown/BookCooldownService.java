package com.spege.tombtweaks.feature.cooldown;

import com.spege.tombtweaks.core.Cooldown;
import com.spege.tombtweaks.core.CooldownRules;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.PlayerData;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Reading and writing one book's cooldown. Wall clock, the same scale as Tombstone.
 *
 * <p>The key is the book's Item itself, not the stack: the book is consumed on a successful
 * use, and an empty stack in 1.20.1 reports its item as minecraft:air - a cooldown keyed
 * by the stack would simply never start.
 */
public final class BookCooldownService {

    private static final String PREFIX = "tombtweaks_cooldown_";

    private BookCooldownService() {
    }

    private static CooldownRules rules() {
        return CooldownRules.parse(Config.INSTANCE.cooldownBooks.get());
    }

    /** @return how many ms are left, 0 when free or when the feature is disabled */
    public static long remaining(Player player, Item book) {
        if (!Config.INSTANCE.cooldownEnabled.get()) {
            return 0L;
        }
        String id = StackViews.idOf(book);
        long length = rules().millisFor(id);
        if (length <= 0L) {
            return 0L;
        }
        CompoundTag persisted = PlayerData.persisted(player);
        return Cooldown.remaining(persisted.getLong(PREFIX + id), length, System.currentTimeMillis());
    }

    public static void start(Player player, Item book) {
        if (!Config.INSTANCE.cooldownEnabled.get()) {
            return;
        }
        String id = StackViews.idOf(book);
        if (rules().millisFor(id) <= 0L) {
            return;
        }
        PlayerData.persisted(player).putLong(PREFIX + id, System.currentTimeMillis());
    }

    public static void tellRemaining(Player player, long remainingMillis) {
        long seconds = (remainingMillis + 999L) / 1000L;
        player.displayClientMessage(
                Component.translatable("message.tombtweaks.book_cooldown", seconds), true);
    }
}

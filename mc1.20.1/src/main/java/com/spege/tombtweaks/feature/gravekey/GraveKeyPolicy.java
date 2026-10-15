package com.spege.tombtweaks.feature.gravekey;

import com.spege.tombtweaks.platform.Config;
import net.minecraft.world.item.ItemStack;
import ovh.corail.tombstone.registry.ModEnchantments;
import ovh.corail.tombstone.registry.ModItems;

/**
 * Whether the grave key is immune to Soulbound.
 *
 * <p>Tombstone 9.1.4 treats every key as soulbound unconditionally: the key has a
 * {@code forceSoulbound} flag, through which {@code ItemGeneric.getEnchantmentLevel} returns 1 for
 * Soulbound, and {@code Helper.isSoulbound} does not look at {@code enableEnchantmentSoulbound}.
 * Disabling Soulbound in Tombstone's config therefore leaves carried keys surviving every
 * death.
 *
 * <p>Both mixins delegate the whole decision here; the config is read live.
 */
public final class GraveKeyPolicy {

    private GraveKeyPolicy() {
    }

    private static boolean keysImmune() {
        return !Config.INSTANCE.graveKeysSurviveDeath.get();
    }

    private static boolean isGraveKey(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.grave_key);
    }

    /**
     * On death: whether this stack should stay with the player as soulbound.
     *
     * @param tombstone Tombstone's own answer ({@code Helper.isSoulbound})
     */
    public static boolean keepOnDeath(ItemStack stack, boolean tombstone) {
        if (tombstone && keysImmune() && isGraveKey(stack)) {
            return false;
        }
        return tombstone;
    }

    /** At the anvil: refuse Soulbound on a key, so nobody wastes a book. */
    public static boolean refusesEnchantment(Object enchantment, ItemStack stack) {
        return enchantment == ModEnchantments.soulbound && keysImmune() && isGraveKey(stack);
    }
}

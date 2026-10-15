package com.spege.tombtweaks.feature.gravekey;

import com.spege.tombtweaks.platform.Config;
import net.minecraft.world.item.ItemStack;
import ovh.corail.tombstone.registry.ModItems;

/**
 * Whether the grave key is immune to Soulbound.
 *
 * <p>Tombstone 9.5.6 treats every key as soulbound unconditionally: the {@code ItemGraveKey}
 * constructor gives the item a default {@code ModDataComponents.SOULBOUND} data component
 * ({@code tb_soulbound}) set to true,
 * and {@code Helper.isSoulbound(ItemStack, Level)} answers true for that component before it
 * even looks at the Soulbound enchantment. Disabling Soulbound in Tombstone's config therefore
 * leaves carried keys surviving every death.
 *
 * <p>There is only the death-time decision here. On 1.20.1 this class also had
 * {@code refusesEnchantment}, used by a mixin that made the anvil refuse Soulbound on a key.
 * On 1.21.1 that is not needed: enchantments are data-driven JSON, Soulbound's
 * {@code supported_items} is {@code #minecraft:enchantable/durability}, and Tombstone does not
 * add the key to that tag - an anvil cannot put Soulbound on a key in the first place.
 *
 * <p>The mixin delegates the whole decision here; the config is read live.
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
}

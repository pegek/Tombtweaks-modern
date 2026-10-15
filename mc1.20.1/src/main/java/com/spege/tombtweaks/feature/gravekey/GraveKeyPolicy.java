package com.spege.tombtweaks.feature.gravekey;

import com.spege.tombtweaks.platform.Config;
import net.minecraft.world.item.ItemStack;
import ovh.corail.tombstone.registry.ModEnchantments;
import ovh.corail.tombstone.registry.ModItems;

/**
 * Czy klucz do grobu jest odporny na Soulbound.
 *
 * <p>Tombstone 9.1.4 traktuje kazdy klucz jako soulbound bezwarunkowo: klucz ma flage
 * {@code forceSoulbound}, przez ktora {@code ItemGeneric.getEnchantmentLevel} zwraca 1 dla
 * Soulbounda, a {@code Helper.isSoulbound} nie patrzy na {@code enableEnchantmentSoulbound}.
 * Wylaczenie Soulbounda w configu Tombstone'a zostawia wiec noszone klucze przezywajace kazda
 * smierc.
 *
 * <p>Oba mixiny deleguja tu cala decyzje; config czytany na biezaco.
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
     * Przy smierci: czy ten stack ma zostac przy graczu jako soulbound.
     *
     * @param tombstone odpowiedz samego Tombstone'a ({@code Helper.isSoulbound})
     */
    public static boolean keepOnDeath(ItemStack stack, boolean tombstone) {
        if (tombstone && keysImmune() && isGraveKey(stack)) {
            return false;
        }
        return tombstone;
    }

    /** W kowadle: odmow Soulbounda na kluczu, zeby nikt nie zmarnowal ksiegi. */
    public static boolean refusesEnchantment(Object enchantment, ItemStack stack) {
        return enchantment == ModEnchantments.soulbound && keysImmune() && isGraveKey(stack);
    }
}

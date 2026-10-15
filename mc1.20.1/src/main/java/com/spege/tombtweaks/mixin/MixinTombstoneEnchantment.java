package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.gravekey.GraveKeyPolicy;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ovh.corail.tombstone.enchantment.TombstoneEnchantment;

/**
 * Dwie poprawki na klasie bazowej wszystkich 12 enchantow Tombstone'a. Zadna podklasa w 9.1.4
 * nie nadpisuje {@code canEnchant}, {@code isDiscoverable} ani {@code isTradeable}, wiec obie
 * dzialaja dla kazdego z nich.
 *
 * <p><b>1. Wylaczony enchant naprawde znika ze skrzyn i od bibliotekarzy.</b> Tombstone uzaleznia
 * od {@code isEnabled()} trzy flagi: {@code canEnchant}, {@code canApplyAtEnchantingTable}
 * i {@code isAllowedOnBooks}. Vanillowe zrodla, ktore pytaja o cos innego, przepuszczaly wiec
 * wylaczony enchant:
 * <ul>
 *   <li>{@code EnchantRandomlyFunction} (ksiegi w lootach skrzyn) filtruje po
 *       {@code isDiscoverable()}, a dla ksiazki pomija {@code canEnchant};</li>
 *   <li>{@code VillagerTrades.EnchantBookForEmeralds} (bibliotekarze) filtruje po
 *       {@code isTradeable()}.</li>
 * </ul>
 * Tombstone nie nadpisuje zadnej z nich, wiec mixin je DODAJE: {@code isEnabled() && super}.
 * Dla wlaczonych enchantow nic sie nie zmienia. Nazwy przezywaja reobfuskacje, bo klasa mixina
 * dziedziczy po {@code Enchantment} - reobf zamienia je na {@code m_6592_} / {@code m_6594_},
 * co sprawdzamy w zbudowanym jarze.
 *
 * <p><b>2. Kowadlo odmawia Soulbounda na kluczu do grobu</b>, gdy klucze sa odporne na Soulbound
 * (patrz {@link GraveKeyPolicy}). {@code canEnchant} to metoda Minecrafta nadpisana przez
 * Tombstone'a, wiec selektor jest remapowany przez refmape ({@code m_6081_}).
 */
@Mixin(TombstoneEnchantment.class)
public abstract class MixinTombstoneEnchantment extends Enchantment {

    protected MixinTombstoneEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots) {
        super(rarity, category, slots);
    }

    @Shadow(remap = false)
    public abstract boolean isEnabled();

    @Override
    public boolean isDiscoverable() {
        return isEnabled() && super.isDiscoverable();
    }

    @Override
    public boolean isTradeable() {
        return isEnabled() && super.isTradeable();
    }

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void tombtweaks$noSoulboundOnKey(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (GraveKeyPolicy.refusesEnchantment(this, stack)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}

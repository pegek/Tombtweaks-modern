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
 * Two fixes on the base class of all 12 Tombstone enchantments. No subclass in 9.1.4
 * overrides {@code canEnchant}, {@code isDiscoverable} or {@code isTradeable}, so both
 * work for each of them.
 *
 * <p><b>1. A disabled enchantment really disappears from chests and from librarians.</b>
 * Tombstone makes three flags depend on {@code isEnabled()}: {@code canEnchant},
 * {@code canApplyAtEnchantingTable} and {@code isAllowedOnBooks}. Vanilla sources that ask
 * about something else therefore let a disabled enchantment through:
 * <ul>
 *   <li>{@code EnchantRandomlyFunction} (books in chest loot) filters by
 *       {@code isDiscoverable()}, and for a book skips {@code canEnchant};</li>
 *   <li>{@code VillagerTrades.EnchantBookForEmeralds} (librarians) filters by
 *       {@code isTradeable()}.</li>
 * </ul>
 * Tombstone overrides neither of them, so the mixin ADDS them: {@code isEnabled() && super}.
 * For enabled enchantments nothing changes. The names survive reobfuscation, because the mixin
 * class inherits from {@code Enchantment} - reobf turns them into {@code m_6592_} /
 * {@code m_6594_}, which we check in the built jar.
 *
 * <p><b>2. The anvil refuses Soulbound on a grave key</b>, when keys are immune to Soulbound
 * (see {@link GraveKeyPolicy}). {@code canEnchant} is a Minecraft method overridden by
 * Tombstone, so the selector is remapped through the refmap ({@code m_6081_}).
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

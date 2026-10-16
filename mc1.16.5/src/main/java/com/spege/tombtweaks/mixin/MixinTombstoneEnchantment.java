package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.gravekey.GraveKeyPolicy;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ovh.corail.tombstone.enchantment.TombstoneEnchantment;

/**
 * Fixes on the base class of all seven Tombstone 7.0.0 enchantments. No subclass overrides
 * {@code canEnchant}, so the fix works for each of them.
 *
 * <p><b>The anvil refuses Soulbound on a grave key</b>, when keys are immune to Soulbound
 * (see {@link GraveKeyPolicy}). Without this the anvil accepts it on 7.0.0:
 * {@code RepairContainer.createResult} asks {@code Enchantment.canEnchant(ItemStack)} for each
 * enchantment of the book on the right, and {@code TombstoneEnchantment.canEnchant} answers
 * {@code isEnabled() && category.canEnchant(item)}. Soulbound's category is
 * {@code ModTombstone.TYPE_TOMBSTONE_ALL}, whose predicate accepts any item with
 * {@code getEnchantmentValue() > 0 || getMaxStackSize() == 1}. The grave key has enchantment
 * value 0 (no Tombstone item overrides it), but {@code ItemGraveMagic.getBuilder()} gives it
 * {@code stacksTo(1)} - so it passes, and the key takes Soulbound whenever Soulbound is enabled.
 * Tombstone's own {@code AnvilUpdateEvent} handler only covers engraving and does not touch it.
 *
 * <p>{@code canEnchant} is a Minecraft method overridden by Tombstone, so the selector is remapped
 * through the refmap ({@code func_92089_a}).
 */
@Mixin(TombstoneEnchantment.class)
public abstract class MixinTombstoneEnchantment extends Enchantment {

    protected MixinTombstoneEnchantment(Rarity rarity, EnchantmentType category, EquipmentSlotType[] slots) {
        super(rarity, category, slots);
    }

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void tombtweaks$noSoulboundOnKey(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (GraveKeyPolicy.refusesEnchantment(this, stack)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}

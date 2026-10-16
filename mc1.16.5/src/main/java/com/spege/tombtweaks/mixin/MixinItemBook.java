package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ovh.corail.tombstone.item.ItemBook;

/**
 * Blocks book use during the cooldown.
 *
 * <p>Sits on the base class of all books, so it works regardless of where someone calls it
 * from. On Tombstone 7.0.0 none of the four books (disenchantment, magic impregnation,
 * recycling, repairing) overrides {@code canEnchant}, so this HEAD covers all of them.
 * remap = false: canEnchant is a Tombstone method.
 */
@Mixin(ItemBook.class)
public abstract class MixinItemBook {

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true, remap = false)
    private void tombtweaks$blockDuringCooldown(World level, BlockPos pos, PlayerEntity player,
                                                ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (level == null || level.isClientSide || player == null) {
            return;
        }
        Item self = (Item) (Object) this;
        long remaining = BookCooldownService.remaining(player, self);
        if (remaining > 0L) {
            BookCooldownService.tellRemaining(player, remaining);
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}

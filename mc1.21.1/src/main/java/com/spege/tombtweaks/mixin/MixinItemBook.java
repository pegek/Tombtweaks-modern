package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ovh.corail.tombstone.item.ItemBook;

/**
 * Blocks book use during the cooldown.
 *
 * <p>Sits on the base class of all books, so it works regardless of where
 * someone calls it from. In Tombstone 9.5.6 none of the seven {@code ItemBookOf*} subclasses
 * overrides {@code canEnchant}, so this one injection covers every book.
 *
 * <p>remap = false everywhere: canEnchant is a Tombstone method (from
 * {@code ovh.corail.tombstone.api.item.ISoulConsumer}), and this build has no refmap.
 */
@Mixin(value = ItemBook.class, remap = false)
public class MixinItemBook {

    @Inject(method = "canEnchant", at = @At(value = "HEAD", remap = false), cancellable = true, remap = false)
    private void tombtweaks$blockDuringCooldown(Level level, BlockPos pos, Player player,
                                                ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (level == null || level.isClientSide() || player == null) {
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

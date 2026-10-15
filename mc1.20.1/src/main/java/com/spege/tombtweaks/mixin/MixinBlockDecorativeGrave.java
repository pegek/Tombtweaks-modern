package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ovh.corail.tombstone.api.capability.ISoulConsumer;
import ovh.corail.tombstone.block.BlockDecorativeGrave;
import ovh.corail.tombstone.item.ItemBook;

/**
 * Starts the cooldown exactly when the book use succeeded.
 *
 * <p>ISoulConsumer.setEnchant is called from a single place in all of Tombstone - so
 * one redirect covers every book.
 *
 * <p>Two levels of remapping: the selector method = "use" targets a Minecraft method
 * (BlockBehaviour.use), so it MUST be remapped through the refmap - and that is the reason
 * refmaps are enabled in this project. The inner @At targets a Tombstone member
 * and gets its own remap = false.
 *
 * <p>The redirect's receiver is ISoulConsumer, because the call is invokeinterface on that
 * interface - not on a concrete book. The same receiver is the cooldown key:
 * it is the book's Item, and the stack may already be empty, because the book was consumed
 * on use.
 */
@Mixin(BlockDecorativeGrave.class)
public class MixinBlockDecorativeGrave {

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lovh/corail/tombstone/api/capability/ISoulConsumer;setEnchant("
                           + "Lnet/minecraft/world/level/Level;"
                           + "Lnet/minecraft/core/BlockPos;"
                           + "Lnet/minecraft/server/level/ServerPlayer;"
                           + "Lnet/minecraft/world/item/ItemStack;I)"
                           + "Lovh/corail/tombstone/api/capability/ISoulConsumer$ConsumeResult;",
                    remap = false))
    private ISoulConsumer.ConsumeResult tombtweaks$startCooldown(ISoulConsumer consumer,
                                                                 Level level, BlockPos pos,
                                                                 ServerPlayer player,
                                                                 ItemStack stack, int soulStrength) {
        ISoulConsumer.ConsumeResult result = consumer.setEnchant(level, pos, player, stack, soulStrength);
        // ItemBook only: the block sits solely on ItemBook.canEnchant, so a cooldown for
        // another ISoulConsumer (scrolls, tablets, receptacles) would start and block nothing.
        if (result != null && result.result() == ISoulConsumer.ConsumeResult.Result.SUCCESS
                && consumer instanceof ItemBook book) {
            BookCooldownService.start(player, book);
        }
        return result;
    }
}

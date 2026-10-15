package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ovh.corail.tombstone.api.item.ISoulConsumer;
import ovh.corail.tombstone.block.BlockDecorativeGrave;
import ovh.corail.tombstone.item.ItemBook;

/**
 * Starts the cooldown exactly when the book use succeeded.
 *
 * <p>ISoulConsumer.setEnchant is invoked from a single place in all of Tombstone 9.5.6 -
 * {@code BlockDecorativeGrave.useItemOn} - so one redirect covers every book.
 *
 * <p>No remapping at any level. On 1.20.1 the selector targeted a Minecraft method
 * ({@code BlockBehaviour.use}) and had to go through the refmap. On 1.21.1 the right-click with
 * an item became {@code useItemOn}, and NeoForge runs on Mojang's official names, so the
 * selector is the plain name: remap = false on the mixin, the redirect and the @At alike.
 *
 * <p>The redirect's receiver is ISoulConsumer (now in {@code ovh.corail.tombstone.api.item},
 * moved from {@code api.capability}), because the call is invokeinterface on that interface -
 * not on a concrete book. The same receiver is the cooldown key: it is the book's Item, and the
 * stack may already be empty, because the book was consumed on use.
 */
@Mixin(value = BlockDecorativeGrave.class, remap = false)
public class MixinBlockDecorativeGrave {

    @Redirect(
            method = "useItemOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lovh/corail/tombstone/api/item/ISoulConsumer;setEnchant("
                           + "Lnet/minecraft/world/level/Level;"
                           + "Lnet/minecraft/core/BlockPos;"
                           + "Lnet/minecraft/server/level/ServerPlayer;"
                           + "Lnet/minecraft/world/item/ItemStack;I)"
                           + "Lovh/corail/tombstone/api/item/ISoulConsumer$ConsumeResult;",
                    remap = false),
            remap = false)
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

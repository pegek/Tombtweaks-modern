package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ovh.corail.tombstone.api.capability.ISoulConsumer;
import ovh.corail.tombstone.block.BlockDecorativeGrave;
import ovh.corail.tombstone.item.ItemBook;

/**
 * Starts the cooldown exactly when the book use succeeded.
 *
 * <p>On Tombstone 7.0.0 ISoulConsumer.setEnchant is called from a single place in the whole
 * jar - {@code BlockDecorativeGrave.use}, once - so one redirect covers every book.
 *
 * <p>Two levels of remapping: the selector method = "use" targets a Minecraft method
 * ({@code AbstractBlock.use}, {@code func_225533_a_} at runtime), so it MUST be remapped through
 * the refmap - and that is the reason refmaps are enabled in this project. The inner @At
 * targets a Tombstone member and gets its own remap = false.
 *
 * <p>The redirect's receiver is ISoulConsumer, because the call is invokeinterface on that
 * interface - not on a concrete book. The same receiver is the cooldown key: it is the book's
 * Item, and the stack may already be empty, because the book was consumed on use. On 7.0.0
 * {@code use} gets the consumer from the stack's {@code CAP_SOUL_CONSUMER} capability, and
 * {@code ItemBook.initCapabilities} builds that as {@code new SoulConsumerProvider(this)}, whose
 * LazyOptional hands back the item itself - so {@code instanceof ItemBook} sees the book.
 */
@Mixin(BlockDecorativeGrave.class)
public abstract class MixinBlockDecorativeGrave {

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lovh/corail/tombstone/api/capability/ISoulConsumer;setEnchant("
                           + "Lnet/minecraft/world/World;"
                           + "Lnet/minecraft/util/math/BlockPos;"
                           + "Lnet/minecraft/entity/player/ServerPlayerEntity;"
                           + "Lnet/minecraft/item/ItemStack;I)"
                           + "Lovh/corail/tombstone/api/capability/ISoulConsumer$ConsumeResult;",
                    remap = false))
    private ISoulConsumer.ConsumeResult tombtweaks$startCooldown(ISoulConsumer consumer,
                                                                 World level, BlockPos pos,
                                                                 ServerPlayerEntity player,
                                                                 ItemStack stack, int soulStrength) {
        ISoulConsumer.ConsumeResult result = consumer.setEnchant(level, pos, player, stack, soulStrength);
        // ItemBook only: the block sits solely on ItemBook.canEnchant, so a cooldown for
        // another ISoulConsumer (scrolls, tablets, receptacles) would start and block nothing.
        if (result != null && result.result() == ISoulConsumer.ConsumeResult.Result.SUCCESS
                && consumer instanceof ItemBook) {
            BookCooldownService.start(player, (ItemBook) consumer);
        }
        return result;
    }
}

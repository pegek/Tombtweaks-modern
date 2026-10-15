package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.gravekey.GraveKeyPolicy;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ovh.corail.tombstone.helper.DeathHandler;
import ovh.corail.tombstone.helper.Helper;

/**
 * Grave keys survive death = false: a carried key loses its soulbound status on death.
 *
 * <p>{@code storeSoulboundsOnBody} walks over the drops and every one for which
 * {@code Helper.isSoulbound} answers true it takes out of them - keys into {@code tb_key_stacks},
 * the rest into {@code tb_soulbound_stacks} - so that they come back after respawn. For a key
 * the answer is always true. An answer of false leaves the key in the drops, so it ends up in
 * the new grave.
 *
 * <p>The only other call of {@code isSoulbound} in Tombstone is in {@code ItemBookOfSoulbound},
 * so this redirect is the whole decision at death and touches nothing else. The key to the grave
 * being created right now is issued by Tombstone separately, in {@code handleLoot}.
 *
 * <p>remap = false in both places: the target and the call are Tombstone members, not
 * Minecraft's.
 */
@Mixin(DeathHandler.class)
public class MixinDeathHandlerKeys {

    @Redirect(
            method = "storeSoulboundsOnBody",
            at = @At(
                    value = "INVOKE",
                    target = "Lovh/corail/tombstone/helper/Helper;isSoulbound(Lnet/minecraft/world/item/ItemStack;)Z",
                    remap = false),
            remap = false)
    private boolean tombtweaks$graveKeySoulbound(ItemStack stack) {
        return GraveKeyPolicy.keepOnDeath(stack, Helper.isSoulbound(stack));
    }
}

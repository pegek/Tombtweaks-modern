package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.gravekey.GraveKeyPolicy;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ovh.corail.tombstone.helper.DeathHandler;
import ovh.corail.tombstone.helper.EntityHelper;

/**
 * Grave keys survive death = false: a carried key loses its soulbound status on death.
 *
 * <p>On Tombstone 7.0.0 {@code storeSoulboundsOnBody} walks over the drops and every one for
 * which {@code EntityHelper.hasSoulbound} answers true it takes out of them - keys into
 * {@code tb_key_stacks}, the rest into {@code tb_soulbound_stacks} - so that they come back after
 * respawn. For a key the answer is always true. An answer of false leaves the key in the drops.
 * It runs from {@code onHandleSoulbound} (LivingDropsEvent, priority HIGH), before
 * {@code onDrops} (priority LOW) builds the grave from what is left, so the key ends up in the
 * new grave.
 *
 * <p>{@code storeSoulboundsOnBody} holds the only call of {@code hasSoulbound} in Tombstone 7.0.0,
 * so this redirect is the whole decision at death and touches nothing else. The key to the grave
 * being created right now never passes through it: {@code handleLoot} creates it and writes it
 * straight into {@code tb_key_stacks}.
 *
 * <p>remap = false in both places: the target and the call are Tombstone members, not
 * Minecraft's.
 */
@Mixin(DeathHandler.class)
public abstract class MixinDeathHandlerKeys {

    @Redirect(
            method = "storeSoulboundsOnBody",
            at = @At(
                    value = "INVOKE",
                    target = "Lovh/corail/tombstone/helper/EntityHelper;hasSoulbound(Lnet/minecraft/item/ItemStack;)Z",
                    remap = false),
            remap = false)
    private boolean tombtweaks$graveKeySoulbound(ItemStack stack) {
        return GraveKeyPolicy.keepOnDeath(stack, EntityHelper.hasSoulbound(stack));
    }
}

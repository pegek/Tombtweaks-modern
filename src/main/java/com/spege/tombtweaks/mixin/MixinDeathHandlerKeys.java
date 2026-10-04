package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.gravekey.GraveKeyPolicy;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ovh.corail.tombstone.helper.DeathHandler;
import ovh.corail.tombstone.helper.Helper;

/**
 * Grave keys survive death = false: noszony klucz traci status soulbound przy smierci.
 *
 * <p>{@code storeSoulboundsOnBody} przechodzi po dropach i kazdy, dla ktorego
 * {@code Helper.isSoulbound} odpowiada true, wyjmuje z nich - klucze do {@code tb_key_stacks},
 * reszte do {@code tb_soulbound_stacks} - zeby wrocily po respawnie. Dla klucza odpowiedz jest
 * zawsze true. Odpowiedz false zostawia klucz w dropach, wiec trafia do nowego grobu.
 *
 * <p>Jedyne inne wywolanie {@code isSoulbound} w Tombstonie jest w {@code ItemBookOfSoulbound},
 * wiec ten redirect jest cala decyzja przy smierci i nie rusza niczego innego. Klucz do grobu,
 * ktory wlasnie powstaje, Tombstone wydaje osobno, w {@code handleLoot}.
 *
 * <p>remap = false w obu miejscach: cel i wywolanie to czlonkowie Tombstone'a, nie Minecrafta.
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

package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.decay.GraveDecayService;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ovh.corail.tombstone.tileentity.TileEntityPlayerGrave;
import ovh.corail.tombstone.tileentity.TileEntityWritableGrave;

/**
 * Hooks decay into the grave's own ticker - one call per grave per tick, with no scanning of
 * all tile entities as on 1.12.2.
 *
 * <p>On Tombstone 7.0.0 {@code tick()} is declared on {@link TileEntityWritableGrave} and the
 * player grave does not override it, so the target is the base class, and the handler filters:
 * decorative graves ({@code TileEntityDecorativeGrave}) share the ticker but hold no inventory.
 * {@code tick} runs on both sides (it spawns the fog particles on the client and increments
 * {@code countTicks} on both), so the handler also filters to the server.
 *
 * <p>NOT remap = false: {@code tick} is {@code ITickableTileEntity.tick}, a Minecraft method,
 * which at runtime is called {@code func_73660_a}; the refmap supplies that name.
 */
@Mixin(TileEntityWritableGrave.class)
public abstract class MixinTileEntityWritableGrave {

    @Inject(method = "tick", at = @At("HEAD"))
    private void tombtweaks$decay(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof TileEntityPlayerGrave)) {
            return;
        }
        TileEntityPlayerGrave grave = (TileEntityPlayerGrave) self;
        World level = grave.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        GraveDecayService.tick(level, grave.getBlockPos(), grave);
    }
}

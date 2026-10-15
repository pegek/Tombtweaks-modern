package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.decay.GraveDecayService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ovh.corail.tombstone.block.entity.BlockEntityPlayerGrave;

/**
 * Hooks decay into the grave's own ticker - one call per grave per tick, with no scanning of
 * all block entities as on 1.12.2.
 *
 * <p>remap = false everywhere: serverTick is a Tombstone method, and NeoForge 1.21.1 runs on
 * Mojang's official names anyway - this build has no refmap for anything to look up.
 */
@Mixin(value = BlockEntityPlayerGrave.class, remap = false)
public class MixinBlockEntityPlayerGrave {

    @Inject(method = "serverTick", at = @At(value = "HEAD", remap = false), remap = false)
    private static void tombtweaks$decay(Level level, BlockPos pos, BlockState state,
                                         BlockEntityPlayerGrave grave, CallbackInfo ci) {
        GraveDecayService.tick(level, pos, grave);
    }
}

package com.spege.tombtweaks;

import com.spege.tombtweaks.platform.Config;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TombTweaks.MODID)
public final class TombTweaks {

    public static final String MODID = "tombtweaks";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public TombTweaks() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "tombtweaks-common.toml");
        // The version is read from the mod container - i.e. from the jar manifest, through
        // ${file.jarVersion} in mods.toml. We do NOT keep a second copy in a constant:
        // such a constant drifts away from build.gradle and then the log lies about
        // what is actually installed.
        LOGGER.info("[TombTweaks] {} loading",
                ModLoadingContext.get().getActiveContainer().getModInfo().getVersion());
    }
}

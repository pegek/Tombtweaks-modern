package com.spege.tombtweaks;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TombTweaks.MODID)
public final class TombTweaks {

    public static final String MODID = "tombtweaks";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public TombTweaks(IEventBus modBus, ModContainer container) {
        // The version is read from the mod container - i.e. from the jar manifest, via
        // ${file.jarVersion} in neoforge.mods.toml. We deliberately keep NO second copy in a
        // constant: such a constant drifts from build.gradle and then the log lies about
        // what is actually installed.
        LOGGER.info("[TombTweaks] {} loading", container.getModInfo().getVersion());
    }
}

package com.noirm;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point of the NOIRM mod. Forge finds this class through the {@link Mod} annotation
 * and constructs it once while the game is loading.
 */
@Mod(Noirm.MOD_ID)
public final class Noirm {
    /** Must match {@code mod_id} in gradle.properties. */
    public static final String MOD_ID = "noirm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Noirm() {
        LOGGER.info("NOIRM is loading");
    }
}

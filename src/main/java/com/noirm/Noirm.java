package com.noirm;

import com.mojang.logging.LogUtils;
import com.noirm.network.NoirmNetwork;
import com.noirm.registry.NoirmBlocks;
import com.noirm.registry.NoirmEntities;
import com.noirm.registry.NoirmItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

    public Noirm(FMLJavaModLoadingContext context) {
        LOGGER.info("NOIRM is loading");
        IEventBus modBus = context.getModEventBus();
        NoirmBlocks.register(modBus);
        NoirmEntities.register(modBus);
        NoirmItems.register(modBus);
        NoirmNetwork.register();
    }

    /** Builds a {@code noirm:<path>} id. Newer Minecraft versions rename this constructor; update it here only. */
    @SuppressWarnings("removal")
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /** Builds a {@code minecraft:<path>} id, for vanilla assets. */
    @SuppressWarnings("removal")
    public static ResourceLocation vanillaId(String path) {
        return new ResourceLocation("minecraft", path);
    }
}

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
        com.noirm.hound.HoundRegistry.register(modBus); // the ninja hound and its items (docs/INUZUKA.md)
        NoirmItems.register(modBus);
        com.noirm.gear.NoirmGear.register(modBus);
        com.noirm.weapon.NinjaWeapons.register(modBus);
        com.noirm.weapon.SpecialWeapons.register(modBus);
        com.noirm.weapon.NinjaBlades.register(modBus);
        com.noirm.nature.NatureItems.register(modBus); // chakra paper, nature and jutsu scrolls (+ their chest loot)
        com.noirm.combat.StyleItems.register(modBus); // the style scroll (fighting styles found as loot)
        com.noirm.workshop.WorkshopItems.register(modBus); // blank and creation scrolls (the jutsu workshop)
        com.noirm.registry.NoirmStructures.register(modBus);
        com.noirm.mission.CareerItems.register(modBus); // the mission board, ryo pouches, mission items
        com.noirm.bounty.BountyItems.register(modBus); // the bingo book
        com.noirm.medical.MedicalItems.register(modBus); // the antidote
        com.noirm.guide.GuideItems.register(modBus); // the Shinobi Handbook
        com.noirm.hiraishin.HiraishinItems.register(modBus); // the Flying Raijin kunai and the Space-Time Formula scroll
        com.noirm.pills.PillItems.register(modBus); // foods, herbs, pills and the apothecary (docs/PILLS.md)
        com.noirm.registry.NoirmMenus.register(modBus);
        com.noirm.vfx.NoirmParticles.register(modBus);
        com.noirm.traits.PerkEffects.register(modBus); // clan technique effects (the chakra seal)
        com.noirm.sound.NoirmSounds.register(modBus); // our own synthesised sounds (tools/soundgen, docs/SOUND.md)
        com.noirm.world.Destruction.registerGameRules();
        com.noirm.movement.MovementFeel.registerGameRules();
        com.noirm.gore.Gore.registerGameRules();
        com.noirm.world.wild.WildSpawner.registerGameRules();
        com.noirm.gore.GoreParticles.register(modBus); // blood drops and pools
        com.noirm.gore.GoreSettings.register(); // the player's gore level (config/noirm-client.toml)
        com.noirm.creator.CharacterCreator.registerGameRules();
        com.noirm.config.NoirmConfig.register(); // survival tuning (config/noirm-common.toml)
        modBus.addListener(com.noirm.progress.NoirmTriggers::setup); // the advancement triggers
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

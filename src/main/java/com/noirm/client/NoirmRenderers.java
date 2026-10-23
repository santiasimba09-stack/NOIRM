package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.registry.NoirmEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers a renderer for every NOIRM entity. Every entity type needs one, even if it draws
 * nothing — without it, the client crashes the moment the entity appears.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NoirmRenderers {
    private NoirmRenderers() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NoirmEntities.SPARRING_NINJA.get(), SparringNinjaRenderer::new);
        event.registerEntityRenderer(NoirmEntities.SHADOW_CLONE.get(), ShadowCloneRenderer::new);
        // Masters are ninjas with a fixed look (their skin key comes from data/noirm/masters/).
        event.registerEntityRenderer(NoirmEntities.MASTER.get(), SparringNinjaRenderer::new);
        event.registerEntityRenderer(NoirmEntities.TRAINING_DUMMY.get(), TrainingDummyRenderer::new);
        event.registerEntityRenderer(NoirmEntities.TAILED_BEAST.get(), TailedBeastRenderer::new);
        event.registerEntityRenderer(NoirmEntities.VILLAGE_MERCHANT.get(), VillageMerchantRenderer::new);
        // Jutsu projectiles are drawn entirely with particles (see JutsuProjectile#trail).
        event.registerEntityRenderer(NoirmEntities.JUTSU_PROJECTILE.get(), NoopRenderer::new);
        // Seats are invisible: villagers sitting are all you see.
        event.registerEntityRenderer(NoirmEntities.SEAT.get(), NoopRenderer::new);
        // Kunai, shuriken, senbon, explosive tags in flight or stuck in a wall.
        event.registerEntityRenderer(com.noirm.weapon.NinjaWeapons.THROWN_WEAPON.get(), ThrownWeaponRenderer::new);
        // Gore: a severed arm, leg or head in the victim's skin and armor.
        event.registerEntityRenderer(NoirmEntities.SEVERED_LIMB.get(), SeveredLimbRenderer::new);
    }
}

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
        // Jutsu projectiles are drawn entirely with particles (see JutsuProjectile#trail).
        event.registerEntityRenderer(NoirmEntities.JUTSU_PROJECTILE.get(), NoopRenderer::new);
    }
}

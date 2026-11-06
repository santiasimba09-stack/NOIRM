package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.weapon.NinjaBladeItem;
import com.noirm.weapon.NinjaBlades;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The chakra-flow glow of the v0.13 blades: an item property {@code noirm:flow} that is 1 while the
 * blade is in its holder's main hand and the server says chakra flows into it (the stack's
 * {@link NinjaBladeItem#FLOW_TAG}). The item model then swaps to its glowing variant (a full-bright
 * layer, see {@code models/item/katana_flow.json}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BladeFlowClient {
    private BladeFlowClient() {}

    @SubscribeEvent
    public static void bladeFlowSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> NinjaBlades.ALL.forEach(blade -> {
            if (blade.get().stats().flow() != null) {
                ItemProperties.register(blade.get(), Noirm.id("flow"), (stack, level, entity, seed) ->
                        entity != null && entity.getMainHandItem() == stack && NinjaBladeItem.isFlowing(stack) ? 1f : 0f);
            }
        }));
    }
}

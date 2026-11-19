package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.gear.GearPerks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A mask hides its wearer's name tag (players and ninjas alike). See {@code gear/GearPerk.HIDE_NAME}. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MaskedNameTags {
    private MaskedNameTags() {}

    @SubscribeEvent
    public static void nameTag(RenderNameTagEvent event) {
        if (GearPerks.hidesName(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }
}

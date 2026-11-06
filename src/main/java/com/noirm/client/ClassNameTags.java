package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.bounty.ClientClasses;
import com.noirm.bounty.NinjaClass;
import com.noirm.entity.SparringNinja;
import com.noirm.gear.GearPerks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The class tag in front of names: "[B] Kenji of the Hoshi clan" over players and ninjas. A ninja's
 * name shows while you look at them or have them locked on, and always for a legend within
 * {@link #LEGEND_RANGE} blocks. Masks still hide names ({@link MaskedNameTags}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClassNameTags {
    public static final double LEGEND_RANGE = 32;

    private ClassNameTags() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void nameTag(RenderNameTagEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player || entity instanceof SparringNinja) || GearPerks.hidesName(entity)
                || event.getResult() == Event.Result.DENY) {
            return;
        }
        int code = ClientClasses.get(entity.getId());
        if (code < 0) {
            return;
        }
        event.setContent(NinjaClass.nameplate(code, event.getContent()));
        Minecraft mc = Minecraft.getInstance();
        if (entity instanceof SparringNinja && mc.player != null && !entity.isInvisible()) {
            boolean looked = mc.crosshairPickEntity == entity || LockOn.target() == entity;
            boolean legend = code == NinjaClass.LEGENDARY_CODE && entity.distanceTo(mc.player) <= LEGEND_RANGE;
            if (looked || legend) {
                event.setResult(Event.Result.ALLOW);
            }
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientClasses.reset();
    }
}

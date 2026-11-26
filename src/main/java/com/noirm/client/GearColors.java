package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.gear.NoirmGear;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The dyeable cape's icon takes its dye colour (the cloth layer; the clasp keeps its own). */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GearColors {
    private GearColors() {}

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 && stack.getItem() instanceof DyeableLeatherItem dye ? dye.getColor(stack) : -1,
                NoirmGear.CAPE.get());
    }
}

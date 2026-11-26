package com.noirm.client.ui;

import com.noirm.Noirm;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tooltips on NOIRM screens use the kit's look: the flat ash surface and one iron hairline
 * instead of vanilla's purple frame. Other screens keep vanilla tooltips.
 */
public final class TooltipStyle {
    private TooltipStyle() {}

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class NoirmTooltipColors {
        private NoirmTooltipColors() {}

        @SubscribeEvent
        public static void noirmTooltipColors(RenderTooltipEvent.Color event) {
            if (!(Minecraft.getInstance().screen instanceof NoirmStyled)) {
                return;
            }
            // dark paper with a lacquer-brown edge that fades down (the look's tooltip)
            event.setBackground(Theme.fade(Theme.SURFACE, 0.95f));
            event.setBorderStart(Theme.IRON);
            event.setBorderEnd(Theme.mix(Theme.HAIRLINE, Theme.SEAL, 0.35f));
        }
    }
}

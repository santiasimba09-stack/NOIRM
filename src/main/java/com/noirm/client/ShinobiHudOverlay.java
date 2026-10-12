package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.network.SyncShinobiDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the chakra and stamina bars in the top-left corner. Placeholder art: plain colored
 * bars, no textures needed.
 */
public final class ShinobiHudOverlay {
    private static final int X = 6;
    private static final int Y = 6;
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 4;
    /** Vertical space for one label + bar. */
    private static final int ROW_HEIGHT = 18;
    private static final int CHAKRA_COLOR = 0xFF2E7BFF;
    private static final int STAMINA_COLOR = 0xFF3DDC5A;

    private ShinobiHudOverlay() {}

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        SyncShinobiDataPacket data = ClientShinobiData.get();
        // Hidden with F1 (hideGui), with F3 (it would overlap the debug text), and in spectator mode.
        if (data == null || minecraft.options.hideGui || minecraft.options.renderDebug
                || minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        Font font = minecraft.font;
        drawBar(graphics, font, Y, "hud.noirm.chakra", data.chakra(), data.maxChakra(), CHAKRA_COLOR);
        drawBar(graphics, font, Y + ROW_HEIGHT, "hud.noirm.stamina", data.stamina(), data.maxStamina(), STAMINA_COLOR);
    }

    private static void drawBar(GuiGraphics graphics, Font font, int y, String labelKey, float value, float max, int color) {
        Component label = Component.translatable(labelKey, Math.round(value), Math.round(max));
        graphics.drawString(font, label, X, y, 0xFFFFFF, true);
        int barY = y + font.lineHeight + 1;
        int filled = max > 0 ? Math.round(BAR_WIDTH * Math.min(value / max, 1f)) : 0;
        graphics.fill(X - 1, barY - 1, X + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xA0000000);
        graphics.fill(X, barY, X + filled, barY + BAR_HEIGHT, color);
    }

    /** Registers the overlay while the game loads. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("shinobi_bars", ShinobiHudOverlay::render);
        }
    }

    /** Clears the bars when leaving a world, so stale values never show on the next one. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientShinobiData.reset();
        }
    }
}

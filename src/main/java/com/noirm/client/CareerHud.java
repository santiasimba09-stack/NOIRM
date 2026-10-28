package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.economy.Ryo;
import com.noirm.mission.CareerPackets;
import com.noirm.mission.MissionTemplate;
import com.noirm.mission.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The career HUD, top right: your rank and ryo, and the mission tracker (the objective, an arrow
 * to the marked place with its distance, the time left), or the exam round and its clock. The
 * wallet also shows above the inventory.
 */
public final class CareerHud {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int GOLD = 0xFFE8C060;
    private static final int PAPER = 0xFFEDE4D3;
    private static final int DIM = 0xFFB8B0BE;
    private static final int RED = 0xFFE05A5A;
    private static final int GREEN = 0xFF6FD38A;

    private CareerHud() {}

    static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        CareerPackets.Sync data = ClientCareer.get();
        if (data == null || mc.options.hideGui || mc.options.renderDebug || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        Font font = mc.font;
        int right = width - 6;
        int y = 6;
        Component head = Component.translatable("hud.noirm.career", PlayerRank.byOrdinal(data.rank()).title(), Ryo.format(data.ryo()));
        g.drawString(font, head, right - font.width(head), y, GOLD, true);
        // Your class (D–S) in front: how strong you are, next to your rank (your standing).
        int code = com.noirm.bounty.ClientClasses.get(mc.player.getId());
        if (code >= 0) {
            Component tag = com.noirm.bounty.NinjaClass.tag(code);
            g.drawString(font, tag, right - font.width(head) - 4 - font.width(tag), y, 0xFFFFFFFF, true);
        }
        y += 11;
        if (data.examReady() && !data.onMission() && !data.examFight()) {
            Component ready = Component.translatable("hud.noirm.exam_ready", PlayerRank.byOrdinal(data.rank() + 1).title());
            g.drawString(font, ready, right - font.width(ready), y, 0xFF7AD7F0, true);
            y += 11;
        }
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (data.examFight()) {
            Component line = Component.translatable("hud.noirm.exam_round", PlayerRank.byOrdinal(data.examTarget()).title(),
                    data.round() + 1, Math.max(1, data.rounds()));
            g.drawString(font, line, right - font.width(line), y, 0xFFFFD27A, true);
            y += 11;
            y = drawClock(g, font, right, y, data.examDeadline() - now);
        }
        if (!data.onMission()) {
            return;
        }
        MissionTemplate.Letter letter = MissionTemplate.Letter.byOrdinal(data.letter());
        ResourceLocation id = ResourceLocation.tryParse(data.mission());
        Component title = id == null ? Component.empty() : Component.translatable(MissionTemplate.nameKey(id));
        String name = fit(font, title.getString(), 170);
        Component badge = Component.literal("[" + letter.name() + "] ");
        int w = font.width(badge) + font.width(name);
        g.drawString(font, badge, right - w, y, letter.color(), true);
        g.drawString(font, name, right - font.width(name), y, PAPER, true);
        y += 11;
        String objective = fit(font, data.objective().getString(), 200);
        g.drawString(font, objective, right - font.width(objective), y, data.report() ? GREEN : DIM, true);
        y += 11;
        // The arrow: where the marked place is, from where you're looking.
        double dx = data.target().getX() + 0.5 - mc.player.getX();
        double dz = data.target().getZ() + 0.5 - mc.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float diff = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        int index = Math.floorMod(Math.round(diff / 45f), 8);
        Component where = Component.translatable("hud.noirm.mission_distance", (int) Math.round(distance));
        String arrow = distance < 3 ? "•" : ARROWS[index];
        int ww = font.width(where) + 4 + font.width(arrow) * 2;
        int ax = right - ww;
        g.pose().pushPose();
        g.pose().translate(ax, y - 2, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, arrow, 0, 0, data.report() ? GREEN : GOLD, true);
        g.pose().popPose();
        g.drawString(font, where, right - font.width(where), y + 3, PAPER, true);
        y += 19;
        if (data.deadline() > 0) {
            drawClock(g, font, right, y, data.deadline() - now);
        }
    }

    private static int drawClock(GuiGraphics g, Font font, int right, int y, long ticksLeft) {
        long seconds = Math.max(0, ticksLeft / 20);
        Component clock = Component.translatable("hud.noirm.time_left", seconds / 60, String.format("%02d", seconds % 60));
        g.drawString(font, clock, right - font.width(clock), y, seconds < 30 ? RED : PAPER, true);
        return y + 11;
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    /** Registers the overlay while the game loads. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class CareerHudRegistration {
        private CareerHudRegistration() {}

        @SubscribeEvent
        public static void registerCareerOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("career", CareerHud::render);
        }
    }

    /** The wallet above the inventory; clears the copy when leaving a world. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class CareerHudEvents {
        private CareerHudEvents() {}

        @SubscribeEvent
        public static void inventoryWallet(ScreenEvent.Render.Post event) {
            CareerPackets.Sync data = ClientCareer.get();
            if (data == null || !(event.getScreen() instanceof InventoryScreen screen)) {
                return;
            }
            Font font = Minecraft.getInstance().font;
            Component text = Component.translatable("hud.noirm.wallet", Ryo.format(data.ryo()), PlayerRank.byOrdinal(data.rank()).title());
            int x = screen.getGuiLeft() + screen.getXSize() - font.width(text) - 2;
            int y = screen.getGuiTop() - 11;
            event.getGuiGraphics().drawString(font, text, x, y, GOLD, true);
        }

        @SubscribeEvent
        public static void careerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientCareer.reset();
        }
    }
}

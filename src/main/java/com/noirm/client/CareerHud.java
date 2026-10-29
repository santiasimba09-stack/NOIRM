package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.client.ui.Anim;
import com.noirm.client.ui.HudLayout;
import com.noirm.client.ui.Smooth;
import com.noirm.client.ui.Theme;
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
 * The career HUD, top right (below the effect icons and any boss bar): your rank and ryo, quiet
 * until they change, and the mission tracker (the objective, an arrow to the marked place with its
 * distance, the time left), or the exam round and its clock. The wallet also shows above the inventory.
 */
public final class CareerHud {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int GOLD = Theme.GOLD;
    private static final int PAPER = Theme.TEXT;
    private static final int DIM = Theme.TEXT_DIM;
    private static final int RED = Theme.RED;
    private static final int GREEN = Theme.GREEN;
    private static final int LINE = 11;
    /** Ryo and rank light up for a moment when they change, then settle back to a quiet grey. */
    private static final Smooth RYO = new Smooth(Anim.BAR_TAU_MS);
    private static final Smooth RANK = new Smooth(Anim.BAR_TAU_MS);

    private CareerHud() {}

    static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        CareerPackets.Sync data = ClientCareer.get();
        if (data == null || mc.options.hideGui || mc.options.renderDebug || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        Font font = mc.font;
        int right = width - HudLayout.MARGIN;
        int y = HudLayout.topRight(mc);
        RYO.update(data.ryo());
        RANK.update(data.rank());
        float lit = Math.max(RYO.recency(), RANK.recency());
        Component head = Component.translatable("hud.noirm.career", PlayerRank.byOrdinal(data.rank()).title(), Ryo.format(data.ryo()));
        // In combat mode the target sits top-centre: if this column would reach it, go below it.
        int widest = Math.max(font.width(head) + 30, data.onMission() ? 190 : 0);
        if (ClientCombat.isCombatMode() && right - widest < width / 2 + 75) {
            y = Math.max(y, HudLayout.topCenter() + 20);
        }
        g.drawString(font, head, right - font.width(head), y, Theme.mix(Theme.mix(DIM, GOLD, 0.35f), GOLD, lit), true);
        // Your class (D–S) in front: how strong you are, next to your rank (your standing).
        int code = com.noirm.bounty.ClientClasses.get(mc.player.getId());
        if (code >= 0) {
            Component tag = com.noirm.bounty.NinjaClass.tag(code);
            g.drawString(font, tag, right - font.width(head) - 4 - font.width(tag), y, 0xFFFFFFFF, true);
        }
        y += LINE;
        if (data.examReady() && !data.onMission() && !data.examFight()) {
            Component ready = Component.translatable("hud.noirm.exam_ready", PlayerRank.byOrdinal(data.rank() + 1).title());
            g.drawString(font, ready, right - font.width(ready), y, Theme.CHAKRA_LIGHT, true);
            y += LINE;
        }
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (data.examFight()) {
            Component line = Component.translatable("hud.noirm.exam_round", PlayerRank.byOrdinal(data.examTarget()).title(),
                    data.round() + 1, Math.max(1, data.rounds()));
            g.drawString(font, line, right - font.width(line), y, GOLD, true);
            y += LINE;
            y = drawClock(g, font, right, y, data.examDeadline() - now);
        }
        if (!data.onMission()) {
            return;
        }
        MissionTemplate.Letter letter = MissionTemplate.Letter.byOrdinal(data.letter());
        ResourceLocation id = ResourceLocation.tryParse(data.mission());
        Component title = id == null ? Component.empty() : Component.translatable(MissionTemplate.nameKey(id));
        y += 3; // a little air between your standing and the mission
        String name = fit(font, title.getString(), 160);
        String badge = letter.name();
        int w = font.width(badge) + 5 + font.width(name);
        g.drawString(font, badge, right - w, y, letter.color(), true);
        g.fill(right - w + font.width(badge) + 2, y + 1, right - w + font.width(badge) + 3, y + 8, Theme.fade(Theme.TEXT_DIM, 0.6f));
        g.drawString(font, name, right - font.width(name), y, PAPER, true);
        y += LINE;
        String objective = fit(font, data.objective().getString(), 180);
        g.drawString(font, objective, right - font.width(objective), y, data.report() ? GREEN : DIM, true);
        y += LINE;
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
        g.drawString(font, where, right - font.width(where), y + 3, DIM, true);
        y += 19;
        if (data.deadline() > 0) {
            drawClock(g, font, right, y, data.deadline() - now);
        }
    }

    private static int drawClock(GuiGraphics g, Font font, int right, int y, long ticksLeft) {
        long seconds = Math.max(0, ticksLeft / 20);
        Component clock = Component.translatable("hud.noirm.time_left", seconds / 60, String.format("%02d", seconds % 60));
        g.drawString(font, clock, right - font.width(clock), y, seconds < 30 ? RED : DIM, true);
        return y + LINE;
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

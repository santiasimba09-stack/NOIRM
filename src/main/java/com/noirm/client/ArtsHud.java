package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.arts.ArtsData;
import com.noirm.arts.ArtsSync;
import com.noirm.arts.ChakraShape;
import com.noirm.arts.FusionTrial;
import com.noirm.arts.ShapeTraining;
import com.noirm.clan.Nature;
import com.noirm.client.ui.Anim;
import com.noirm.client.ui.HudKit;
import com.noirm.client.ui.Smooth;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.network.PowerPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** While meditating: what's moving among the shape drills, shape + nature fusions and the fusion trial (a line and a bar under the nature drill). */
public final class ArtsHud {
    private static final int BAR_W = 120;
    private static final Smooth BAR = new Smooth(Anim.BAR_TAU_MS);

    private ArtsHud() {}

    /** Whether a shape drill or the trial moved at the last check. */
    public static boolean moving() {
        ArtsSync arts = ArtsScreens.arts();
        return arts.movingShape() >= 0 || arts.movingTrial();
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        PowerPackets.SyncPowers powers = ClientPowers.get();
        if (mc.options.hideGui || mc.player == null || powers == null || !powers.meditating() || !moving()) {
            return;
        }
        ArtsSync arts = ArtsScreens.arts();
        int cx = width / 2;
        int y = height / 2 + 56;
        Component title;
        float share;
        int color;
        if (arts.movingTrial() && arts.trialNature() >= 0) {
            Nature n = Nature.values()[arts.trialNature()];
            FusionTrial.Stage stage = FusionTrial.stages(n).get(Math.min(FusionTrial.stages(n).size() - 1, arts.trialStage()));
            title = Component.translatable("hud.noirm.arts.trial", Component.translatable(n.translationKey()),
                    Component.translatable(FusionTrial.stepKey(stage)), Math.round(arts.trialShare() * 100));
            share = arts.trialShare();
            color = 0xFF000000 | n.color();
        } else if (arts.movingPair() >= 0) {
            ChakraShape shape = ArtsData.pairShape(arts.movingPair());
            Nature n = ArtsData.pairNature(arts.movingPair());
            share = arts.pairShare(arts.movingPair());
            title = Component.translatable("hud.noirm.arts.pair", Component.translatable(shape.translationKey()),
                    Component.translatable(n.translationKey()), Math.round(share * 100));
            color = 0xFF000000 | n.color();
        } else {
            ChakraShape shape = ChakraShape.values()[arts.movingShape()];
            int stage = arts.stage(shape);
            share = arts.share(shape);
            title = Component.translatable("hud.noirm.arts.shape", Component.translatable(shape.translationKey()),
                    Component.translatable(ShapeTraining.stepKey(shape, Math.min(stage, ShapeTraining.STAGES - 1))), Math.round(share * 100));
            color = 0xFF000000 | shape.color();
        }
        g.drawCenteredString(mc.font, title, cx, y, color);
        BAR.update(Math.min(1f, share));
        Ui.bar(g, cx - BAR_W / 2, y + 11, BAR_W, 3, BAR.value(), BAR.value(), color, HudKit.TRACK);
        g.drawCenteredString(mc.font, Component.translatable("hud.noirm.arts.open"), cx, y + 18, Theme.TEXT_DIM);
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ArtsHudRegistration {
        private ArtsHudRegistration() {}

        @SubscribeEvent
        public static void registerArtsOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("solo_arts", ArtsHud::render);
        }
    }
}

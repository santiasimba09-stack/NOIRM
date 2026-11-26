package com.noirm.client.ui;

import com.noirm.Noirm;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the HUD may draw this frame without covering vanilla: the boss bars (top centre) and the
 * status-effect icons (top right). Our two corners: vitals and status top-left, career and
 * mission top-right (below effects and boss bars); jutsu slots bottom-right, clear of the hotbar
 * and the hunger/air rows; the target top-centre below any boss bar.
 */
public final class HudLayout {
    /** The gap from the screen edge. */
    public static final int MARGIN = 6;

    /** How far down this frame's boss bars reach (they're drawn before our overlays); 0 = none. */
    private static int bossBottom;

    private HudLayout() {}

    /** Top of the top-centre area (below boss bars). */
    public static int topCenter() {
        return bossBottom > 0 ? bossBottom + 4 : MARGIN;
    }

    /** Top of the top-right column: below the effect icons, and below boss bars when any show. */
    public static int topRight(Minecraft mc) {
        int y = Math.max(MARGIN, effectsBottom(mc) + 3);
        if (bossBottom > 0) {
            y = Math.max(y, bossBottom + 4);
        }
        return y;
    }

    /** The bottom of vanilla's effect icons in the top-right corner (0 when none show). */
    public static int effectsBottom(Minecraft mc) {
        if (mc.player == null) {
            return 0;
        }
        boolean beneficial = false;
        boolean harmful = false;
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            if (!effect.showIcon()) {
                continue;
            }
            if (effect.getEffect().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        int top = mc.isDemo() ? 16 : 1;
        return harmful ? top + 26 + 24 : beneficial ? top + 24 : 0;
    }

    /** The left edge the bottom-right jutsu column may start at (right of the hunger and air rows). */
    public static int bottomRightMinX(int width) {
        return width / 2 + 100;
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class HudLayoutEvents {
        private HudLayoutEvents() {}

        /** Notes how far down each boss bar is drawn. */
        @SubscribeEvent
        public static void layoutBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
            bossBottom = Math.max(bossBottom, event.getY() + 6); // the bar is 5 pixels tall, its name above it
        }

        /** A new frame: no boss bars drawn yet. */
        @SubscribeEvent
        public static void layoutFrameStart(RenderGuiEvent.Pre event) {
            bossBottom = 0;
        }
    }
}

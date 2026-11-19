package com.noirm.client;

import com.noirm.client.ui.HudKit;
import com.noirm.client.ui.Ui;
import com.noirm.senjutsu.Meditation;
import com.noirm.senjutsu.SagePackets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The sage path on the HUD, under the nature-energy bar ({@link PowerHud}): a bone tick where your
 * safe limit is (energy past it shows ochre-red), a stone bar while you petrify, and a line for
 * Sage Mode / exhaustion / the statue.
 */
public final class SageHud {
    private static final int LIMIT_TICK = 0xFFF2E8C8;
    private static final int OVER = 0xFFC0502A;
    private static final int STONE = 0xFF9A968C;

    private SageHud() {}

    /** Should the nature block show even with no energy (petrifying, a statue)? */
    public static boolean wantsShowing() {
        SagePackets.Visuals self = ClientSage.self();
        return self != null && (self.flags() != 0 || self.petrification() > 0f);
    }

    /** Over the bar ({@code width} pixels from {@code x}, {@link HudKit#BAR_H} high at {@code barY}): the limit tick and the excess. */
    public static void decorateBar(GuiGraphics g, int x, int barY, int width, float energy) {
        SagePackets.Visuals self = ClientSage.self();
        if (self == null) {
            return;
        }
        float max = Meditation.NATURE_MAX;
        int limitX = x + Math.round(width * Math.min(1f, self.limit() / max));
        if (energy > self.limit()) {
            g.fill(limitX, barY, x + Math.round(width * Math.min(1f, energy / max)), barY + HudKit.BAR_H, OVER);
        }
        if (self.limit() < max) {
            g.fill(limitX, barY - 2, limitX + 1, barY + HudKit.BAR_H + 2, LIMIT_TICK);
        }
    }

    /** Lines under the bar; returns the y below them. */
    public static int draw(GuiGraphics g, Font font, int x, int y, int tick) {
        SagePackets.Visuals self = ClientSage.self();
        if (self == null) {
            return y;
        }
        if (self.statue()) {
            g.drawString(font, Component.translatable("hud.noirm.sage.statue"), x, y, OVER, true);
            y += 11;
        } else if (self.petrification() > 0f) {
            g.drawString(font, Component.translatable("hud.noirm.sage.stone", Math.round(self.petrification())), x, y, STONE, true);
            int barY = y + font.lineHeight + 1;
            boolean blink = self.petrification() >= 75f && (tick / 6) % 2 == 0;
            float stone = Math.min(1f, self.petrification() / 100f);
            Ui.bar(g, x, barY, 92, HudKit.BAR_H, stone, stone, blink ? 0xFFE8E2D4 : STONE, HudKit.TRACK);
            y += HudKit.BAR_ROW;
        }
        if (self.sage()) {
            g.drawString(font, Component.translatable("hud.noirm.sage.mode"), x, y, 0xFF8FBF5A, true);
            y += 11;
        } else if ((self.flags() & SagePackets.EXHAUSTED) != 0) {
            g.drawString(font, Component.translatable("hud.noirm.sage.exhausted"), x, y, 0xFF9A968C, true);
            y += 11;
        }
        return y;
    }
}

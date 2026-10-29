package com.noirm.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * HUD pieces: no panels, just shadowed captions and thin bars over the world. Values (numbers)
 * fade in when they change and fade out again (see {@link Smooth#recency()}).
 */
public final class HudKit {
    /** A bar row: caption line + 3-px bar. */
    public static final int BAR_ROW = 15;
    public static final int BAR_H = 3;
    public static final int TRACK = 0x90000000;

    private HudKit() {}

    /** Shadowed text with alpha 0..1 (skipped when nearly invisible: the font ignores tiny alphas). */
    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color, float alpha) {
        if (alpha < 0.03f) {
            return;
        }
        g.drawString(font, text, x, y, Theme.fade(color, alpha), true);
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color, float alpha) {
        if (alpha < 0.03f) {
            return;
        }
        g.drawString(font, text, x, y, Theme.fade(color, alpha), true);
    }

    /**
     * "LABEL            80/100" over a thin bar. The number shows at {@code valueAlpha}
     * (1 just after a change, fading out). Returns the y below the row.
     */
    public static int barRow(GuiGraphics g, Font font, int x, int y, int w, String label, String value, float valueAlpha,
                             Smooth smooth, int color) {
        text(g, font, label, x, y, Theme.mix(color, Theme.TEXT, 0.55f), 0.9f);
        if (value != null) {
            text(g, font, value, x + w - font.width(value), y, Theme.TEXT, valueAlpha);
        }
        int barY = y + font.lineHeight + 1;
        Ui.bar(g, x, barY, w, BAR_H, smooth.value(), smooth.ghost(), color, TRACK);
        return y + BAR_ROW;
    }
}

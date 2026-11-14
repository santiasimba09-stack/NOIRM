package com.noirm.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Text tabs with one underline that slides (eases) to the active tab. Tabs are sized by their
 * labels with a gap between; if they don't fit, they share the width evenly and labels are cut.
 * Hit testing: {@link #at(double, double)} after {@link #draw}.
 */
public final class TabStrip {
    private static final int MAX = 12;
    private final int[] xs = new int[MAX];
    private final int[] ws = new int[MAX];
    private final Smooth lineX = new Smooth(Anim.BAR_TAU_MS);
    private final Smooth lineW = new Smooth(Anim.BAR_TAU_MS);
    private final HoverTracker hover = new HoverTracker();
    private int count;
    private int y;
    private int h;

    /**
     * Draws the tabs in {x, y, w} (14 high). {@code accent} is the underline colour of each tab (or
     * one colour for all, if the array has one entry).
     */
    public void draw(GuiGraphics g, Font font, int x, int y, int w, String[] labels, int active, int[] accent, int mouseX, int mouseY) {
        count = Math.min(MAX, labels.length);
        this.y = y;
        this.h = Theme.ROW;
        int gap = Theme.S5;
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += font.width(labels[i]);
        }
        total += gap * (count - 1);
        boolean even = total > w;
        int cx = x;
        for (int i = 0; i < count; i++) {
            int tw = even ? w / count : font.width(labels[i]);
            xs[i] = cx;
            ws[i] = tw;
            cx += tw + (even ? 0 : gap);
        }
        // The rule under all tabs, and the sliding underline.
        g.fill(x, y + h - 1, x + w, y + h, Theme.HAIRLINE);
        if (active >= 0 && active < count) {
            int ax = even ? xs[active] + 4 : xs[active];
            int aw = even ? ws[active] - 8 : ws[active];
            float lx = lineX.update(ax);
            float lw = lineW.update(aw);
            int color = accent.length == 1 ? accent[0] : accent[Math.min(active, accent.length - 1)];
            g.fill(Math.round(lx), y + h - 2, Math.round(lx + lw), y + h, color);
        }
        for (int i = 0; i < count; i++) {
            boolean on = i == active;
            float hv = hover.get(i, mouseX, mouseY, xs[i] - (even ? 0 : gap / 2), y, ws[i] + (even ? 0 : gap), h);
            String text = Ui.fit(font, labels[i], even ? ws[i] - 4 : ws[i] + 1);
            int tx = even ? xs[i] + (ws[i] - font.width(text)) / 2 : xs[i];
            int color = on ? Theme.TEXT : Theme.mix(Theme.TEXT_FAINT, Theme.TEXT_DIM, 0.5f + hv * 0.5f);
            if (!on && hv > 0f) {
                color = Theme.mix(color, Theme.TEXT, hv * 0.4f);
            }
            g.drawString(font, text, tx, y + 3, color, false);
        }
    }

    /** The tab under the mouse (from the last draw), or -1. */
    public int at(double mx, double my) {
        if (my < y || my >= y + h) {
            return -1;
        }
        for (int i = 0; i < count; i++) {
            int pad = Theme.S5 / 2;
            if (mx >= xs[i] - pad && mx < xs[i] + ws[i] + pad) {
                return i;
            }
        }
        return -1;
    }

    /** Snap the underline to the active tab (no slide, e.g. right after opening). */
    public void snap(int active) {
        if (active >= 0 && active < count) {
            lineX.snap(xs[active]);
            lineW.snap(ws[active]);
        }
    }
}

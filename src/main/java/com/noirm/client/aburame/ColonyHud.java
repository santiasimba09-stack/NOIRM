package com.noirm.client.aburame;

import com.noirm.aburame.ColonyPackets;
import com.noirm.client.ui.HudKit;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * The colony line on the HUD (docs/ABURAME.md, 2.8), under the chakra bar, only for colony owners:
 * {@code 蟲 12,480 / 16,000} over a thin three-part bar (body dark, jars and gourd grey, out in the
 * field teal). The number flashes red when insects die and gold when they grow by feeding; small
 * icons after it count your marks (◆) and swarms out (●).
 */
public final class ColonyHud {
    public static final int TEAL = 0xFF3FA8A8;
    public static final int BODY = 0xFF55505E;
    public static final int STORED = 0xFF9A929F;
    private static final NumberFormat FMT = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static int lastTotal = -1;
    private static long flashAt;
    private static boolean flashRed;

    private ColonyHud() {}

    /** Draws from {@code y}; returns the y below. */
    public static int draw(GuiGraphics g, Font font, int x, int y, int w, boolean combat) {
        ColonyPackets.ColonySync c = ClientColony.sync();
        if (!c.has()) {
            return y;
        }
        int total = c.bodyTotal() + c.stored() + c.field();
        long now = Util.getMillis();
        if (lastTotal >= 0 && total != lastTotal && Math.abs(total - lastTotal) >= 5) {
            flashRed = total < lastTotal && c.field() == 0 || total < lastTotal - 20;
            flashAt = now;
        }
        lastTotal = total;
        float flash = Math.max(0f, 1f - (now - flashAt) / 900f);
        int cap = Math.max(1, c.capacity() + c.storeCap());
        int colour = flash > 0 ? Theme.mix(Theme.TEXT, flashRed ? Theme.RED : Theme.GOLD, flash) : Theme.TEXT;
        String label = "蟲 " + FMT.format(c.bodyTotal() + c.stored()) + " / " + FMT.format(cap);
        HudKit.text(g, font, label, x, y, colour, 0.95f);
        int ix = x + font.width(label) + 4;
        // marks and swarms out
        for (int i = 0; i < c.marks().size(); i++) {
            HudKit.text(g, font, "◆", ix, y, TEAL, 0.9f);
            ix += 7;
        }
        if (c.swarms() > 0) {
            String sw = "●" + c.swarms();
            HudKit.text(g, font, sw, ix + 2, y, Theme.TEXT_DIM, 0.9f);
            ix += font.width(sw) + 4;
        }
        if (c.flag(ColonyPackets.ColonySync.RESTLESS)) {
            HudKit.text(g, font, "!", ix + 2, y, Theme.RED, (now / 300) % 2 == 0 ? 1f : 0.4f);
        }
        int barY = y + font.lineHeight + 1;
        int full = cap + c.field();
        g.fill(x, barY, x + w, barY + 2, HudKit.TRACK);
        int bw = Math.round(w * c.bodyTotal() / (float) full);
        int sw = Math.round(w * c.stored() / (float) full);
        int fw = Math.round(w * c.field() / (float) full);
        g.fill(x, barY, x + bw, barY + 2, BODY);
        g.fill(x + bw, barY, x + bw + sw, barY + 2, STORED);
        g.fill(x + bw + sw, barY, Math.min(x + w, x + bw + sw + fw), barY + 2, TEAL);
        y += font.lineHeight + 4;
        // the nearest mark's distance (tracking)
        if (combat && !c.marks().isEmpty()) {
            ColonyPackets.MarkView m = c.marks().stream().filter(v -> v.distance() >= 0).min((a, b) -> Integer.compare(a.distance(), b.distance()))
                    .orElse(null);
            if (m != null) {
                HudKit.text(g, font, Ui.fit(font, "◆ " + m.name() + "  " + m.distance() + "m", w + 20), x, y, TEAL, 0.85f);
                y += font.lineHeight + 1;
            }
        }
        return y;
    }

    /** "12,480" */
    public static String number(int n) {
        return FMT.format(n);
    }
}

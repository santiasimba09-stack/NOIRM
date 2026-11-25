package com.noirm.client.aburame;

import com.noirm.aburame.ColonyPackets;
import com.noirm.aburame.WildKind;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The Insect Case (docs/ABURAME.md, 2.6): twelve slots, one per wild insect. Caught ones show in their
 * colours with their strain; the rest show where to find them. A full case: Entomologist.
 */
public class InsectCaseScreen extends AburameScreen {
    public InsectCaseScreen() {
        super(Component.translatable("item.noirm.insect_case"), 400, 236);
    }

    @Override
    protected void act(String action) {}

    @Override
    protected Component drawContent(GuiGraphics g, int mx, int my, float partial) {
        ColonyPackets.ColonySync c = ClientColony.sync();
        int inner = w - PAD * 2;
        int x0 = left + PAD;
        int y = top + PAD;
        WildKind[] all = WildKind.collection();
        Ui.header(g, font, x0, y, inner, "虫", ColonyHud.TEAL, title,
                Component.translatable("screen.noirm.case.count", c.caught().size(), all.length), Theme.GOLD);
        y += Theme.ROW + Theme.S4;
        int cols = 3;
        int cw = (inner - (cols - 1) * Theme.S3) / cols;
        int rows = (all.length + cols - 1) / cols;
        int ch = Math.max(32, (top + h - PAD - y - 12 - (rows - 1) * Theme.S3) / rows);
        Component tip = null;
        for (int i = 0; i < all.length; i++) {
            WildKind k = all[i];
            int cx = x0 + (i % cols) * (cw + Theme.S3);
            int cy = y + (i / cols) * (ch + Theme.S3);
            boolean have = c.caught().contains(k.key());
            boolean bred = c.strains().contains(k.key());
            Ui.card(g, cx, cy, cw, ch, have ? k.accent | 0xFF000000 : Theme.IRON);
            g.fill(cx + 5, cy + 5, cx + 13, cy + 11, have ? k.body | 0xFF000000 : Theme.WELL);
            g.fill(cx + 7, cy + 4, cx + 11, cy + 6, have ? k.accent | 0xFF000000 : Theme.WELL);
            g.drawString(font, Ui.fit(font, Component.translatable(have ? k.translationKey() : "screen.noirm.case.unknown").getString(), cw - 22),
                    cx + 17, cy + 4, have ? Theme.TEXT : Theme.TEXT_FAINT, false);
            Component sub = Component.translatable(have ? k.translationKey() + ".strain" : k.translationKey() + ".where");
            g.drawString(font, Ui.fit(font, sub.getString(), cw - 8), cx + 5, cy + 16, have ? (bred ? Theme.GREEN : Theme.TEXT_DIM) : Theme.TEXT_DIM, false);
            if (Ui.in(mx, my, cx, cy, cw, ch)) {
                tip = Component.translatable(k.translationKey()).append("\n").append(Component.translatable(k.translationKey() + ".where"))
                        .append("\n").append(Component.translatable(k.translationKey() + ".strain"))
                        .append(bred ? Component.literal("\n✓ ").append(Component.translatable("screen.noirm.case.bred")) : Component.empty());
            }
        }
        if (c.caught().size() >= all.length) {
            Ui.textCentered(g, font, Component.translatable("screen.noirm.case.entomologist").getString(), left + w / 2, top + h - PAD - 9, Theme.GOLD);
        }
        return tip;
    }
}

package com.noirm.client.aburame;

import com.noirm.aburame.ColonyPackets;
import com.noirm.aburame.Harm;
import com.noirm.aburame.HiveRank;
import com.noirm.aburame.Species;
import com.noirm.aburame.WildKind;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The colony panel (double-tap M; docs/ABURAME.md, 2.8), built like the hound panel: a slowly turning
 * close-up beetle, the count by place and species, Vigour, upkeep and growth, generations and
 * adaptations, strains, hives, the hive rank with what the next one needs, and your marks.
 */
public class ColonyScreen extends AburameScreen {
    public ColonyScreen() {
        super(Component.translatable("screen.noirm.colony"), 430, 250);
    }

    @Override
    protected void act(String action) {}

    @Override
    protected Component drawContent(GuiGraphics g, int mx, int my, float partial) {
        ColonyPackets.ColonySync c = ClientColony.sync();
        int inner = w - PAD * 2;
        int x0 = left + PAD;
        int y = top + PAD;
        if (!c.has()) {
            Ui.header(g, font, x0, y, inner, "蟲", ColonyHud.TEAL, title, null, 0);
            Ui.wrap(g, font, Component.translatable("screen.noirm.colony.none"), x0, y + 24, inner, top + h - PAD, Theme.TEXT_DIM);
            return null;
        }
        HiveRank rank = c.rankEnum();
        Ui.header(g, font, x0, y, inner, "蟲", ColonyHud.TEAL, title,
                Component.translatable(rank.translationKey()), Theme.GOLD);
        y += Theme.ROW + Theme.S4;
        int bottom = top + h - PAD;

        // ---- left column: the beetle, then species
        int lw = Math.min(130, inner * 32 / 100);
        Ui.well(g, x0, y, lw, 74);
        Species main = c.flag(ColonyPackets.ColonySync.RINKAICHU) ? Species.RINKAICHU : Species.KIKAICHU;
        beetle(g, x0 + lw / 2, y + 40, 46f, main, partial);
        int ly = y + 80;
        ly = Ui.section(g, font, x0, ly, lw, Component.translatable("screen.noirm.colony.species"), Theme.TEXT_DIM);
        for (Species s : Species.values()) {
            int n = c.body(s);
            if (n <= 0 && s != Species.KIKAICHU) {
                continue;
            }
            g.drawString(font, Ui.fit(font, Component.translatable(s.translationKey()).getString(), lw - 44), x0, ly, s.eyes, false);
            Ui.textRight(g, font, ColonyHud.number(n), x0 + lw, ly, Theme.TEXT);
            ly += 10;
        }
        ly += 3;
        ly = Ui.section(g, font, x0, ly, lw, Component.translatable("screen.noirm.colony.health"), Theme.TEXT_DIM);
        g.drawString(font, Component.translatable("screen.noirm.colony.vigour"), x0, ly, Theme.TEXT_DIM, false);
        Ui.bar(g, x0 + 44, ly + 3, lw - 44, 3, c.vigour() / 100f, c.vigour() > 40 ? Theme.GREEN : Theme.RED);
        ly += 11;
        line(g, x0, ly, lw, "screen.noirm.colony.upkeep", String.format("%.2f/s", c.upkeep()));
        ly += 10;
        line(g, x0, ly, lw, "screen.noirm.colony.growth", "+" + Math.round(c.growthPerMin()) + "/min");
        ly += 10;
        line(g, x0, ly, lw, "screen.noirm.colony.generation", String.valueOf(c.generation()));
        ly += 10;
        if (c.flag(ColonyPackets.ColonySync.RESTLESS)) {
            g.drawString(font, Component.translatable("screen.noirm.colony.restless"), x0, ly, Theme.RED, false);
        }

        // ---- right column
        int rx = x0 + lw + Theme.S6;
        int rw = inner - lw - Theme.S6;
        int ry = y;
        ry = Ui.section(g, font, rx, ry, rw, Component.translatable("screen.noirm.colony.where"), Theme.TEXT_DIM);
        int hiveTotal = c.hives().stream().mapToInt(ColonyPackets.HiveView::count).sum();
        int col = rw / 4;
        cell(g, rx, ry, col, "screen.noirm.colony.body", ColonyHud.compact(c.bodyTotal()) + "/" + ColonyHud.compact(c.capacity()), ColonyHud.BODY);
        cell(g, rx + col, ry, col, "screen.noirm.colony.jars", ColonyHud.compact(c.stored()) + "/" + ColonyHud.compact(c.storeCap()), ColonyHud.STORED);
        cell(g, rx + col * 2, ry, col, "screen.noirm.colony.field", ColonyHud.compact(c.field()), ColonyHud.TEAL);
        cell(g, rx + col * 3, ry, col, "screen.noirm.colony.hives", ColonyHud.compact(hiveTotal), Theme.AMBER);
        ry += 24;
        // the rank and what's next
        ry = Ui.section(g, font, rx, ry, rw, Component.translatable("screen.noirm.colony.rank"), Theme.TEXT_DIM);
        HiveRank next = rank.next();
        if (next == null) {
            g.drawString(font, Component.translatable("screen.noirm.colony.rank_top"), rx, ry, Theme.GOLD, false);
            ry += 11;
        } else {
            Component goal = Component.translatable("screen.noirm.colony.next", Component.translatable(next.translationKey()),
                    ColonyHud.number(c.total()), ColonyHud.number(next.colony));
            g.drawString(font, Ui.fit(font, goal, rw), rx, ry, Theme.TEXT, false);
            ry += 10;
            Ui.bar(g, rx, ry, rw, 3, Math.min(1f, c.total() / (float) next.colony), ColonyHud.TEAL);
            ry += 6;
            Component deed = Component.translatable("screen.noirm.colony.deed." + next.key(), Math.round(c.drained()));
            ry = Ui.wrap(g, font, Component.literal(c.deedDone() ? "✓ " : "○ ").append(deed), rx, ry, rw, ry + 20, c.deedDone() ? Theme.GREEN : Theme.TEXT_DIM);
            ry += 2;
        }
        // adaptation
        ry = Ui.section(g, font, rx, ry, rw, Component.translatable("screen.noirm.colony.adaptation"), Theme.TEXT_DIM);
        int ax = rx;
        boolean any = false;
        for (Harm hm : Harm.values()) {
            float v = c.resist(hm);
            if (v <= 0.001f) {
                continue;
            }
            any = true;
            String s = Component.translatable("screen.noirm.colony.strain." + hm.key(), Math.round(v * 100)).getString();
            if (ax + font.width(s) > rx + rw) {
                ax = rx;
                ry += 10;
            }
            g.drawString(font, s, ax, ry, Theme.AMBER, false);
            ax += font.width(s) + 8;
        }
        if (!any) {
            g.drawString(font, Ui.fit(font, Component.translatable("screen.noirm.colony.no_adaptation").getString(), rw), rx, ry, Theme.TEXT_FAINT, false);
        }
        ry += 12;
        // strains and collection
        ry = Ui.section(g, font, rx, ry, rw, Component.translatable("screen.noirm.colony.strains", c.caught().size(), WildKind.collection().length),
                Theme.TEXT_DIM);
        StringBuilder strains = new StringBuilder();
        for (String s : c.strains()) {
            WildKind k = WildKind.byKey(s);
            if (k != null) {
                strains.append(strains.length() > 0 ? ", " : "").append(Component.translatable(k.translationKey()).getString());
            }
        }
        if (c.flag(ColonyPackets.ColonySync.ANTIVENOM)) {
            strains.append(strains.length() > 0 ? ", " : "").append(Component.translatable("screen.noirm.colony.antivenom").getString());
        }
        ry = Ui.wrap(g, font, Component.literal(strains.length() == 0 ? "—" : strains.toString()), rx, ry, rw, ry + 20, Theme.TEXT);
        ry += 2;
        // hives and marks side by side
        int half = (rw - Theme.S5) / 2;
        int hy = Ui.section(g, font, rx, ry, half, Component.translatable("screen.noirm.colony.hive_list"), Theme.TEXT_DIM);
        int my2 = Ui.section(g, font, rx + half + Theme.S5, ry, half, Component.translatable("screen.noirm.colony.marks"), Theme.TEXT_DIM);
        for (ColonyPackets.HiveView hv : c.hives()) {
            if (hy > bottom - 9) {
                break;
            }
            g.drawString(font, Ui.fit(font, hv.where() + "  " + ColonyHud.number(hv.count()), half), rx, hy, Theme.TEXT, false);
            hy += 10;
        }
        if (c.hives().isEmpty()) {
            g.drawString(font, "—", rx, hy, Theme.TEXT_FAINT, false);
        }
        for (ColonyPackets.MarkView m : c.marks()) {
            if (my2 > bottom - 9) {
                break;
            }
            String d = m.distance() >= 0 ? m.distance() + "m" : "?";
            g.drawString(font, Ui.fit(font, "◆ " + m.name() + " " + d + " " + (m.seconds() / 60) + "′", half), rx + half + Theme.S5, my2, ColonyHud.TEAL, false);
            my2 += 10;
        }
        if (c.marks().isEmpty()) {
            g.drawString(font, "—", rx + half + Theme.S5, my2, Theme.TEXT_FAINT, false);
        }
        return null;
    }

    private void line(GuiGraphics g, int x, int y, int w, String key, String value) {
        g.drawString(font, Component.translatable(key), x, y, Theme.TEXT_DIM, false);
        Ui.textRight(g, font, value, x + w, y, Theme.TEXT);
    }

    private void cell(GuiGraphics g, int x, int y, int w, String key, String value, int colour) {
        g.fill(x, y, x + 2, y + 18, colour);
        g.drawString(font, Ui.fit(font, Component.translatable(key).getString(), w - 6), x + 5, y, Theme.TEXT_DIM, false);
        g.drawString(font, Ui.fit(font, value, w - 6), x + 5, y + 10, Theme.TEXT, false);
    }
}

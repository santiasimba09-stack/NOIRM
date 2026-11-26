package com.noirm.client.aburame;

import com.noirm.aburame.NestRoll;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.creator.CreatorPackets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The creator's "The Nest" card for an Aburame roll (docs/ABURAME.md, 2.8): the starting colony, the
 * nest species (rinkaichū with a gold "1 in 12" chip) and the temperament. It's worked out from the
 * roll itself ({@link NestRoll}), so rerolling the clan rerolls it, and the server grants the same.
 */
public final class NestCard {
    private NestCard() {}

    /** Draws the card (two short lines) if {@code roll} is an Aburame; returns the y below it. */
    public static int draw(GuiGraphics g, Font font, int x, int y, int w, CreatorPackets.RollView roll) {
        if (!"aburame".equals(roll.clanId())) {
            return y;
        }
        NestRoll nest = NestRoll.of(roll.clanId(), roll.base(), roll.talents().stream().map(t -> t.id()).toList());
        Component species = Component.translatable(nest.rinkaichu() ? "aburame.noirm.species.rinkaichu" : "aburame.noirm.species.kikaichu");
        Component line = Component.translatable("creator.noirm.nest.line", ColonyHud.number(nest.colony()), species,
                Component.translatable("creator.noirm.nest.temper." + nest.temperament()));
        g.drawString(font, "蟲", x, y, ColonyHud.TEAL, false);
        int lw = w - 12;
        if (nest.rinkaichu()) {
            String chip = Component.translatable("creator.noirm.nest.rare").getString();
            int cw = font.width(chip) + 6;
            Ui.chip(g, font, x + w - cw, y - 1, chip, Theme.GOLD);
            lw -= cw + 4;
        }
        g.drawString(font, Ui.fit(font, line, lw), x + 11, y, Theme.TEXT_DIM, false);
        return y + 11;
    }
}

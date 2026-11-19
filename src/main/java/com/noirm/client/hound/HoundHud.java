package com.noirm.client.hound;

import com.noirm.client.ui.HudKit;
import com.noirm.client.ui.Smooth;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.hound.HoundPackets;
import com.noirm.hound.NinjaHound;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Your hound on the HUD, top left under the status lines: its name and stage over a thin health
 * bar, and a red line while it's knocked out. It shows when it matters: in combat mode, when the
 * hound is hurt, down, tracking or frenzied, and for a few seconds after its bond, XP or stage
 * changes. Otherwise it keeps quiet.
 */
public final class HoundHud {
    private static final int BAR_W = 92;
    private static final Smooth HEALTH = new Smooth(120f);
    private static final Smooth GROWTH = new Smooth(120f);

    private HoundHud() {}

    /** Draws from {@code y}; returns the y below. */
    public static int draw(GuiGraphics g, Font font, int x, int y, boolean combat) {
        HoundPackets.HoundSync sync = ClientHound.sync();
        if (!sync.has() || !sync.out()) {
            return y;
        }
        NinjaHound hound = ClientHound.entity();
        float hp = hound == null || hound.getMaxHealth() <= 0 ? 0f : hound.getHealth() / hound.getMaxHealth();
        HEALTH.update(hp);
        float growth = sync.level() * 1000f + sync.bond() + sync.xp();
        GROWTH.update(growth);
        boolean hurt = hound != null && hp < 0.999f;
        boolean busy = hound != null && (hound.isDown() || hound.isFrenzied() || hound.tracking() != null || hound.isFused() || hound.isHidden());
        float recent = GROWTH.recency();
        if (!combat && !hurt && !busy && recent <= 0.01f) {
            return y;
        }
        float alpha = combat || hurt || busy ? 1f : recent;
        Component stage = Component.translatable("screen.noirm.hound.level_short", sync.level());
        Component line;
        int colour;
        if (hound == null) {
            line = Component.translatable("hud.noirm.hound.away", sync.name());
            colour = Theme.TEXT_DIM;
        } else if (hound.isDown()) {
            line = Component.translatable("hud.noirm.hound.down", sync.name(), (hound.downTicks() + 19) / 20);
            colour = Theme.RED;
        } else if (hound.isFused()) {
            line = Component.translatable("hud.noirm.hound.fused", sync.name(), (hound.fusedTicks() + 19) / 20);
            colour = Theme.GOLD;
        } else {
            line = Component.translatable("hud.noirm.hound.line", sync.name(), stage, Math.round(sync.bond()));
            colour = hound.isFrenzied() ? 0xFFE0705A : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.5f);
        }
        HudKit.text(g, font, "犬", x, y, Theme.SEAL, alpha);
        HudKit.text(g, font, Ui.fit(font, line, BAR_W + 40), x + font.width("犬") + 3, y, colour, alpha);
        if (hound != null) {
            int barY = y + font.lineHeight + 1;
            Ui.bar(g, x, barY, BAR_W, 2, HEALTH.value(), HEALTH.ghost(), Theme.fade(hound.isDown() ? Theme.RED : Theme.HEALTH, alpha), HudKit.TRACK);
            y += 4;
        }
        return y + 11;
    }
}

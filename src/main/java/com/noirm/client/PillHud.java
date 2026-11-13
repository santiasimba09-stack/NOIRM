package com.noirm.client;

import com.noirm.client.ui.HudKit;
import com.noirm.client.ui.Theme;
import com.noirm.client.ui.Ui;
import com.noirm.pills.PillClientState;
import com.noirm.pills.PillData;
import com.noirm.pills.PillEffects;
import com.noirm.pills.PillPackets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Foods and pills on the HUD, top left under the status lines (no panels: shadowed text and thin
 * bars, like the rest of the HUD). One line per food or pill at work, in its colour, with a bar of
 * the time left; a pill's crash in red (dim while it waits to hit); cooldowns in grey; and the
 * body's toxicity when there is any. Quiet when nothing is running.
 */
public final class PillHud {
    private static final int BAR_W = 92;
    private static final int LINE = 11;
    /** At most this many effect lines (the rest are summed up as "+N"). */
    private static final int MAX_LINES = 5;
    private static final int TOXIC = 0xFFB8C040;

    /** One summarised line, rebuilt only when a new copy arrives. */
    private record Entry(ResourceLocation source, boolean crash, int left, int total, int delay, int color) {}

    private static PillPackets.Sync builtFor;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private PillHud() {}

    private static void rebuild(PillPackets.Sync sync) {
        builtFor = sync;
        ENTRIES.clear();
        Map<String, Entry> grouped = new LinkedHashMap<>();
        for (PillPackets.Line l : sync.lines()) {
            String key = l.source() + (l.crash() ? "#crash" : "");
            Entry old = grouped.get(key);
            int end = l.delay() + l.left();
            if (old == null || end > old.delay() + old.left()) {
                grouped.put(key, new Entry(l.source(), l.crash(), l.left(), l.total(), l.delay(), l.color()));
            }
        }
        ENTRIES.addAll(grouped.values());
    }

    /** Draws from {@code y}; returns the y below. */
    static int draw(GuiGraphics g, Font font, int x, int y, boolean combat) {
        PillPackets.Sync sync = PillClientState.get();
        if (sync == null) {
            return y;
        }
        if (sync != builtFor) {
            rebuild(sync);
        }
        int since = PillClientState.ticksSince();
        int shown = 0;
        for (Entry e : ENTRIES) {
            if (e.crash() && e.delay() - since > 0 && hasActive(e.source(), since)) {
                continue; // said on the pill's own line ("then crash")
            }
            int delay = Math.max(0, e.delay() - since);
            int left = e.delay() > 0 ? (delay > 0 ? e.left() : Math.max(0, e.left() - (since - e.delay()))) : Math.max(0, e.left() - since);
            if (left <= 0) {
                continue;
            }
            if (shown >= MAX_LINES) {
                shown++;
                continue;
            }
            Component name = Component.translatable(PillEffects.itemKey(e.source()));
            Component line;
            int color;
            if (e.crash() && delay > 0) {
                line = Component.translatable("hud.noirm.pill.crash_soon", name, clock(delay));
                color = Theme.fade(Theme.RED, 0.6f);
            } else if (e.crash()) {
                line = Component.translatable("hud.noirm.pill.crash", name, clock(left));
                color = Theme.RED;
            } else {
                boolean crashNext = ENTRIES.stream().anyMatch(o -> o.crash() && o.source().equals(e.source()) && o.delay() - since > 0);
                line = Component.translatable(crashNext ? "hud.noirm.pill.active_crash" : "hud.noirm.pill.active", name, clock(left));
                color = 0xFF000000 | e.color();
            }
            line = Ui.fit(font, line, BAR_W + 40);
            HudKit.text(g, font, line, x, y, color, 1f);
            if (!(e.crash() && delay > 0)) {
                float frac = Math.min(1f, left / (float) Math.max(1, e.total()));
                Ui.bar(g, x, y + font.lineHeight, BAR_W, 1, frac, frac, color, HudKit.TRACK);
                y += 2;
            }
            y += LINE;
            shown++;
        }
        if (shown > MAX_LINES) {
            HudKit.text(g, font, "+" + (shown - MAX_LINES), x, y, Theme.TEXT_DIM, 1f);
            y += LINE;
        }
        // Cooldowns: one quiet line.
        StringBuilder cds = null;
        for (PillPackets.Timer t : sync.cooldowns()) {
            int left = t.left() - since;
            if (left <= 0) {
                continue;
            }
            if (cds == null) {
                cds = new StringBuilder();
            } else {
                cds.append("  ");
            }
            cds.append(Component.translatable(PillEffects.itemKey(t.source())).getString()).append(' ').append(clock(left));
        }
        if (cds != null) {
            Component line = Ui.fit(font, Component.translatable("hud.noirm.pill.cooldowns", cds.toString()), BAR_W + 60);
            HudKit.text(g, font, line, x, y, Theme.TEXT_DIM, 0.9f);
            y += LINE;
        }
        // Toxicity, while there's any (it wears off slowly).
        float tox = Math.max(0f, sync.toxicity() - since / 20f * PillEffects.TOXICITY_DECAY_PER_SECOND);
        if (tox >= 1f) {
            int color = tox >= PillEffects.SICK_AT ? Theme.RED : tox >= PillEffects.NAUSEA_AT ? Theme.AMBER : TOXIC;
            HudKit.text(g, font, Component.translatable("hud.noirm.pill.toxicity", Math.round(tox)), x, y, color, combat ? 1f : 0.85f);
            float frac = tox / PillData.MAX_TOXICITY;
            Ui.bar(g, x, y + font.lineHeight + 1, BAR_W, 2, frac, frac, color, HudKit.TRACK);
            y += LINE + 4;
        }
        return y;
    }

    private static boolean hasActive(ResourceLocation source, int since) {
        for (Entry o : ENTRIES) {
            if (!o.crash() && o.source().equals(source) && o.left() - since > 0) {
                return true;
            }
        }
        return false;
    }

    /** Clears the client's copy when leaving a world. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.noirm.Noirm.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class PillHudEvents {
        private PillHudEvents() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void pillsLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            PillClientState.reset();
            builtFor = null;
            ENTRIES.clear();
        }
    }

    private static String clock(int ticks) {
        int s = (ticks + 19) / 20;
        return s >= 60 ? (s / 60) + ":" + String.format("%02d", s % 60) : s + "s";
    }
}

package com.noirm.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

/**
 * The NOIRM component kit: flat, minimalist pieces in the {@link Theme} palette, drawn in code.
 * Immediate mode: call them every frame with the current state (and an eased hover amount from a
 * {@link HoverTracker}); they draw and return sizes, the screen keeps its own hit boxes.
 *
 * <p>Rules (docs/UI.md): flat surfaces, at most a 1-px hairline, no double frames or gradients;
 * hierarchy from text colour and spacing; accents are thin (an underline, a 1-px bar).
 */
public final class Ui {
    private Ui() {}

    // ================================================================ surfaces

    /** A screen panel: a flat dark surface with a single hairline edge. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Theme.SURFACE);
        outline(g, x, y, w, h, Theme.HAIRLINE);
    }

    /** A flat fill. */
    public static void surface(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    /** A sunken well with no edge (3D views, slot backs). */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Theme.WELL);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, color);
    }

    /** A horizontal 1-px rule. */
    public static void hairline(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, Theme.HAIRLINE);
    }

    /** A vertical 1-px rule. */
    public static void vline(GuiGraphics g, int x, int y, int h) {
        g.fill(x, y, x + 1, y + h, Theme.HAIRLINE);
    }

    // ================================================================ text

    /** A section header: small upper-case label (dim, or tinted) and a thin rule to the right. Returns the y below it. */
    public static int section(GuiGraphics g, Font font, int x, int y, int w, Component title, int color) {
        String text = fit(font, title.getString().toUpperCase(Locale.ROOT), w - 10);
        g.drawString(font, text, x, y, color, false);
        int lx = x + font.width(text) + Theme.S3;
        if (lx < x + w) {
            g.fill(lx, y + 4, x + w, y + 5, Theme.HAIRLINE);
        }
        return y + 12;
    }

    /** {@code text} cut to {@code width} pixels with "…" if it doesn't fit. */
    public static String fit(Font font, String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    /** Keeps the style of {@code text} when cutting it. */
    public static Component fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return Component.literal(fit(font, text.getString(), width)).withStyle(text.getStyle());
    }

    /** True if {@code text} would be cut at {@code width} (show the full text as a tooltip then). */
    public static boolean truncated(Font font, Component text, int width) {
        return font.width(text) > width;
    }

    /** Draws {@code text} cut to {@code width}; returns true if it was cut. */
    public static boolean text(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        boolean cut = font.width(text) > width;
        g.drawString(font, cut ? fit(font, text, width) : text, x, y, color, false);
        return cut;
    }

    /** Right-aligned at {@code right}. */
    public static void textRight(GuiGraphics g, Font font, String text, int right, int y, int color) {
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    public static void textCentered(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    /** Wrapped text that stops before {@code maxY} (the last visible line ends in "…"); returns the y below it. */
    public static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int w, int maxY, int color) {
        List<FormattedCharSequence> lines = font.split(FormattedText.composite(text), Math.max(10, w));
        for (int i = 0; i < lines.size(); i++) {
            if (y + 9 > maxY) {
                break;
            }
            if (i < lines.size() - 1 && y + 18 > maxY) {
                StringBuilder sb = new StringBuilder();
                lines.get(i).accept((index, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                g.drawString(font, fit(font, sb + "…", w), x, y, color, false);
                return y + Theme.LINE;
            }
            g.drawString(font, lines.get(i), x, y, color, false);
            y += Theme.LINE;
        }
        return y;
    }

    /** A glyph and a label on one line; returns the x after it. */
    public static int iconLabel(GuiGraphics g, Font font, int x, int y, String glyph, int glyphColor, Component label, int labelColor, int maxW) {
        g.drawString(font, glyph, x, y, glyphColor, false);
        int lx = x + font.width(glyph) + Theme.S2;
        String text = fit(font, label.getString(), maxW - (lx - x));
        g.drawString(font, text, lx, y, labelColor, false);
        return lx + font.width(text);
    }

    // ================================================================ controls

    /**
     * A flat button: raised surface that lightens on hover and an accent underline that grows from
     * the centre with the hover amount. A primary button has a tinted surface and a full underline.
     */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, float hover, boolean enabled,
                              int accent, boolean primary) {
        int base = primary && enabled ? Theme.mix(Theme.RAISED, accent, 0.18f) : Theme.RAISED;
        int over = primary && enabled ? Theme.mix(Theme.RAISED, accent, 0.30f) : Theme.RAISED_HOVER;
        g.fill(x, y, x + w, y + h, enabled ? Theme.mix(base, over, hover) : Theme.RAISED);
        if (enabled) {
            float line = primary ? 1f : hover;
            int lw = Math.round(w * line);
            if (lw > 0) {
                int lx = x + (w - lw) / 2;
                g.fill(lx, y + h - 1, lx + lw, y + h, primary ? accent : Theme.mix(Theme.darken(accent, 0.6f), accent, hover));
            }
        }
        int color = !enabled ? Theme.TEXT_FAINT : Theme.mix(primary ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, 0.6f), Theme.TEXT, hover);
        String text = fit(font, label.getString(), w - 6);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, color, false);
    }

    /** A lit toggle ("pill"): like a button, but showing an on state with a tinted surface and accent text. */
    public static void toggle(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean on, float hover, int accent) {
        int base = on ? Theme.mix(Theme.RAISED, accent, 0.22f) : Theme.RAISED;
        g.fill(x, y, x + w, y + h, Theme.mix(base, on ? Theme.mix(Theme.RAISED, accent, 0.32f) : Theme.RAISED_HOVER, hover));
        if (on || hover > 0f) {
            int lw = on ? w : Math.round(w * hover);
            int lx = x + (w - lw) / 2;
            g.fill(lx, y + h - 1, lx + lw, y + h, on ? accent : Theme.darken(accent, 0.7f));
        }
        String text = fit(font, label.getString(), w - 6);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2,
                on ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hover), false);
    }

    /**
     * One segment of a segmented control ({@code count} equal parts across {@code w}). The active
     * one is tinted with an accent underline; hairlines separate the rest. Its rectangle is
     * {@link #segX} / {@link #segW}.
     */
    public static void segment(GuiGraphics g, Font font, int x, int y, int w, int h, int i, int count, Component label, boolean active,
                                float hover, int accent) {
        int sx = segX(x, w, i, count);
        int width = segW(w, i, count);
        int fill = active ? Theme.mix(Theme.RAISED, accent, 0.22f) : Theme.mix(Theme.RAISED, Theme.RAISED_HOVER, hover);
        g.fill(sx, y, sx + width, y + h, fill);
        if (i > 0 && !active) {
            g.fill(sx, y + 3, sx + 1, y + h - 3, Theme.HAIRLINE);
        }
        if (active) {
            g.fill(sx, y + h - 1, sx + width, y + h, accent);
        }
        String text = fit(font, label.getString(), width - 6);
        g.drawString(font, text, sx + (width - font.width(text)) / 2, y + (h - 8) / 2,
                active ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hover), false);
    }

    public static int segX(int x, int w, int i, int count) {
        return x + i * (w / count);
    }

    public static int segW(int w, int i, int count) {
        int sw = w / count;
        return i == count - 1 ? w - sw * (count - 1) : sw;
    }

    /** A slider: a 2-px track, the filled part in the accent, a small bone knob. {@code t} 0..1. */
    public static void slider(GuiGraphics g, int x, int y, int w, float t, int accent, float hover) {
        t = Anim.clamp01(t);
        g.fill(x, y + 3, x + w, y + 5, Theme.HAIRLINE);
        int fill = Math.round(t * w);
        g.fill(x, y + 3, x + fill, y + 5, accent);
        int knob = x + Math.round(t * (w - 1));
        int grow = hover > 0.5f ? 1 : 0;
        g.fill(knob - 1 - grow, y - grow, knob + 2 + grow, y + 8 + grow, Theme.TEXT);
    }

    /**
     * A thin progress bar with rounded-looking ends (the corner pixels are left out when it's at
     * least 3 high). {@code ghost} ≥ {@code fraction} draws a lighter trail (a recent drop); pass
     * the same value for none.
     */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, float ghost, int color, int track) {
        if (w <= 0) {
            return;
        }
        roundRect(g, x, y, w, h, track);
        int ghostW = Math.round(w * Anim.clamp01(ghost));
        int fill = Math.round(w * Anim.clamp01(fraction));
        if (ghostW > fill) {
            roundRect(g, x, y, ghostW, h, Theme.fade(Theme.mix(color, 0xFFFFFFFF, 0.45f), 0.55f));
        }
        if (fill > 0) {
            roundRect(g, x, y, fill, h, color);
            if (h >= 3 && fill > 2) {
                g.fill(x + 1, y, x + fill - 1, y + 1, 0x30FFFFFF); // a faint lit top edge
            }
        }
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        bar(g, x, y, w, h, fraction, fraction, color, Theme.mix(Theme.WELL, Theme.HAIRLINE, 0.6f));
    }

    /** A rectangle without its four corner pixels (reads as rounded at GUI scale). */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (h < 3 || w < 3) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * A list row / card background: transparent at rest, lightening with hover; a selected row gets a
     * raised fill and a 1-px accent bar on its left.
     */
    public static void row(GuiGraphics g, int x, int y, int w, int h, float hover, boolean selected, int accent) {
        int fill = selected ? Theme.mix(Theme.RAISED, accent, 0.16f) : Theme.mix(Theme.RAISED, Theme.RAISED_HOVER, hover);
        g.fill(x, y, x + w, y + h, fill);
        if (selected) {
            g.fill(x, y, x + 1, y + h, accent);
        } else if (hover > 0.01f) {
            g.fill(x, y, x + 1, y + h, Theme.fade(accent, hover * 0.8f));
        }
    }

    /** A small tag: text in {@code color} on a faint tint of it. Returns its width. */
    public static int chip(GuiGraphics g, Font font, int x, int y, String text, int color) {
        int w = font.width(text) + 6;
        g.fill(x, y, x + w, y + 11, Theme.mix(Theme.RAISED, color, 0.16f));
        g.drawString(font, text, x + 3, y + 2, color, false);
        return w;
    }

    /** A thin scrollbar (only when the list overflows). */
    public static void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int rows, int total, int color) {
        if (total <= rows || h <= 0) {
            return;
        }
        g.fill(x, y, x + 1, y + h, Theme.HAIRLINE);
        int thumb = Math.max(8, h * rows / total);
        int top = y + (h - thumb) * scroll / Math.max(1, total - rows);
        g.fill(x - 1, top, x + 1, top + thumb, color);
    }

    /** A small dot-and-label legend item; returns the x after it. */
    public static int legend(GuiGraphics g, Font font, int x, int y, int color, String text, int maxX) {
        if (x + 12 > maxX) {
            return x;
        }
        Ui.roundRect(g, x, y + 2, 4, 4, color);
        String label = fit(font, text, maxX - x - 8);
        g.drawString(font, label, x + 7, y, Theme.TEXT_DIM, false);
        return x + 7 + font.width(label) + Theme.S4;
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}

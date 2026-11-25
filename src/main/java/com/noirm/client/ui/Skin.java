package com.noirm.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.noirm.Noirm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * The kit's ninja look (docs/UI.md, "The ninja look"): every panel, card, button, header rail, seal and brush
 * stroke is drawn from one 256×256 atlas ({@code textures/gui/ui/<look>.png}, by {@code tools/uigen/gen_ui.py})
 * with nine-slicing, so the pixels stay crisp at any size and every screen changes at once.
 *
 * <p>Three looks were mocked side by side; {@link Look#SHOJI} (lacquered wood, dark paper, hanko seals) was
 * chosen. The others stay selectable for comparison with the environment variable {@code NOIRM_UI_LOOK}
 * ({@code scroll}, {@code steel}); {@link Theme}'s surface colours follow the look.
 */
public final class Skin {
    /** The looks. Atlas layout: see gen_ui.py. */
    public enum Look { SHOJI, SCROLL, STEEL }

    public static final Look LOOK = pick();
    public static final ResourceLocation ATLAS = Noirm.id("textures/gui/ui/" + LOOK.name().toLowerCase(Locale.ROOT) + ".png");

    private Skin() {}

    private static Look pick() {
        String env = System.getenv("NOIRM_UI_LOOK");
        if (env != null) {
            for (Look look : Look.values()) {
                if (look.name().equalsIgnoreCase(env.trim())) {
                    return look;
                }
            }
        }
        return Look.SHOJI;
    }

    /** {@code shoji}, {@code scroll} or {@code steel} by the current look (for colour tokens). */
    static int pick(int shoji, int scroll, int steel) {
        return switch (LOOK) {
            case SHOJI -> shoji;
            case SCROLL -> scroll;
            case STEEL -> steel;
        };
    }

    private static void nine(GuiGraphics g, int x, int y, int w, int h, int border, int u, int v, int uw, int vh) {
        nine(g, x, y, w, h, border, border, u, v, uw, vh);
    }

    /**
     * Nine-slicing with the middle and edges tiled (not stretched, so the paper and wood grain stay pixel-crisp).
     * Our own, because vanilla's divides by zero on some sizes. Borders shrink to fit a small box.
     */
    private static void nine(GuiGraphics g, int x, int y, int w, int h, int bx, int by, int u, int v, int uw, int vh) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int l = Math.min(bx, w / 2);
        int r = Math.min(bx, w - l);
        int t = Math.min(by, h / 2);
        int b = Math.min(by, h - t);
        int cw = Math.max(1, uw - 2 * bx);   // the texture's middle
        int ch = Math.max(1, vh - 2 * by);
        int mw = w - l - r;
        int mh = h - t - b;
        // corners
        blit(g, x, y, u, v, l, t);
        blit(g, x + w - r, y, u + uw - r, v, r, t);
        blit(g, x, y + h - b, u, v + vh - b, l, b);
        blit(g, x + w - r, y + h - b, u + uw - r, v + vh - b, r, b);
        // edges and middle, tiled
        for (int dx = 0; dx < mw; dx += cw) {
            int sw = Math.min(cw, mw - dx);
            blit(g, x + l + dx, y, u + bx, v, sw, t);
            blit(g, x + l + dx, y + h - b, u + bx, v + vh - b, sw, b);
            for (int dy = 0; dy < mh; dy += ch) {
                blit(g, x + l + dx, y + t + dy, u + bx, v + by, sw, Math.min(ch, mh - dy));
            }
        }
        for (int dy = 0; dy < mh; dy += ch) {
            int sh = Math.min(ch, mh - dy);
            blit(g, x, y + t + dy, u, v + by, l, sh);
            blit(g, x + w - r, y + t + dy, u + uw - r, v + by, r, sh);
        }
    }

    /** Three-slice for brush strokes: the ends kept, the middle stretched, all scaled to the height. */
    private static void hslice(GuiGraphics g, int x, int y, int w, int h, int bx, int u, int v, int uw, int vh) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int l = Math.min(bx, w / 2);
        int r = Math.min(bx, w - l);
        g.blit(ATLAS, x, y, l, h, u, v, l, vh, 256, 256);
        g.blit(ATLAS, x + l, y, w - l - r, h, u + bx, v, uw - 2 * bx, vh, 256, 256);
        g.blit(ATLAS, x + w - r, y, r, h, u + uw - r, v, r, vh, 256, 256);
    }

    private static void blit(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        if (w > 0 && h > 0) {
            g.blit(ATLAS, x, y, u, v, w, h, 256, 256);
        }
    }

    private static void tint(GuiGraphics g, int argb, float alpha) {
        RenderSystem.enableBlend();
        float a = ((argb >>> 24) & 0xFF) / 255f;
        g.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, (a == 0 ? 1f : a) * alpha);
    }

    private static void untint(GuiGraphics g) {
        g.setColor(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ pieces

    /** A screen panel: the framed surface; the scroll look adds its wooden rollers top and bottom. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        nine(g, x, y, w, h, 7, 0, 0, 64, 64);
        if (LOOK == Look.SCROLL && w > 24) {
            nine(g, x - 4, y - 5, w + 8, 12, 10, 6, 0, 128, 128, 12);
            nine(g, x - 4, y + h - 7, w + 8, 12, 10, 6, 0, 128, 128, 12);
        }
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h, float hover) {
        RenderSystem.enableBlend();
        nine(g, x, y, w, h, 3, 64, 0, 32, 32);
        if (hover > 0.01f) {
            g.setColor(1f, 1f, 1f, hover);
            nine(g, x, y, w, h, 3, 96, 0, 32, 32);
            untint(g);
        }
    }

    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        nine(g, x, y, w, h, 2, 128, 0, 32, 32);
    }

    /** Buttons: a wooden tag, lighter under the mouse; primary in red lacquer; disabled darker. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, float hover, boolean enabled, boolean primary) {
        RenderSystem.enableBlend();
        int v = !enabled ? 112 : primary ? 96 : 64;
        nine(g, x, y, w, h, 4, 3, 0, v, 64, 16);
        if (enabled && hover > 0.01f) {
            g.setColor(1f, 1f, 1f, primary ? hover * 0.35f : hover);
            nine(g, x, y, w, h, 4, 3, 0, 80, 64, 16);
            untint(g);
        }
    }

    /** The header rail behind a screen's title row (the steel look: a forehead-protector plate). */
    public static void headerRail(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        nine(g, x, y, w, h, 6, 3, 192, 0, 64, 16);
    }

    /** A red-ink brush swash behind a title (the creator's big title). */
    public static void swash(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        hslice(g, x, y, w, h, 8, 192, 16, 64, 16);
    }

    /** A hanko-style seal stamp in {@code color}. */
    public static void seal(GuiGraphics g, int x, int y, int size, int color) {
        tint(g, color, 1f);
        g.blit(ATLAS, x, y, size, size, 64, 64, 16, 16, 256, 256);
        untint(g);
    }

    /** A brush stroke (w × h, h up to 6) in {@code color}: the active tab, a lit mark. */
    public static void stroke(GuiGraphics g, int x, int y, int w, int h, int color, float alpha) {
        if (w <= 0) {
            return;
        }
        tint(g, color, alpha);
        hslice(g, x, y, w, h, 8, 64, 80, 64, 6);
        untint(g);
    }

    /** A thin brush rule (section and tab rules). */
    public static void rule(GuiGraphics g, int x, int y, int w, int color) {
        if (w <= 0) {
            return;
        }
        tint(g, color, 1f);
        hslice(g, x, y, w, 3, 6, 64, 88, 64, 3);
        untint(g);
    }

    /** A dark lacquer channel around a bar (x, y, w, h is the bar itself). */
    public static void barFrame(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        nine(g, x - 1, y - 1, w + 2, h + 2, 1, 64, 96, 64, 8);
    }

    /** Corner ornaments on a box (brass brackets, gilt caps or rivets, by look). */
    public static void corners(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        int s = 6;
        g.blit(ATLAS, x, y, s, s, 128, 64, s, s, 256, 256);
        g.pose().pushPose();
        g.pose().translate(x + w, y, 0);
        g.pose().scale(-1f, 1f, 1f);
        g.blit(ATLAS, 0, 0, s, s, 128, 64, s, s, 256, 256);
        g.pose().popPose();
    }
}

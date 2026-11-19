package com.noirm.client.ui;

/**
 * The NOIRM UI tokens: every colour and spacing step the screens and the HUD use, in one place.
 * The palette is the one we established (ash and iron near-blacks, bone white text, a blood-red
 * accent, gold for highlights, chakra blue and green), trimmed to a small set. See {@code docs/UI.md}.
 *
 * <p>Colours are ARGB ints, ready for {@code GuiGraphics.fill} and {@code drawString}.
 */
public final class Theme {
    private Theme() {}

    // ------------------------------------------------------------------ surfaces (ash and iron)

    /** The dim layer behind a screen (the world shows through). */
    public static final int SCRIM = 0xB0060508;
    /** A screen's main surface: flat ash-black. */
    public static final int SURFACE = 0xFF121016;
    /** A raised surface inside a panel: cards, list rows, inputs. */
    public static final int RAISED = 0xFF19171E;
    /** A raised surface under the mouse. */
    public static final int RAISED_HOVER = 0xFF231F29;
    /** A sunken well: bar tracks, slot backs, the 3D view. */
    public static final int WELL = 0xFF0C0B0F;
    /** The one hairline: panel edges and section rules. */
    public static final int HAIRLINE = 0xFF2B2731;
    /** A stronger hairline (iron) for focus and hover outlines. */
    public static final int IRON = 0xFF4A4452;

    // ------------------------------------------------------------------ text (bone and ash)

    /** Body text and values: bone/paper white. */
    public static final int TEXT = 0xFFEDE4D3;
    /** Secondary text: labels, descriptions. */
    public static final int TEXT_DIM = 0xFF9A929F;
    /** Tertiary text: hints, disabled, empty states. */
    public static final int TEXT_FAINT = 0xFF5F5967;

    // ------------------------------------------------------------------ accents

    /** The NOIRM accent: blood red (seals, the active tab, primary actions). */
    public static final int SEAL = 0xFFB8322E;
    /** Brighter red for warnings, damage and "no". */
    public static final int RED = 0xFFE05A5A;
    /** Gold: highlights, rank, mastery stars, "you can do this now". */
    public static final int GOLD = 0xFFE8C060;
    /** Positive: owned, ready, done. */
    public static final int GREEN = 0xFF6FD38A;
    /** Chakra blue. */
    public static final int CHAKRA = 0xFF4A90F0;
    /** A lighter chakra blue for text and the creator's highlights. */
    public static final int CHAKRA_LIGHT = 0xFF6FB3E8;
    /** Nature energy / stamina green. */
    public static final int NATURE = 0xFF7FD36A;
    /** Posture and "careful": amber. */
    public static final int AMBER = 0xFFE8A050;
    /** Health. */
    public static final int HEALTH = 0xFFD23C3C;

    // ------------------------------------------------------------------ spacing scale (GUI pixels)

    public static final int S1 = 2;
    public static final int S2 = 4;
    public static final int S3 = 6;
    public static final int S4 = 8;
    public static final int S5 = 12;
    public static final int S6 = 16;

    /** One line of text (the vanilla font is 9 high). */
    public static final int LINE = 10;
    /** A clickable row (button, tab, list row). */
    public static final int ROW = 14;

    // ------------------------------------------------------------------ colour maths

    /** {@code color} with its alpha replaced ({@code alpha} 0..1). */
    public static int alpha(int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** {@code color} with its own alpha multiplied by {@code factor}. */
    public static int fade(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        return (Math.round(a * Math.max(0f, Math.min(1f, factor))) << 24) | (color & 0xFFFFFF);
    }

    /** Darker (factor below 1), opaque. */
    public static int darken(int color, float factor) {
        int r = Math.round((color >> 16 & 0xFF) * factor);
        int g = Math.round((color >> 8 & 0xFF) * factor);
        int b = Math.round((color & 0xFF) * factor);
        return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    /** Mixes two colours (alpha included), {@code t} 0 = a, 1 = b. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24;
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int na = Math.round(aa + ((b >>> 24) - aa) * t);
        int nr = Math.round(ar + ((b >> 16 & 0xFF) - ar) * t);
        int ng = Math.round(ag + ((b >> 8 & 0xFF) - ag) * t);
        int nb = Math.round(ab + ((b & 0xFF) - ab) * t);
        return na << 24 | nr << 16 | ng << 8 | nb;
    }
}

package com.noirm.client.ui;

import net.minecraft.Util;

/**
 * Time-based animation helpers. Everything is driven by the wall clock (milliseconds), never by
 * frame counts, so motion looks the same at 30 or 240 fps. No allocation: plain maths.
 */
public final class Anim {
    /** Screen open (fade + slide). */
    public static final int OPEN_MS = 170;
    /** Screen close. */
    public static final int CLOSE_MS = 110;
    /** Tab content cross-fade. */
    public static final int TAB_MS = 140;
    /** Hover colour / underline ease: time constant. */
    public static final float HOVER_TAU_MS = 55f;
    /** Bar fill ease: time constant. */
    public static final float BAR_TAU_MS = 90f;
    /** A HUD value stays on screen this long after it changes, then fades. */
    public static final int SHOW_ON_CHANGE_MS = 2200;
    public static final int FADE_OUT_MS = 450;

    private Anim() {}

    /** Milliseconds, monotonic. */
    public static long now() {
        return Util.getMillis();
    }

    public static float clamp01(float t) {
        return t < 0f ? 0f : t > 1f ? 1f : t;
    }

    /** 0..1 for how far {@code since} is into a {@code durationMs} animation. */
    public static float progress(long since, int durationMs) {
        return clamp01((now() - since) / (float) Math.max(1, durationMs));
    }

    public static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /** A gentle overshoot for small pops (scale-in). */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.2f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /**
     * Frame-rate independent exponential approach: how much of the remaining gap to close this frame,
     * given {@code dtMs} since the last frame and a time constant {@code tauMs}.
     */
    public static float approach(float dtMs, float tauMs) {
        if (dtMs <= 0f) {
            return 0f;
        }
        return 1f - (float) Math.exp(-dtMs / Math.max(1f, tauMs));
    }

    /** 1 while fresh, fading to 0 between {@code holdMs} and {@code holdMs + fadeMs} after {@code since}. */
    public static float holdThenFade(long since, int holdMs, int fadeMs) {
        long age = now() - since;
        if (age <= holdMs) {
            return 1f;
        }
        return 1f - clamp01((age - holdMs) / (float) Math.max(1, fadeMs));
    }
}

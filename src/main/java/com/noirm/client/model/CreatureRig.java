package com.noirm.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The shared maths that make NOIRM creatures move like animals instead of robots (skill {@code noirm-entity-animation}).
 * A creature's renderer keeps one small per-entity state object built from these pieces and supplies only its own
 * profile: bone names, amplitudes, mood postures. First used by Shukaku ({@code client/beast/ShukakuRenderer}).
 *
 * <ul>
 *   <li>{@link #approach}: frame-rate independent easing toward a target (postures blend, never snap);</li>
 *   <li>{@link Spring}: a damped spring per chain segment (tails, ears, belly) for follow-through;</li>
 *   <li>{@link Blinker} and {@link Twitch}: seeded random timers so no two creatures blink or flick together;</li>
 *   <li>{@link #lod}: 0 full, 1 mid, 2 far, from the camera distance scaled by the creature's size.</li>
 * </ul>
 */
public final class CreatureRig {
    /** LOD distances in blocks (divided by the creature's scale first): under LOD0 everything, under LOD1 no springs. */
    public static final float LOD0_DISTANCE = 24f, LOD1_DISTANCE = 64f;

    private CreatureRig() {}

    /** Moves {@code current} toward {@code target}, closing 63 % of the gap every {@code tau} seconds. */
    public static float approach(float current, float target, float dt, float tau) {
        return current + (target - current) * (1f - (float) Math.exp(-dt / Math.max(1e-3f, tau)));
    }

    /** A stable 0..1 number from a seed and a counter (no allocation, same on every frame). */
    public static float hash(long seed, int n) {
        long h = seed * 0x9E3779B97F4A7C15L + n * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Ease in and out (0..1 → 0..1). */
    public static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /**
     * The four beats of an action as one weight curve over {@code len} seconds: a quick rise (anticipation into the
     * action), a hold, and a slower settle. 0 outside the window.
     */
    public static float envelope(float seconds, float len) {
        if (seconds <= 0f || seconds >= len) {
            return 0f;
        }
        float in = Math.min(0.2f, len * 0.25f), out = Math.min(0.35f, len * 0.35f);
        if (seconds < in) {
            return smooth(seconds / in);
        }
        if (seconds > len - out) {
            return smooth((len - seconds) / out);
        }
        return 1f;
    }

    /** 0 near, 1 mid, 2 far: the camera distance divided by the creature's size. */
    public static int lod(Entity entity, float scale) {
        double d = Math.sqrt(Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity)) / Math.max(1f, scale);
        return d < LOD0_DISTANCE ? 0 : d < LOD1_DISTANCE ? 1 : 2;
    }

    /** A damped spring (angle {@link #a}, velocity {@link #v}): stiffness 30–80 and damping 6–12 suit tails. */
    public static final class Spring {
        public float a, v;

        public void step(float target, float stiffness, float damping, float dt) {
            v += (stiffness * (target - a) - damping * v) * dt;
            a += v * dt;
        }

        public void kick(float velocity) {
            v += velocity;
        }
    }

    /**
     * Blinks at a seeded random interval (2–6 s), 120 ms closing and 80 ms opening, sometimes twice in a row.
     * {@link #closed} returns 0 (open) to 1 (shut).
     */
    public static final class Blinker {
        private final long seed;
        private int count;
        private float next, start = -10f;
        private boolean twice;

        public Blinker(long seed) {
            this.seed = seed;
            this.next = 1f + 4f * hash(seed, 0);
        }

        public float closed(float seconds) {
            if (seconds >= next) {
                start = next;
                count++;
                twice = hash(seed, count * 3 + 1) < 0.2f;
                next = start + 2f + 4f * hash(seed, count * 3 + 2);
            }
            float s = seconds - start;
            float one = one(s);
            return twice ? Math.max(one, one(s - 0.28f)) : one;
        }

        private static float one(float s) {
            if (s < 0f || s > 0.2f) {
                return 0f;
            }
            return s < 0.12f ? s / 0.12f : 1f - (s - 0.12f) / 0.08f;
        }
    }

    /**
     * A twitch (ear flick, tail-tip curl) every {@code min}–{@code max} seconds: a fast ease-out to 1 and a settle back,
     * over {@code length} seconds. {@link #value} is 0 between twitches.
     */
    public static final class Twitch {
        private final long seed;
        private final float min, max, length;
        private int count;
        private float next, start = -10f;

        public Twitch(long seed, float min, float max, float length) {
            this.seed = seed;
            this.min = min;
            this.max = max;
            this.length = length;
            this.next = min * hash(seed, 0) + 0.5f;
        }

        public float value(float seconds) {
            if (seconds >= next) {
                start = next;
                count++;
                next = start + min + (max - min) * hash(seed, count);
            }
            float s = (seconds - start) / length;
            if (s < 0f || s > 1f) {
                return 0f;
            }
            return s < 0.25f ? 1f - (1f - s / 0.25f) * (1f - s / 0.25f) : 1f - smooth((s - 0.25f) / 0.75f);
        }
    }
}

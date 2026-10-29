package com.noirm.client.ui;

/**
 * A number that eases toward its target over time: bar fills, counters, underline positions.
 * Also keeps a slow "ghost" (the light trail a bar leaves when it drops) and when the target last
 * changed (for HUD values that show on change, then fade). One instance per animated thing, made
 * once and reused: nothing is allocated per frame.
 */
public final class Smooth {
    private final float tauMs;
    private float value = Float.NaN;
    private float ghost;
    private float target;
    private long last;
    private long changedAt = Long.MIN_VALUE / 2;
    private long ghostHoldUntil;

    public Smooth(float tauMs) {
        this.tauMs = tauMs;
    }

    /** Sets the target and advances the animation to now; returns the shown value. */
    public float update(float newTarget) {
        long now = Anim.now();
        if (Float.isNaN(value)) {
            value = ghost = target = newTarget;
            last = now;
            return value;
        }
        if (Math.abs(newTarget - target) > 1e-4f) {
            if (newTarget < target) {
                ghostHoldUntil = now + 260; // the trail waits a moment before catching up
            }
            changedAt = now;
            target = newTarget;
        }
        float dt = Math.min(250f, now - last);
        last = now;
        value += (target - value) * Anim.approach(dt, tauMs);
        if (Math.abs(target - value) < 1e-3f) {
            value = target;
        }
        if (ghost < value) {
            ghost = value;
        } else if (now > ghostHoldUntil) {
            ghost += (value - ghost) * Anim.approach(dt, tauMs * 3f);
        }
        return value;
    }

    /** Jumps straight to {@code v} (no easing). */
    public void snap(float v) {
        value = ghost = target = v;
        last = Anim.now();
    }

    public float value() {
        return Float.isNaN(value) ? 0f : value;
    }

    /** The slow trail behind a drop (≥ value). */
    public float ghost() {
        return Float.isNaN(value) ? 0f : ghost;
    }

    public float target() {
        return target;
    }

    /** When the target last changed (for "show on change"). */
    public long changedAt() {
        return changedAt;
    }

    /** 1 just after a change, fading to 0 later ({@link Anim#SHOW_ON_CHANGE_MS}). */
    public float recency() {
        return Anim.holdThenFade(changedAt, Anim.SHOW_ON_CHANGE_MS, Anim.FADE_OUT_MS);
    }
}

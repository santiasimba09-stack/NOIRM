package com.noirm.gear;

/**
 * A small cloth simulation for capes, coat tails and hat strips (one per wearer, client side).
 *
 * <p>The cloth is a chain of {@link #ROWS} rows hanging from the shoulders. Each row has an angle
 * away from the back (0 = hanging straight down, 1.5 = blown out flat behind). Every tick each row
 * springs towards where the motion pushes it:
 * <ul>
 *   <li>running forward lifts it behind you (a sprint about 70°);</li>
 *   <li>falling blows it up, jumping presses it down;</li>
 *   <li>moving sideways or turning swings it to the side ({@link #roll});</li>
 *   <li>fast motion adds a flutter that runs down the cloth, and it always sways a little.</li>
 * </ul>
 * Lower rows follow the row above them with a lag, so the cloth curves and waves instead of
 * moving like a board. Stiff cloth ({@code stiff} near 1) lifts less and springs back faster.
 *
 * <p>It's plain maths (no client classes), so the game tests can run it with wild inputs.
 * Numbers are in blocks per tick (what an entity moves in one tick) and radians.
 */
public final class ClothSim {
    public static final int ROWS = 6;
    /** How far a forward speed of 1 block/tick lifts the cloth (radians), before the cap. */
    public static final float RUN_LIFT = 4.2f;
    /** How far falling at 1 block/tick lifts it. */
    public static final float FALL_LIFT = 2.2f;
    /** How hard rising (a jump) presses it down. */
    public static final float RISE_PRESS = 1.4f;
    /** The most the motion can lift the cloth (radians, about 77°). */
    public static final float MAX_LIFT = 1.35f;
    /** Angles never leave this range (radians). */
    public static final float MIN_ANGLE = -0.25f, MAX_ANGLE = 1.5f;

    private final float[] pitch = new float[ROWS];
    private final float[] prevPitch = new float[ROWS];
    private final float[] vel = new float[ROWS];
    private float roll, prevRoll, rollVel;
    private float flutter, prevFlutter;
    private float lift, prevLift;
    private float time;
    private boolean started;

    /**
     * One tick. {@code forward}: speed along where the wearer faces (negative walking backwards);
     * {@code vertical}: up (+) or down (−); {@code side}: speed to the wearer's right; {@code turn}:
     * how far the body turned this tick (degrees); {@code stiff}: 0 loose .. 1 stiff.
     */
    public void step(float forward, float vertical, float side, float turn, float stiff) {
        forward = safe(forward, 2f);
        vertical = safe(vertical, 4f);
        side = safe(side, 2f);
        turn = safe(turn, 180f);
        stiff = Math.max(0f, Math.min(1f, safe(stiff, 1f)));
        System.arraycopy(pitch, 0, prevPitch, 0, ROWS);
        prevRoll = roll;
        prevFlutter = flutter;
        prevLift = lift;
        time += 1f;

        float fall = Math.max(0f, -vertical);
        float rise = Math.max(0f, vertical);
        float target = RUN_LIFT * Math.max(0f, forward) - 1.5f * Math.max(0f, -forward) + FALL_LIFT * fall - RISE_PRESS * rise;
        target = Math.max(-0.2f, Math.min(MAX_LIFT, target)) * (1f - 0.4f * stiff);
        lift += (target - lift) * 0.35f;
        float wantFlutter = Math.min(1f, Math.abs(forward) * 3f + fall * 2f + Math.abs(side) * 2f);
        flutter += (wantFlutter - flutter) * 0.2f;

        float k = 0.16f + 0.24f * stiff;
        float damp = 0.72f - 0.12f * stiff;
        for (int i = 0; i < ROWS; i++) {
            float w = (i + 1f) / ROWS;
            float want = 0.05f + lift * (0.45f + 0.55f * w)
                    + (float) Math.sin(time * 0.9f + i * 0.9f) * 0.1f * flutter * w * (1f - stiff)
                    + (float) Math.sin(time * 0.08f + i * 0.4f) * 0.02f * w;
            if (i > 0) {
                want = 0.65f * want + 0.35f * pitch[i - 1]; // lower rows lag behind the row above
            }
            vel[i] = vel[i] * damp + (want - pitch[i]) * k;
            pitch[i] = clamp(pitch[i] + vel[i]);
            if (i > 0) { // the cloth can't fold sharply between two rows
                pitch[i] = Math.max(pitch[i - 1] - 0.45f, Math.min(pitch[i - 1] + 0.45f, pitch[i]));
            }
        }
        float wantRoll = Math.max(-0.6f, Math.min(0.6f, -side * 2.5f - turn * 0.015f));
        rollVel = rollVel * 0.7f + (wantRoll - roll) * (0.12f + 0.2f * stiff);
        roll = Math.max(-0.8f, Math.min(0.8f, roll + rollVel));
        if (!started) { // the first tick starts at rest, not swinging in from nothing
            started = true;
            System.arraycopy(pitch, 0, prevPitch, 0, ROWS);
            prevRoll = roll;
            prevFlutter = flutter;
            prevLift = lift;
        }
    }

    /** Row {@code row}'s angle away from the back, between the last tick and this one. */
    public float pitch(int row, float partial) {
        int i = Math.max(0, Math.min(ROWS - 1, row));
        return prevPitch[i] + (pitch[i] - prevPitch[i]) * partial;
    }

    /** The sideways swing (radians, + to the wearer's left). */
    public float roll(float partial) {
        return prevRoll + (roll - prevRoll) * partial;
    }

    /** How much the cloth flutters (0 still .. 1 at a run or a fall). */
    public float flutter(float partial) {
        return prevFlutter + (flutter - prevFlutter) * partial;
    }

    /** How far the motion lifts the cloth (radians), for skirts and strips. */
    public float lift(float partial) {
        return prevLift + (lift - prevLift) * partial;
    }

    /** Ticks simulated (drives the ripples). */
    public float time(float partial) {
        return time - 1f + partial;
    }

    private static float clamp(float a) {
        return Math.max(MIN_ANGLE, Math.min(MAX_ANGLE, a));
    }

    private static float safe(float v, float limit) {
        if (!Float.isFinite(v)) {
            return 0f;
        }
        return Math.max(-limit, Math.min(limit, v));
    }
}

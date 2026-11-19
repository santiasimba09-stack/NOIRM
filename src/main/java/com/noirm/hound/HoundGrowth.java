package com.noirm.hound;

import net.minecraft.util.Mth;

/**
 * How a ninja hound grows (docs/INUZUKA.md, "Growth"). There are no fixed stages: a hound has a
 * <b>level</b> that keeps rising, and everything follows it smoothly.
 * <ul>
 *   <li><b>Size</b> rises every level along a curve and levels off around level 40 (bigger than a
 *   horse, still fits through the world).</li>
 *   <li><b>Proportions</b> (head-to-body, leg length, the fur's ruff and mane) blend from puppy to
 *   adult over the first dozen levels (the client builds the model from these numbers).</li>
 *   <li><b>Strength</b> (health, bite, smell, armour) never stops climbing, with diminishing
 *   returns so it stays balanced.</li>
 *   <li>A level needs three things: the hound's own XP, your bond, and your own power. So it
 *   grows as you do.</li>
 * </ul>
 * Abilities unlock at milestone levels ({@link HoundStage}).
 */
public final class HoundGrowth {
    /** Shoulder height in blocks at these levels (straight lines between, flat after the last). */
    private static final int[] SIZE_LEVELS = {1, 5, 6, 12, 20, 30, 40};
    private static final float[] SIZE_SHOULDER = {0.30f, 0.42f, 0.48f, 0.95f, 1.65f, 2.15f, 2.40f};
    /** The levels over which a puppy turns into an adult (proportions). */
    public static final int ADULT_LEVEL = 12;
    /** Carried on your head or in your jacket while its shoulder is at most this high (blocks). */
    public static final float CARRY_SHOULDER = 0.47f;
    /** Ridden from this shoulder height on (and from the GREAT milestone). */
    public static final float RIDE_SHOULDER = 1.25f;

    private HoundGrowth() {}

    // ================================================================ size and shape

    /** Shoulder height (blocks) at {@code level}, before breed and traits. Strictly rising up to level 40. */
    public static float shoulder(float level) {
        if (level <= SIZE_LEVELS[0]) {
            return SIZE_SHOULDER[0];
        }
        for (int i = 1; i < SIZE_LEVELS.length; i++) {
            if (level <= SIZE_LEVELS[i]) {
                float t = (level - SIZE_LEVELS[i - 1]) / (float) (SIZE_LEVELS[i] - SIZE_LEVELS[i - 1]);
                return Mth.lerp(t, SIZE_SHOULDER[i - 1], SIZE_SHOULDER[i]);
            }
        }
        return SIZE_SHOULDER[SIZE_SHOULDER.length - 1];
    }

    /** The biggest a hound gets (before breed and traits), in blocks at the shoulder. */
    public static float maxShoulder() {
        return SIZE_SHOULDER[SIZE_SHOULDER.length - 1];
    }

    /** 0 = puppy proportions, 1 = adult (body). */
    public static float adulthood(float level) {
        return Mth.clamp((level - 1f) / (ADULT_LEVEL - 1f), 0f, 1f);
    }

    /** Legs shoot up first (an adolescent is all legs)… */
    public static float legGrowth(float level) {
        float t = adulthood(level);
        return 1f - (float) Math.pow(1f - t, 1.8);
    }

    /** …and it grows into its head last. */
    public static float headGrowth(float level) {
        return (float) Math.pow(adulthood(level), 1.4);
    }

    /** Extra mass past adulthood (a broad chest, thick legs): 1 → 1.2 by level 30. */
    public static float mass(float level) {
        return 1f + 0.2f * Mth.clamp((level - ADULT_LEVEL) / 18f, 0f, 1f);
    }

    /** How thick and rough the fur is: 0 = puppy fluff, 1 = an old fighter's coat (level 26). */
    public static float furAge(float level) {
        return Mth.clamp((level - 1f) / 25f, 0f, 1f);
    }

    /** Hitbox width and height (blocks) for a hound of this level and size multiplier. */
    public static float width(float level, float size) {
        return shoulder(level) * size * Mth.lerp(adulthood(level), 1.45f, 0.85f);
    }

    public static float height(float level, float size) {
        return shoulder(level) * size * Mth.lerp(adulthood(level), 1.6f, 1.27f);
    }

    // ================================================================ strength (never stops, slows down)

    public static float health(int level) {
        return 12f + 5.5f * (float) Math.pow(Math.max(0, level - 1), 0.85);
    }

    public static float bite(int level) {
        return 2f + 0.75f * (float) Math.pow(Math.max(0, level - 1), 0.8);
    }

    /** Movement speed attribute (a wolf is 0.3): a little faster as it grows, leveling off. */
    public static double speed(int level) {
        return 0.30 + 0.06 * (1.0 - Math.exp(-(level - 1) / 10.0));
    }

    /** How far it smells hidden enemies (blocks). */
    public static double smell(int level) {
        return 10.0 + 5.5 * Math.log(Math.max(1, level));
    }

    public static double armour(int level) {
        return 1.6 * Math.log(Math.max(1, level));
    }

    // ================================================================ what a level takes

    /** Total hound XP to reach {@code level} (level 1 = 0). */
    public static float xpFor(int level) {
        return level <= 1 ? 0f : 10f * (float) Math.pow(level - 1, 1.55);
    }

    /** Bond needed for {@code level} (it stops mattering once the bond is full, around level 26). */
    public static float bondFor(int level) {
        return Math.min(100f, 4f * (level - 1));
    }

    /** Your own power (mean of speed and taijutsu) needed for {@code level}: it grows as you do, without end. */
    public static float powerFor(int level) {
        return 10f + 0.75f * (level - 1);
    }

    // ================================================================ abilities

    public static boolean canBeCarried(int level, float size) {
        return shoulder(level) * size <= CARRY_SHOULDER;
    }

    public static boolean canBeRidden(int level, float size) {
        return level >= HoundStage.GREAT.minLevel && shoulder(level) * size >= RIDE_SHOULDER;
    }

    public static boolean canSpin(int level) {
        return level >= HoundStage.YOUNG.minLevel;
    }

    public static boolean canGuard(int level) {
        return level >= HoundStage.GROWN.minLevel;
    }
}

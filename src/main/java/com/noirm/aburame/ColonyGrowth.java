package com.noirm.aburame;

import net.minecraft.util.Mth;

/**
 * Every number that makes the colony live (docs/ABURAME.md, 2.2 and 2.5). <b>All the tuning is here
 * at the top</b>; the rest of the file is small formulas that use it.
 */
public final class ColonyGrowth {
    // ================================================================ where the insects live
    /** Body capacity = BODY_BASE + BODY_PER_RESERVES × Reserves + BODY_PER_CONTROL × Control. */
    public static final int BODY_BASE = 4000;
    public static final int BODY_PER_RESERVES = 600;
    public static final int BODY_PER_CONTROL = 200;
    /** A belt jar holds this many; only {@link #JAR_SLOTS} jars count. */
    public static final int JAR_CAPACITY = 5000;
    public static final int JAR_SLOTS = 2;
    /** The gourd (one counts). */
    public static final int GOURD_CAPACITY = 25000;
    /** A hive at home; up to {@link #MAX_HIVES} per player. */
    public static final int HIVE_CAPACITY = 60000;
    public static final int MAX_HIVES = 3;
    /** The Jewel Beetle strain adds this to every hive. */
    public static final int JEWEL_HIVE_BONUS = 10000;

    // ================================================================ the pact's price (per second)
    /** Chakra per second per 10,000 insects in the body. */
    public static final float UPKEEP_PER_10K = 0.08f;
    /** Breeding per minute: this share of the body colony plus a flat number. */
    public static final float BREED_SHARE_PER_MIN = 0.012f;
    public static final float BREED_FLAT_PER_MIN = 15f;
    /** Breeding only above this share of max chakra. */
    public static final float BREED_MIN_CHAKRA = 0.20f;
    /** Chakra per new insect, paid out of regen. */
    public static final float BREED_CHAKRA_PER_INSECT = 0.1f;
    /** Each chakra your insects drain from someone else becomes this many insects (on digesting). */
    public static final float INSECTS_PER_CHAKRA_EATEN = 6f;
    /** Half of what's drained goes straight to you. */
    public static final float DRAIN_TO_OWNER = 0.5f;

    // ================================================================ starving
    /** At 0 chakra: lose this share every {@link #STARVE_STEP_SECONDS}. */
    public static final float STARVE_LOSS = 0.01f;
    public static final int STARVE_STEP_SECONDS = 10;
    /** Restless (starving): techniques cost this much more. */
    public static final float RESTLESS_COST = 1.5f;
    /** After this long starving, they start leaving the body (another loss on top). */
    public static final int LEAVE_AFTER_SECONDS = 60;
    public static final float LEAVE_LOSS = 0.02f;

    // ================================================================ generations and adaptation
    /** One generation passes every this many seconds of fed play. */
    public static final int GENERATION_SECONDS = 600;
    /** Pressure per insect killed by an adaptable harm (1,000 killed = one step). */
    public static final float PRESSURE_PER_INSECT = 0.01f;
    /** Each generation spends this much pressure of the leading kind… */
    public static final float PRESSURE_PER_STEP = 10f;
    /** …for this much resistance (twice that with the Breeding Jar's focus), up to the cap. */
    public static final float ADAPT_STEP = 0.02f;
    public static final float ADAPT_CAP = 0.40f;
    /** Resistance from a strain stacks with adaptation up to this. */
    public static final float RESIST_CAP_WITH_STRAIN = 0.50f;

    // ================================================================ vigour
    public static final float VIGOUR_START = 70f;
    public static final float VIGOUR_CALM_BONUS = 10f;
    /** Breeding ×0.25 at 0 Vigour, ×1 at {@link #VIGOUR_NORMAL}, ×1.5 at 100. */
    public static final float VIGOUR_NORMAL = 60f;
    /** Vigour per second: down while starving, up while fed (towards {@link #VIGOUR_REST}). */
    public static final float VIGOUR_STARVE_PER_SEC = -0.25f;
    public static final float VIGOUR_FED_PER_SEC = 0.02f;
    public static final float VIGOUR_REST = 70f;
    /** Near an insect eater (frogs, chickens, parrots, a pitcher plant). */
    public static final float VIGOUR_EATER_PER_SEC = -0.3f;
    public static final float VIGOUR_SALAD = 10f;

    // ================================================================ swarms
    /** A swarm's radius: 0.6 + count^0.33 / 14 blocks (2,000 → 1.5; 10,000 → 2.2; 50,000 → 3.2). */
    public static final float RADIUS_BASE = 0.6f;
    public static final float RADIUS_DIV = 14f;
    /** Drain: chakra per second per 1,000 clinging insects. */
    public static final float DRAIN_PER_1K = 0.5f;
    /** The appetite cap: a swarm eats at most count ÷ this in total before it's full. */
    public static final float APPETITE_DIV = 50f;
    /** Overfeeding (beast cloaks, Sage Mode, more than this × the owner's max chakra): it loses this share a second. */
    public static final float OVERFEED_RATIO = 10f;
    public static final float OVERFEED_LOSS = 0.05f;
    /** Vanilla mobs (no chakra): health per second per 1,000 insects. */
    public static final float MOB_DRAIN_PER_1K = 0.3f;
    /** Below this, a clinging swarm isn't noticed. */
    public static final int NOTICED_AT = 800;
    /** A dodge shakes off this share of the clinging insects. */
    public static final float SHAKE_OFF = 0.25f;
    /** Leash: beyond this a swarm loses its link and comes home (scouts: {@link #SCOUT_LEASH}). */
    public static final double LEASH = 64;
    public static final double SCOUT_LEASH = 256;
    /** Recall speed, blocks per second. */
    public static final float RECALL_SPEED = 14f;
    /** Heavy rain slows swarms. */
    public static final float RAIN_SLOW = 0.7f;
    /** A swarm out of a jar flies this much faster. */
    public static final float JAR_SPEED = 1.5f;
    /** The most swarm entities one owner can have at once (the performance cap). */
    public static final int MAX_SWARMS_PER_OWNER = 8;
    /** Mastery: +15% count, speed and drain per star. */
    public static final float MASTERY_STEP = 0.15f;

    // ================================================================ drained
    public static final int DRAINED_TICKS = 80;
    /** Exhausted lasts until the target is back to this share of its chakra. */
    public static final float EXHAUSTED_UNTIL = 0.20f;
    public static final float EXHAUSTED_DAMAGE = 0.7f;

    // ================================================================ radar
    /** Insect Radar: 16 + 8 per 5,000 insects out in the field, up to 96. */
    public static final float RADAR_BASE = 16f;
    public static final float RADAR_PER_5K = 8f;
    public static final float RADAR_MAX = 96f;

    // ================================================================ the gourd
    /** 8+ explosion damage: this chance the gourd bursts (its insects die, +4 damage to you). */
    public static final float GOURD_BURST_DAMAGE = 8f;
    public static final float GOURD_BURST_CHANCE = 0.35f;
    public static final float GOURD_BURST_HURT = 4f;

    private ColonyGrowth() {}

    /** Body capacity for these stats (Reserves 10, Control 10 → 12,000). */
    public static int bodyCapacity(float reserves, float control) {
        return Math.round(BODY_BASE + BODY_PER_RESERVES * reserves + BODY_PER_CONTROL * control);
    }

    /** Chakra per second the body colony eats. */
    public static float upkeep(int body) {
        return UPKEEP_PER_10K * body / 10000f;
    }

    /** New insects per second (before Vigour), for a body colony of {@code body}. */
    public static float breedPerSecond(int body) {
        return (BREED_SHARE_PER_MIN * body + BREED_FLAT_PER_MIN) / 60f;
    }

    /** Breeding multiplier from Vigour. */
    public static float vigourBreeding(float vigour) {
        if (vigour <= VIGOUR_NORMAL) {
            return Mth.lerp(vigour / VIGOUR_NORMAL, 0.25f, 1f);
        }
        return Mth.lerp((vigour - VIGOUR_NORMAL) / (100f - VIGOUR_NORMAL), 1f, 1.5f);
    }

    /** Drain multiplier from Vigour: ×0.7 to ×1.15. */
    public static float vigourDrain(float vigour) {
        return Mth.lerp(Mth.clamp(vigour / 100f, 0f, 1f), 0.7f, 1.15f);
    }

    /** A swarm's radius in blocks. */
    public static float radius(int count) {
        return RADIUS_BASE + (float) Math.pow(Math.max(0, count), 0.33) / RADIUS_DIV;
    }

    /** How much a swarm of {@code count} can eat before it's full. */
    public static float appetite(int count) {
        return count / APPETITE_DIV;
    }

    /** Insect Radar range for this many insects out in the field. */
    public static float radar(int field) {
        return Math.min(RADAR_MAX, RADAR_BASE + RADAR_PER_5K * field / 5000f);
    }
}

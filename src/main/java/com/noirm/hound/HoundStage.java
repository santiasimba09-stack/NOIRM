package com.noirm.hound;

import java.util.Locale;

/**
 * A ninja hound's growth stages (docs/INUZUKA.md). A hound grows when three things are all far
 * enough along: its own <b>XP</b> (fights and training at your side), the <b>bond</b> between you
 * (time together, food, petting, fights) and <b>your</b> progress (your base power: the mean of
 * speed and taijutsu, see {@code Perception.basePower}). So it grows as you do.
 *
 * <p>All the tuning for the stages is here: size (the hitbox; the model's scale comes from
 * {@code assets/noirm/hound/geometry.json}), health, bite, speed, how far it smells, what it can
 * do, and what it takes to reach each stage.
 */
public enum HoundStage {
    //      w     h     health bite  speed smell  xp    bond  power
    PUPPY(0.45f, 0.50f, 12f, 2.0f, 0.30, 10.0, 0f, 0f, 0f),
    YOUNG(0.65f, 0.85f, 26f, 4.0f, 0.32, 14.0, 120f, 20f, 11f),
    GROWN(0.90f, 1.25f, 44f, 6.5f, 0.34, 18.0, 450f, 45f, 16f),
    GREAT(1.40f, 2.10f, 75f, 10.0f, 0.35, 24.0, 1300f, 70f, 25f),
    GIANT(2.00f, 3.00f, 115f, 14.0f, 0.35, 32.0, 3200f, 92f, 40f);

    /** Hitbox width and height (blocks). */
    public final float width;
    public final float height;
    public final float maxHealth;
    /** A bite's damage before the bond bonus. */
    public final float bite;
    /** Movement speed attribute (a wolf is 0.3). */
    public final double speed;
    /** How far it smells hidden enemies (blocks). */
    public final double smell;
    /** Hound XP, bond and the owner's base power needed to reach this stage. */
    public final float xpNeeded;
    public final float bondNeeded;
    public final float ownerPowerNeeded;

    HoundStage(float width, float height, float maxHealth, float bite, double speed, double smell,
               float xpNeeded, float bondNeeded, float ownerPowerNeeded) {
        this.width = width;
        this.height = height;
        this.maxHealth = maxHealth;
        this.bite = bite;
        this.speed = speed;
        this.smell = smell;
        this.xpNeeded = xpNeeded;
        this.bondNeeded = bondNeeded;
        this.ownerPowerNeeded = ownerPowerNeeded;
    }

    /** Small enough to ride on your head or in your jacket. */
    public boolean canBeCarried() {
        return this == PUPPY;
    }

    /** Big enough to carry you. */
    public boolean canBeRidden() {
        return ordinal() >= GREAT.ordinal();
    }

    /** Big enough to spin beside you in Fang Over Fang. */
    public boolean canSpin() {
        return ordinal() >= YOUNG.ordinal();
    }

    public HoundStage next() {
        return this == GIANT ? null : values()[ordinal() + 1];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static HoundStage of(int ordinal) {
        HoundStage[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    public static HoundStage byKey(String key) {
        for (HoundStage s : values()) {
            if (s.key().equals(key)) {
                return s;
            }
        }
        return null;
    }
}

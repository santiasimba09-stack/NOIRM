package com.noirm.hound;

import java.util.Locale;

/**
 * The milestones on a hound's way up (docs/INUZUKA.md). A hound doesn't jump between stages: it
 * has a level that keeps rising ({@link HoundGrowth}), and these are the names for the stretches
 * of it, each opening something new at its level:
 * <ul>
 *   <li>{@link #PUPPY} (level 1): smell, Dynamic Marking, riding on your head while it's small;</li>
 *   <li>{@link #YOUNG} (level 6): spins beside you in Fang Over Fang; Man Beast Clone (Inuzuka);</li>
 *   <li>{@link #GROWN} (level 12): holds a post alone (guard), wears the clan scarf;</li>
 *   <li>{@link #GREAT} (level 20): big enough to ride; the Two-Headed Wolf (Inuzuka);</li>
 *   <li>{@link #GIANT} (level 30): near its full size; from here it only gets stronger.</li>
 * </ul>
 */
public enum HoundStage {
    PUPPY(1),
    YOUNG(6),
    GROWN(12),
    GREAT(20),
    GIANT(30);

    /** The level this milestone is reached at. */
    public final int minLevel;

    HoundStage(int minLevel) {
        this.minLevel = minLevel;
    }

    public HoundStage next() {
        return this == GIANT ? null : values()[ordinal() + 1];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The milestone a hound of {@code level} has reached. */
    public static HoundStage forLevel(int level) {
        HoundStage at = PUPPY;
        for (HoundStage s : values()) {
            if (level >= s.minLevel) {
                at = s;
            }
        }
        return at;
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

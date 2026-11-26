package com.noirm.aburame;

import java.util.Locale;

/**
 * What can kill insects, and how many die per point of damage (docs/ABURAME.md, 2.5). A swarm has no
 * health: every point of damage kills insects. Blades barely thin it; fire wipes it.
 * <p>The adaptable kinds build <b>pressure</b> when they kill, and the colony slowly breeds a
 * resistance to the one that kills it most ({@link ColonyGrowth#adapt}).
 */
public enum Harm {
    /** Melee, a weapon or an arrow: half of one slash goes through the cloud. */
    MELEE(30, false),
    /** Most jutsu. */
    JUTSU(60, false),
    FIRE(180, true),
    EXPLOSION(220, true),
    LIGHTNING(90, true),
    COLD(90, true),
    POISON(60, true),
    /** Wind doesn't kill much: it scatters (see {@code InsectSwarm#scatter}). */
    WIND(10, true);

    /** Insects killed per point of damage, before adaptation. */
    public final int perDamage;
    /** Builds pressure and can be adapted to. */
    public final boolean adaptable;

    Harm(int perDamage, boolean adaptable) {
        this.perDamage = perDamage;
        this.adaptable = adaptable;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Harm byKey(String key) {
        for (Harm h : values()) {
            if (h.key().equals(key)) {
                return h;
            }
        }
        return null;
    }
}

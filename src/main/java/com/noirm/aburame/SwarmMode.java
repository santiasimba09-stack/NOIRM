package com.noirm.aburame;

import java.util.Locale;

/** What a swarm is doing (docs/ABURAME.md, 2.5). */
public enum SwarmMode {
    /** Flying to a target or a spot. */
    TRAVEL,
    /** Hanging in the air (an ambush cloud); eats jutsu that touch it. */
    CLOUD,
    /** Covering a creature and eating its chakra. */
    CLING,
    /** A ball around the target: rooted, blind, drain ×3. */
    SPHERE,
    /** A 6×4 wall that stops projectiles and creatures and eats jutsu. */
    WALL,
    /** A spinning dome around its owner. */
    DOME,
    /** Watching a spot for 3 minutes and reporting. */
    SCOUT,
    /** Broadcasting false chakra over 24 blocks. */
    JAM,
    /** A rope of insects (gone in half a second). */
    TETHER,
    /** A tornado round the target. */
    TORNADO,
    /** Flying home. */
    RETURN,
    /** The Black Sea. */
    SEA,
    /** A rinkaichū poison cloud. */
    POISON,
    /** The Jar of Poison ritual, then its storm. */
    JAR;

    public static SwarmMode of(int ordinal) {
        SwarmMode[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    public static SwarmMode byKey(String key) {
        for (SwarmMode m : values()) {
            if (m.name().toLowerCase(Locale.ROOT).equals(key)) {
                return m;
            }
        }
        return CLING;
    }
}

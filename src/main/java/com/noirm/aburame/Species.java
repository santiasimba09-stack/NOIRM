package com.noirm.aburame;

import java.util.Locale;

/**
 * The insect species an Aburame can host (docs/ABURAME.md, 2.3). Each has its own count in the
 * body, and all of them share the body's capacity.
 * <ul>
 *   <li>{@link #KIKAICHU}: the default flying beetles; every technique uses them.</li>
 *   <li>{@link #KIDAICHU}: giants that burrow in and eat (Bug Bite). At most {@link #KIDAICHU_MAX}.</li>
 *   <li>{@link #SHOKAICHU}: worms that scout underground.</li>
 *   <li>{@link #RINKAICHU}: nano-poison insects, birth only (1 in 12).</li>
 *   <li>{@link #KOCHU}: the Root assassin's poison. <b>A hook only</b>: the Root path comes later
 *   (owner's decision 3), so nothing grants it yet except the command.</li>
 * </ul>
 */
public enum Species {
    KIKAICHU(0xFF2B2B30, 0xFF6E6A78, 0xFF3FA8A8),
    KIDAICHU(0xFF3A2A22, 0xFF8A6A50, 0xFFD0A040),
    SHOKAICHU(0xFF5A4A3A, 0xFF9A8670, 0xFF7A6A50),
    RINKAICHU(0xFF2A1830, 0xFF5A3A6A, 0xFFB060D0),
    KOCHU(0xFF302020, 0xFF6A4040, 0xFFD04040);

    /** The most kidaichū a body can keep (they're troublesome to house). */
    public static final int KIDAICHU_MAX = 2000;

    /** Shell, shield and eye colours (the swarm renderer's palette; kikaichū from the owner's ref 33). */
    public final int shell, shield, eyes;

    Species(int shell, int shield, int eyes) {
        this.shell = shell;
        this.shield = shield;
        this.eyes = eyes;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "aburame.noirm.species." + key();
    }

    public static Species byKey(String key) {
        for (Species s : values()) {
            if (s.key().equals(key)) {
                return s;
            }
        }
        return null;
    }

    public static Species of(int ordinal) {
        Species[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }
}

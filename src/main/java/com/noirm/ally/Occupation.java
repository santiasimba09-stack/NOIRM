package com.noirm.ally;

import java.util.Locale;

/**
 * A crew mate's job, on top of their orders (see {@link CrewBrain}).
 * <ul>
 *   <li><b>Bodyguard</b>: sticks to you and goes for anything that targets you, before it strikes.</li>
 *   <li><b>Vanguard</b>: charges hostiles near you first, faster than the rest.</li>
 *   <li><b>Scout</b>: ranges ahead, spots hostiles far away, marks them (they glow) and tells you.</li>
 *   <li><b>Medic</b>: spends chakra to heal you and the crew when hurt; hangs back in a fight.</li>
 *   <li><b>Gatherer</b>: picks up the loot lying around you and brings it to you.</li>
 * </ul>
 */
public enum Occupation {
    NONE, BODYGUARD, VANGUARD, SCOUT, MEDIC, GATHERER;

    public String key() {
        return "screen.noirm.ally.job." + name().toLowerCase(Locale.ROOT);
    }
}

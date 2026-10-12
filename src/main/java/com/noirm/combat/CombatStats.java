package com.noirm.combat;

/**
 * Implemented by NOIRM fighters (e.g. the sparring ninja) so perception and damage can read
 * their stats. Players read theirs from {@code ShinobiData}; vanilla mobs use the defaults.
 */
public interface CombatStats {
    float combatSpeed();

    float combatTaijutsu();
}

package com.noirm.combat;

/**
 * Implemented by NOIRM fighters (e.g. the sparring ninja) so perception and damage can read
 * their stats. Players read theirs from {@code ShinobiData}; vanilla mobs use the defaults.
 */
public interface CombatStats {
    float combatSpeed();

    float combatTaijutsu();

    /** The fighting style this fighter uses ({@code data/noirm/combat/styles/}), or null for the basic one. */
    default net.minecraft.resources.ResourceLocation combatStyle() {
        return null;
    }
}

package com.noirm.combat;

/**
 * Implemented by NOIRM fighters (e.g. the sparring ninja) so perception and damage can read
 * their stats. Players read theirs from {@code ShinobiData}; vanilla mobs use the defaults.
 */
public interface CombatStats {
    float combatSpeed();

    float combatTaijutsu();

    /** Toughness (see {@code Perception.durability}): by default the average of speed and taijutsu. */
    default float combatVitality() {
        return (combatSpeed() + combatTaijutsu()) / 2f;
    }

    /**
     * DUR (APX 0.1, {@code training.Durability}): the share of damage this fighter negates. By
     * default 10 (none): only ninjas override it (their strength), not beasts or clones.
     */
    default float combatDurability() {
        return com.noirm.player.ShinobiData.DEFAULT_STAT;
    }

    /** Chakra behind jutsu: by default the average of speed and taijutsu. */
    default float combatReserves() {
        return (combatSpeed() + combatTaijutsu()) / 2f;
    }

    /** The fighting style this fighter uses ({@code data/noirm/combat/styles/}), or null for the basic one. */
    default net.minecraft.resources.ResourceLocation combatStyle() {
        return null;
    }
}

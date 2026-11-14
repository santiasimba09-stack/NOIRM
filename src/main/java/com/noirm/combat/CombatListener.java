package com.noirm.combat;

/** Implemented by entities that show their combat state visually (e.g. the sparring ninja's poses). */
public interface CombatListener {
    void onCombatStateChanged(CombatState state);
}

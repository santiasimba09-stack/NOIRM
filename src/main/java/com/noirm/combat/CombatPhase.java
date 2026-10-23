package com.noirm.combat;

/** What a fighter is doing right now. */
public enum CombatPhase {
    /** Free to act. */
    IDLE,
    /** Telegraphing an attack. This is the part perception and focus stretch. */
    WINDUP,
    /** The attack can connect. */
    ACTIVE,
    /** Finishing the attack. The next combo attack may start after {@code cancel_after}. */
    RECOVERY,
    /** Rolling or stepping away. The first ticks are invulnerable. */
    DODGE,
    /** Hit-stunned or guard-broken: can't act. */
    STAGGER
}

package com.noirm.combat;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * One fighter's moment-to-moment combat state. Server-side only and not saved: it resets when
 * the entity unloads. Rules live in {@link CombatSystem}; this class just holds the data.
 */
public final class CombatState {
    public static final float DEFAULT_MAX_POSTURE = 100f;

    // Mode and current action
    boolean combatMode;
    CombatPhase phase = CombatPhase.IDLE;
    ResourceLocation moveId;
    CombatMove move;
    /** Ticks elapsed in the current phase (fractional while slowed). */
    float phaseTime;
    float phaseLength;
    /** The move that just finished, used to pick the next move of a combo route. */
    ResourceLocation comboFrom;
    int comboResetTicks;
    final Set<Integer> hitThisSwing = new HashSet<>();

    // Input buffer: a press that arrives while busy is remembered briefly
    AttackInput bufferedAttack;
    boolean bufferedDodge;
    float bufferedForward;
    float bufferedStrafe;
    int bufferTicks;

    // Defence
    boolean wantsGuard;
    boolean guarding;
    long guardStartTick;
    boolean perfectDodgeUsed;
    float posture;
    float maxPosture = DEFAULT_MAX_POSTURE;
    int postureRegenDelay;
    /** Game time of the last posture break (masters count them in a spar). */
    long lastPostureBreak = Long.MIN_VALUE;

    // Clinch (grappling): who this fighter holds, or who holds them
    net.minecraft.world.entity.LivingEntity clinchTarget;
    ResourceLocation clinchMove;
    int clinchTicks;
    net.minecraft.world.entity.LivingEntity clinchedBy;
    /** Hits taken in the air since last landing (juggles float less and hurt less each hit). */
    int juggleHits;

    // Time
    int hitstop;
    /** 1 = normal speed. Below 1 while caught in someone's focus moment. */
    float timeScale = 1f;
    int timeScaleTicks;

    // Perception focus and the just-frame parry
    /** Entity id of the defender whose perception is slowing this fighter's current attack (-1 = none). */
    int perceivedBy = -1;
    /** How much slower that attack plays for them (0.2 = 20% slower). */
    float perceivedSlowdown;
    /** After a just-frame parry: ticks left in which an attack you start becomes a counter. */
    int counterTicks;
    /** The attack in progress was started inside the counter window: its next hit is a counter. */
    boolean counterArmed;

    // Sub-stance (picked automatically, see CombatSystem.pickSubStance) and the stance it shows
    SubStance subStance = SubStance.NEUTRAL;
    ResourceLocation stanceAnimation;
    int stanceSwitch = 2;
    /** Game time this fighter last took / landed a hit (sub-stances). */
    long lastHitTaken = Long.MIN_VALUE;
    long lastHitDealt = Long.MIN_VALUE;
    /** Hits of the current string so far (0 = an opener). */
    int comboDepth;
    /** Ticks in the air (the air stance waits for a real jump, not a step). */
    int airTicks;

    // Dodge into counter, dash strike
    /** After a perfect dodge: ticks left in which your next attack becomes a dodge counter. */
    int dodgeCounterTicks;
    /** Whose attack was dodged (the counter lands behind or beside them). */
    net.minecraft.world.entity.LivingEntity dodgeCounterFrom;
    /** The last dodge's sideways direction (-1..1, + = left): a side dodge counters from the side. */
    float dodgeStrafe;
    /** The attack in progress is a dodge counter / a dash strike. */
    boolean dodgeCountering;
    boolean dashStrike;

    // Sync
    private boolean dirty = true;
    int lastSyncedPosture = -1;

    public boolean isCombatMode() {
        return combatMode;
    }

    public CombatPhase phase() {
        return phase;
    }

    public ResourceLocation moveId() {
        return moveId;
    }

    public float phaseLength() {
        return phaseLength;
    }

    /** The attack in progress (or just finished), or null. */
    public CombatMove currentMove() {
        return move;
    }

    /** Who this fighter has in a clinch, or null. */
    public net.minecraft.world.entity.LivingEntity clinchTarget() {
        return clinchTarget;
    }

    /** Who holds this fighter in a clinch, or null. */
    public net.minecraft.world.entity.LivingEntity clinchedBy() {
        return clinchedBy;
    }

    public boolean isGuarding() {
        return guarding;
    }

    public float posture() {
        return posture;
    }

    public float maxPosture() {
        return maxPosture;
    }

    public float timeScale() {
        return timeScale;
    }

    /** Entity id of the defender who sees this fighter's attack slowed (perception focus), or -1. */
    public int perceivedBy() {
        return perceivedBy;
    }

    /** Ticks left in this fighter's counter window (after a just-frame parry). */
    public int counterTicks() {
        return counterTicks;
    }

    /** True while the next hit of this fighter's attack is a counter (bonus damage, can't be guarded). */
    public boolean hasCounter() {
        return counterTicks > 0 || counterArmed;
    }

    /** The sub-stance the fighter is in (it changes the idle pose and which variant of a move comes out). */
    public SubStance subStance() {
        return subStance;
    }

    /** The stance animation the sub-stance shows (null = the client picks the style's own). */
    public ResourceLocation stanceAnimation() {
        return stanceAnimation;
    }

    /** Ticks the switch into the current sub-stance snaps over. */
    public int stanceSwitch() {
        return stanceSwitch;
    }

    /** Hits so far in the current string (0 = the opener). */
    public int comboDepth() {
        return comboDepth;
    }

    /** Ticks left in the window a perfect dodge opened (the next attack is a dodge counter). */
    public int dodgeCounterTicks() {
        return dodgeCounterTicks;
    }

    /** True while the attack in progress is a dodge counter. */
    public boolean isDodgeCounter() {
        return dodgeCountering;
    }

    /** True while the attack in progress is a dash strike (sprinting into an attack). */
    public boolean isDashStrike() {
        return dashStrike;
    }

    public void setMaxPosture(float value) {
        maxPosture = Math.max(1f, value);
    }

    void markDirty() {
        dirty = true;
    }

    /** True if the owner's HUD needs an update; clears the flag. Posture syncs per whole point. */
    boolean consumeDirty() {
        int roundedPosture = Math.round(posture);
        boolean wasDirty = dirty || roundedPosture != lastSyncedPosture;
        dirty = false;
        lastSyncedPosture = roundedPosture;
        return wasDirty;
    }
}

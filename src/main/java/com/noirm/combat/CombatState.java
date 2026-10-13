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

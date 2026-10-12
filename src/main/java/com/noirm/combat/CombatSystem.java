package com.noirm.combat;

import com.noirm.awakening.AwakeningSystem;
import com.noirm.biju.BijuSystem;
import com.noirm.dojutsu.Byakugan;
import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.HyugaTechniques;
import com.noirm.jutsu.Substitution;
import com.noirm.player.ShinobiCapability;
import com.noirm.training.TrainingSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;

/**
 * The combat rules. Everything here runs on the server; clients only send button presses and
 * show the results. All tuning numbers are constants at the top (moves themselves are JSON).
 */
public final class CombatSystem {
    /** How long an early button press is remembered (paused during hit-stop). */
    public static final int BUFFER_TICKS = 10;
    /** After a move ends, how long the next press still continues the combo route. */
    public static final int COMBO_RESET_TICKS = 12;

    public static final int DODGE_TICKS = 10;
    /** The first ticks of a dodge can't be hit. */
    public static final int DODGE_IFRAMES = 6;
    /** Being "hit" during the first ticks of a dodge counts as a perfect dodge. */
    public static final int PERFECT_DODGE_TICKS = 3;
    public static final float DODGE_STAMINA_COST = 20f;
    public static final double DODGE_SPEED = 1.0;

    /** Raising guard this many ticks before a hit lands parries it. */
    public static final int PARRY_WINDOW = 5;
    public static final float GUARD_DAMAGE_MULTIPLIER = 0.25f;
    public static final float GUARD_STAMINA_COST = 4f;
    public static final int POSTURE_BREAK_STUN = 30;
    public static final int PARRY_STUN = 16;
    public static final float PARRY_POSTURE_DAMAGE = 25f;
    /** Posture damage from vanilla attacks (zombies, arrows…), per point of damage. */
    public static final float VANILLA_POSTURE_PER_DAMAGE = 3f;
    /** Unguarded hits still build some posture. */
    public static final float UNGUARDED_POSTURE_RATIO = 0.5f;
    public static final int POSTURE_REGEN_DELAY = 40;

    public static final double FOCUS_RADIUS = 10;
    public static final int FOCUS_TICKS = 20;
    /** Enemies caught in a focus moment act at this fraction of normal speed. */
    public static final float FOCUS_TIME_SCALE = 0.3f;

    private static final UUID GUARD_SLOW_ID = UUID.fromString("6f4a3c1e-2b7d-4e0a-9c55-1d2e3f4a5b6c");
    private static final AttributeModifier GUARD_SLOW = new AttributeModifier(GUARD_SLOW_ID, "noirm_guard", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);

    /** True while NOIRM itself is applying a hit, so the damage events don't handle it twice. */
    private static boolean applyingHit;

    /** What happens to an incoming attack. */
    public enum Outcome { HIT, GUARDED, PARRIED, DODGED }

    private CombatSystem() {}

    // ---------------------------------------------------------------- actions

    public static void setCombatMode(LivingEntity entity, boolean enabled) {
        CombatState state = CombatStates.get(entity);
        if (state.combatMode == enabled) {
            return;
        }
        state.combatMode = enabled;
        if (!enabled) {
            state.wantsGuard = false;
            stopGuard(entity, state);
            clearBuffer(state);
        }
        state.markDirty();
    }

    /** Starts an attack now if possible, otherwise remembers it briefly. Returns true if it started. */
    public static boolean requestAttack(LivingEntity entity, AttackInput input) {
        CombatState state = CombatStates.get(entity);
        if (canAttack(state)) {
            return startAttack(entity, state, input);
        }
        if (state.phase != CombatPhase.STAGGER) {
            clearBuffer(state);
            state.bufferedAttack = input;
            state.bufferTicks = BUFFER_TICKS;
        }
        return false;
    }

    /**
     * Dodges in a direction relative to where the entity faces ({@code forward}/{@code strafe}
     * in -1..1, strafe positive = left). No direction = step back.
     */
    public static boolean requestDodge(LivingEntity entity, float forward, float strafe) {
        CombatState state = CombatStates.get(entity);
        if (canDodge(state)) {
            return doDodge(entity, state, forward, strafe);
        }
        if (state.phase == CombatPhase.ACTIVE || state.phase == CombatPhase.DODGE) {
            clearBuffer(state);
            state.bufferedDodge = true;
            state.bufferedForward = forward;
            state.bufferedStrafe = strafe;
            state.bufferTicks = BUFFER_TICKS;
        }
        return false;
    }

    /** Raise or lower guard. A raised request waits until the fighter is free. */
    public static void setGuard(LivingEntity entity, boolean raised) {
        CombatState state = CombatStates.get(entity);
        state.wantsGuard = raised && BijuSystem.canGuard(entity);
        if (!state.wantsGuard) {
            stopGuard(entity, state);
        } else if (canGuard(state)) {
            startGuard(entity, state);
        }
    }

    /** Free to start a jutsu: not mid-attack, dodging, staggered or guarding. */
    public static boolean canAct(LivingEntity entity) {
        CombatState state = CombatStates.getIfPresent(entity);
        return state == null || (!state.guarding
                && (state.phase == CombatPhase.IDLE || state.phase == CombatPhase.RECOVERY));
    }

    /** Stuns a fighter (interrupting what they were doing) for {@code ticks}. Used by jutsu. */
    public static void stun(LivingEntity entity, int ticks) {
        CombatState state = CombatStates.get(entity);
        stopGuard(entity, state);
        stagger(entity, state, ticks);
    }

    /** Posture damage from a jutsu (may break the guard). */
    public static void addPostureDamage(LivingEntity entity, float amount) {
        addPosture(entity, CombatStates.get(entity), amount);
    }

    /** Is the fighter staggered right now? */
    public static boolean isStaggered(LivingEntity entity) {
        CombatState state = CombatStates.getIfPresent(entity);
        return state != null && state.phase == CombatPhase.STAGGER;
    }

    /** Could this fighter start an attack this tick (rather than buffering it)? */
    public static boolean canAttackNow(LivingEntity entity) {
        return canAttack(CombatStates.get(entity));
    }

    // ---------------------------------------------------------------- tick

    /** Advances one fighter by one server tick. */
    public static void tick(LivingEntity entity) {
        CombatState state = CombatStates.getIfPresent(entity);
        if (state == null) {
            return;
        }
        if (state.timeScaleTicks > 0 && --state.timeScaleTicks == 0) {
            state.timeScale = 1f;
        }
        if (state.postureRegenDelay > 0) {
            state.postureRegenDelay--;
        } else if (state.posture > 0) {
            state.posture = Math.max(0f, state.posture - (state.guarding ? 0.6f : 0.4f));
        }
        if (state.wantsGuard && !state.guarding && canGuard(state)) {
            startGuard(entity, state);
        }
        if (state.hitstop > 0) {
            state.hitstop--;
            return;
        }
        if (state.bufferTicks > 0 && --state.bufferTicks == 0) {
            clearBuffer(state);
        }
        if (state.phase == CombatPhase.IDLE) {
            if (state.comboResetTicks > 0 && --state.comboResetTicks == 0) {
                state.comboFrom = null;
            }
        } else {
            state.phaseTime += state.timeScale;
            if (state.phase == CombatPhase.ACTIVE) {
                sweep(entity, state);
            }
            if (state.phaseTime >= state.phaseLength) {
                advance(entity, state);
            }
        }
        consumeBuffer(entity, state);
    }

    // ---------------------------------------------------------------- incoming damage (all sources)

    /** Called before damage is applied. Returns true if the attack is avoided entirely. */
    public static boolean onIncomingAttack(LivingEntity target, DamageSource source) {
        if (applyingHit) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (Substitution.trySubstitute(target, attacker) || HyugaTechniques.deflect(target, attacker)) {
            return true;
        }
        CombatState state = CombatStates.getIfPresent(target);
        if (state == null) {
            return false;
        }
        return switch (classify(target, state, attacker)) {
            case DODGED -> {
                onDodged(target, state);
                yield true;
            }
            case PARRIED -> {
                onParried(target, state, attacker);
                yield true;
            }
            default -> false;
        };
    }

    /** Called when damage is about to be applied. Returns the (possibly reduced) amount. */
    public static float onIncomingDamage(LivingEntity target, DamageSource source, float amount) {
        if (applyingHit) {
            return amount;
        }
        CombatState state = CombatStates.getIfPresent(target);
        if (state == null) {
            return amount;
        }
        Entity attacker = source.getEntity();
        if (classify(target, state, attacker) == Outcome.GUARDED) {
            absorbGuardedHit(target, state, amount * VANILLA_POSTURE_PER_DAMAGE, amount);
            return amount * GUARD_DAMAGE_MULTIPLIER;
        }
        if (attacker != null) {
            addPosture(target, state, amount * VANILLA_POSTURE_PER_DAMAGE * UNGUARDED_POSTURE_RATIO);
        }
        return amount;
    }

    /** How an attack from {@code attacker} would resolve against {@code target} right now. */
    public static Outcome classify(LivingEntity target, CombatState state, Entity attacker) {
        if (state.phase == CombatPhase.DODGE && state.phaseTime < DODGE_IFRAMES) {
            return Outcome.DODGED;
        }
        if (state.guarding && attacker != null && (isFacing(target, attacker) || Byakugan.isActive(target))) {
            long sinceRaised = target.level().getGameTime() - state.guardStartTick;
            return sinceRaised <= PARRY_WINDOW ? Outcome.PARRIED : Outcome.GUARDED;
        }
        return Outcome.HIT;
    }

    /**
     * Vanilla adds its own knockback to every hit. For NOIRM's attacks only the move's own
     * {@code knockback} should apply, so light hits keep the target in range and combos connect.
     */
    public static boolean shouldCancelVanillaKnockback() {
        return applyingHit;
    }

    /** True while a NOIRM move's hit is being applied (rather than a vanilla attack). */
    public static boolean isApplyingHit() {
        return applyingHit;
    }

    // ---------------------------------------------------------------- focus

    /** A perfect dodge or parry: everyone near {@code center} slows down for a moment. */
    public static void focus(LivingEntity center) {
        AABB area = center.getBoundingBox().inflate(FOCUS_RADIUS);
        for (LivingEntity other : center.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != center && e.isAlive())) {
            CombatState state = CombatStates.get(other);
            state.timeScale = FOCUS_TIME_SCALE;
            state.timeScaleTicks = FOCUS_TICKS;
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FOCUS_TICKS, 3, false, false));
        }
        for (Projectile projectile : center.level().getEntitiesOfClass(Projectile.class, area)) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(FOCUS_TIME_SCALE));
        }
        CombatEffects.focus(center);
    }

    // ---------------------------------------------------------------- routing (public for tests)

    /** Which move {@code input} starts, continuing {@code from}'s combo route if it has one. */
    public static ResourceLocation resolveMove(LivingEntity entity, ResourceLocation from, AttackInput input) {
        if (from != null) {
            CombatMove previous = CombatData.move(from);
            if (previous != null && previous.next().containsKey(input)) {
                return previous.next().get(input);
            }
        }
        CombatStyle style = CombatData.style(styleFor(entity));
        return style == null ? null : style.opener(input);
    }

    /** The fighting style an entity uses. Masters will change this later. */
    static ResourceLocation styleFor(LivingEntity entity) {
        if (entity instanceof CombatStats stats && stats.combatStyle() != null) {
            return stats.combatStyle();
        }
        return CombatData.DEFAULT_STYLE;
    }

    // ---------------------------------------------------------------- internals

    static boolean canAttack(CombatState state) {
        if (state.guarding) {
            return false;
        }
        return state.phase == CombatPhase.IDLE
                || (state.phase == CombatPhase.RECOVERY && state.phaseTime >= state.move.timing().cancelAfter());
    }

    static boolean canDodge(CombatState state) {
        return state.phase == CombatPhase.IDLE || state.phase == CombatPhase.WINDUP || state.phase == CombatPhase.RECOVERY;
    }

    static boolean canGuard(CombatState state) {
        return state.phase == CombatPhase.IDLE || state.phase == CombatPhase.RECOVERY;
    }

    private static boolean startAttack(LivingEntity entity, CombatState state, AttackInput input) {
        ResourceLocation from = state.phase == CombatPhase.RECOVERY ? state.moveId : state.comboFrom;
        ResourceLocation id = resolveMove(entity, from, input);
        CombatMove move = id == null ? null : CombatData.move(id);
        if (move == null) {
            return false;
        }
        LivingEntity target = findTarget(entity, move.hit().range() + 1.5f);
        float windup = move.timing().windup() * (target == null ? 1f : Perception.slowFactor(target, entity));

        if (target != null) {
            Sharingan.onAttackWindup(entity, target, Math.round(windup));
            Byakugan.onAttackWindup(entity, target, Math.round(windup));
        }
        clearBuffer(state);
        state.moveId = id;
        state.move = move;
        state.comboFrom = null;
        state.hitThisSwing.clear();
        if (windup > 0) {
            enterPhase(entity, state, CombatPhase.WINDUP, windup);
        } else {
            enterPhase(entity, state, CombatPhase.ACTIVE, move.timing().active());
            onActiveStart(entity, state);
        }
        CombatEffects.attackStarted(entity, move, Math.round(windup) + move.timing().active() + move.timing().recovery());
        return true;
    }

    private static boolean doDodge(LivingEntity entity, CombatState state, float forward, float strafe) {
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            boolean paid = ShinobiCapability.get(player).map(d -> d.tryConsumeStamina(DODGE_STAMINA_COST)).orElse(true);
            if (!paid) {
                CombatEffects.outOfStamina(entity);
                return false;
            }
        }
        stopGuard(entity, state);
        clearBuffer(state);
        if (forward == 0 && strafe == 0) {
            forward = -1;
        }
        float length = Mth.sqrt(forward * forward + strafe * strafe);
        forward /= length;
        strafe /= length;
        float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
        double x = strafe * Mth.cos(yaw) - forward * Mth.sin(yaw);
        double z = forward * Mth.cos(yaw) + strafe * Mth.sin(yaw);
        double y = entity.onGround() ? 0.12 : entity.getDeltaMovement().y;
        entity.setDeltaMovement(x * DODGE_SPEED, y, z * DODGE_SPEED);
        entity.hurtMarked = true;

        state.moveId = null;
        state.move = null;
        state.comboFrom = null;
        state.perfectDodgeUsed = false;
        enterPhase(entity, state, CombatPhase.DODGE, DODGE_TICKS);
        CombatEffects.dodge(entity, forward >= 0, DODGE_TICKS);
        TrainingSystem.onDodge(entity);
        return true;
    }

    private static void startGuard(LivingEntity entity, CombatState state) {
        if (state.phase == CombatPhase.RECOVERY) {
            enterPhase(entity, state, CombatPhase.IDLE, 0);
        }
        state.guarding = true;
        state.guardStartTick = entity.level().getGameTime();
        state.comboFrom = null;
        clearBuffer(state);
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(GUARD_SLOW_ID) == null) {
            speed.addTransientModifier(GUARD_SLOW);
        }
        state.markDirty();
        notifyListener(entity, state);
    }

    private static void stopGuard(LivingEntity entity, CombatState state) {
        if (!state.guarding) {
            return;
        }
        state.guarding = false;
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(GUARD_SLOW_ID);
        }
        state.markDirty();
        notifyListener(entity, state);
    }

    private static void advance(LivingEntity entity, CombatState state) {
        switch (state.phase) {
            case WINDUP -> {
                enterPhase(entity, state, CombatPhase.ACTIVE, state.move.timing().active());
                onActiveStart(entity, state);
            }
            case ACTIVE -> enterPhase(entity, state, CombatPhase.RECOVERY, state.move.timing().recovery());
            case RECOVERY -> {
                state.comboFrom = state.moveId;
                state.comboResetTicks = COMBO_RESET_TICKS;
                enterPhase(entity, state, CombatPhase.IDLE, 0);
            }
            case DODGE, STAGGER -> enterPhase(entity, state, CombatPhase.IDLE, 0);
            case IDLE -> { }
        }
    }

    private static void enterPhase(LivingEntity entity, CombatState state, CombatPhase phase, float length) {
        state.phase = phase;
        state.phaseTime = 0;
        state.phaseLength = length;
        notifyListener(entity, state);
    }

    private static void consumeBuffer(LivingEntity entity, CombatState state) {
        if (state.bufferTicks <= 0) {
            return;
        }
        if (state.bufferedDodge && canDodge(state)) {
            doDodge(entity, state, state.bufferedForward, state.bufferedStrafe);
        } else if (state.bufferedAttack != null && canAttack(state)) {
            startAttack(entity, state, state.bufferedAttack);
        }
    }

    private static void clearBuffer(CombatState state) {
        state.bufferedAttack = null;
        state.bufferedDodge = false;
        state.bufferTicks = 0;
    }

    private static void onActiveStart(LivingEntity entity, CombatState state) {
        float lunge = state.move.hit().lunge();
        if (lunge > 0) {
            Vec3 facing = Vec3.directionFromRotation(0, entity.getYHeadRot());
            entity.setDeltaMovement(entity.getDeltaMovement().add(facing.x * lunge, 0, facing.z * lunge));
            entity.hurtMarked = true;
        }
        CombatEffects.swing(entity, state.move);
    }

    private static void sweep(LivingEntity attacker, CombatState state) {
        CombatMove.Hit hit = state.move.hit();
        AABB area = attacker.getBoundingBox().inflate(hit.range() + 1);
        for (LivingEntity target : attacker.level().getEntitiesOfClass(LivingEntity.class, area,
                t -> t != attacker && t.isAlive() && !t.isSpectator() && !attacker.isAlliedTo(t)
                        && !state.hitThisSwing.contains(t.getId()) && inSwing(attacker, t, hit.range(), hit.arc()))) {
            state.hitThisSwing.add(target.getId());
            applyHit(attacker, state, target, state.move);
        }
    }

    private static void applyHit(LivingEntity attacker, CombatState attackerState, LivingEntity target, CombatMove move) {
        if (Substitution.trySubstitute(target, attacker) || HyugaTechniques.deflect(target, attacker)) {
            return;
        }
        CombatState targetState = CombatStates.get(target);
        Outcome outcome = classify(target, targetState, attacker);
        if (outcome == Outcome.DODGED) {
            onDodged(target, targetState);
            return;
        }
        if (outcome == Outcome.PARRIED) {
            onParried(target, targetState, attacker);
            return;
        }
        CombatMove.Hit hit = move.hit();
        boolean guarded = outcome == Outcome.GUARDED;
        // Gentle Fist (Byakugan open): more posture damage, and a guard keeps out less.
        boolean gentleFist = Byakugan.isActive(attacker);
        float posture = hit.posture() * (gentleFist ? Byakugan.GENTLE_FIST_POSTURE : 1f);
        float damage = hit.damage() * Perception.damageMultiplier(attacker) + weaponBonus(attacker);
        if (guarded) {
            absorbGuardedHit(target, targetState, posture, damage);
            damage *= gentleFist ? Byakugan.GENTLE_FIST_GUARD : GUARD_DAMAGE_MULTIPLIER;
        } else {
            addPosture(target, targetState, posture * UNGUARDED_POSTURE_RATIO);
            stagger(target, targetState, hit.hitstun());
        }

        boolean hurt;
        applyingHit = true;
        try {
            target.invulnerableTime = 0;
            hurt = target.hurt(damageSource(attacker), damage);
        } finally {
            applyingHit = false;
        }
        if (!hurt) {
            return;
        }

        float yaw = attacker.getYHeadRot() * Mth.DEG_TO_RAD;
        float knockback = hit.knockback() * (guarded ? 0.4f : 1f);
        if (knockback > 0) {
            target.knockback(knockback, Mth.sin(yaw), -Mth.cos(yaw));
        }
        if (!guarded && hit.lift() > 0) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x, Math.max(motion.y, hit.lift()), motion.z);
        }
        target.hurtMarked = true;

        TrainingSystem.onHitLanded(attacker, target, hit.isHeavy(), guarded);
        if (gentleFist) {
            Byakugan.gentleFist(attacker, target, hit.isHeavy());
        }
        int hitstop = guarded ? hit.hitstop() / 2 : hit.hitstop();
        attackerState.hitstop = Math.max(attackerState.hitstop, hitstop);
        targetState.hitstop = Math.max(targetState.hitstop, hitstop);
        CombatEffects.hitLanded(attacker, target, move, guarded, targetState);
    }

    private static void onDodged(LivingEntity target, CombatState state) {
        if (!state.perfectDodgeUsed && state.phaseTime < PERFECT_DODGE_TICKS) {
            state.perfectDodgeUsed = true;
            CombatEffects.perfectDodge(target);
            TrainingSystem.onPerfectDodge(target);
            AwakeningSystem.onClutch(target);
            focus(target);
        }
    }

    private static void onParried(LivingEntity defender, CombatState defenderState, Entity attacker) {
        CombatEffects.parry(defender);
        TrainingSystem.onParry(defender, attacker);
        AwakeningSystem.onClutch(defender);
        if (attacker instanceof LivingEntity livingAttacker) {
            CombatState attackerState = CombatStates.get(livingAttacker);
            addPosture(livingAttacker, attackerState, PARRY_POSTURE_DAMAGE);
            if (attackerState.phase != CombatPhase.STAGGER) {
                stagger(livingAttacker, attackerState, PARRY_STUN);
            }
        }
        focus(defender);
    }

    /** Applies posture (and stamina, for players) from a blocked hit. May break the guard. */
    private static void absorbGuardedHit(LivingEntity target, CombatState state, float posture, float damage) {
        CombatEffects.guarded(target);
        if (target instanceof Player player && !player.getAbilities().instabuild) {
            boolean paid = ShinobiCapability.get(player)
                    .map(d -> d.tryConsumeStamina(GUARD_STAMINA_COST + damage * 0.5f)).orElse(true);
            if (!paid) {
                postureBreak(target, state);
                return;
            }
        }
        addPosture(target, state, posture);
    }

    private static void addPosture(LivingEntity entity, CombatState state, float amount) {
        if (amount <= 0) {
            return;
        }
        state.posture = Math.min(state.maxPosture, state.posture + amount);
        state.postureRegenDelay = POSTURE_REGEN_DELAY;
        if (state.posture >= state.maxPosture) {
            postureBreak(entity, state);
        }
    }

    /** Posture full (or out of stamina while blocking): guard drops and the fighter is stunned. */
    private static void postureBreak(LivingEntity entity, CombatState state) {
        state.wantsGuard = false;
        stopGuard(entity, state);
        state.posture = 0;
        clearBuffer(state);
        enterPhase(entity, state, CombatPhase.STAGGER, POSTURE_BREAK_STUN);
        state.markDirty();
        CombatEffects.postureBreak(entity);
    }

    private static void stagger(LivingEntity entity, CombatState state, int ticks) {
        if (ticks <= 0) {
            return;
        }
        if (state.phase == CombatPhase.STAGGER && state.phaseLength - state.phaseTime >= ticks) {
            return;
        }
        clearBuffer(state);
        state.comboFrom = null;
        enterPhase(entity, state, CombatPhase.STAGGER, ticks);
    }

    private static void notifyListener(LivingEntity entity, CombatState state) {
        if (entity instanceof CombatListener listener) {
            listener.onCombatStateChanged(state);
        }
    }

    /** Is {@code target} within {@code range} blocks and inside the attacker's swing arc? */
    static boolean inSwing(LivingEntity attacker, LivingEntity target, double range, float arcDegrees) {
        Vec3 offset = target.position().add(0, target.getBbHeight() / 2, 0)
                .subtract(attacker.position().add(0, attacker.getBbHeight() / 2, 0));
        double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        if (horizontal - target.getBbWidth() / 2 > range || Math.abs(offset.y) > 2.5) {
            return false;
        }
        if (horizontal < 0.5) {
            return true;
        }
        Vec3 facing = Vec3.directionFromRotation(0, attacker.getYHeadRot());
        double cos = (facing.x * offset.x + facing.z * offset.z) / horizontal;
        return cos >= Math.cos(Math.toRadians(arcDegrees / 2));
    }

    /** Is {@code target} facing {@code source} closely enough to block it (within 75°)? */
    private static boolean isFacing(LivingEntity target, Entity source) {
        Vec3 offset = source.position().subtract(target.position());
        double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        if (horizontal < 0.01) {
            return true;
        }
        Vec3 facing = Vec3.directionFromRotation(0, target.getYHeadRot());
        return (facing.x * offset.x + facing.z * offset.z) / horizontal >= Math.cos(Math.toRadians(75));
    }

    /** Who an attack is aimed at, for perception: a mob's target, or whoever is in front. */
    private static LivingEntity findTarget(LivingEntity attacker, double range) {
        if (attacker instanceof Mob mob && mob.getTarget() != null && mob.distanceTo(mob.getTarget()) <= range + 1) {
            return mob.getTarget();
        }
        AABB area = attacker.getBoundingBox().inflate(range);
        return attacker.level().getEntitiesOfClass(LivingEntity.class, area,
                        t -> t != attacker && t.isAlive() && inSwing(attacker, t, range, 120))
                .stream().min(Comparator.comparingDouble(attacker::distanceToSqr)).orElse(null);
    }

    private static DamageSource damageSource(LivingEntity attacker) {
        return attacker instanceof Player player
                ? attacker.damageSources().playerAttack(player)
                : attacker.damageSources().mobAttack(attacker);
    }

    /** Holding a weapon in combat mode adds half its bonus damage on top of taijutsu. */
    private static float weaponBonus(LivingEntity attacker) {
        if (!(attacker instanceof Player player)) {
            return 0;
        }
        return Math.max(0f, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1f) * 0.5f;
    }
}

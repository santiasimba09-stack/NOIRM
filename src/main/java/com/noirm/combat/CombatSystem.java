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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
    public static final int COMBO_RESET_TICKS = 10;

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

    /** How long a clinch lasts at most. */
    public static final int CLINCH_TICKS = 50;
    /** Breaking out of a clinch costs this much more stamina than a dodge. */
    public static final float CLINCH_ESCAPE_COST = 1.5f;
    /** A grab whiffs on anyone this wide (tailed beasts). */
    public static final float MAX_GRAB_WIDTH = 1.5f;
    /** Getting grabbed mid-swing: the swing wins and the grabber reels. */
    public static final int GRAB_BROKEN_STUN = 12;
    public static final int KNOCKDOWN_TICKS = 32;
    /** Hits that push at least this hard slam the target into a wall behind them. */
    public static final float WALL_SPLAT_KNOCKBACK = 0.8f;
    public static final float WALL_SPLAT_DAMAGE = 0.4f;
    public static final int WALL_SPLAT_STUN = 24;
    /** Airborne hits keep the target floating, up to this many; each one hurts 10% less. */
    public static final int MAX_JUGGLE = 5;
    public static final double JUGGLE_FLOAT = 0.28;

    public static final double FOCUS_RADIUS = 10;
    public static final int FOCUS_TICKS = 20;
    /** Enemies caught in a focus moment act at this fraction of normal speed. */
    public static final float FOCUS_TIME_SCALE = 0.3f;

    /**
     * Perception focus (blueprint §3): an incoming attack your perception slows by at least this
     * much (0.15 = it plays 15% slower) gives you the focus feel (cool edges, muffled world). The
     * client's effect is strongest from {@link #PERCEPTION_FOCUS_FULL} (40%) up.
     */
    public static final float PERCEPTION_FOCUS_MIN = 0.15f;
    public static final float PERCEPTION_FOCUS_FULL = 0.40f;
    /** Only heavy attacks (hit-stop 4+) trigger it, like the blueprint's "incoming heavy threats". */
    public static final boolean PERCEPTION_FOCUS_HEAVY_ONLY = true;
    /**
     * Just-frame parry: a parry of an attack in perception focus negates it, breaks the attacker's
     * posture and opens a counter window: an attack you start within it hits ×{@link #COUNTER_DAMAGE}
     * and can't be guarded or parried.
     */
    public static final int COUNTER_TICKS = 10;
    public static final float COUNTER_DAMAGE = 1.5f;

    // ---- Combat animation rebuild (2026-11): sub-stances, dodge counters, dash strikes, guard breaks, enders

    /** Sub-stances: you stay aggressive this long after landing a hit (and while a string goes on). */
    public static final int AGGRESSIVE_TICKS = 30;
    /** …and defensive this long after taking a hit (and while guarding). */
    public static final int DEFENSIVE_TICKS = 24;
    /** The air stance starts after this many ticks off the ground (not on every step or hop). */
    public static final int AIR_STANCE_TICKS = 4;

    /**
     * Dodge into counter: a perfect dodge opens a window of this many ticks; the next attack you
     * start in it becomes your style's counter, thrown from {@link #DODGE_COUNTER_DISTANCE} blocks
     * behind the enemy (or beside them, after a side dodge), with a {@link #DODGE_COUNTER_WINDUP}-tick
     * wind-up. It hits like a parry counter: ×{@link #COUNTER_DAMAGE}, can't be guarded.
     */
    public static final int DODGE_COUNTER_WINDOW = 14;
    public static final float DODGE_COUNTER_DISTANCE = 1.2f;
    public static final int DODGE_COUNTER_WINDUP = 1;
    /** A dodge this far sideways (strafe) counters from the side instead of from behind. */
    public static final float DODGE_COUNTER_SIDE = 0.35f;
    /** The enemy must be this close for the counter to reach them. */
    public static final double DODGE_COUNTER_RANGE = 6.0;

    /**
     * Dash strike: sprinting into an attack (the forward inputs). The wind-up is this many ticks
     * shorter (never under 1), the fighter carries this much extra lunge, and the hit does
     * ×{@link #DASH_STRIKE_DAMAGE} damage and ×{@link #DASH_STRIKE_POSTURE} posture.
     */
    public static final int DASH_STRIKE_WINDUP_CUT = 1;
    public static final float DASH_STRIKE_LUNGE = 0.45f;
    public static final float DASH_STRIKE_DAMAGE = 1.2f;
    public static final float DASH_STRIKE_POSTURE = 1.3f;
    /** A player counts as dashing from this horizontal speed (blocks/tick) or while sprinting. */
    public static final double DASH_STRIKE_SPEED = 0.18;

    /** Guard break ({@code "guard_break": true}): a guard that blocks it shatters; the hit then does this share of its damage. */
    public static final float GUARD_BREAK_DAMAGE = 0.6f;

    /** Combo enders: a launch sends the target up at least this fast… */
    public static final float ENDER_LAUNCH_LIFT = 0.8f;
    /** …a splat blasts them away at least this hard (into a wall if one is there)… */
    public static final float ENDER_SPLAT_KNOCKBACK = 1.4f;
    /** …and every ender shakes the camera this much more. */
    public static final float ENDER_SHAKE = 0.3f;

    /** Hit-stop: short and sharp. No hit freezes the fighters longer than this; a wall splat adds this much. */
    public static final int MAX_HITSTOP = 5;
    public static final int WALL_SPLAT_HITSTOP = 2;

    /**
     * Weapons. Each hit adds this share of the held weapon's attack damage (what it adds over bare
     * fists, so Strength potions count too), scaled by how heavy the move is: its damage over
     * {@link #WEAPON_REFERENCE_DAMAGE}, kept between {@link #WEAPON_WEIGHT_MIN} and
     * {@link #WEAPON_WEIGHT_MAX}. Taijutsu doesn't multiply the weapon part, so fists stay the
     * thing to train; enchantments (Sharpness, Smite, Bane) add their full bonus like vanilla.
     */
    public static final float WEAPON_DAMAGE_SHARE = 0.6f;
    public static final float WEAPON_REFERENCE_DAMAGE = 4f;
    public static final float WEAPON_WEIGHT_MIN = 0.6f;
    public static final float WEAPON_WEIGHT_MAX = 1.5f;
    /** Knockback enchantment: extra push per level. */
    public static final float KNOCKBACK_PER_LEVEL = 0.4f;
    /** Fire Aspect: seconds on fire per level (vanilla's 4). */
    public static final int FIRE_ASPECT_SECONDS = 4;
    /** Sweeping Edge: the swing's arc widens by this many degrees per level. */
    public static final float SWEEP_ARC_PER_LEVEL = 20f;
    /** Guarding with a shield in either hand: this much of a blocked hit gets through, and posture builds slower. */
    public static final float SHIELD_DAMAGE_MULTIPLIER = 0f;
    public static final float SHIELD_POSTURE_MULTIPLIER = 0.6f;

    private static final UUID GUARD_SLOW_ID = UUID.fromString("6f4a3c1e-2b7d-4e0a-9c55-1d2e3f4a5b6c");
    private static final AttributeModifier GUARD_SLOW = new AttributeModifier(GUARD_SLOW_ID, "noirm_guard", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);

    /** True while NOIRM itself is applying a hit, so the damage events don't handle it twice. */
    private static boolean applyingHit;
    /** True while that hit is a heavy one (hit-stop 4+ or a knockdown): gore uses it to sever limbs. */
    private static boolean applyingHeavy;

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
        if (SealLock.blocksMelee(entity)) {
            return false; // hands busy forming seals: refused, not buffered
        }
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
        if (state.clinchedBy != null) {
            return escapeClinch(entity, state);
        }
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

    /** Ends a stun early (a genjutsu broken by Kai). Does nothing if they aren't staggered. */
    public static void clearStun(LivingEntity entity) {
        CombatState state = CombatStates.getIfPresent(entity);
        if (state != null && state.phase == CombatPhase.STAGGER) {
            enterPhase(entity, state, CombatPhase.IDLE, 0);
        }
    }

    /** Knocks a fighter flat (at least {@link #KNOCKDOWN_TICKS} of stun, a thud and dust). Used by jutsu. */
    public static void knockDown(LivingEntity entity, int ticks) {
        stun(entity, Math.max(KNOCKDOWN_TICKS, ticks));
        CombatEffects.knockdown(entity);
    }

    /** Game time this fighter's posture last broke ({@code Long.MIN_VALUE} if never). For masters' tests. */
    public static long lastPostureBreak(LivingEntity entity) {
        CombatState state = CombatStates.getIfPresent(entity);
        return state == null ? Long.MIN_VALUE : state.lastPostureBreak;
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
        if (state.counterTicks > 0) {
            state.counterTicks--;
        }
        if (state.dodgeCounterTicks > 0 && --state.dodgeCounterTicks == 0) {
            state.dodgeCounterFrom = null;
        }
        state.airTicks = entity.onGround() || entity.isInWater() ? 0 : state.airTicks + 1;
        updateSubStance(entity, state);
        if (state.postureRegenDelay > 0) {
            state.postureRegenDelay--;
        } else if (state.posture > 0) {
            state.posture = Math.max(0f, state.posture - (state.guarding ? 0.6f : 0.4f));
        }
        if (state.wantsGuard && !state.guarding && canGuard(state)) {
            startGuard(entity, state);
        }
        if (state.clinchTarget != null) {
            tickClinch(entity, state);
        }
        if (state.juggleHits > 0 && entity.onGround()) {
            state.juggleHits = 0;
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
            state.phaseTime += state.timeScale * com.noirm.jutsu.ModeSystem.actionSpeed(entity);
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
                onDodged(target, state, attacker);
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
        if (attacker != null) {
            state.lastHitTaken = target.level().getGameTime();
        }
        if (classify(target, state, attacker) == Outcome.GUARDED) {
            boolean shield = blocksWithShield(target, amount);
            absorbGuardedHit(target, state, amount * VANILLA_POSTURE_PER_DAMAGE * (shield ? SHIELD_POSTURE_MULTIPLIER : 1f), amount);
            return amount * (shield ? SHIELD_DAMAGE_MULTIPLIER : GUARD_DAMAGE_MULTIPLIER);
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
        return applyingHit || quietKnockback;
    }

    /** Set while {@link #withoutVanillaKnockback} runs. */
    private static boolean quietKnockback;

    /**
     * Runs {@code hit} (a jutsu's damage) without vanilla's extra push, for techniques that hold
     * their target in place between blows (64 Palms' waves). Guard, dodge and parry still apply.
     */
    public static void withoutVanillaKnockback(Runnable hit) {
        boolean was = quietKnockback;
        quietKnockback = true;
        try {
            hit.run();
        } finally {
            quietKnockback = was;
        }
    }

    /** True while a NOIRM move's hit is being applied (rather than a vanilla attack). */
    public static boolean isApplyingHit() {
        return applyingHit;
    }

    /** True while a heavy NOIRM hit (hit-stop 4+ or a knockdown) is being applied. */
    public static boolean isApplyingHeavyHit() {
        return applyingHit && applyingHeavy;
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

    /**
     * Which move {@code input} starts, continuing {@code from}'s combo route if it has one, in the
     * fighter's current sub-stance (a move's {@code variants} swap in another move for it).
     */
    public static ResourceLocation resolveMove(LivingEntity entity, ResourceLocation from, AttackInput input) {
        CombatState state = CombatStates.getIfPresent(entity);
        return variant(resolveBase(entity, from, input), state == null ? SubStance.NEUTRAL : state.subStance);
    }

    /** {@code id}'s variant for {@code sub} (one step, so variants can't loop), or {@code id} itself. */
    public static ResourceLocation variant(ResourceLocation id, SubStance sub) {
        CombatMove move = id == null ? null : CombatData.move(id);
        if (move == null || sub == SubStance.NEUTRAL) {
            return id;
        }
        ResourceLocation other = move.variants().get(sub);
        return other != null && CombatData.move(other) != null ? other : id;
    }

    private static ResourceLocation resolveBase(LivingEntity entity, ResourceLocation from, AttackInput input) {
        CombatState state = CombatStates.getIfPresent(entity);
        if (state != null && state.clinchTarget != null) {
            // In a clinch, the grab's routes decide everything: knees, headbutt, throw.
            CombatMove grab = CombatData.move(state.clinchMove);
            return grab == null ? null : route(grab, input);
        }
        if (from != null) {
            CombatMove previous = CombatData.move(from);
            ResourceLocation next = previous == null ? null : route(previous, input);
            if (next != null) {
                return next;
            }
        }
        CombatStyle style = CombatData.style(styleFor(entity));
        return style == null ? null : style.opener(input);
    }

    private static ResourceLocation route(CombatMove move, AttackInput input) {
        ResourceLocation next = move.next().get(input);
        if (next == null && input.fallback() != null) {
            next = move.next().get(input.fallback());
        }
        return next;
    }

    /** The fighting style an entity uses: a player's choice, or an NPC's own. */
    public static ResourceLocation styleFor(LivingEntity entity) {
        // A weapon in hand (kunai, staff, sword) brings its own moveset.
        ResourceLocation weapon = com.noirm.weapon.WeaponMoves.styleFor(entity.getMainHandItem());
        if (weapon != null) {
            return weapon;
        }
        if (entity instanceof Player player) {
            var data = ShinobiCapability.get(player).orElse(null);
            if (data != null) {
                return StyleSystem.styleOf(data);
            }
        }
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
                || (state.phase == CombatPhase.RECOVERY && state.phaseTime >= state.move.timing().cancelAfter())
                // After a perfect dodge the counter cuts the roll short (once its invulnerable start is over).
                || (state.phase == CombatPhase.DODGE && state.dodgeCounterTicks > 0 && state.phaseTime >= PERFECT_DODGE_TICKS);
    }

    static boolean canDodge(CombatState state) {
        return state.phase == CombatPhase.IDLE || state.phase == CombatPhase.WINDUP || state.phase == CombatPhase.RECOVERY;
    }

    static boolean canGuard(CombatState state) {
        return state.phase == CombatPhase.IDLE || state.phase == CombatPhase.RECOVERY;
    }

    private static boolean startAttack(LivingEntity entity, CombatState state, AttackInput input) {
        if (SealLock.blocksMelee(entity)) {
            clearBuffer(state); // an attack buffered before the seals doesn't fire through them
            return false;
        }
        ResourceLocation from = state.phase == CombatPhase.RECOVERY ? state.moveId : state.comboFrom;
        updateSubStance(entity, state);
        // A perfect dodge just opened a window: this attack is the style's counter, from behind or beside.
        LivingEntity dodged = state.dodgeCounterTicks > 0 ? state.dodgeCounterFrom : null;
        boolean dodgeCounter = dodged != null && dodged.isAlive() && dodged.level() == entity.level()
                && entity.distanceTo(dodged) <= DODGE_COUNTER_RANGE && state.clinchTarget == null && input != AttackInput.GRAB;
        ResourceLocation id = dodgeCounter ? dodgeCounterMove(entity) : resolveMove(entity, from, input);
        CombatMove move = id == null ? null : CombatData.move(id);
        if (move == null) {
            return false;
        }
        boolean dash = !dodgeCounter && isDashInput(entity, input);
        if (dodgeCounter) {
            slipAround(entity, state, dodged);
        }
        LivingEntity target = dodgeCounter ? dodged
                : findTarget(entity, move.hit().range() + com.noirm.senjutsu.SageMode.reachBonus(entity) + 1.5f);
        float slow = target == null ? 1f : Perception.slowFactor(target, entity);
        float windup = move.timing().windup() * slow;
        if (dodgeCounter) {
            windup = Math.min(windup, DODGE_COUNTER_WINDUP);
        } else if (dash && windup > 1f) {
            windup = Math.max(1f, windup - DASH_STRIKE_WINDUP_CUT);
        }
        // Perception focus: the defender sees this (heavy) attack coming in slow motion.
        if (target != null && slow - 1f >= PERCEPTION_FOCUS_MIN && (!PERCEPTION_FOCUS_HEAVY_ONLY || move.hit().isHeavy())) {
            state.perceivedBy = target.getId();
            state.perceivedSlowdown = slow - 1f;
            CombatEffects.perceptionFocus(target, entity, Math.round(windup) + move.timing().active(), slow - 1f);
        } else {
            state.perceivedBy = -1;
            state.perceivedSlowdown = 0f;
        }
        // Started inside the counter window after a just-frame parry (or a perfect dodge): this attack is the counter.
        state.counterArmed = state.counterTicks > 0 || dodgeCounter;
        state.counterTicks = 0;
        state.dodgeCountering = dodgeCounter;
        state.dodgeCounterTicks = 0;
        state.dodgeCounterFrom = null;
        state.dashStrike = dash;
        CombatMove previous = from == null ? null : CombatData.move(from);
        state.comboDepth = !dodgeCounter && previous != null && route(previous, input) != null ? state.comboDepth + 1 : 0;

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
        if (dash) {
            CombatEffects.dashStrike(entity);
        }
        return true;
    }

    /** The style's dodge counter. */
    private static ResourceLocation dodgeCounterMove(LivingEntity entity) {
        CombatStyle style = CombatData.style(styleFor(entity));
        ResourceLocation id = style == null ? CombatStyle.DEFAULT_COUNTER : style.counterMove();
        return CombatData.move(id) != null ? id : CombatStyle.DEFAULT_COUNTER;
    }

    /**
     * A dash strike: the forward inputs (sent while sprinting). NPCs ask for them only when they
     * mean to dash; a player must really be moving fast (or sprinting) when it reaches the server.
     */
    private static boolean isDashInput(LivingEntity entity, AttackInput input) {
        if (input != AttackInput.FORWARD_LIGHT && input != AttackInput.FORWARD_HEAVY) {
            return false;
        }
        if (!(entity instanceof Player)) {
            return true;
        }
        Vec3 motion = entity.getDeltaMovement();
        return entity.isSprinting() || motion.x * motion.x + motion.z * motion.z >= DASH_STRIKE_SPEED * DASH_STRIKE_SPEED;
    }

    /**
     * The dodge counter's slip: to {@link #DODGE_COUNTER_DISTANCE} behind {@code enemy} (beside
     * them after a side dodge), facing them. Stays put if a wall is in the way.
     */
    private static void slipAround(LivingEntity entity, CombatState state, LivingEntity enemy) {
        Vec3 facing = Vec3.directionFromRotation(0, enemy.getYRot());
        Vec3 side = new Vec3(-facing.z, 0, facing.x); // the enemy's left
        Vec3 offset;
        if (Math.abs(state.dodgeStrafe) >= DODGE_COUNTER_SIDE) {
            // A side dodge: come in from the side you dodged to (as seen from the enemy).
            Vec3 toMe = entity.position().subtract(enemy.position());
            offset = side.scale(Math.signum(toMe.dot(side)) == 0 ? 1 : Math.signum(toMe.dot(side)));
        } else {
            offset = facing.scale(-1);
        }
        double reach = DODGE_COUNTER_DISTANCE + enemy.getBbWidth() / 2;
        Vec3 spot = enemy.position().add(offset.scale(reach));
        Vec3 from = entity.position();
        AABB moved = entity.getBoundingBox().move(spot.subtract(from));
        if (!entity.level().noCollision(entity, moved)) {
            spot = from; // no room: counter from where you stand
        }
        double dx = enemy.getX() - spot.x, dz = enemy.getZ() - spot.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.connection.teleport(spot.x, spot.y, spot.z, yaw, player.getXRot());
        } else {
            entity.moveTo(spot.x, spot.y, spot.z, yaw, entity.getXRot());
        }
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        CombatEffects.dodgeCounter(entity, from, spot);
    }

    // ---------------------------------------------------------------- sub-stances

    /**
     * The sub-stance {@code entity} is in now: air (off the ground for a while), low (crouching),
     * defensive (guarding, or hit recently), aggressive (in a string, or landed a hit recently), or
     * neutral. Whichever of the last two happened more recently wins. A style without that
     * sub-stance stays neutral.
     */
    public static SubStance pickSubStance(LivingEntity entity) {
        CombatState state = CombatStates.get(entity);
        CombatStyle style = CombatData.style(styleFor(entity));
        SubStance sub = rawSubStance(entity, state);
        return style == null || style.has(sub) ? sub : SubStance.NEUTRAL;
    }

    private static SubStance rawSubStance(LivingEntity entity, CombatState state) {
        if (state.airTicks >= AIR_STANCE_TICKS && !entity.onGround()) {
            return SubStance.AIR;
        }
        if (entity.isCrouching()) {
            return SubStance.LOW;
        }
        long now = entity.level().getGameTime();
        boolean attacking = state.phase == CombatPhase.WINDUP || state.phase == CombatPhase.ACTIVE || state.phase == CombatPhase.RECOVERY;
        boolean defensive = state.guarding || now - state.lastHitTaken <= DEFENSIVE_TICKS;
        boolean aggressive = (attacking && state.comboDepth >= 1) || now - state.lastHitDealt <= AGGRESSIVE_TICKS;
        if (state.guarding) {
            return SubStance.DEFENSIVE;
        }
        if (defensive && aggressive) {
            return state.lastHitTaken >= state.lastHitDealt ? SubStance.DEFENSIVE : SubStance.AGGRESSIVE;
        }
        return defensive ? SubStance.DEFENSIVE : aggressive ? SubStance.AGGRESSIVE : SubStance.NEUTRAL;
    }

    /** Recomputes the sub-stance and its stance; tells the HUD and the animation when they change. */
    private static void updateSubStance(LivingEntity entity, CombatState state) {
        SubStance sub = pickSubStance(entity);
        CombatStyle style = CombatData.style(styleFor(entity));
        CombatStyle.Stance stance = style == null ? null : style.stance(sub);
        ResourceLocation animation = stance == null ? null : stance.animation();
        int switchTicks = stance == null ? 2 : stance.switchTicks();
        if (sub != state.subStance || !java.util.Objects.equals(animation, state.stanceAnimation) || switchTicks != state.stanceSwitch) {
            state.subStance = sub;
            state.stanceAnimation = animation;
            state.stanceSwitch = switchTicks;
            state.markDirty();
            if (entity instanceof CombatAnimated animated) {
                animated.setCombatStance(animation, sub, switchTicks);
            }
        }
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
        state.dodgeStrafe = strafe;
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
        com.noirm.movement.MovementFeel.noteDodge(entity); // a dodge is the roll that skips landing recovery
        CombatEffects.dodge(entity, forward, strafe, DODGE_TICKS);
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
        float lunge = state.move.hit().lunge() + (state.dashStrike ? DASH_STRIKE_LUNGE : 0f);
        if (lunge > 0) {
            Vec3 facing = Vec3.directionFromRotation(0, entity.getYHeadRot());
            entity.setDeltaMovement(entity.getDeltaMovement().add(facing.x * lunge, 0, facing.z * lunge));
            entity.hurtMarked = true;
        }
        CombatEffects.swing(entity, state.move);
    }

    private static void sweep(LivingEntity attacker, CombatState state) {
        CombatMove.Hit hit = state.move.hit();
        float range = hit.range() + com.noirm.senjutsu.SageMode.reachBonus(attacker); // Sage Mode's aura reaches a little further
        AABB area = attacker.getBoundingBox().inflate(range + 1);
        int sweeping = sweepingLevel(attacker);
        float arc = Math.min(360f, hit.arc() + SWEEP_ARC_PER_LEVEL * sweeping);
        int hits = 0;
        for (LivingEntity target : attacker.level().getEntitiesOfClass(LivingEntity.class, area,
                t -> t != attacker && t.isAlive() && !t.isSpectator() && !attacker.isAlliedTo(t) && !com.noirm.ally.Crew.sameSide(attacker, t)
                        && !state.hitThisSwing.contains(t.getId()) && inSwing(attacker, t, range, arc))) {
            // The weapon wears once per swing, like vanilla (on the first one hit); the rest is the sweep.
            boolean first = state.hitThisSwing.isEmpty();
            state.hitThisSwing.add(target.getId());
            applyHit(attacker, state, target, state.move, first);
            hits++;
        }
        if (hits > 0 && sweeping > 0 && attacker.level() instanceof net.minecraft.server.level.ServerLevel level) {
            Vec3 front = attacker.position().add(Vec3.directionFromRotation(0, attacker.getYHeadRot()).scale(1.2))
                    .add(0, attacker.getBbHeight() * 0.55, 0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0, 0, 0, 0);
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), com.noirm.sound.NoirmSounds.SWING_HEAVY.get(),
                    attacker.getSoundSource(), 1f, 1f);
        }
    }

    private static void applyHit(LivingEntity attacker, CombatState attackerState, LivingEntity target, CombatMove move, boolean firstOfSwing) {
        if (Substitution.trySubstitute(target, attacker) || HyugaTechniques.deflect(target, attacker)) {
            return;
        }
        CombatState targetState = CombatStates.get(target);
        Outcome outcome = classify(target, targetState, attacker);
        if (outcome == Outcome.DODGED) {
            onDodged(target, targetState, attacker);
            return;
        }
        // A counter (after a just-frame parry) can't be guarded or parried.
        boolean counter = attackerState.hasCounter();
        if (counter && (outcome == Outcome.GUARDED || outcome == Outcome.PARRIED)) {
            outcome = Outcome.HIT;
        }
        if (outcome == Outcome.PARRIED) {
            onParried(target, targetState, attacker);
            return;
        }
        CombatMove.Hit hit = move.hit();
        if (hit.clinch() == CombatMove.Hit.Clinch.GRAB) {
            grab(attacker, attackerState, target, targetState);
            return; // grabs ignore guard: that's what they're for
        }
        // A fighter held in your clinch can't guard.
        boolean guarded = outcome == Outcome.GUARDED && targetState.clinchedBy != attacker;
        // A guard break shatters the guard that blocks it (a parry still beats it, above).
        boolean guardBroken = guarded && hit.guardBreak();
        if (guardBroken) {
            postureBreak(target, targetState);
            CombatEffects.guardBreak(attacker, target);
            guarded = false;
        }
        long now = attacker.level().getGameTime();
        targetState.lastHitTaken = now;
        // Gentle Fist (Byakugan open): more posture damage, and a guard keeps out less.
        boolean gentleFist = Byakugan.isActive(attacker);
        float posture = hit.posture() * (gentleFist ? Byakugan.GENTLE_FIST_POSTURE : 1f);
        float damage = hit.damage() * Perception.damageMultiplier(attacker) + weaponDamage(attacker, target, hit.damage());
        if (attackerState.dashStrike) {
            damage *= DASH_STRIKE_DAMAGE;
            posture *= DASH_STRIKE_POSTURE;
        }
        if (guardBroken) {
            damage *= GUARD_BREAK_DAMAGE;
        }
        if (counter) {
            damage *= COUNTER_DAMAGE;
            attackerState.counterArmed = false;
            attackerState.counterTicks = 0;
            CombatEffects.counterHit(attacker, target);
        }
        boolean airborne = !target.onGround() && !guarded;
        if (airborne) {
            damage *= Math.max(0.5f, 1f - 0.1f * targetState.juggleHits);
        }
        if (guarded) {
            boolean shield = !gentleFist && blocksWithShield(target, damage); // Gentle Fist goes through shields
            absorbGuardedHit(target, targetState, posture * (shield ? SHIELD_POSTURE_MULTIPLIER : 1f), damage);
            float kept = Math.max(gentleFist ? Byakugan.GENTLE_FIST_GUARD : shield ? SHIELD_DAMAGE_MULTIPLIER : GUARD_DAMAGE_MULTIPLIER,
                    hit.pierce());
            damage *= kept;
        } else {
            if (!guardBroken) {
                addPosture(target, targetState, posture * UNGUARDED_POSTURE_RATIO);
            }
            boolean floored = hit.knockdown() || hit.ender() == CombatMove.Hit.Ender.KNOCKDOWN;
            stagger(target, targetState, floored ? Math.max(hit.hitstun(), KNOCKDOWN_TICKS) : hit.hitstun());
        }
        if (hit.clinch() == CombatMove.Hit.Clinch.RELEASE && attackerState.clinchTarget == target) {
            releaseClinch(attacker, attackerState);
        }

        boolean hurt;
        applyingHit = true;
        applyingHeavy = hit.isHeavy() || hit.knockdown();
        try {
            target.invulnerableTime = 0;
            hurt = target.hurt(damageSource(attacker), damage);
        } finally {
            applyingHit = false;
            applyingHeavy = false;
        }
        if (!hurt) {
            return;
        }
        attacker.setLastHurtMob(target); // like a vanilla attack: crew mates fight what their leader fights
        attackerState.lastHitDealt = now;
        CombatMove.Hit.Ender ender = guarded ? CombatMove.Hit.Ender.NONE : hit.ender();
        applyWeaponEffects(attacker, target, guarded, firstOfSwing);

        float yaw = attacker.getYHeadRot() * Mth.DEG_TO_RAD;
        float knockback = (hit.knockback() + KNOCKBACK_PER_LEVEL * EnchantmentHelper.getKnockbackBonus(attacker)) * (guarded ? 0.4f : 1f);
        if (ender == CombatMove.Hit.Ender.SPLAT) {
            knockback = Math.max(knockback, ENDER_SPLAT_KNOCKBACK);
        }
        boolean splatted = !guarded && knockback >= WALL_SPLAT_KNOCKBACK && wallSplat(attacker, target, targetState, damage, knockback);
        if (knockback > 0 && !splatted) {
            target.knockback(knockback, Mth.sin(yaw), -Mth.cos(yaw));
        }
        float lift = ender == CombatMove.Hit.Ender.LAUNCH ? Math.max(hit.lift(), ENDER_LAUNCH_LIFT) : hit.lift();
        if (!guarded && !splatted && lift > 0) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x, Math.max(motion.y, lift), motion.z);
        } else if (airborne && !splatted && targetState.juggleHits < MAX_JUGGLE) {
            // Juggle: hits in the air keep them up, a little less each time.
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x * 0.5, Math.max(motion.y, JUGGLE_FLOAT), motion.z * 0.5);
        }
        if (airborne) {
            targetState.juggleHits++;
        }
        if (!guarded && (hit.knockdown() || ender == CombatMove.Hit.Ender.KNOCKDOWN) && !splatted) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x, Math.max(motion.y, 0.3), motion.z);
            CombatEffects.knockdown(target);
        }
        if (hit.chakra() > 0) {
            drainChakra(target, targetState, hit.chakra() * (guarded ? 0.5f : 1f));
        }
        target.hurtMarked = true;

        TrainingSystem.onHitLanded(attacker, target, hit.isHeavy(), guarded);
        if (gentleFist) {
            Byakugan.gentleFist(attacker, target, hit.isHeavy());
        }
        int hitstop = Math.min(MAX_HITSTOP, guarded ? hit.hitstop() / 2 : splatted ? hit.hitstop() + WALL_SPLAT_HITSTOP : hit.hitstop());
        attackerState.hitstop = Math.max(attackerState.hitstop, hitstop);
        targetState.hitstop = Math.max(targetState.hitstop, hitstop);
        CombatEffects.hitstop(attacker, target, hitstop);
        CombatEffects.hitLanded(attacker, target, move, guarded, targetState);
        if (ender != CombatMove.Hit.Ender.NONE || splatted) {
            CombatEffects.ender(attacker, target, splatted ? CombatMove.Hit.Ender.SPLAT : ender, hit);
        }
    }

    private static void onDodged(LivingEntity target, CombatState state, Entity attacker) {
        if (!state.perfectDodgeUsed && state.phaseTime < PERFECT_DODGE_TICKS) {
            state.perfectDodgeUsed = true;
            if (attacker instanceof LivingEntity enemy) {
                state.dodgeCounterTicks = DODGE_COUNTER_WINDOW; // your next attack: a counter from behind or beside
                state.dodgeCounterFrom = enemy;
            }
            CombatEffects.perfectDodge(target);
            TrainingSystem.onAttackDodged(target, attacker, true);
            AwakeningSystem.onClutch(target);
            focus(target);
        } else {
            TrainingSystem.onAttackDodged(target, attacker, false); // APX 0.1: dodging real attacks trains speed
        }
    }

    private static void onParried(LivingEntity defender, CombatState defenderState, Entity attacker) {
        if (attacker instanceof LivingEntity livingAttacker && isJustFrame(defender, CombatStates.get(livingAttacker))) {
            onJustFrameParry(defender, defenderState, livingAttacker, CombatStates.get(livingAttacker));
            return;
        }
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

    /** The attack being parried is one {@code defender}'s perception is slowing (perception focus). */
    static boolean isJustFrame(LivingEntity defender, CombatState attackerState) {
        return attackerState.perceivedBy == defender.getId()
                && (attackerState.phase == CombatPhase.WINDUP || attackerState.phase == CombatPhase.ACTIVE);
    }

    /**
     * Just-frame parry (blueprint §3): no damage, the attacker's posture breaks (a full stun), and
     * the defender gets {@link #COUNTER_TICKS} to start a counter.
     */
    private static void onJustFrameParry(LivingEntity defender, CombatState defenderState, LivingEntity attacker, CombatState attackerState) {
        CombatEffects.parry(defender);
        CombatEffects.justFrame(defender, attacker);
        TrainingSystem.onParry(defender, attacker);
        AwakeningSystem.onClutch(defender);
        if (defender instanceof net.minecraft.world.entity.player.Player player) {
            com.noirm.progress.CombatFeatTrigger.justFrameParry(player); // "Path of the Shinobi"
        }
        attackerState.perceivedBy = -1;
        attackerState.counterArmed = false;
        attackerState.counterTicks = 0;
        postureBreak(attacker, attackerState);
        defenderState.counterTicks = COUNTER_TICKS;
        defenderState.counterArmed = false;
        focus(defender);
    }

    /** Opens a dodge-counter window as if {@code entity} had just perfectly dodged {@code enemy} (tests, debugging). */
    public static void openDodgeCounter(LivingEntity entity, LivingEntity enemy, float strafe) {
        CombatState state = CombatStates.get(entity);
        state.dodgeCounterTicks = DODGE_COUNTER_WINDOW;
        state.dodgeCounterFrom = enemy;
        state.dodgeStrafe = strafe;
    }

    /** Opens a counter window as if {@code entity} had just made a just-frame parry (tests, debugging). */
    public static void openCounterWindow(LivingEntity entity) {
        CombatState state = CombatStates.get(entity);
        state.counterTicks = COUNTER_TICKS;
    }

    /** Applies posture (and stamina, for players) from a blocked hit. May break the guard. */
    private static void absorbGuardedHit(LivingEntity target, CombatState state, float posture, float damage) {
        CombatEffects.guarded(target);
        TrainingSystem.onGuarded(target); // counts toward kill XP
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
        state.lastPostureBreak = entity.level().getGameTime();
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
        if (state.clinchTarget != null) {
            releaseClinch(entity, state); // hit while grappling: you let go
        }
        enterPhase(entity, state, CombatPhase.STAGGER, ticks);
    }

    // ---------------------------------------------------------------- clinch

    /**
     * A grab connected. Grabs beat guard; strikes beat grabs (grab someone mid-swing and you eat
     * it); too-big targets can't be held.
     */
    private static void grab(LivingEntity attacker, CombatState attackerState, LivingEntity target, CombatState targetState) {
        if (targetState.phase == CombatPhase.ACTIVE || target.getBbWidth() > MAX_GRAB_WIDTH || targetState.clinchTarget != null) {
            stagger(attacker, attackerState, GRAB_BROKEN_STUN);
            CombatEffects.grabBroken(attacker);
            return;
        }
        if (targetState.clinchedBy != null && targetState.clinchedBy != attacker) {
            return;
        }
        targetState.wantsGuard = false;
        stopGuard(target, targetState);
        attackerState.clinchTarget = target;
        attackerState.clinchMove = attackerState.moveId;
        attackerState.clinchTicks = CLINCH_TICKS;
        targetState.clinchedBy = attacker;
        stagger(target, targetState, CLINCH_TICKS);
        attackerState.markDirty();
        CombatEffects.grabbed(attacker, target);
    }

    private static void tickClinch(LivingEntity holder, CombatState state) {
        LivingEntity target = state.clinchTarget;
        if (--state.clinchTicks <= 0 || !target.isAlive() || !holder.isAlive() || target.level() != holder.level()
                || holder.distanceTo(target) > 4 || state.phase == CombatPhase.STAGGER || state.phase == CombatPhase.DODGE) {
            releaseClinch(holder, state);
            return;
        }
        // Keep them pulled in close, in front, and unable to act.
        Vec3 front = holder.position().add(Vec3.directionFromRotation(0, holder.getYRot()).scale(0.9));
        Vec3 pull = front.subtract(target.position());
        target.setDeltaMovement(pull.x * 0.5, target.getDeltaMovement().y, pull.z * 0.5);
        target.hurtMarked = true;
        CombatState targetState = CombatStates.get(target);
        if (targetState.phase != CombatPhase.STAGGER || targetState.phaseLength - targetState.phaseTime < 3) {
            enterPhase(target, targetState, CombatPhase.STAGGER, 3);
        }
    }

    /** Lets go of whoever {@code holder} has in a clinch. */
    public static void releaseClinch(LivingEntity holder, CombatState state) {
        LivingEntity target = state.clinchTarget;
        state.clinchTarget = null;
        state.clinchMove = null;
        state.clinchTicks = 0;
        state.markDirty();
        if (target != null) {
            CombatState targetState = CombatStates.get(target);
            if (targetState.clinchedBy == holder) {
                targetState.clinchedBy = null;
                if (targetState.phase == CombatPhase.STAGGER && targetState.phaseLength - targetState.phaseTime > 6) {
                    targetState.phaseLength = targetState.phaseTime + 6; // a moment to recover, not the whole clinch
                }
            }
        }
    }

    /** The held fighter breaks free with a dodge: costly, but it shoves both apart. */
    private static boolean escapeClinch(LivingEntity entity, CombatState state) {
        LivingEntity holder = state.clinchedBy;
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            boolean paid = ShinobiCapability.get(player)
                    .map(d -> d.tryConsumeStamina(DODGE_STAMINA_COST * CLINCH_ESCAPE_COST)).orElse(true);
            if (!paid) {
                CombatEffects.outOfStamina(entity);
                return false;
            }
        }
        CombatState holderState = CombatStates.get(holder);
        releaseClinch(holder, holderState);
        state.clinchedBy = null;
        enterPhase(entity, state, CombatPhase.IDLE, 0);
        Vec3 away = entity.position().subtract(holder.position());
        Vec3 flat = new Vec3(away.x, 0, away.z).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
        entity.setDeltaMovement(flat.x * 0.7, 0.15, flat.z * 0.7);
        entity.hurtMarked = true;
        holder.setDeltaMovement(-flat.x * 0.4, 0.05, -flat.z * 0.4);
        holder.hurtMarked = true;
        stagger(holder, holderState, 8);
        CombatEffects.clinchEscape(entity);
        return true;
    }

    // ---------------------------------------------------------------- impacts

    /**
     * A strong hit with a wall right behind the target: they slam into it instead of flying. More
     * damage, a long stun, and the wall cracks (or breaks, for strong enough fighters).
     */
    private static boolean wallSplat(LivingEntity attacker, LivingEntity target, CombatState targetState, float damage, float knockback) {
        Vec3 dir = Vec3.directionFromRotation(0, attacker.getYHeadRot());
        double reach = 0.6 + knockback * 1.6;
        net.minecraft.core.BlockPos wall = null;
        for (double d = 0.4; d <= reach; d += 0.3) {
            Vec3 at = target.position().add(dir.x * (d + target.getBbWidth() / 2), 0.9, dir.z * (d + target.getBbWidth() / 2));
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at);
            if (target.level().getBlockState(pos).blocksMotion()) {
                wall = pos;
                break;
            }
        }
        if (wall == null) {
            return false;
        }
        target.setDeltaMovement(Vec3.ZERO);
        applyingHit = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(damageSource(attacker), damage * WALL_SPLAT_DAMAGE);
        } finally {
            applyingHit = false;
        }
        stagger(target, targetState, WALL_SPLAT_STUN);
        if (target.level() instanceof net.minecraft.server.level.ServerLevel level) {
            float taijutsu = attacker instanceof Player player
                    ? ShinobiCapability.get(player).map(d -> d.getTaijutsu()).orElse(10f)
                    : attacker instanceof CombatStats stats ? stats.combatTaijutsu() : 10f;
            com.noirm.world.Destruction.smashWall(level, wall, damage / 6f * (taijutsu / 10f));
        }
        CombatEffects.wallSplat(target, Vec3.atCenterOf(wall));
        return true;
    }

    /** Gentle Fist: chakra knocked out of the target's network. NPCs lose posture instead. */
    private static void drainChakra(LivingEntity target, CombatState state, float amount) {
        if (target instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(d -> d.setChakra(Math.max(0f, d.getChakra() - amount)));
        } else {
            addPosture(target, state, amount * 0.5f);
        }
        CombatEffects.chakraStruck(target);
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

    // ---------------------------------------------------------------- weapons

    /**
     * What the weapon in hand adds to a hit of {@code moveDamage}: a share of its attack damage
     * scaled by the move's weight, plus its enchantment bonus against this target, times the
     * fighter's state (locked in, cloak, mode). 0 with bare hands. See {@link #WEAPON_DAMAGE_SHARE}.
     */
    public static float weaponDamage(LivingEntity attacker, LivingEntity target, float moveDamage) {
        AttributeInstance attack = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        float bonus = attack == null ? 0f : Math.max(0f, (float) (attack.getValue() - attack.getBaseValue()));
        float weight = Mth.clamp(moveDamage / WEAPON_REFERENCE_DAMAGE, WEAPON_WEIGHT_MIN, WEAPON_WEIGHT_MAX);
        float enchanted = target == null ? 0f : EnchantmentHelper.getDamageBonus(attacker.getMainHandItem(), target.getMobType());
        float extra = bonus * WEAPON_DAMAGE_SHARE * weight + enchanted;
        return extra <= 0 ? 0f : extra * Perception.stateMultiplier(attacker);
    }

    /**
     * After a landed hit the weapon in hand acts like it does in vanilla: Fire Aspect sets the
     * target alight (not through a guard), the enchantment hooks run (Bane of Arthropods' slowness,
     * Thorns on the target's armor…), and the weapon wears once per swing. A player's broken
     * weapon leaves their hand.
     */
    private static void applyWeaponEffects(LivingEntity attacker, LivingEntity target, boolean guarded, boolean firstOfSwing) {
        int fire = EnchantmentHelper.getFireAspect(attacker);
        if (fire > 0 && !guarded && target.isAlive()) {
            target.setSecondsOnFire(fire * FIRE_ASPECT_SECONDS);
        }
        EnchantmentHelper.doPostHurtEffects(target, attacker);
        EnchantmentHelper.doPostDamageEffects(attacker, target);
        ItemStack weapon = attacker.getMainHandItem();
        if (!firstOfSwing || weapon.isEmpty()) {
            return;
        }
        if (attacker instanceof Player player) {
            ItemStack before = weapon.copy();
            weapon.hurtEnemy(target, player);
            if (weapon.isEmpty()) {
                net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(player, before, net.minecraft.world.InteractionHand.MAIN_HAND);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        } else {
            weapon.getItem().hurtEnemy(weapon, target, attacker);
        }
    }

    /** The Sweeping Edge level of the weapon in hand (only weapons that can sweep, like swords). */
    private static int sweepingLevel(LivingEntity attacker) {
        ItemStack weapon = attacker.getMainHandItem();
        return weapon.canPerformAction(net.minecraftforge.common.ToolActions.SWORD_SWEEP)
                ? EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE, weapon) : 0;
    }

    /** The shield {@code entity} holds (off hand first), or an empty stack. */
    public static ItemStack heldShield(LivingEntity entity) {
        for (ItemStack stack : new ItemStack[] {entity.getOffhandItem(), entity.getMainHandItem()}) {
            if (stack.canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * A guarded hit on a fighter holding a shield: the shield takes it, with its block sound, and
     * wears like vanilla (1 + the damage, for hits of 3 or more). False without a shield.
     */
    private static boolean blocksWithShield(LivingEntity target, float damage) {
        ItemStack shield = heldShield(target);
        if (shield.isEmpty()) {
            return false;
        }
        if (damage >= 3f) {
            net.minecraft.world.entity.EquipmentSlot slot = target.getOffhandItem() == shield
                    ? net.minecraft.world.entity.EquipmentSlot.OFFHAND : net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            shield.hurtAndBreak(1 + Mth.floor(damage), target, e -> e.broadcastBreakEvent(slot));
        }
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), com.noirm.sound.NoirmSounds.GUARD.get(),
                target.getSoundSource(), 0.9f, 0.9f + target.getRandom().nextFloat() * 0.2f);
        return true;
    }
}

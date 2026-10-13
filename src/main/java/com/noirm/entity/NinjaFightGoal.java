package com.noirm.entity;

import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.Perception;
import com.noirm.dojutsu.Byakugan;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

/**
 * How the sparring ninja fights: close in, throw a combo, back off, cast its clan's jutsu from
 * range, and react to the opponent's wind-ups with a guard, a dodge or a substitution. It reacts more often to weaker opponents, since their attacks
 * look slower to it (perception works both ways).
 */
class NinjaFightGoal extends Goal {
    private static final double ATTACK_RANGE = 2.7;
    private static final float BASE_REACTION_CHANCE = 0.25f;
    private static final List<List<AttackInput>> COMBOS = List.of(
            List.of(AttackInput.LIGHT, AttackInput.LIGHT, AttackInput.LIGHT),
            List.of(AttackInput.LIGHT, AttackInput.LIGHT, AttackInput.HEAVY),
            List.of(AttackInput.LIGHT, AttackInput.HEAVY),
            List.of(AttackInput.HEAVY),
            List.of(AttackInput.LAUNCHER, AttackInput.LIGHT),
            List.of(AttackInput.LIGHT, AttackInput.BACK_HEAVY),
            List.of(AttackInput.LIGHT, AttackInput.LIGHT, AttackInput.LAUNCHER, AttackInput.LIGHT, AttackInput.LIGHT));
    /** Against a raised guard: grab (grabs beat guard), knee twice, then headbutt or throw. */
    private static final List<List<AttackInput>> GRAPPLES = List.of(
            List.of(AttackInput.GRAB, AttackInput.LIGHT, AttackInput.LIGHT, AttackInput.HEAVY),
            List.of(AttackInput.GRAB, AttackInput.LIGHT, AttackInput.BACK_HEAVY));

    private static final ResourceLocation SUBSTITUTION = com.noirm.Noirm.id("universal/substitution");
    private static final java.util.Set<String> RANGED_BEHAVIORS = java.util.Set.of("projectile", "genjutsu", "dash_strike",
            "beam", "bind", "pillar", "rain", "cloud", "strike", "wave");

    private final SparringNinja ninja;
    private final Deque<AttackInput> combo = new ArrayDeque<>();
    private int attackCooldown;
    private int jutsuCooldown = 40;
    private int guardTicks;
    private boolean reactedToCurrentWindup;

    NinjaFightGoal(SparringNinja ninja) {
        this.ninja = ninja;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = ninja.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        combo.clear();
        CombatSystem.setGuard(ninja, false);
        ninja.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = ninja.getTarget();
        if (target == null) {
            return;
        }
        ninja.getLookControl().setLookAt(target, 60f, 60f);
        CombatState self = CombatStates.get(ninja);
        double distance = ninja.distanceTo(target);
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (self.phase() == CombatPhase.STAGGER) {
            combo.clear();
            // Held in a clinch: struggle free now and then.
            if (self.clinchedBy() != null && ninja.getRandom().nextFloat() < 0.04f) {
                CombatSystem.requestDodge(ninja, -1f, 0f);
            }
            return;
        }
        if (guardTicks > 0 && --guardTicks == 0) {
            CombatSystem.setGuard(ninja, false);
        }
        reactToOpponent(target, distance);

        // Badly hurt crew mates back off to recover, guard up, and only throw jutsu from range.
        if (com.noirm.ally.AllyMind.shouldRetreat(ninja)) {
            combo.clear();
            if (distance < 8) {
                net.minecraft.world.phys.Vec3 away = ninja.position().subtract(target.position()).normalize().scale(6);
                ninja.getNavigation().moveTo(ninja.getX() + away.x, ninja.getY(), ninja.getZ() + away.z, 1.2);
            }
            if (distance < 3 && !self.isGuarding()) {
                CombatSystem.setGuard(ninja, true);
                guardTicks = 20;
            }
            if (distance > ATTACK_RANGE + 1 && jutsuCooldown-- <= 0 && CombatSystem.canAct(ninja)) {
                castRangedJutsu();
            }
            return;
        }
        boolean busy = self.phase() != CombatPhase.IDLE || self.isGuarding();
        if (busy) {
            ninja.getNavigation().stop();
        } else if (distance > ATTACK_RANGE - 0.2) {
            ninja.getNavigation().moveTo(target, 1.0);
        } else {
            ninja.getNavigation().stop();
        }

        if (combo.isEmpty() && distance > ATTACK_RANGE + 1 && distance < 14 && jutsuCooldown-- <= 0
                && ninja.hasLineOfSight(target) && CombatSystem.canAct(ninja)) {
            castRangedJutsu();
        }
        if (combo.isEmpty() && attackCooldown <= 0 && distance <= ATTACK_RANGE && !self.isGuarding()
                && ninja.knownJutsu().contains(Byakugan.SIXTY_FOUR_PALMS) && ninja.getRandom().nextFloat() < 0.04f
                && JutsuCaster.npcCast(ninja, Byakugan.SIXTY_FOUR_PALMS, 1f)) {
            attackCooldown = 40;
        }
        CombatState opponent = CombatStates.getIfPresent(target);
        if (combo.isEmpty() && attackCooldown <= 0 && distance <= 2.2 && !self.isGuarding()
                && opponent != null && opponent.isGuarding() && ninja.getRandom().nextFloat() < 0.5f) {
            combo.addAll(GRAPPLES.get(ninja.getRandom().nextInt(GRAPPLES.size())));
        }
        if (combo.isEmpty() && attackCooldown <= 0 && distance > ATTACK_RANGE + 0.5 && distance < 5 && !self.isGuarding()
                && ninja.getRandom().nextFloat() < 0.03f) {
            combo.add(AttackInput.FORWARD_HEAVY); // a dash attack to close the gap
        }
        if (combo.isEmpty() && attackCooldown <= 0 && distance <= ATTACK_RANGE && !self.isGuarding()) {
            combo.addAll(COMBOS.get(ninja.getRandom().nextInt(COMBOS.size())));
        }
        if (!combo.isEmpty() && CombatSystem.canAttackNow(ninja)) {
            if (distance > (combo.peek() == AttackInput.FORWARD_HEAVY ? 5.5 : ATTACK_RANGE + 1)) {
                combo.clear(); // target escaped: stop swinging at air
            } else if (CombatSystem.requestAttack(ninja, combo.peek())) {
                combo.poll();
                if (combo.isEmpty()) {
                    attackCooldown = 20 + ninja.getRandom().nextInt(25);
                }
            }
        }
    }

    /** At range: throw one of its clan's jutsu (fireball, water bullet, genjutsu…). */
    private void castRangedJutsu() {
        List<ResourceLocation> options = ninja.castableJutsu().stream()
                .filter(id -> RANGED_BEHAVIORS.contains(behavior(id)))
                .toList();
        jutsuCooldown = 40 + ninja.getRandom().nextInt(60);
        if (!options.isEmpty()) {
            ResourceLocation id = options.get(ninja.getRandom().nextInt(options.size()));
            JutsuCaster.npcCast(ninja, id, 0.4f + 0.6f * ninja.getRandom().nextFloat());
        }
    }

    private static String behavior(ResourceLocation id) {
        JutsuDefinition def = JutsuData.get(id);
        return def == null ? "" : def.behavior();
    }

    /** When the opponent winds up nearby, maybe guard or dodge — once per wind-up. */
    private void reactToOpponent(LivingEntity target, double distance) {
        CombatState opponent = CombatStates.getIfPresent(target);
        if (opponent == null || opponent.phase() != CombatPhase.WINDUP) {
            reactedToCurrentWindup = false;
            return;
        }
        if (reactedToCurrentWindup || distance > 4.5) {
            return;
        }
        reactedToCurrentWindup = true;
        CombatState self = CombatStates.get(ninja);
        if (self.phase() != CombatPhase.IDLE && self.phase() != CombatPhase.RECOVERY) {
            return;
        }
        float chance = Math.min(0.85f, BASE_REACTION_CHANCE * Perception.slowFactor(ninja, target));
        if (ninja.getRandom().nextFloat() >= chance) {
            return;
        }
        combo.clear();
        if (ninja.knownJutsu().contains(SUBSTITUTION) && ninja.getRandom().nextFloat() < 0.25f
                && JutsuCaster.npcCast(ninja, SUBSTITUTION, 1f)) {
            return;
        }
        if (ninja.knownJutsu().contains(Byakugan.ROTATION) && ninja.getRandom().nextFloat() < 0.35f
                && JutsuCaster.npcCast(ninja, Byakugan.ROTATION, 1f)) {
            return;
        }
        if (ninja.getRandom().nextFloat() < 0.6f) {
            CombatSystem.setGuard(ninja, true);
            guardTicks = 15;
        } else {
            float strafe = ninja.getRandom().nextBoolean() ? 0.6f : -0.6f;
            CombatSystem.requestDodge(ninja, -1f, strafe);
        }
    }
}

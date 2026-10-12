package com.noirm.entity;

import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.Perception;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

/**
 * How the sparring ninja fights: close in, throw a combo, back off, and react to the opponent's
 * wind-ups with a guard or a dodge. It reacts more often to weaker opponents, since their attacks
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
            List.of(AttackInput.LAUNCHER, AttackInput.LIGHT));

    private final SparringNinja ninja;
    private final Deque<AttackInput> combo = new ArrayDeque<>();
    private int attackCooldown;
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
            return;
        }
        if (guardTicks > 0 && --guardTicks == 0) {
            CombatSystem.setGuard(ninja, false);
        }
        reactToOpponent(target, distance);

        boolean busy = self.phase() != CombatPhase.IDLE || self.isGuarding();
        if (busy) {
            ninja.getNavigation().stop();
        } else if (distance > ATTACK_RANGE - 0.2) {
            ninja.getNavigation().moveTo(target, 1.0);
        } else {
            ninja.getNavigation().stop();
        }

        if (combo.isEmpty() && attackCooldown <= 0 && distance <= ATTACK_RANGE && !self.isGuarding()) {
            combo.addAll(COMBOS.get(ninja.getRandom().nextInt(COMBOS.size())));
        }
        if (!combo.isEmpty() && CombatSystem.canAttackNow(ninja)) {
            if (distance > ATTACK_RANGE + 1) {
                combo.clear(); // target escaped: stop swinging at air
            } else if (CombatSystem.requestAttack(ninja, combo.peek())) {
                combo.poll();
                if (combo.isEmpty()) {
                    attackCooldown = 20 + ninja.getRandom().nextInt(25);
                }
            }
        }
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
        if (ninja.getRandom().nextFloat() < 0.6f) {
            CombatSystem.setGuard(ninja, true);
            guardTicks = 15;
        } else {
            float strafe = ninja.getRandom().nextBoolean() ? 0.6f : -0.6f;
            CombatSystem.requestDodge(ninja, -1f, strafe);
        }
    }
}

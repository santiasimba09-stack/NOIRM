package com.noirm.entity;

import com.noirm.biju.BijuSystem;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.jutsu.JutsuCaster;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * How a tailed beast fights: close in and claw or slam (telegraphed, so you can dodge or parry),
 * and from range, stop, gather a Tailed Beast Ball for {@link #BALL_CHARGE} ticks (dark chakra
 * swirls at its mouth, a roar) and fire it.
 */
final class BeastFightGoal extends Goal {
    static final int BALL_CHARGE = 30;
    /** Shukaku's Drilling Air Bullet: two belly pounds, then the wind ball leaves its mouth (the "drum" clip is 20 ticks). */
    static final int DRUM_TICKS = 20, DRUM_FIRE = 14;
    static final ResourceLocation AIR_BULLET = com.noirm.Noirm.id("futon/vacuum_bullets");
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.2f, 0.02f, 0.3f), 2f);

    private final TailedBeast beast;
    private int attackCooldown = 20;
    private int ballCooldown = 100;
    private int charging = -1;
    private int drumCooldown = 60;
    private int drumming = -1;

    BeastFightGoal(TailedBeast beast) {
        this.beast = beast;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = beast.getTarget();
        return target != null && target.isAlive() && !beast.isSubdued();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        charging = -1;
        if (drumming >= 0) {
            beast.clearAction(TailedBeast.ACTION_DRUM);
        }
        drumming = -1;
        beast.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = beast.getTarget();
        if (target == null) {
            return;
        }
        beast.getLookControl().setLookAt(target, 30f, 30f);
        double distance = beast.distanceTo(target);
        double reach = beast.getBbWidth() / 2 + 2.5;
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (ballCooldown > 0) {
            ballCooldown--;
        }
        if (drumCooldown > 0) {
            drumCooldown--;
        }
        CombatState self = CombatStates.get(beast);
        if (self.phase() == CombatPhase.STAGGER) {
            charging = -1;
            if (drumming >= 0) {
                beast.clearAction(TailedBeast.ACTION_DRUM); // knocked out of it
                drumming = -1;
            }
            return;
        }
        if (drumming >= 0) {
            drum(target);
            return;
        }
        if (charging >= 0) {
            chargeBall(target);
            return;
        }
        boolean busy = self.phase() != CombatPhase.IDLE;
        if (busy || distance <= reach - 0.3) {
            beast.getNavigation().stop();
        } else {
            beast.getNavigation().moveTo(target, 1.0);
        }
        if (ballCooldown <= 0 && distance > reach + 2 && distance < 28 && beast.hasLineOfSight(target) && CombatSystem.canAct(beast)) {
            charging = 0;
            beast.playSound(SoundEvents.RAVAGER_ROAR, 3f, beast.getVoicePitch());
            return;
        }
        if (beast.isShukaku() && drumCooldown <= 0 && distance > reach + 1 && distance < 20 && beast.hasLineOfSight(target)
                && CombatSystem.canAct(beast)) {
            drumming = 0;
            beast.setAction(TailedBeast.ACTION_DRUM);
            return;
        }
        if (attackCooldown <= 0 && distance <= reach && CombatSystem.canAttackNow(beast)) {
            AttackInput input = beast.getRandom().nextFloat() < 0.65f ? AttackInput.LIGHT : AttackInput.HEAVY;
            if (CombatSystem.requestAttack(beast, input)) {
                attackCooldown = 20 + beast.getRandom().nextInt(25);
            }
        }
    }

    /** Shukaku pounds its belly twice (a wind-up you can hear), then a ball of wind shoots from its mouth. */
    private void drum(LivingEntity target) {
        beast.getNavigation().stop();
        drumming++;
        if (drumming == 6 || drumming == 12) {
            beast.playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 3f, 0.5f);
        }
        if (drumming == DRUM_FIRE) {
            beast.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            JutsuCaster.npcCast(beast, AIR_BULLET, 1.5f);
            beast.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.5f);
        }
        if (drumming >= DRUM_TICKS) {
            drumming = -1;
            drumCooldown = 120 + beast.getRandom().nextInt(80);
            beast.clearAction(TailedBeast.ACTION_DRUM);
        }
    }

    /** Stands still, gathers chakra at its mouth, then fires. */
    private void chargeBall(LivingEntity target) {
        beast.getNavigation().stop();
        charging++;
        Vec3 mouth = beast.getEyePosition().add(beast.getViewVector(1f).scale(beast.getBbWidth() * 0.6));
        if (beast.level() instanceof ServerLevel level) {
            level.sendParticles(DARK, mouth.x, mouth.y, mouth.z, 6, 0.4 + charging * 0.02, 0.4 + charging * 0.02, 0.4 + charging * 0.02, 0);
        }
        if (charging >= BALL_CHARGE) {
            charging = -1;
            ballCooldown = 160 + beast.getRandom().nextInt(100);
            beast.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            JutsuCaster.npcCast(beast, BijuSystem.BEAST_BALL, 1f);
        }
    }
}

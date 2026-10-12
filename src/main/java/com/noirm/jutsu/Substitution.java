package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Substitution Jutsu: for a moment after casting, the next hit lands on a log instead and you
 * reappear behind the attacker, who is briefly thrown off.
 */
public final class Substitution {
    private static final Map<LivingEntity, Long> ARMED_UNTIL = new WeakHashMap<>();
    private static final int ATTACKER_STUN = 12;

    private Substitution() {}

    public static void arm(LivingEntity entity, int ticks) {
        ARMED_UNTIL.put(entity, entity.level().getGameTime() + ticks);
    }

    public static boolean isArmed(LivingEntity entity) {
        Long until = ARMED_UNTIL.get(entity);
        return until != null && entity.level().getGameTime() <= until;
    }

    /** If armed, dodges this hit by swapping with a log. Returns true if the hit is avoided. */
    public static boolean trySubstitute(LivingEntity target, Entity attacker) {
        if (attacker == null || attacker == target || !isArmed(target) || !(target.level() instanceof ServerLevel level)) {
            return false;
        }
        ARMED_UNTIL.remove(target);
        Vec3 from = target.position();
        Vec3 behind = Vec3.directionFromRotation(0, attacker.getYRot()).scale(-2.0);
        Vec3 destination = attacker.position().add(behind.x, 0, behind.z);
        if (!level.noCollision(target, target.getBoundingBox().move(destination.subtract(from)))) {
            destination = from.subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(2.5));
        }
        level.sendParticles(ParticleTypes.POOF, from.x, from.y + 1, from.z, 20, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LOG.defaultBlockState()),
                from.x, from.y + 1, from.z, 25, 0.3, 0.5, 0.3, 0.1);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
        target.teleportTo(destination.x, destination.y, destination.z);
        target.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, attacker.getEyePosition());
        level.sendParticles(ParticleTypes.POOF, destination.x, destination.y + 1, destination.z, 12, 0.3, 0.5, 0.3, 0.02);
        if (attacker instanceof LivingEntity living) {
            CombatSystem.stun(living, ATTACKER_STUN);
        }
        return true;
    }
}

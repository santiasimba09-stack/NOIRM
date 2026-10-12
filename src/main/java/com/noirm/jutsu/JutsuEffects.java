package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared pieces of jutsu effects: how a jutsu hits something, and particle helpers. */
public final class JutsuEffects {
    private JutsuEffects() {}

    /**
     * Damages {@code target} with a jutsu. {@code source} is the projectile (or the caster for
     * direct jutsu); the owner counts as the attacker, so guard, parry, dodge and substitution
     * all work against jutsu too.
     */
    public static void hit(Entity source, Entity target, float damage, float knockback, float posture, int fireSeconds, Vec3 direction) {
        if (!(target instanceof LivingEntity living) || damage <= 0 && knockback <= 0) {
            return;
        }
        Entity owner = source instanceof net.minecraft.world.entity.projectile.Projectile p && p.getOwner() != null ? p.getOwner() : source;
        DamageSource damageSource = source.damageSources().indirectMagic(source, owner);
        if (damage > 0) {
            living.invulnerableTime = 0;
            if (!living.hurt(damageSource, damage)) {
                return;
            }
        }
        if (posture > 0) {
            CombatSystem.addPostureDamage(living, posture);
        }
        if (fireSeconds > 0) {
            living.setSecondsOnFire(fireSeconds);
        }
        Vec3 flat = new Vec3(direction.x, 0, direction.z);
        if (knockback > 0 && flat.lengthSqr() > 1e-6) {
            flat = flat.normalize();
            living.knockback(knockback, -flat.x, -flat.z);
            living.hurtMarked = true;
        }
    }

    public static void particles(Entity at, ParticleOptions particle, int count, double spread, double speed) {
        if (at.level() instanceof ServerLevel level) {
            level.sendParticles(particle, at.getX(), at.getY() + at.getBbHeight() * 0.6, at.getZ(), count, spread, spread, spread, speed);
        }
    }

    public static void particlesAt(ServerLevel level, Vec3 pos, ParticleOptions particle, int count, double spread, double speed) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }
}

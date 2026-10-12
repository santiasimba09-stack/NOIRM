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

    /**
     * Applies effects like {@code "slowness:1,weakness:0"} (vanilla effect ids and levels, 0 =
     * level I) for {@code ticks}. Unknown ids are ignored.
     */
    public static void applyEffects(LivingEntity target, String spec, int ticks) {
        if (spec == null || spec.isBlank() || ticks <= 0) {
            return;
        }
        for (String part : spec.split(",")) {
            String[] bits = part.trim().split(":");
            if (bits.length == 0 || bits[0].isBlank()) {
                continue;
            }
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(bits[0]);
            net.minecraft.world.effect.MobEffect effect = id == null ? null : net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect == null) {
                continue;
            }
            int level = 0;
            if (bits.length > 1) {
                try {
                    level = Integer.parseInt(bits[1].trim());
                } catch (NumberFormatException ignored) {
                    // keep level I
                }
            }
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, ticks, level));
        }
    }

    /** The particle of a block given by id ({@code "minecraft:oak_log"}), or {@code fallback}. */
    public static ParticleOptions blockParticle(String blockId, ParticleOptions fallback) {
        if (blockId == null || blockId.isBlank()) {
            return fallback;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(blockId);
        net.minecraft.world.level.block.Block block = id == null ? null : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            return fallback;
        }
        return new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, block.defaultBlockState());
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

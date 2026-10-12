package com.noirm.jutsu;

import com.noirm.clan.Nature;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A ball of chakra: fireball, water bullet… Flies straight (no gravity), drawn entirely with
 * particles in its nature's style, and bursts on impact.
 */
public class JutsuProjectile extends Projectile {
    private static final EntityDataAccessor<Byte> NATURE = SynchedEntityData.defineId(JutsuProjectile.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(JutsuProjectile.class, EntityDataSerializers.FLOAT);
    private static final int MAX_LIFE = 80;

    private float damage;
    private float knockback;
    private float posture;
    private float blastRadius;
    private int fireSeconds;
    private int life;

    public JutsuProjectile(EntityType<? extends JutsuProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** Sets everything the projectile does on impact. */
    public void configure(Nature nature, float size, float damage, float knockback, float posture, float blastRadius, int fireSeconds) {
        entityData.set(NATURE, (byte) (nature == null ? -1 : nature.ordinal()));
        entityData.set(SIZE, size);
        this.damage = damage;
        this.knockback = knockback;
        this.posture = posture;
        this.blastRadius = blastRadius;
        this.fireSeconds = fireSeconds;
    }

    public Nature nature() {
        byte index = entityData.get(NATURE);
        return index >= 0 && index < Nature.values().length ? Nature.values()[index] : null;
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public float damage() {
        return damage;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(NATURE, (byte) -1);
        entityData.define(SIZE, 0.5f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
            if (++life > MAX_LIFE || (nature() == Nature.FIRE && isInWater())) {
                burst();
                return;
            }
        } else {
            trail();
        }
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (blastRadius <= 0) {
            JutsuEffects.hit(this, target, damage, knockback, posture, fireSeconds, getDeltaMovement());
        }
        burst();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        burst();
    }

    /** Impact: blast everything nearby (if this jutsu explodes), then vanish in a puff. */
    private void burst() {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            if (blastRadius > 0) {
                AABB area = getBoundingBox().inflate(blastRadius);
                for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != getOwner() && e.isAlive() && e.distanceTo(this) <= blastRadius + e.getBbWidth())) {
                    Vec3 push = victim.position().subtract(position()).normalize();
                    float falloff = 1f - (float) Math.min(1, victim.distanceTo(this) / (blastRadius + 1));
                    JutsuEffects.hit(this, victim, damage * (0.5f + 0.5f * falloff), knockback, posture, fireSeconds, push);
                }
                serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1 + (int) blastRadius, blastRadius * 0.3, blastRadius * 0.3, blastRadius * 0.3, 0);
                serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            serverLevel.sendParticles(particle(nature()), getX(), getY(), getZ(), 20, size() * 0.6, size() * 0.6, size() * 0.6, 0.05);
        }
        discard();
    }

    /** Client side: fills a ball the projectile's size with its nature's particles. */
    private void trail() {
        Nature nature = nature();
        float radius = size() * 0.5f;
        int count = 4 + (int) (radius * radius * 40);
        ParticleOptions particle = particle(nature);
        for (int i = 0; i < count; i++) {
            // a random point inside the ball (uniform-ish: push toward the surface)
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * random.nextDouble() - 1);
            double r = radius * Math.cbrt(random.nextDouble());
            double ox = r * Math.sin(phi) * Math.cos(theta);
            double oy = r * Math.cos(phi);
            double oz = r * Math.sin(phi) * Math.sin(theta);
            level().addParticle(particle, getX() + ox, getY() + oy, getZ() + oz, 0, 0, 0);
        }
        if (nature == Nature.FIRE) {
            level().addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0, 0);
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX() - getDeltaMovement().x, getY(), getZ() - getDeltaMovement().z, 0, 0.02, 0);
            }
        }
    }

    static ParticleOptions particle(Nature nature) {
        if (nature == null) {
            return ParticleTypes.CLOUD;
        }
        return switch (nature) {
            case FIRE -> ParticleTypes.FLAME;
            case WATER -> ParticleTypes.SPLASH;
            case WIND -> ParticleTypes.CLOUD;
            case EARTH -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_MUD.defaultBlockState());
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
        };
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Life", life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life = MAX_LIFE; // a reloaded jutsu fizzles rather than keeping no damage values
    }
}

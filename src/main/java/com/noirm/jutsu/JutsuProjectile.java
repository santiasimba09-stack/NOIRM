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
    private static final EntityDataAccessor<Byte> LOOK = SynchedEntityData.defineId(JutsuProjectile.class, EntityDataSerializers.BYTE);

    /**
     * How a projectile is drawn, from the jutsu's {@code "look"} param: the plain nature orb, the
     * Great Fireball's bubbling sphere, a Phoenix Flower bullet with its tail, a fire dragon with a
     * head, a Flame Bomb's burning oil (which also sticks and keeps burning where it lands), a
     * lightning needle.
     */
    public enum Look {
        ORB, FIREBALL, BULLET, DRAGON, OIL, NEEDLE;

        public static Look of(String name) {
            for (Look look : values()) {
                if (look.name().equalsIgnoreCase(name)) {
                    return look;
                }
            }
            return ORB;
        }
    }
    private static final int MAX_LIFE = 80;
    private static final byte BEAST_CHAKRA = -2;
    private static final net.minecraft.core.particles.DustParticleOptions BEAST_DARK =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.2f, 0.02f, 0.3f), 2f);
    private static final net.minecraft.core.particles.DustParticleOptions BEAST_RED =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.9f, 0.1f, 0.1f), 1.5f);

    private float damage;
    private float knockback;
    private float posture;
    private float blastRadius;
    private int fireSeconds;
    private int life;
    /** Effects on whoever it hits ({@code "slowness:1"}), and for how long. */
    private String effect = "";
    private float homing;
    private LivingEntity homingTarget;
    private int effectTicks;
    /** How its last clash with another jutsu ended. */
    private JutsuClash.Outcome clash = JutsuClash.Outcome.NONE;
    /** Burning oil left where it lands (Flame Bomb): radius, ticks, damage per second. 0 = none. */
    private float lingerRadius;
    private int lingerTicks;
    private float lingerDamage;

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

    /** Steers toward {@code target} by {@code strength} each tick (chakra-guided flames, dragons). */
    public void setHoming(float strength, LivingEntity target) {
        this.homing = strength;
        this.homingTarget = target;
    }

    public void setLook(Look look) {
        entityData.set(LOOK, (byte) look.ordinal());
    }

    public Look look() {
        byte index = entityData.get(LOOK);
        return index >= 0 && index < Look.values().length ? Look.values()[index] : Look.ORB;
    }

    /** Leaves a patch of burning oil of {@code radius} for {@code ticks} where it lands, hurting {@code damagePerSecond}. */
    public void setLingering(float radius, int ticks, float damagePerSecond) {
        this.lingerRadius = radius;
        this.lingerTicks = ticks;
        this.lingerDamage = damagePerSecond;
    }

    public void setOnHitEffect(String effect, int ticks) {
        this.effect = effect == null ? "" : effect;
        this.effectTicks = ticks;
    }

    /** Marks this as tailed-beast chakra (the Tailed Beast Ball): dark, violent particles. */
    public void setBeastChakra() {
        entityData.set(NATURE, BEAST_CHAKRA);
    }

    public boolean isBeastChakra() {
        return entityData.get(NATURE) == BEAST_CHAKRA;
    }

    private ParticleOptions trailParticle() {
        if (isBeastChakra()) {
            return random.nextInt(3) == 0 ? BEAST_RED : BEAST_DARK;
        }
        return particle(nature());
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

    public float blastRadius() {
        return blastRadius;
    }

    /** What it brings to a clash with another jutsu: its damage (already scaled by power and charge). */
    public float clashPower() {
        return damage;
    }

    public JutsuClash.Outcome clashOutcome() {
        return clash;
    }

    /** Won a clash: flies on with {@code remaining} of its strength, a little smaller. */
    void clashWon(float remaining) {
        clash = JutsuClash.Outcome.WON;
        damage *= remaining;
        posture *= remaining;
        blastRadius *= (float) Math.sqrt(remaining);
        entityData.set(SIZE, Math.max(0.2f, size() * (float) Math.sqrt(remaining)));
    }

    /** Lost a clash: snuffed out (steam if water put out fire), hurting no one. */
    void clashLost(Nature by) {
        clash = JutsuClash.Outcome.LOST;
        if (level() instanceof ServerLevel serverLevel) {
            com.noirm.vfx.Vfx.hit(serverLevel, position(), palette(), size() > 0.6f);
            boolean doused = nature() == Nature.FIRE && by == Nature.WATER;
            serverLevel.playSound(null, getX(), getY(), getZ(), doused ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.GENERIC_EXTINGUISH_FIRE,
                    SoundSource.PLAYERS, 0.9f, doused ? 1f : 1.4f);
            if (doused) {
                com.noirm.vfx.Vfx.burst(serverLevel, com.noirm.vfx.Vfx.smoke(0xE8EEF2, 0.5f, 40), position(), 10, size() * 0.6, 0.04);
            }
        }
        discard();
    }

    /** Burst together with the jutsu it met (the explosion is drawn by {@link JutsuClash}). */
    void clashBurst() {
        clash = JutsuClash.Outcome.BURST;
        discard();
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide()) {
            JutsuClash.track(this);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!level().isClientSide()) {
            JutsuClash.untrack(this);
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(NATURE, (byte) -1);
        entityData.define(SIZE, 0.5f);
        entityData.define(LOOK, (byte) 0);
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
            // A big ball hits whatever it visibly touches, not just what its small hitbox does.
            float reach = size() * 0.5f;
            if (reach > 0.4f) {
                for (Entity other : level().getEntities(this, getBoundingBox().inflate(reach), this::canHitEntity)) {
                    onHit(new EntityHitResult(other));
                    return;
                }
            }
            // Meeting another side's jutsu mid-air: the elements clash (cheap: every few ticks, projectiles only).
            if (tickCount % JutsuClash.rules().checkInterval() == 0 && JutsuClash.check(this)) {
                return;
            }
            if ((nature() == Nature.LIGHTNING || nature() == Nature.STORM) && tickCount % 2 == 0) {
                com.noirm.vfx.Vfx.arcs(level(), this, 1, com.noirm.vfx.Vfx.LIGHTNING);
            }
            if (++life > MAX_LIFE || (nature() == Nature.FIRE && isInWater())) {
                burst();
                return;
            }
        } else {
            trail();
        }
        if (homing > 0 && homingTarget != null && homingTarget.isAlive() && !level().isClientSide()) {
            Vec3 want = homingTarget.position().add(0, homingTarget.getBbHeight() * 0.5, 0).subtract(position()).normalize();
            Vec3 now = getDeltaMovement();
            double speed = now.length();
            Vec3 turned = now.normalize().add(want.scale(homing)).normalize().scale(speed);
            setDeltaMovement(turned);
            hasImpulse = true;
        }
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
    }

    /** Never the caster, nor anyone on the caster's side (their clones, their crew). */
    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = getOwner();
        if (owner != null && (entity == owner || com.noirm.jutsu.JutsuEffects.isFriendly(owner, entity))) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (blastRadius <= 0) {
            JutsuEffects.hit(this, target, damage, knockback, posture, fireSeconds, getDeltaMovement());
            if (target instanceof LivingEntity living) {
                JutsuEffects.applyEffects(living, effect, effectTicks);
            }
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
                        e -> e != getOwner() && !(getOwner() != null && JutsuEffects.isFriendly(getOwner(), e)) && e.isAlive() && e.distanceTo(this) <= blastRadius + e.getBbWidth())) {
                    Vec3 push = victim.position().subtract(position()).normalize();
                    float falloff = 1f - (float) Math.min(1, victim.distanceTo(this) / (blastRadius + 1));
                    JutsuEffects.hit(this, victim, damage * (0.5f + 0.5f * falloff), knockback, posture, fireSeconds, push);
                    JutsuEffects.applyEffects(victim, effect, effectTicks);
                }
                if (nature() == Nature.FIRE && !isBeastChakra()) {
                    com.noirm.vfx.FireVfx.blast(serverLevel, position(), blastRadius);
                } else {
                    com.noirm.vfx.Vfx.explosion(serverLevel, position(), palette(), blastRadius);
                }
                if (nature() == Nature.LIGHTNING || nature() == Nature.STORM) {
                    com.noirm.vfx.LightningVfx.groundForks(serverLevel, position(), blastRadius * 1.3f, 6);
                    com.noirm.vfx.LightningVfx.contact(serverLevel, position(), 1f);
                }
                float destruction = Math.max(0.5f, damage / 8f);
                if (isBeastChakra()) {
                    com.noirm.world.Destruction.crater(serverLevel, position(), blastRadius * 0.7f, destruction * 1.5f);
                } else {
                    com.noirm.world.Destruction.natureImpact(serverLevel, position(), nature(), blastRadius, destruction);
                }
                serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f + blastRadius * 0.1f, 1.3f - Math.min(0.6f, blastRadius * 0.08f));
            } else {
                if (nature() == Nature.FIRE) {
                    com.noirm.vfx.FireVfx.blast(serverLevel, position(), Math.max(0.6f, size()));
                } else if (nature() == Nature.LIGHTNING || nature() == Nature.STORM) {
                    com.noirm.vfx.LightningVfx.contact(serverLevel, position(), 0.7f + size());
                    com.noirm.vfx.Vfx.hit(serverLevel, position(), palette(), false);
                } else {
                    com.noirm.vfx.Vfx.hit(serverLevel, position(), palette(), size() > 0.6f);
                }
                com.noirm.world.Destruction.shatterFragile(serverLevel, blockPosition(), 1);
            }
            if (lingerTicks > 0 && lingerRadius > 0) {
                // The oil sticks where it lands and keeps burning.
                Vec3 ground = com.noirm.jutsu.ElementalBehaviors.ground(serverLevel, position());
                LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
                JutsuAreas.add(new JutsuAreas.Area(serverLevel, ground, null, owner, JutsuAreas.Mode.BLAZE, lingerRadius,
                        serverLevel.getGameTime() + lingerTicks, 10, lingerDamage * 0.5f, effect, 30, 0, ParticleTypes.FLAME));
                com.noirm.world.Destruction.scorch(serverLevel, ground, lingerRadius);
            }
            if (nature() != Nature.FIRE) {
                serverLevel.sendParticles(trailParticle(), getX(), getY(), getZ(), 12, size() * 0.6, size() * 0.6, size() * 0.6, 0.05);
            }
        }
        discard();
    }

    /** This projectile's colours (the Tailed Beast Ball is dark and red). */
    com.noirm.vfx.Vfx.Palette palette() {
        return isBeastChakra() ? new com.noirm.vfx.Vfx.Palette(0xFF6060, 0x3A0A40, 0x0A0008) : com.noirm.vfx.Vfx.palette(nature());
    }

    /** Client side: a glowing orb with a hot core and a smoky tail, plus a touch of its nature. */
    private void trail() {
        Look look = look();
        if (nature() == Nature.FIRE && !isBeastChakra()) {
            switch (look) {
                case BULLET -> com.noirm.vfx.FireVfx.bullet(level(), position(), getDeltaMovement(), size());
                case DRAGON -> com.noirm.vfx.FireVfx.dragon(level(), position(), getDeltaMovement(), size() * 0.75f, tickCount);
                case OIL -> com.noirm.vfx.FireVfx.oilGlob(level(), position(), getDeltaMovement(), size());
                default -> com.noirm.vfx.FireVfx.fireball(level(), position(), getDeltaMovement(), size() * 0.55f);
            }
            return;
        }
        if (look == Look.NEEDLE) {
            // A lightning needle: a thin white-hot streak and a few sparks.
            Vec3 motion = getDeltaMovement();
            for (int i = 0; i < 3; i++) {
                com.noirm.vfx.Vfx.one(level(), com.noirm.vfx.Vfx.glow(0xFFFFFF, 0x5FB4FF, 0.14f, 0.04f, 3), position().subtract(motion.scale(i * 0.3)), Vec3.ZERO);
            }
            if (random.nextInt(2) == 0) {
                com.noirm.vfx.Vfx.one(level(), com.noirm.vfx.Vfx.spark(0x9FD4FF, 0.1f, 5), position(), motion.scale(-0.1));
            }
            return;
        }
        com.noirm.vfx.Vfx.orb(level(), position(), getDeltaMovement(), palette(), size());
        if (random.nextInt(2) == 0) {
            float r = size() * 0.5f;
            level().addParticle(trailParticle(), getX() + random.nextGaussian() * r, getY() + random.nextGaussian() * r,
                    getZ() + random.nextGaussian() * r, 0, 0, 0);
        }
        if (nature() == Nature.FIRE && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    public static ParticleOptions particle(Nature nature) {
        if (nature == null) {
            return ParticleTypes.CLOUD;
        }
        return switch (nature) {
            case FIRE -> ParticleTypes.FLAME;
            case WATER -> ParticleTypes.SPLASH;
            case WIND -> ParticleTypes.CLOUD;
            case EARTH -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_MUD.defaultBlockState());
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case WOOD -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LOG.defaultBlockState());
            case ICE -> ParticleTypes.SNOWFLAKE;
            case LAVA -> ParticleTypes.LAVA;
            case STORM -> new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.45f, 0.8f, 1f), 1.4f);
            case BOIL -> ParticleTypes.POOF;
            case EXPLOSION -> ParticleTypes.SMALL_FLAME;
            case MAGNET -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACK_CONCRETE_POWDER.defaultBlockState());
            case SCORCH -> new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.55f, 0.15f), 1.6f);
            case DUST -> ParticleTypes.END_ROD;
            case YIN -> ParticleTypes.SQUID_INK;
            case YANG -> ParticleTypes.HAPPY_VILLAGER;
            case YIN_YANG -> ParticleTypes.END_ROD;
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

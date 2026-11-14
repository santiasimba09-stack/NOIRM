package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.WoodVfx;
import com.noirm.world.Destruction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Wood Release: the Thousand-Arm Colossus (our own design). A towering figure of grey-green
 * wood rises out of the ground behind the caster: a broad trunk of a body, its two great hands
 * clasped in a seal, a hooded stump of a head with a crown of broken branches, and behind it a
 * halo of {@link #ARMS} arms fanned out in two rings. Then the halo's hands come down one after
 * another, a fist-forest crashing round the target, and it sinks back into the earth.
 *
 * <p>It's one entity drawn as a model (client/WoodReleaseRenderers), never blocks. The server
 * and client share the timeline and every fist's landing spot ({@link #fistTarget}, from a synced
 * seed), so what you see land is what hits.
 */
public class WoodColossus extends Entity {
    /** Arms in the halo (inner ring + outer ring). */
    public static final int ARMS = 30;
    public static final int INNER_ARMS = 12;
    /** Ticks to rise out of the ground, and to sink back. */
    public static final int RISE = 30;
    public static final int SINK = 30;
    /** Ticks between one fist and the next, and how long a fist's swing lasts (wind-up, slam, rest, back). */
    public static final int STRIKE_GAP = 5;
    public static final int STRIKE_LEN = 16;
    /** The tick within a swing when the fist lands. */
    public static final int STRIKE_LAND = 8;
    /** How tall it stands (blocks, to the top of the head). */
    public static final float HEIGHT = 17f;
    /** Where the halo's hub sits (height), and its two rings' radii. */
    public static final float HUB = 13.5f;
    public static final float INNER_RING = 6.5f;
    public static final float OUTER_RING = 10f;
    /** How far round the target the fists fall. */
    public static final float SPREAD = 6f;

    private static final EntityDataAccessor<Vector3f> FOCUS = SynchedEntityData.defineId(WoodColossus.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(WoodColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STRIKES = SynchedEntityData.defineId(WoodColossus.class, EntityDataSerializers.INT);

    private LivingEntity owner;
    private float damage;
    private float radius;
    private float posture;
    private int stun;
    private float power = 1f;
    private int landed;

    public WoodColossus(EntityType<? extends WoodColossus> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void launch(LivingEntity owner, Vec3 focus, int strikes, float damage, float radius, float posture, int stun, float power) {
        this.owner = owner;
        this.damage = damage;
        this.radius = radius;
        this.posture = posture;
        this.stun = stun;
        this.power = power;
        entityData.set(FOCUS, new Vector3f((float) focus.x, (float) focus.y, (float) focus.z));
        entityData.set(SEED, level().random.nextInt());
        entityData.set(STRIKES, Mth.clamp(strikes, 1, ARMS));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FOCUS, new Vector3f());
        entityData.define(SEED, 0);
        entityData.define(STRIKES, 12);
    }

    public Vec3 focus() {
        Vector3f v = entityData.get(FOCUS);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public int strikes() {
        return entityData.get(STRIKES);
    }

    /** The whole show, in ticks. */
    public int lifetime() {
        return RISE + (strikes() - 1) * STRIKE_GAP + STRIKE_LEN + SINK;
    }

    // ---------------------------------------------------------------- shared geometry

    /** Where it faces (flat). */
    public Vec3 forward() {
        return Vec3.directionFromRotation(0, getYRot());
    }

    /** Its right, seen from behind. */
    public Vec3 right() {
        Vec3 f = forward();
        return new Vec3(-f.z, 0, f.x);
    }

    /** How far below ground it still is at {@code age} (0 when fully risen). */
    public float sunk(float age) {
        float rise = Mth.clamp(age / RISE, 0f, 1f);
        float sink = Mth.clamp((age - (lifetime() - SINK)) / SINK, 0f, 1f);
        float up = 1 - (1 - rise) * (1 - rise) * (1 - rise);
        return (HEIGHT + OUTER_RING) * (1 - up + sink * sink);
    }

    /** The halo's hub (behind the head), world space, at {@code age}. */
    public Vec3 hub(float age) {
        return position().add(0, HUB - sunk(age), 0).subtract(forward().scale(2.2));
    }

    /** Arm {@code arm}'s direction out from the hub: a fan round the top, open at the bottom. */
    public Vec3 armDirection(int arm) {
        boolean inner = arm < INNER_ARMS;
        int n = inner ? INNER_ARMS : ARMS - INNER_ARMS;
        int k = inner ? arm : arm - INNER_ARMS;
        // From 35° below the horizontal on one side, over the top, to 35° below on the other.
        double from = Math.toRadians(-35 + (inner ? 0 : 6));
        double to = Math.toRadians(215 - (inner ? 0 : 6));
        double a = from + (to - from) * (k + 0.5) / n;
        Vec3 up = new Vec3(0, 1, 0);
        // Leaning a little backward, so the rings fan out like a halo.
        return right().scale(Math.cos(a)).add(up.scale(Math.sin(a))).add(forward().scale(-0.18)).normalize();
    }

    /** Where arm {@code arm} joins its ring. */
    public Vec3 armRoot(int arm, float age) {
        float ring = arm < INNER_ARMS ? INNER_RING * 0.55f : OUTER_RING * 0.6f;
        return hub(age).add(armDirection(arm).scale(ring));
    }

    /** An idle arm's open hand, swaying slowly. */
    public Vec3 idleHand(int arm, float age) {
        float ring = arm < INNER_ARMS ? INNER_RING : OUTER_RING;
        double sway = Math.sin(age * 0.06 + arm * 1.7) * 0.35;
        return hub(age).add(armDirection(arm).scale(ring)).add(forward().scale(0.6 + sway)).add(0, sway * 0.5, 0);
    }

    /** Which halo arm throws fist {@code strike} (each a different arm). */
    public static int strikeArm(int strike) {
        return (strike * 11 + 3) % ARMS;
    }

    public static int strikeStart(int strike) {
        return RISE + strike * STRIKE_GAP;
    }

    /** Where fist {@code strike} lands: the first on the target itself, the rest scattered round it. */
    public Vec3 fistTarget(int strike) {
        Vec3 focus = focus();
        if (strike == 0) {
            return focus;
        }
        int seed = entityData.get(SEED);
        double a = rand(seed, strike, 1) * Math.PI * 2;
        double d = SPREAD * Math.sqrt(0.15 + 0.85 * rand(seed, strike, 2));
        return focus.add(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    private static double rand(int seed, int a, int b) {
        long n = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL;
        n ^= n >>> 31;
        n *= 0xD6E8FEB86659FD93L;
        n ^= n >>> 32;
        return (n & 0xFFFFFF) / (double) 0x1000000;
    }

    /**
     * Where the hand of the arm throwing {@code strike} is at {@code age}, or null when that fist
     * isn't swinging: it draws back and up, comes down hard on its spot, rests a moment, returns.
     */
    public Vec3 strikeHand(int strike, float age) {
        float t = age - strikeStart(strike);
        if (t < 0 || t > STRIKE_LEN) {
            return null;
        }
        int arm = strikeArm(strike);
        Vec3 rest = idleHand(arm, age);
        Vec3 raised = rest.add(armDirection(arm).scale(3)).add(0, 4, 0).subtract(forward().scale(1.5));
        Vec3 land = fistTarget(strike).add(0, 1.1, 0);
        if (t < STRIKE_LAND - 2) {
            return rest.lerp(raised, ease(t / (STRIKE_LAND - 2)));
        }
        if (t < STRIKE_LAND) {
            float f = (t - (STRIKE_LAND - 2)) / 2f;
            return raised.lerp(land, f * f); // accelerates into the ground
        }
        if (t < STRIKE_LAND + 3) {
            return land;
        }
        return land.lerp(rest, ease((t - STRIKE_LAND - 3) / (STRIKE_LEN - STRIKE_LAND - 3)));
    }

    private static double ease(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        int age = tickCount;
        if (owner == null || !owner.isAlive() || age > lifetime()) {
            discard();
            return;
        }
        Vec3 base = position();
        if (age == 1) {
            level.playSound(null, base.x, base.y, base.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 3f, 0.5f);
            level.playSound(null, base.x, base.y, base.z, com.noirm.sound.NoirmSounds.WOOD_BURST.get(), SoundSource.PLAYERS, 2f, 0.63f);
        }
        if (age < RISE || age > lifetime() - SINK) {
            // The earth heaves round it as it comes up (and as it goes down).
            if (age % 3 == 0) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double d = 3 + level.random.nextDouble() * 4;
                WoodVfx.emerge(level, base.add(Math.cos(a) * d, 0, Math.sin(a) * d), 2.6f);
            }
            if (age % 6 == 0) {
                Vfx.quake(level, base, 0.7f, 40);
            }
        }
        for (int s = landed; s < strikes(); s++) {
            if (age == strikeStart(s) + STRIKE_LAND) {
                land(level, s);
                landed = s + 1;
            } else if (age == strikeStart(s) + 1) {
                Vec3 hand = idleHand(strikeArm(s), age);
                level.playSound(null, hand.x, hand.y, hand.z, com.noirm.sound.NoirmSounds.SWING_HEAVY.get(), SoundSource.PLAYERS, 2f, 0.63f);
            }
        }
    }

    /** A fist comes down: everyone under it is crushed into the ground. */
    private void land(ServerLevel level, int strike) {
        Vec3 at = fistTarget(strike);
        level.playSound(null, at.x, at.y, at.z, com.noirm.sound.NoirmSounds.BIG_IMPACT.get(), SoundSource.PLAYERS, 1.6f, 0.71f);
        level.playSound(null, at.x, at.y, at.z, com.noirm.sound.NoirmSounds.WOOD_CREAK.get(), SoundSource.PLAYERS, 2f, 0.71f);
        WoodVfx.emerge(level, at, radius);
        Vfx.shockwave(level, at.add(0, 0.1, 0), new Vfx.Palette(0xE8E4D0, WoodVfx.DUST, WoodVfx.DUST_DARK, 0xC8C0A8), radius * 1.8f);
        Vfx.quake(level, at, 1.1f, 36);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 3, radius),
                e -> JutsuEffects.isTarget(owner, e) && e.isAlive() && !e.isSpectator()
                        && Math.hypot(e.getX() - at.x, e.getZ() - at.z) <= radius + e.getBbWidth() / 2)) {
            JutsuEffects.hit(owner, victim, damage * power, 0, posture * power, 0, Vec3.ZERO);
            victim.setDeltaMovement(victim.getDeltaMovement().x * 0.2, -0.6, victim.getDeltaMovement().z * 0.2);
            victim.hurtMarked = true;
            CombatSystem.stun(victim, stun);
            WoodDragon.hitStop(victim, 4);
        }
        Destruction.crater(level, at, Math.min(2.2f, radius * 0.6f), power * 1.2f);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(OUTER_RING + 6, HEIGHT + OUTER_RING, OUTER_RING + 6).expandTowards(focus().subtract(position()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}

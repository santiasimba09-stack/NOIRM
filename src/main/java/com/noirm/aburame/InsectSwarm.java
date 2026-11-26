package com.noirm.aburame;

import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.sound.NoirmSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * A swarm: one entity, many insects (docs/ABURAME.md, 2.5). It has a <b>count</b> instead of health:
 * every point of damage kills insects ({@link Harm}), fire six times as many as a blade. It flies,
 * clings and eats chakra, walls, domes, spheres, scouts, jams, and comes home with the survivors. The
 * client draws it as hundreds of voxel beetles over a dust of particles ({@code InsectSwarmRenderer}),
 * so there is only ever one entity per swarm, never one per insect.
 */
public class InsectSwarm extends Entity {
    private static final EntityDataAccessor<Integer> COUNT = SynchedEntityData.defineId(InsectSwarm.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> SPECIES = SynchedEntityData.defineId(InsectSwarm.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(InsectSwarm.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(InsectSwarm.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(InsectSwarm.class, EntityDataSerializers.INT);
    /** WALL: the wall's facing (degrees). SEA, JAM, DOME, POISON: their radius. */
    private static final EntityDataAccessor<Float> SHAPE = SynchedEntityData.defineId(InsectSwarm.class, EntityDataSerializers.FLOAT);

    /** The local player's entity id, set by the client (so you can't target your own swarm by mistake). */
    public static volatile int localPlayerId = Integer.MIN_VALUE;

    /** Wall size (blocks). */
    public static final float WALL_WIDTH = 6f, WALL_HEIGHT = 4f;
    /** Dome radius (blocks); an explosion this big breaks it. */
    public static final float DOME_RADIUS = 2.5f, DOME_BREAK = 12f;
    /** Sphere: lasts this long, then clings; breaking this share of it frees the target. */
    public static final int SPHERE_TICKS = 120;
    public static final float SPHERE_BREAK = 0.3f;
    /** Scouts watch for 3 minutes; a parked cloud waits a minute. */
    public static final int SCOUT_TICKS = 20 * 180, CLOUD_TICKS = 20 * 60;
    /** Insects eaten per second by each insect eater within 3 blocks. */
    public static final int EATER_PER_SECOND = 40;
    /** Insects per chakra a jutsu cost needed to eat it, and the insects it gives. */
    public static final int DEVOUR_PER_CHAKRA = 100, DEVOUR_GAIN = 2;

    private UUID ownerId;
    /** The owner as created (fake players and others not listed in the level are still found this way). */
    private LivingEntity ownerRef;
    private LivingEntity targetRef;
    private UUID targetId;
    private Vec3 park;
    private int life;
    private int maxLife = -1;
    private float eaten;
    private float toDigest;
    private float speedMul = 1f;
    private float drainMul = 1f;
    private int scatterTicks;
    private int sphereStart;
    private int sphereLost;
    private SwarmMode arrival = SwarmMode.CLING;
    private boolean settled;
    private boolean reported;
    private int mapRow = -1;
    private net.minecraft.world.item.ItemStack mapStack = net.minecraft.world.item.ItemStack.EMPTY;
    private Vec3 anchor;

    public InsectSwarm(EntityType<? extends InsectSwarm> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(COUNT, 0);
        entityData.define(SPECIES, (byte) 0);
        entityData.define(MODE, (byte) 0);
        entityData.define(TARGET, -1);
        entityData.define(OWNER, -1);
        entityData.define(SHAPE, 0f);
    }

    // ================================================================ set-up

    /** A new swarm of {@code count} {@code species} insects for {@code owner}, at {@code at}. Not yet in the world. */
    public static InsectSwarm create(ServerLevel level, LivingEntity owner, Species species, int count, Vec3 at, SwarmMode mode) {
        InsectSwarm swarm = new InsectSwarm(AburameRegistry.INSECT_SWARM.get(), level);
        swarm.setPos(at.x, at.y, at.z);
        swarm.ownerId = owner.getUUID();
        swarm.ownerRef = owner;
        swarm.entityData.set(OWNER, owner.getId());
        swarm.entityData.set(SPECIES, (byte) species.ordinal());
        swarm.entityData.set(COUNT, count);
        swarm.setMode(mode);
        swarm.refreshDimensions();
        return swarm;
    }

    public void setTarget(LivingEntity target, SwarmMode onArrival) {
        targetRef = target;
        targetId = target == null ? null : target.getUUID();
        entityData.set(TARGET, target == null ? -1 : target.getId());
        arrival = onArrival;
    }

    public void setPark(Vec3 at) {
        park = at;
    }

    public void setLife(int ticks) {
        maxLife = ticks;
        life = 0;
    }

    public void setSpeedMul(float mul) {
        speedMul = mul;
    }

    public void setDrainMul(float mul) {
        drainMul = mul;
    }

    public void setShape(float value) {
        entityData.set(SHAPE, value);
    }

    public float shape() {
        return entityData.get(SHAPE);
    }

    public void setMode(SwarmMode mode) {
        entityData.set(MODE, (byte) mode.ordinal());
        if (mode == SwarmMode.SPHERE) {
            sphereStart = count();
            sphereLost = 0;
            life = 0;
            maxLife = SPHERE_TICKS;
        }
        refreshDimensions();
    }

    // ================================================================ reading

    public int count() {
        return entityData.get(COUNT);
    }

    public void setCount(int n) {
        entityData.set(COUNT, Math.max(0, n));
        if (Math.abs(ColonyGrowth.radius(n) * 2 - getBbWidth()) > 0.15f) {
            refreshDimensions();
        }
    }

    public Species species() {
        return Species.of(entityData.get(SPECIES));
    }

    public SwarmMode mode() {
        return SwarmMode.of(entityData.get(MODE));
    }

    public int targetEntityId() {
        return entityData.get(TARGET);
    }

    public int ownerEntityId() {
        return entityData.get(OWNER);
    }

    public UUID ownerId() {
        return ownerId;
    }

    public float radius() {
        return switch (mode()) {
            case SEA, JAM, POISON, JAR -> shape();
            case DOME -> DOME_RADIUS;
            default -> ColonyGrowth.radius(count());
        };
    }

    public float eaten() {
        return eaten;
    }

    public float appetiteLeft() {
        return Math.max(0f, ColonyGrowth.appetite(count()) - eaten);
    }

    /** The owner, if loaded in this level. */
    public LivingEntity owner() {
        if (ownerRef != null && ownerRef.isAlive() && !ownerRef.isRemoved() && ownerRef.level() == level()) {
            return ownerRef;
        }
        if (ownerId == null || !(level() instanceof ServerLevel level)) {
            Entity e = level().getEntity(ownerEntityId());
            return e instanceof LivingEntity l ? l : null;
        }
        Entity e = level.getEntity(ownerId);
        return e instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    public LivingEntity target() {
        if (targetRef != null && targetId != null && targetRef.getUUID().equals(targetId) && targetRef.level() == level()) {
            return targetRef.isAlive() && !targetRef.isRemoved() ? targetRef : null;
        }
        if (targetId == null || !(level() instanceof ServerLevel level)) {
            return null;
        }
        Entity e = level.getEntity(targetId);
        return e instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return switch (mode()) {
            case WALL -> EntityDimensions.scalable(WALL_WIDTH, WALL_HEIGHT);
            case DOME -> EntityDimensions.scalable(DOME_RADIUS * 2, DOME_RADIUS * 2);
            case SEA, JAM, POISON, JAR, TETHER, SCOUT -> EntityDimensions.scalable(1.5f, 1.5f);
            default -> {
                float d = Math.min(7f, ColonyGrowth.radius(count()) * 2f);
                yield EntityDimensions.scalable(d, d);
            }
        };
    }

    @Override
    public boolean isPickable() {
        if (level().isClientSide() && ownerEntityId() == localPlayerId) {
            return false; // your own swarm never gets in the way of your strikes
        }
        return switch (mode()) {
            case TRAVEL, CLOUD, CLING, SPHERE, WALL, DOME, TORNADO -> count() > 0;
            default -> false;
        };
    }

    @Override
    public boolean isAttackable() {
        return isPickable();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 96 + radius() * 8;
        return distance < d * d;
    }

    // ================================================================ taking damage

    /** Damage kills insects; how many depends on what hit it (2.5). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isRemoved() || amount <= 0) {
            return false;
        }
        LivingEntity owner = owner();
        Entity attacker = source.getEntity();
        if (attacker != null && owner != null && (attacker == owner || attacker instanceof LivingEntity l && JutsuEffects.isFriendly(owner, l))) {
            return false; // your own side's blows pass through
        }
        Harm harm = classify(source);
        if (harm == Harm.WIND) {
            scatter();
        }
        if (harm == Harm.EXPLOSION && mode() == SwarmMode.DOME && amount >= DOME_BREAK) {
            harm(Harm.EXPLOSION, amount);
            setMode(SwarmMode.RETURN); // the dome breaks
            return true;
        }
        int killed = harm(harm, amount);
        if (mode() == SwarmMode.SPHERE && attacker != null && attacker == target()) {
            sphereLost += killed;
            if (sphereLost >= sphereStart * SPHERE_BREAK) {
                setMode(SwarmMode.CLING); // broken open: what's left still clings
            }
        }
        return killed > 0;
    }

    /** What kind of harm a damage source is. */
    public static Harm classify(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return Harm.FIRE;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return Harm.EXPLOSION;
        }
        if (source.is(DamageTypeTags.IS_LIGHTNING)) {
            return Harm.LIGHTNING;
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return Harm.COLD;
        }
        if (source.getDirectEntity() instanceof JutsuProjectile p) {
            return Swarms.harmOf(p.nature(), p.blastRadius() > 0);
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return Harm.JUTSU;
        }
        return Harm.MELEE;
    }

    /** Kills insects for {@code damage} points of {@code harm}; returns how many died. */
    public int harm(Harm harm, float damage) {
        LivingEntity owner = owner();
        int killed = Math.min(count(), Math.round(damage * Colony.killRate(owner, harm)));
        if (killed <= 0) {
            return 0;
        }
        setCount(count() - killed);
        if (owner != null) {
            Colony.killed(owner, killed, harm);
        } else {
            orphanKilled += killed;
        }
        if (level() instanceof ServerLevel level) {
            SwarmFx.deaths(level, position().add(0, getBbHeight() / 2, 0), killed, harm == Harm.FIRE);
        }
        if (count() <= 0) {
            settled = true;
            discard();
        }
        return killed;
    }

    private int orphanKilled;

    /** Wind: the swarm loses its hold and drops into a loose cloud for 2 s (spheres, walls, domes break). */
    public void scatter() {
        scatterTicks = 40;
        SwarmMode m = mode();
        if (m == SwarmMode.CLING || m == SwarmMode.SPHERE || m == SwarmMode.TORNADO || m == SwarmMode.WALL || m == SwarmMode.DOME) {
            park = position();
            setMode(SwarmMode.CLOUD);
            life = 0;
            maxLife = CLOUD_TICKS;
        }
    }

    // ================================================================ the life of a swarm

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            clientDust();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        LivingEntity owner = owner();
        if (owner == null) {
            settled = true; // the owner is gone (logged out, another world): the insects fly home on their own
            discard();
            return;
        }
        life++;
        if (scatterTicks > 0) {
            scatterTicks--;
        }
        double leash = mode() == SwarmMode.SCOUT ? ColonyGrowth.SCOUT_LEASH : ColonyGrowth.LEASH;
        if (mode() != SwarmMode.RETURN && distanceToSqr(owner) > leash * leash) {
            setMode(SwarmMode.RETURN);
        }
        if (maxLife > 0 && life > maxLife) {
            onTimeUp(owner);
        }
        Vec3 old = position();
        switch (mode()) {
            case TRAVEL -> travel(owner);
            case CLING, SPHERE, TORNADO -> cling(level, owner);
            case CLOUD -> cloud(level, owner);
            case WALL -> SwarmShapes.wall(this, level, owner);
            case DOME -> SwarmShapes.dome(this, level, owner);
            case SCOUT -> SwarmShapes.scout(this, level, owner);
            case JAM -> SwarmShapes.jam(this, level, owner);
            case SEA -> SwarmShapes.sea(this, level, owner);
            case POISON -> SwarmShapes.poisonCloud(this, level, owner);
            case JAR -> SwarmShapes.jarOfPoison(this, level, owner);
            case TETHER -> {
                if (life > 10) {
                    returnHome(owner);
                }
            }
            case RETURN -> fly(owner.position().add(0, owner.getBbHeight() * 0.6, 0), ColonyGrowth.RECALL_SPEED, () -> returnHome(owner));
        }
        if (isRemoved()) {
            return;
        }
        setDeltaMovement(position().subtract(old));
        if (tickCount % 10 == 0) {
            environment(level, owner);
        }
        Swarms.devourProjectiles(this, level, owner);
        if (tickCount % 20 == 0) {
            insectEaters(level);
            hum(level);
        }
    }

    /** The dark dust around the beetles: denser for bigger swarms (client side, a few motes a tick). */
    private void clientDust() {
        int n = Math.min(18, 2 + count() / 700);
        SwarmMode m = mode();
        float r = radius();
        net.minecraft.core.particles.ParticleOptions dust = species() == Species.RINKAICHU ? SwarmFx.PURPLE : SwarmFx.DUST;
        if (m == SwarmMode.SEA || m == SwarmMode.JAM || m == SwarmMode.JAR) {
            n = m == SwarmMode.SEA ? 24 : 4;
        }
        Vec3 c = center();
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = r * Math.sqrt(random.nextDouble());
            double y = m == SwarmMode.SEA ? random.nextDouble() * 2.5 - getBbHeight() / 2 : (random.nextDouble() - 0.5) * r * 1.4;
            level().addParticle(dust, c.x + Math.cos(a) * d, c.y + y, c.z + Math.sin(a) * d,
                    (random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.5) * 0.05);
        }
    }

    private void onTimeUp(LivingEntity owner) {
        switch (mode()) {
            case SPHERE -> setMode(SwarmMode.CLING);
            case TORNADO -> {
                LivingEntity t = target();
                if (t != null) {
                    t.setDeltaMovement(t.getDeltaMovement().x, -0.6, t.getDeltaMovement().z); // the drop
                    t.hurtMarked = true;
                }
                setMode(SwarmMode.CLING);
            }
            case SEA -> {
                // The Black Sea's cost: 60% of the flood dies; the rest comes home.
                int dead = Math.round(count() * 0.6f);
                setCount(count() - dead);
                Colony.killed(owner, dead, Harm.JUTSU);
                setMode(SwarmMode.RETURN);
            }
            case JAR -> SwarmShapes.jarBurst(this, (ServerLevel) level(), owner);
            default -> setMode(SwarmMode.RETURN);
        }
        maxLife = mode() == SwarmMode.CLING ? -1 : maxLife;
        life = 0;
    }

    /** Flies toward {@code to} at {@code speed} blocks a second; runs {@code arrived} when it gets there. */
    boolean fly(Vec3 to, float speed, Runnable arrived) {
        Vec3 here = position().add(0, getBbHeight() / 2, 0);
        Vec3 delta = to.subtract(here);
        double step = speed * speedMul * (level().isRaining() && level().canSeeSky(blockPosition()) ? ColonyGrowth.RAIN_SLOW : 1f) / 20.0;
        LivingEntity o = owner();
        if (o != null && Colony.of(o) != null && Colony.of(o).restless() && mode() == SwarmMode.RETURN) {
            step *= 0.6; // a starving colony comes back slower
        }
        if (delta.length() <= Math.max(step, 0.6)) {
            moveCenter(to);
            if (arrived != null) {
                arrived.run();
            }
            return true;
        }
        // A living swarm weaves a little as it flies (a stream, not a bullet).
        Vec3 dir = delta.normalize();
        Vec3 wobble = new Vec3(Mth.sin(tickCount * 0.31f + getId()), Mth.sin(tickCount * 0.23f) * 0.6, Mth.cos(tickCount * 0.27f + getId())).scale(0.08);
        moveCenter(here.add(dir.scale(step)).add(wobble));
        return false;
    }

    void moveCenter(Vec3 center) {
        setPos(center.x, center.y - getBbHeight() / 2, center.z);
    }

    public Vec3 center() {
        return position().add(0, getBbHeight() / 2, 0);
    }

    private void travel(LivingEntity owner) {
        LivingEntity target = target();
        if (target != null) {
            if (target.isInWater() && !Colony.of(owner).hasStrain(WildKind.WATER_STRIDER) && target.isEyeInFluid(FluidTags.WATER)) {
                park = target.position().add(0, target.getBbHeight() + 1, 0); // it won't follow underwater
                setMode(SwarmMode.CLOUD);
                maxLife = CLOUD_TICKS;
                life = 0;
                return;
            }
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0);
            fly(aim, baseSpeed(), () -> arrive(target, owner));
        } else if (park != null) {
            fly(park, baseSpeed(), () -> {
                setMode(arrival == SwarmMode.SCOUT ? SwarmMode.SCOUT : arrival == SwarmMode.JAM ? SwarmMode.JAM : SwarmMode.CLOUD);
                life = 0;
                maxLife = mode() == SwarmMode.SCOUT ? SCOUT_TICKS : mode() == SwarmMode.JAM ? maxLife : CLOUD_TICKS;
            });
        } else {
            setMode(SwarmMode.RETURN);
        }
    }

    /** Blocks a second this swarm flies when sent (12; scouts 16; ×1.5 out of a jar). */
    float baseSpeed() {
        return arrival == SwarmMode.SCOUT ? 16f : 12f;
    }

    private void arrive(LivingEntity target, LivingEntity owner) {
        SwarmMode next = arrival == null ? SwarmMode.CLING : arrival;
        if (next == SwarmMode.SCOUT || next == SwarmMode.JAM) {
            next = SwarmMode.CLING;
        }
        setMode(next);
        anchor = target.position();
        if (next == SwarmMode.CLING && colonyHas(owner, WildKind.MANTIS)) {
            JutsuEffects.hit(owner, target, 1f, 0f, 0f, 0, Vec3.ZERO); // the Mantis strain: they bite on arrival
        }
        if (species() == Species.KIDAICHU) {
            BugBite.start(owner, target);
            settled = true;
            Colony.killed(owner, count(), Harm.JUTSU); // the giants stay inside the victim
            discard();
        }
        if (species() == Species.RINKAICHU) {
            RotTouch.infect(target, 2);
        }
    }

    static boolean colonyHas(LivingEntity owner, WildKind strain) {
        ColonyData c = Colony.of(owner);
        return c != null && c.hasStrain(strain);
    }

    // ================================================================ clinging and draining

    private void cling(ServerLevel level, LivingEntity owner) {
        LivingEntity target = target();
        if (target == null) {
            park = center();
            setMode(SwarmMode.CLOUD); // the target died or vanished: hang there
            maxLife = CLOUD_TICKS;
            life = 0;
            return;
        }
        if (target.isEyeInFluid(FluidTags.WATER)) {
            park = target.position().add(0, target.getBbHeight() + 1.2, 0);
            setMode(SwarmMode.CLOUD); // diving shakes them off
            maxLife = CLOUD_TICKS;
            life = 0;
            return;
        }
        if (com.noirm.dojutsu.MangekyoAbilities.isPhased(target)) {
            park = center();
            setMode(SwarmMode.CLOUD); // intangible: a Kamui user simply walks out (Tobi, ch. 395)
            maxLife = CLOUD_TICKS;
            life = 0;
            return;
        }
        SwarmMode m = mode();
        if (m == SwarmMode.SPHERE) {
            if (anchor == null) {
                anchor = target.position();
            }
            if (target.position().distanceToSqr(anchor) > 9) {
                setMode(SwarmMode.CLING); // teleported out
            } else {
                target.teleportTo(anchor.x, target.getY(), anchor.z); // rooted inside the ball
                target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
                target.hurtMarked = true;
                if (tickCount % 20 == 0) {
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
                }
            }
        } else if (m == SwarmMode.TORNADO) {
            if (anchor == null) {
                anchor = target.position();
            }
            double lift = Math.min(3.0, life / 20.0 * 1.5);
            target.teleportTo(anchor.x + Mth.sin(life * 0.5f) * 0.3, anchor.y + lift, anchor.z + Mth.cos(life * 0.5f) * 0.3);
            target.setDeltaMovement(Vec3.ZERO);
            target.fallDistance = 0;
            target.hurtMarked = true;
            SwarmShapes.tornadoPull(this, level, owner, target);
        }
        moveCenter(target.position().add(0, target.getBbHeight() * 0.55, 0));
        if (tickCount % 20 == 0) {
            drainSecond(level, owner, target);
        }
    }

    /** One second of eating (2.5). */
    public void drainSecond(ServerLevel level, LivingEntity owner, LivingEntity target) {
        ColonyData colony = Colony.of(owner);
        float vigour = colony == null ? ColonyGrowth.VIGOUR_NORMAL : colony.vigour();
        float mul = drainMul * ColonyGrowth.vigourDrain(vigour) * (mode() == SwarmMode.SPHERE ? 3f : mode() == SwarmMode.TORNADO ? 2f : 1f);
        float rate = ColonyGrowth.DRAIN_PER_1K * count() / 1000f * mul;
        if (mode() == SwarmMode.TORNADO) {
            JutsuEffects.hit(owner, target, 2f, 0f, 0f, 0, Vec3.ZERO);
        }
        if (!Drain.hasChakra(target)) {
            // A wolf or a zombie has no chakra: they eat at it instead.
            target.hurt(level.damageSources().indirectMagic(this, owner), ColonyGrowth.MOB_DRAIN_PER_1K * count() / 1000f * mul);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, true));
            return;
        }
        if (overfed(owner, target)) {
            int dead = Math.max(1, Math.round(count() * ColonyGrowth.OVERFEED_LOSS));
            harm(Harm.JUTSU, dead / Colony.killRate(owner, Harm.JUTSU)); // they burst on too much chakra
            return;
        }
        float bite = Math.min(rate, appetiteLeft());
        if (bite <= 0f) {
            return; // full: it stops eating until recalled
        }
        float took = Drain.take(target, bite);
        if (took <= 0f) {
            return;
        }
        eaten += took;
        float mine = took * ColonyGrowth.DRAIN_TO_OWNER;
        Drain.give(owner, mine);
        toDigest += took - mine;
        if (colony != null) {
            colony.addDrained(took);
            colony.setBanked(colony.banked() + took - mine);
            if (Drain.chakra(target) <= 0.01f && (com.noirm.bounty.Legendary.isLegendary(target) || com.noirm.biju.BijuSystem.hasBeastChakra(target)
                    || target instanceof Player p && com.noirm.player.ShinobiCapability.get(p).map(d -> d.biju().tails() > 0).orElse(false))) {
                colony.setLegendDrained(true);
            }
            Colony.changed(owner);
        }
        if (count() < ColonyGrowth.NOTICED_AT || colonyHas(owner, WildKind.MOTH) && count() < 1000) {
            if (target instanceof Player && tickCount % 60 == 0) {
                SwarmFx.sound(level, target.position(), NoirmSounds.INSECT_ITCH, 0.25f, 1.2f); // only a faint itch
            }
        } else if (target instanceof SparringNinja ninja && random.nextFloat() < 0.35f) {
            shakeOff(ninja);
        }
    }

    /** An NPC that notices tries to get rid of them: a roll (−25%). */
    void shakeOff(SparringNinja ninja) {
        if (com.noirm.combat.CombatSystem.requestDodge(ninja, -1f, ninja.getRandom().nextBoolean() ? 1f : -1f)) {
            int lost = Math.round(count() * ColonyGrowth.SHAKE_OFF);
            setCount(count() - lost);
            LivingEntity owner = owner();
            if (owner != null) {
                Colony.killed(owner, lost, Harm.MELEE);
            }
        }
    }

    /** Beast cloaks, Sage Mode and huge reserves overfeed the insects (Shibi vs the Nine-Tails). */
    static boolean overfed(LivingEntity owner, LivingEntity target) {
        if (com.noirm.biju.BijuSystem.hasBeastChakra(target) || com.noirm.senjutsu.SageMode.isActive(target)) {
            return true;
        }
        float mine = Math.max(1f, Drain.maxChakra(owner));
        return Drain.maxChakra(target) > mine * ColonyGrowth.OVERFEED_RATIO;
    }

    // ================================================================ parked

    private void cloud(ServerLevel level, LivingEntity owner) {
        if (park == null) {
            park = center();
        }
        Vec3 drift = new Vec3(Mth.sin(tickCount * 0.05f) * 0.3, Mth.sin(tickCount * 0.07f) * 0.2, Mth.cos(tickCount * 0.05f) * 0.3);
        fly(park.add(drift), 6f, null);
        if (scatterTicks > 0 || tickCount % 10 != 0) {
            return;
        }
        // An ambush cloud: the first enemy that walks in gets covered.
        double r = radius() + 0.5;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.5),
                e -> e.isAlive() && e != owner && JutsuEffects.isTarget(owner, e) && e.distanceToSqr(center()) < r * r
                        && !(e instanceof net.minecraft.world.entity.animal.Animal) && !(e instanceof WildInsect))) {
            setTarget(e, SwarmMode.CLING);
            setMode(SwarmMode.CLING);
            maxLife = -1;
            return;
        }
    }

    // ================================================================ coming home

    /** Survivors rejoin the colony; what they ate is digested. A clinging swarm leaves a female behind. */
    public void returnHome(LivingEntity owner) {
        if (settled) {
            return;
        }
        settled = true;
        if (count() > 0 && Colony.has(owner)) {
            Colony.returnHome(owner, species(), count(), toDigest * (colonyHas(owner, WildKind.DUNG_BEETLE) ? 1f : 1f));
            if (level() instanceof ServerLevel level) {
                SwarmFx.pour(level, owner, count());
                SwarmFx.sound(level, owner.position(), NoirmSounds.INSECT_RECALL, 0.5f, 1f);
            }
        }
        discard();
    }

    /** Recall: fly home now. A clinging swarm leaves one female on what it held (a free mark). */
    public void recall() {
        LivingEntity owner = owner();
        LivingEntity target = target();
        if (owner != null && target != null && (mode() == SwarmMode.CLING || mode() == SwarmMode.SPHERE)
                && !Colony.marked(owner, target) && count() > 1) {
            Colony.mark(owner, target, Colony.MARK_TICKS);
            setCount(count() - 1);
            Colony.killed(owner, 1, Harm.JUTSU);
        }
        setMode(SwarmMode.RETURN);
    }

    /** Hold the swarm key on a block: park there. */
    public void parkAt(Vec3 at) {
        targetId = null;
        targetRef = null;
        entityData.set(TARGET, -1);
        park = at;
        arrival = SwarmMode.CLOUD;
        setMode(SwarmMode.TRAVEL);
        life = 0;
        maxLife = -1;
    }

    /** Hold the swarm key on a creature: send it there. */
    public void sendAt(LivingEntity target) {
        setTarget(target, SwarmMode.CLING);
        setMode(SwarmMode.TRAVEL);
        life = 0;
        maxLife = -1;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && !settled) {
            settled = true;
            LivingEntity owner = owner();
            if (owner != null && count() > 0) {
                Colony.returnHome(owner, species(), count(), toDigest); // unloaded: they find their way back
            }
        }
        super.remove(reason);
    }

    // ================================================================ the world around it

    /** Fire, lava and burning things in the cloud kill insects; heat comes through every half second. */
    private void environment(ServerLevel level, LivingEntity owner) {
        AABB box = getBoundingBox();
        if (mode() == SwarmMode.SEA || mode() == SwarmMode.JAM || mode() == SwarmMode.POISON || mode() == SwarmMode.JAR) {
            return;
        }
        int fires = 0;
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            var state = level.getBlockState(p);
            if (state.getBlock() instanceof BaseFireBlock || state.is(Blocks.LAVA) || state.is(Blocks.CAMPFIRE) && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                fires++;
                if (fires >= 3) {
                    break;
                }
            }
        }
        for (Entity e : level.getEntities(this, box, e -> e.isOnFire() && e != owner)) {
            fires++;
        }
        if (fires > 0) {
            harm(Harm.FIRE, Math.min(3, fires));
        }
        if (!isRemoved() && mode() != SwarmMode.CLING && mode() != SwarmMode.SPHERE && isInWater()
                && !colonyHas(owner, WildKind.WATER_STRIDER)) {
            moveCenter(center().add(0, 0.5, 0)); // they won't go into water
        }
    }

    /** Frogs, chickens, parrots and pitcher plants eat insects out of any swarm within 3 blocks. */
    private void insectEaters(ServerLevel level) {
        if (mode() == SwarmMode.SEA || mode() == SwarmMode.JAM) {
            return;
        }
        int eaters = level.getEntities(this, getBoundingBox().inflate(3), Swarms::eatsInsects).size();
        if (PitcherPlantBlock.near(level, blockPosition(), 3)) {
            eaters += 2;
        }
        if (eaters > 0) {
            int lost = Math.min(count(), eaters * EATER_PER_SECOND);
            setCount(count() - lost);
            LivingEntity owner = owner();
            if (owner != null) {
                Colony.killed(owner, lost, Harm.MELEE);
                ColonyData c = Colony.of(owner);
                if (c != null) {
                    c.setVigour(c.vigour() + ColonyGrowth.VIGOUR_EATER_PER_SEC);
                }
            }
            if (count() <= 0) {
                settled = true;
                discard();
            }
        }
    }

    private void hum(ServerLevel level) {
        if (random.nextInt(3) == 0 && count() > 0) {
            boolean big = count() >= 6000;
            SwarmFx.sound(level, center(), big ? NoirmSounds.INSECT_HUM_LARGE : NoirmSounds.INSECT_HUM_SMALL,
                    Math.min(1f, 0.25f + count() / 20000f), big ? 0.8f : 1.1f);
        }
    }

    // ================================================================ for the shapes

    int life() {
        return life;
    }

    Vec3 park() {
        return park;
    }

    boolean reported() {
        return reported;
    }

    void setReported(boolean r) {
        reported = r;
    }

    int mapRow() {
        return mapRow;
    }

    void setMapRow(int row) {
        mapRow = row;
    }

    net.minecraft.world.item.ItemStack mapStack() {
        return mapStack;
    }

    void setMapStack(net.minecraft.world.item.ItemStack stack) {
        mapStack = stack;
    }

    void settle() {
        settled = true;
    }

    /** Insects a devour adds (+2 per chakra eaten), which also count as out in the field. */
    void grow(int n) {
        setCount(count() + n);
        LivingEntity owner = owner();
        ColonyData c = owner == null ? null : Colony.of(owner);
        if (c != null) {
            c.addField(n);
        }
    }

    /** A projectile hit this swarm (it's stopped by walls and domes). */
    void projectileHit(Projectile projectile, Harm harm, float damage) {
        harm(harm, damage);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true; // fire kills insects (by count), it doesn't set the swarm alight
    }

    /** Swarms within {@code range} of {@code at}. */
    public static List<InsectSwarm> near(Level level, Vec3 at, double range) {
        return level.getEntitiesOfClass(InsectSwarm.class, new AABB(at, at).inflate(range), s -> !s.isRemoved());
    }
}

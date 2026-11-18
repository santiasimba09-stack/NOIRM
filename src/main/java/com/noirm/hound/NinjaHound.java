package com.noirm.hound;

import com.noirm.ally.Crew;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * A ninja hound (忍犬): a partner bound to one owner (a player, or an Inuzuka NPC ninja), growing
 * from a puppy that rides on your head to a great hound you ride into battle (docs/INUZUKA.md).
 *
 * <p>What it is, all synced to the client so the model can show it: its {@link HoundStage}, coat,
 * body plan, bond and XP, whether it is knocked out, carried, frenzied by a soldier pill, fused
 * into the Two-Headed Wolf, or hidden while it wears your shape (Man Beast Clone). Short moves
 * (a lunge, a howl, the drill spin) reach the client as entity events.
 *
 * <p>It never dies in a fight: a blow that would kill it knocks it out instead ({@link #DOWN_TICKS},
 * shorter with a strong bond); you can wake it early with food or a pill. Only the void or /kill
 * truly ends the body, and then {@link Hounds} brings it back to you after a rest, from what you
 * carry ({@link HoundData}).
 */
public class NinjaHound extends TamableAnimal implements PlayerRideableJumping {
    // ---------------------------------------------------------------- tuning
    /** How long a knockout lasts (ticks), and how much a full bond shortens it. */
    public static final int DOWN_TICKS = 20 * 45;
    public static final int DOWN_BOND_SAVING = 20 * 20;
    /** Health share it wakes with (by itself / woken by you with food or a pill). */
    public static final float WAKE_HEALTH = 0.3f;
    public static final float REVIVE_HEALTH = 0.5f;
    /** The bite gets this much stronger at bond 100. */
    public static final float BOND_BITE_BONUS = 0.25f;
    /** A soldier pill: frenzy length (ticks) and the bite multiplier during it; then this long tired. */
    public static final int FRENZY_TICKS = 20 * 30;
    public static final float FRENZY_BITE = 1.4f;
    public static final int FRENZY_CRASH_TICKS = 20 * 20;
    /** Two-Headed Wolf: the bite multiplier and the share of damage it shrugs off. */
    public static final float FUSED_BITE = 1.7f;
    public static final float FUSED_ARMOUR = 0.5f;
    /** Petting: bond per pet and the wait between pets (ticks). */
    public static final float PET_BOND = 1.5f;
    public static final int PET_COOLDOWN = 20 * 20;
    /** Guarding: it fights hostiles this close to its post. */
    public static final double GUARD_RADIUS = 12.0;

    // ---------------------------------------------------------------- entity events (client animations)
    public static final byte EVENT_LUNGE = 70;
    public static final byte EVENT_HOWL = 71;
    public static final byte EVENT_SPIN = 72;
    public static final byte EVENT_GROW = 73;
    public static final byte EVENT_SNIFF = 74;

    public enum Carry { NONE, HEAD, JACKET }

    private static final EntityDataAccessor<Byte> STAGE = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> COAT = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> BUILD = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> BOND = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> XP = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DOWN = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> ORDER = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> CARRY = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> FRENZY = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FUSED = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(NinjaHound.class, EntityDataSerializers.BOOLEAN);

    private LivingEntity ownerCache;
    private BlockPos guardPost;
    private int petCooldown;
    private int ownerMissingTicks;
    /** Server: drill spin in progress (Fang Over Fang). */
    private int spinTicks;
    private Vec3 spinDir = Vec3.ZERO;
    private float spinDamage;
    private final java.util.Map<Integer, Integer> spinHits = new java.util.HashMap<>();
    /** Client: animation timers started by entity events. */
    public int clientLunge;
    public int clientHowl;
    public int clientSpin;
    public int clientSniff;
    /** Riding: a leap the rider charged (client). */
    private float pendingLeap;
    /** Whoever is tracked (the hound leads you there). */
    private LivingEntity tracking;
    private int trackingTicks;
    /** The Man Beast Clone wearing this hound's place (server). */
    private UUID cloneId;

    public NinjaHound(EntityType<? extends NinjaHound> type, Level level) {
        super(type, level);
        setTame(false);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HoundStage.PUPPY.maxHealth)
                .add(Attributes.MOVEMENT_SPEED, HoundStage.PUPPY.speed)
                .add(Attributes.ATTACK_DAMAGE, HoundStage.PUPPY.bite)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STAGE, (byte) 0);
        entityData.define(COAT, (byte) 0);
        entityData.define(BUILD, (byte) 0);
        entityData.define(BOND, 0f);
        entityData.define(XP, 0f);
        entityData.define(DOWN, 0);
        entityData.define(ORDER, (byte) 0);
        entityData.define(CARRY, (byte) 0);
        entityData.define(FRENZY, 0);
        entityData.define(FUSED, 0);
        entityData.define(HIDDEN, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4f) {
            @Override
            public boolean canUse() {
                return !isCarried() && super.canUse();
            }
        });
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(5, new TrackGoal());
        goalSelector.addGoal(6, new GuardGoal());
        goalSelector.addGoal(7, new FollowOwnerGoal(this, 1.15, 7f, 2.5f, false) {
            @Override
            public boolean canUse() {
                return order() == HoundOrder.FOLLOW && tracking == null && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return order() == HoundOrder.FOLLOW && tracking == null && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
    }

    // ================================================================ state

    public HoundStage stage() {
        return HoundStage.of(entityData.get(STAGE));
    }

    /** Grows (or shrinks, for commands) to {@code stage}: size, health, bite and speed follow; health keeps its share. */
    public void setStage(HoundStage stage) {
        float share = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1f;
        entityData.set(STAGE, (byte) stage.ordinal());
        applyStats();
        setHealth(Math.max(1f, getMaxHealth() * share));
        refreshDimensions();
    }

    /** Max health, bite (with the bond) and speed for the stage. */
    public void applyStats() {
        HoundStage s = stage();
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(s.maxHealth);
        }
        AttributeInstance bite = getAttribute(Attributes.ATTACK_DAMAGE);
        if (bite != null) {
            bite.setBaseValue(s.bite * (1f + BOND_BITE_BONUS * bond() / 100f));
        }
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(s.speed * (isFrenzied() ? 1.2 : 1.0));
        }
        AttributeInstance armour = getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.setBaseValue(s.ordinal() * 2.0);
        }
    }

    public int coat() {
        return entityData.get(COAT);
    }

    public void setCoat(int coat) {
        entityData.set(COAT, (byte) Math.floorMod(coat, Hounds.COATS.length));
    }

    /** The body plan (0 = the one we use; 1 and 2 are the variants we compared it with). */
    public int build() {
        return entityData.get(BUILD);
    }

    public void setBuild(int build) {
        entityData.set(BUILD, (byte) Mth.clamp(build, 0, 2));
    }

    public float bond() {
        return entityData.get(BOND);
    }

    public void setBond(float bond) {
        entityData.set(BOND, Mth.clamp(bond, 0f, 100f));
        applyStats();
    }

    public float xp() {
        return entityData.get(XP);
    }

    public void setXp(float xp) {
        entityData.set(XP, Math.max(0f, xp));
    }

    public HoundOrder order() {
        return HoundOrder.of(entityData.get(ORDER));
    }

    public void setOrder(HoundOrder order) {
        entityData.set(ORDER, (byte) order.ordinal());
        setOrderedToSit(order == HoundOrder.SIT);
        setInSittingPose(order == HoundOrder.SIT);
        if (order == HoundOrder.GUARD) {
            guardPost = blockPosition();
        }
        getNavigation().stop();
        if (order != HoundOrder.FOLLOW) {
            tracking = null;
        }
    }

    int bumpOwnerMissing() {
        return ++ownerMissingTicks;
    }

    void resetOwnerMissing() {
        ownerMissingTicks = 0;
    }

    public BlockPos guardPost() {
        return guardPost;
    }

    public boolean isDown() {
        return entityData.get(DOWN) > 0;
    }

    public int downTicks() {
        return entityData.get(DOWN);
    }

    public Carry carry() {
        return Carry.values()[Mth.clamp(entityData.get(CARRY), 0, 2)];
    }

    public boolean isCarried() {
        return carry() != Carry.NONE && getVehicle() instanceof Player;
    }

    public boolean isFrenzied() {
        return entityData.get(FRENZY) > 0;
    }

    public int fusedTicks() {
        return entityData.get(FUSED);
    }

    public boolean isFused() {
        return fusedTicks() > 0;
    }

    public void setFused(int ticks) {
        entityData.set(FUSED, Math.max(0, ticks));
        refreshDimensions();
    }

    public boolean isHidden() {
        return entityData.get(HIDDEN);
    }

    public boolean isSpinning() {
        return spinTicks > 0 || clientSpin > 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STAGE.equals(key) || FUSED.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        HoundStage s = stage();
        if (isFused()) {
            return EntityDimensions.scalable(2.6f, 3.4f);
        }
        return EntityDimensions.scalable(s.width, s.height);
    }

    // ================================================================ owner

    /** The owner, wherever it can be found (a player, or an NPC ninja), cached. */
    @Override
    @Nullable
    public LivingEntity getOwner() {
        UUID id = getOwnerUUID();
        if (id == null) {
            return null;
        }
        if (ownerCache != null && ownerCache.isAlive() && id.equals(ownerCache.getUUID()) && ownerCache.level() == level()) {
            return ownerCache;
        }
        Player player = level().getPlayerByUUID(id);
        if (player != null) {
            return ownerCache = player;
        }
        if (level() instanceof ServerLevel server && server.getEntity(id) instanceof LivingEntity living) {
            return ownerCache = living;
        }
        return null;
    }

    /** Binds the hound to {@code owner} (players and NPC ninjas alike). */
    public void bondTo(LivingEntity owner) {
        setTame(true);
        setOwnerUUID(owner.getUUID());
        ownerCache = owner;
        setPersistenceRequired();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof LivingEntity living && getOwnerUUID() != null && Crew.sameSide(this, living)) {
            return true;
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof NinjaHound other && other.getOwnerUUID() != null && other.getOwnerUUID().equals(getOwnerUUID())) {
            return false;
        }
        return !Crew.sameSide(this, target) && super.wantsToAttack(target, owner);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isDown() && !isCarried() && !Crew.sameSide(this, target) && super.canAttack(target);
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isDown() && !isHidden() && super.canBeSeenAsEnemy();
    }

    // ================================================================ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (clientLunge > 0) clientLunge--;
            if (clientHowl > 0) clientHowl--;
            if (clientSpin > 0) clientSpin--;
            if (clientSniff > 0) clientSniff--;
            if (isFrenzied() && tickCount % 4 == 0) {
                level().addParticle(ParticleTypes.ANGRY_VILLAGER, getRandomX(0.6), getY() + getBbHeight(), getRandomZ(0.6), 0, 0, 0);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (petCooldown > 0) {
            petCooldown--;
        }
        int down = entityData.get(DOWN);
        if (down > 0) {
            entityData.set(DOWN, down - 1);
            if (down % 20 == 0 && getHealth() < getMaxHealth()) {
                heal(getMaxHealth() * 0.01f);
            }
            if (down == 1) {
                wake(WAKE_HEALTH);
            }
        }
        int frenzy = entityData.get(FRENZY);
        if (frenzy > 0) {
            entityData.set(FRENZY, frenzy - 1);
            if (frenzy == 1) {
                applyStats();
                addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, FRENZY_CRASH_TICKS, 0));
                addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, FRENZY_CRASH_TICKS, 0));
            }
        }
        if (isFused()) {
            HoundTechniques.tickFused(this);
        }
        if (isHidden()) {
            HoundTechniques.tickHidden(this, cloneId);
        }
        if (spinTicks > 0) {
            tickSpin();
        }
        if (tracking != null && (!tracking.isAlive() || ++trackingTicks > 20 * 60 || tracking.level() != level())) {
            tracking = null;
        }
        if (tickCount % 20 == 0) {
            Hounds.tickHound(this);
        }
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isDown() || isHidden() || isCarried();
    }

    // ================================================================ knockout

    /** A blow that would kill it knocks it out instead (only the void and /kill end it for real). */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide() && isTame() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1f);
            knockOut();
            return;
        }
        super.die(source);
    }

    public void knockOut() {
        if (isDown()) {
            return;
        }
        int ticks = DOWN_TICKS - Math.round(DOWN_BOND_SAVING * bond() / 100f);
        entityData.set(DOWN, ticks);
        setTarget(null);
        getNavigation().stop();
        stopRiding();
        ejectPassengers();
        setFused(0);
        clearFire();
        spinTicks = 0;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_WHINE, getSoundSource(), 1f, getVoicePitch());
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 12, getBbWidth() * 0.5, 0.2, getBbWidth() * 0.5, 0.01);
        }
        LivingEntity owner = getOwner();
        if (owner instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.hound.down", getDisplayName(), (ticks + 19) / 20)
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
        Hounds.snapshot(this);
    }

    /** Gets back up with {@code healthShare} of its health. */
    public void wake(float healthShare) {
        entityData.set(DOWN, 0);
        setHealth(Math.max(getHealth(), getMaxHealth() * healthShare));
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_SHAKE, getSoundSource(), 1f, getVoicePitch());
        level().broadcastEntityEvent(this, (byte) 8); // the vanilla wolf shake... (ignored by our model, harmless)
        Hounds.snapshot(this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDown() || isHidden()) {
            return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
        }
        if (source.getEntity() instanceof LivingEntity attacker && getOwnerUUID() != null && Crew.sameSide(this, attacker)) {
            return false; // never hurt by its own side
        }
        if (isCarried() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false; // tucked in your jacket or on your head: you take the blows
        }
        if (isFused()) {
            amount *= 1f - FUSED_ARMOUR;
        }
        return super.hurt(source, amount);
    }

    // ================================================================ fighting

    @Override
    public boolean doHurtTarget(Entity target) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (isFrenzied()) {
            damage *= FRENZY_BITE;
        }
        if (isFused()) {
            damage *= FUSED_BITE;
        }
        if (target instanceof LivingEntity living && HoundTechniques.isMarked(living, this)) {
            damage *= HoundTechniques.MARK_BONUS;
        }
        level().broadcastEntityEvent(this, EVENT_LUNGE);
        boolean hit = target.hurt(damageSources().mobAttack(this), damage);
        if (hit) {
            doEnchantDamageEffects(this, target);
            if (target instanceof LivingEntity living) {
                Vec3 push = living.position().subtract(position());
                if (push.horizontalDistanceSqr() > 1e-4) {
                    push = push.normalize();
                    living.knockback(0.25 + 0.1 * stage().ordinal(), -push.x, -push.z);
                }
            }
            Hounds.addXp(this, damage * Hounds.XP_PER_DAMAGE);
        }
        playSound(SoundEvents.WOLF_GROWL, 0.6f, getVoicePitch());
        return hit;
    }

    /** Starts a drill spin along {@code dir} (Fang Over Fang): it tears through everything on the way. */
    public void startSpin(Vec3 dir, int ticks, float damage) {
        if (isDown() || isCarried()) {
            return;
        }
        spinDir = new Vec3(dir.x, 0, dir.z).normalize();
        spinTicks = ticks;
        spinDamage = damage;
        spinHits.clear();
        setOrderedToSit(false);
        setInSittingPose(false);
        level().broadcastEntityEvent(this, EVENT_SPIN);
    }

    public int spinTicks() {
        return spinTicks;
    }

    private void tickSpin() {
        spinTicks--;
        double speed = 0.9 + 0.08 * stage().ordinal();
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && distanceToSqr(target) < 400) {
            // Steer a little toward the target: the two drills cross on it.
            Vec3 to = target.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() > 0.5) {
                spinDir = spinDir.add(flat.normalize().scale(0.25)).normalize();
            }
        }
        setDeltaMovement(spinDir.x * speed, Math.min(getDeltaMovement().y, 0.1), spinDir.z * speed);
        hasImpulse = true;
        setYRot((float) (Mth.atan2(spinDir.z, spinDir.x) * Mth.RAD_TO_DEG) - 90f);
        yBodyRot = getYRot();
        HoundTechniques.drillHits(this, spinDamage, spinHits, 1.0 + getBbWidth() * 0.5);
        HoundTechniques.drillTrail((ServerLevel) level(), this);
    }

    // ================================================================ tracking and guarding

    /** Leads you to {@code target} by its scent (for a minute at most). */
    public void track(LivingEntity target) {
        tracking = target;
        trackingTicks = 0;
        setOrder(HoundOrder.FOLLOW);
        level().broadcastEntityEvent(this, EVENT_SNIFF);
    }

    public LivingEntity tracking() {
        return tracking;
    }

    /** Walks toward the tracked scent, waiting for its owner to keep up. */
    private final class TrackGoal extends Goal {
        TrackGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return tracking != null && getTarget() == null && !isDown();
        }

        @Override
        public void tick() {
            if (tracking == null) {
                return;
            }
            LivingEntity owner = getOwner();
            boolean ownerClose = owner == null || owner.distanceToSqr(NinjaHound.this) < 10 * 10;
            if (ownerClose && tickCount % 10 == 0) {
                getNavigation().moveTo(tracking, 1.1);
            } else if (!ownerClose) {
                getNavigation().stop(); // wait for you
                getLookControl().setLookAt(owner);
            }
            if (tickCount % 15 == 0 && level() instanceof ServerLevel server) {
                HoundSmell.scentTrail(server, NinjaHound.this, tracking);
            }
        }
    }

    /** Guard: hold the post, go for hostiles near it, come back. */
    private final class GuardGoal extends Goal {
        GuardGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return order() == HoundOrder.GUARD && guardPost != null && !isDown();
        }

        @Override
        public void tick() {
            if (tickCount % 10 != 0) {
                return;
            }
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || target.distanceToSqr(Vec3.atCenterOf(guardPost)) > (GUARD_RADIUS + 6) * (GUARD_RADIUS + 6)) {
                setTarget(null);
                LivingEntity threat = level().getEntitiesOfClass(LivingEntity.class, new AABB(guardPost).inflate(GUARD_RADIUS),
                                e -> e instanceof Enemy && e.isAlive() && !Crew.sameSide(NinjaHound.this, e))
                        .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(NinjaHound.this))).orElse(null);
                if (threat != null) {
                    setTarget(threat);
                } else if (distanceToSqr(Vec3.atBottomCenterOf(guardPost)) > 4) {
                    getNavigation().moveTo(guardPost.getX() + 0.5, guardPost.getY(), guardPost.getZ() + 0.5, 1.0);
                }
            }
        }
    }

    // ================================================================ interaction

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isOwnedBy(player)) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && Hounds.feed(this, player, stack)) {
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            Hounds.cycleOrder(this, player);
            return InteractionResult.CONSUME;
        }
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (isDown()) {
                player.displayClientMessage(Component.translatable("message.noirm.hound.still_down", (downTicks() + 19) / 20), true);
                return InteractionResult.CONSUME;
            }
            if (stage().canBeCarried()) {
                Hounds.carry(this, player, Carry.HEAD);
            } else if (stage().canBeRidden() || isFused()) {
                Hounds.ride(this, player);
            } else {
                Hounds.pet(this, player);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Petting: a little bond (once in a while), hearts and a happy wag. */
    public boolean pet(Player player) {
        if (petCooldown > 0) {
            return false;
        }
        petCooldown = PET_COOLDOWN;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 3, 0.3, 0.2, 0.3, 0);
        }
        playSound(SoundEvents.WOLF_PANT, 1f, getVoicePitch());
        return true;
    }

    public void frenzy() {
        entityData.set(FRENZY, FRENZY_TICKS);
        applyStats();
    }

    public void setCarry(Carry carry) {
        entityData.set(CARRY, (byte) carry.ordinal());
    }

    public void setHidden(boolean hidden, UUID clone) {
        entityData.set(HIDDEN, hidden);
        cloneId = clone;
        setInvisible(hidden);
        noPhysics = hidden;
        setNoGravity(hidden);
    }

    public void howl() {
        level().broadcastEntityEvent(this, EVENT_HOWL);
        playSound(SoundEvents.WOLF_HOWL, 1.2f, getVoicePitch());
    }

    @Override
    public void handleEntityEvent(byte event) {
        switch (event) {
            case EVENT_LUNGE -> clientLunge = 10;
            case EVENT_HOWL -> clientHowl = 50;
            case EVENT_SPIN -> clientSpin = HoundTechniques.SPIN_TICKS;
            case EVENT_SNIFF -> clientSniff = 30;
            case EVENT_GROW -> {
                for (int i = 0; i < 30; i++) {
                    level().addParticle(ParticleTypes.CLOUD, getRandomX(1.0), getRandomY(), getRandomZ(1.0), 0, 0.05, 0);
                }
            }
            default -> super.handleEntityEvent(event);
        }
    }

    // ================================================================ carried (puppy) and ridden (great hound)

    /** On your head or peeking out of your jacket: follows you exactly every tick. */
    @Override
    public void rideTick() {
        super.rideTick();
        if (getVehicle() instanceof Player player && carry() != Carry.NONE) {
            float yaw = player.yBodyRot;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = player.yHeadRot;
            if (carry() == Carry.HEAD) {
                setPos(player.getX(), player.getY() + player.getBbHeight() - 0.02, player.getZ());
            } else {
                Vec3 fwd = Vec3.directionFromRotation(0, yaw).scale(0.16);
                setPos(player.getX() + fwd.x, player.getY() + player.getBbHeight() * 0.5, player.getZ() + fwd.z);
            }
        }
    }

    @Override
    public double getMyRidingOffset() {
        return carry() != Carry.NONE ? 0 : super.getMyRidingOffset();
    }

    @Override
    public double getPassengersRidingOffset() {
        return getBbHeight() * (isFused() ? 0.62 : 0.66);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && (stage().canBeRidden() || isFused());
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player && isOwnedBy(player) ? player : null;
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        setRot(player.getYRot(), player.getXRot() * 0.5f);
        yRotO = yBodyRot = yHeadRot = getYRot();
        if (pendingLeap > 0f && onGround()) {
            Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
            setDeltaMovement(getDeltaMovement().add(fwd.scale(0.6 * pendingLeap)).add(0, 0.55 + 0.55 * pendingLeap, 0));
            hasImpulse = true;
            pendingLeap = 0f;
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 input) {
        float strafe = player.xxa * 0.5f;
        float forward = player.zza;
        if (forward <= 0f) {
            forward *= 0.3f;
        }
        return new Vec3(strafe, 0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * (isFused() ? 1.3f : 1.1f);
    }

    @Override
    public void onPlayerJump(int power) {
        pendingLeap = Mth.clamp(power / 100f, 0f, 1f);
    }

    @Override
    public boolean canJump() {
        return stage().canBeRidden() || isFused();
    }

    @Override
    public void handleStartJump(int power) {
    }

    @Override
    public void handleStopJump() {
    }

    // ================================================================ sounds, breeding, saving

    @Override
    public float getVoicePitch() {
        return switch (stage()) {
            case PUPPY -> 1.6f;
            case YOUNG -> 1.25f;
            case GROWN -> 1.0f;
            case GREAT -> 0.8f;
            case GIANT -> 0.65f;
        } * (isFused() ? 0.6f : 1f);
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f + 0.15f * stage().ordinal();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (isDown()) {
            return SoundEvents.WOLF_WHINE;
        }
        return getTarget() != null ? SoundEvents.WOLF_GROWL : (random.nextInt(4) == 0 ? SoundEvents.WOLF_PANT : SoundEvents.WOLF_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public boolean isFood(net.minecraft.world.item.ItemStack stack) {
        return false; // fed by hand (Hounds.feed), never bred
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Stage", entityData.get(STAGE));
        tag.putByte("Coat", entityData.get(COAT));
        tag.putByte("Build", entityData.get(BUILD));
        tag.putFloat("Bond", bond());
        tag.putFloat("HoundXp", xp());
        tag.putInt("Down", downTicks());
        tag.putByte("HoundOrder", entityData.get(ORDER));
        tag.putInt("Frenzy", entityData.get(FRENZY));
        if (guardPost != null) {
            tag.put("GuardPost", NbtUtils.writeBlockPos(guardPost));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(STAGE, tag.getByte("Stage"));
        setCoat(tag.getByte("Coat"));
        setBuild(tag.getByte("Build"));
        entityData.set(BOND, tag.getFloat("Bond"));
        setXp(tag.getFloat("HoundXp"));
        entityData.set(DOWN, tag.getInt("Down"));
        entityData.set(ORDER, tag.getByte("HoundOrder"));
        entityData.set(FRENZY, tag.getInt("Frenzy"));
        guardPost = tag.contains("GuardPost") ? NbtUtils.readBlockPos(tag.getCompound("GuardPost")) : null;
        applyStats();
        refreshDimensions();
    }
}

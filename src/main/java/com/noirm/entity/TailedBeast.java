package com.noirm.entity;

import com.noirm.Noirm;
import com.noirm.biju.BijuData;
import com.noirm.biju.BijuSystem;
import com.noirm.combat.CombatListener;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatStats;
import com.noirm.clan.Nature;
import com.noirm.jutsu.JutsuUser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.hoglin.HoglinBase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * A tailed beast (1–9 tails). Huge, strong, and fought with the same combat system as everyone
 * else: claw combos and a ground slam with readable wind-ups, plus the charged Tailed Beast Ball
 * from range. More tails = bigger, tougher and stronger.
 *
 * <p>Beaten down to {@link #SUBDUE_FRACTION} of its health it is <b>subdued</b> and lies still for
 * {@link #SUBDUE_TICKS} ticks. That's the moment to choose: use a Sealing Scroll on it, or keep
 * hitting and kill it (its chakra scatters, to reform some day). If you do neither, it gets back
 * up, angry.
 *
 * <p>Placeholder art: the vanilla hoglin model scaled up (see {@code TailedBeastRenderer}).
 */
public class TailedBeast extends PathfinderMob implements Enemy, CombatStats, CombatListener, JutsuUser, HoglinBase {
    public static final ResourceLocation STYLE = Noirm.id("beast");
    public static final float SUBDUE_FRACTION = 0.1f;
    public static final int SUBDUE_TICKS = 1200;
    public static final float RECOVER_FRACTION = 0.5f;

    private static final EntityDataAccessor<Byte> TAILS = SynchedEntityData.defineId(TailedBeast.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> SUBDUED = SynchedEntityData.defineId(TailedBeast.class, EntityDataSerializers.BOOLEAN);
    private static final byte ATTACK_EVENT = 4;

    private int subduedTicks;
    private int attackAnimation;

    public TailedBeast(EntityType<? extends TailedBeast> type, Level level) {
        super(type, level);
        xpReward = 50;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, healthFor(1))
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.ATTACK_DAMAGE, 1);
    }

    /** 150 health with one tail, 550 with nine. */
    public static float healthFor(int tails) {
        return 100f + 50f * tails;
    }

    /** Speed and taijutsu: 19 with one tail, 59 with nine. */
    /**
     * A beast's strength: 30 with one tail, ~470 with nine. Next to a fresh ninja's 10 that's a
     * force of nature (power and toughness scale with it, see {@code Perception.statPower}).
     */
    public static float statFor(int tails) {
        return 30f * (float) Math.pow(Math.max(1, tails), 1.25);
    }

    /** Model and hitbox scale: 1× with one tail, 2.6× with nine. */
    public static float scaleFor(int tails) {
        return 0.8f + 0.2f * tails;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TAILS, (byte) 1);
        entityData.define(SUBDUED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BeastFightGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---------------------------------------------------------------- tails and size

    public int tails() {
        return entityData.get(TAILS);
    }

    /** Sets the number of tails: size, health, posture and strength all follow. */
    public void setTails(int tails) {
        int clamped = Mth.clamp(tails, 1, BijuData.MAX_TAILS);
        entityData.set(TAILS, (byte) clamped);
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(healthFor(clamped));
        }
        setHealth(getMaxHealth());
        CombatStates.get(this).setMaxPosture(150f + 25f * clamped);
        refreshDimensions();
    }

    public float scale() {
        return scaleFor(tails());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(scale());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TAILS.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public Component getName() {
        return hasCustomName() ? super.getName() : BijuSystem.beastName(tails());
    }

    // ---------------------------------------------------------------- subdued

    public boolean isSubdued() {
        return entityData.get(SUBDUED);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount); // /kill and the void
        }
        if (level().isClientSide()) {
            return false;
        }
        if (isSubdued()) {
            // Down and defenceless: finishing blows land in full.
            return super.hurt(source, amount);
        }
        // Standing, it can't be killed outright: first it has to be beaten down.
        boolean hurt = super.hurt(source, Math.min(amount, getHealth() - 1f));
        if (hurt && getHealth() <= getMaxHealth() * SUBDUE_FRACTION) {
            subdue();
        }
        return hurt;
    }

    /** Beaten: lies still for a while, waiting to be sealed. */
    public void subdue() {
        if (isSubdued()) {
            return;
        }
        entityData.set(SUBDUED, true);
        subduedTicks = SUBDUE_TICKS;
        setTarget(null);
        getNavigation().stop();
        playSound(SoundEvents.RAVAGER_STUNNED, 2f, 0.6f);
        announce(Component.translatable("message.noirm.beast.subdued", getName()).withStyle(ChatFormatting.GOLD));
    }

    /** Killed: its chakra bursts out and scatters (canon: a beast reforms in time). */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            com.noirm.vfx.Vfx.Palette palette = new com.noirm.vfx.Vfx.Palette(0xFF8040, 0xC02020, 0x300808);
            com.noirm.vfx.Vfx.explosion(level, position().add(0, getBbHeight() * 0.5, 0), palette, getBbWidth() * 2.5f);
            com.noirm.vfx.Vfx.shockwave(level, position().add(0, 0.3, 0), palette, getBbWidth() * 4f);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, net.minecraft.sounds.SoundSource.HOSTILE, 2f, 0.7f);
            net.minecraft.world.entity.ExperienceOrb.award(level, position(), 100 * Math.max(1, tails()));
            announce(Component.translatable("message.noirm.beast.slain", getName()).withStyle(ChatFormatting.DARK_RED));
        }
    }

    private void recover() {
        entityData.set(SUBDUED, false);
        setHealth(getMaxHealth() * RECOVER_FRACTION);
        playSound(SoundEvents.RAVAGER_ROAR, 3f, 0.5f);
        announce(Component.translatable("message.noirm.beast.recovered", getName()).withStyle(ChatFormatting.RED));
    }

    private void announce(Component message) {
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(48))) {
            player.sendSystemMessage(message);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (attackAnimation > 0) {
            attackAnimation--;
        }
        if (level().isClientSide()) {
            chakraAura();
            return;
        }
        if (tickCount % 100 == 1) {
            CombatStates.get(this).setMaxPosture(150f + 25f * tails());
        }
        if (isSubdued()) {
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            if (--subduedTicks <= 0) {
                recover();
            } else if (tickCount % 20 == 0 && level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, getBbWidth() * 0.3, 0.3, getBbWidth() * 0.3, 0.01);
            }
        }
    }

    private void chakraAura() {
        if (isSubdued() || random.nextInt(2) != 0) {
            return;
        }
        float hue = tails() / 9f;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.9f, 0.5f - 0.35f * hue, 0.1f), 1.6f);
        double w = getBbWidth() * 0.6;
        level().addParticle(dust, getX() + (random.nextDouble() - 0.5) * w * 2, getY() + random.nextDouble() * getBbHeight(),
                getZ() + (random.nextDouble() - 0.5) * w * 2, 0, 0.02, 0);
    }

    // ---------------------------------------------------------------- combat

    @Override
    public float combatSpeed() {
        return statFor(tails());
    }

    @Override
    public float combatTaijutsu() {
        return statFor(tails());
    }

    @Override
    public ResourceLocation combatStyle() {
        return STYLE;
    }

    @Override
    public float jutsuAffinity(Nature nature) {
        return 0f;
    }

    @Override
    public void onCombatStateChanged(CombatState state) {
        if (state.phase() == CombatPhase.ACTIVE && !level().isClientSide()) {
            attackAnimation = 10;
            level().broadcastEntityEvent(this, ATTACK_EVENT);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == ATTACK_EVENT) {
            attackAnimation = 10;
            playSound(SoundEvents.RAVAGER_ATTACK, 1f, 0.7f);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public int getAttackAnimationRemainingTicks() {
        return attackAnimation;
    }

    // ---------------------------------------------------------------- misc

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Tails", tails());
        tag.putBoolean("Subdued", isSubdued());
        tag.putInt("SubduedTicks", subduedTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        float health = getHealth();
        super.readAdditionalSaveData(tag);
        if (tag.contains("Tails")) {
            setTails(tag.getInt("Tails"));
            if (tag.contains("Health")) {
                setHealth(Math.min(getMaxHealth(), tag.getFloat("Health")));
            }
        } else {
            setHealth(health);
        }
        entityData.set(SUBDUED, tag.getBoolean("Subdued"));
        subduedTicks = tag.getInt("SubduedTicks");
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isSubdued() ? null : SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.1f - 0.06f * tails();
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }
}

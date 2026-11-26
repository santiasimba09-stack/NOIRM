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
import net.minecraft.world.entity.LivingEntity;
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
 * <p>Art: the One-Tail is Shukaku, our own Blockbench creature ({@code docs/entities/shukaku.md}, drawn by
 * {@code client/beast/ShukakuRenderer}); the other eight still use the zoglin placeholder. Every beast carries a synced
 * {@link Mood} and {@link #ACTION} so a renderer can animate what the server decides.
 */
public class TailedBeast extends PathfinderMob implements Enemy, CombatStats, CombatListener, JutsuUser, HoglinBase {
    public static final ResourceLocation STYLE = Noirm.id("beast");
    public static final float SUBDUE_FRACTION = 0.1f;
    public static final int SUBDUE_TICKS = 1200;
    public static final float RECOVER_FRACTION = 0.5f;

    private static final EntityDataAccessor<Byte> TAILS = SynchedEntityData.defineId(TailedBeast.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> SUBDUED = SynchedEntityData.defineId(TailedBeast.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> MOOD = SynchedEntityData.defineId(TailedBeast.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> ACTION_ID = SynchedEntityData.defineId(TailedBeast.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> ACTION_START = SynchedEntityData.defineId(TailedBeast.class, EntityDataSerializers.INT);
    private static final byte ATTACK_EVENT = 4;
    /** Shukaku's hitbox at scale 1 (blocks; a hunched quadruped, its body about 3 long); {@code creatures.shukakuScale} multiplies it. */
    public static final float SHUKAKU_WIDTH = 1.35f, SHUKAKU_HEIGHT = 2.3f;
    /** A mood holds at least this long before it can change (no flicker). */
    static final int MOOD_HOLD = 30;
    /** No target and no player near for this long: calm again. */
    static final int CALM_AFTER = 600;

    /** How the beast feels: the renderer turns it into a posture, a breathing rate and eyes (docs/entities/shukaku.md). */
    public enum Mood { CALM, ALERT, ANGRY, MANIC, TIRED, SUBDUED;
        public static Mood of(int id) {
            return id >= 0 && id < values().length ? values()[id] : CALM;
        }
    }

    /** What the beast is doing right now (an idle habit, an attack wind-up, the belly drum). */
    public static final byte ACTION_NONE = 0, ACTION_CLAW = 1, ACTION_DRUM = 2, ACTION_LAUGH = 3, ACTION_SNIFF = 4,
            ACTION_SHAKE = 5, ACTION_YAWN = 6, ACTION_ROAR = 7;

    private int moodHeld;
    private boolean wasOnGround = true;
    private int quietTicks;
    private boolean poofed;

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
        entityData.define(MOOD, (byte) 0);
        entityData.define(ACTION_ID, ACTION_NONE);
        entityData.define(ACTION_START, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BeastFightGoal(this));
        // Idle habits (docs/entities/shukaku.md): each runs only when calm and sets ACTION for the renderer.
        goalSelector.addGoal(4, new BeastIdleGoal(this, ACTION_SNIFF, 60, 200, 500, true));
        goalSelector.addGoal(4, new BeastIdleGoal(this, ACTION_SHAKE, 30, 400, 800, true));
        goalSelector.addGoal(4, new BeastIdleGoal(this, ACTION_LAUGH, 36, 400, 800, true));
        goalSelector.addGoal(4, new BeastIdleGoal(this, ACTION_YAWN, 40, 600, 1200, true));
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
            health.setBaseValue(healthFor(clamped) * com.noirm.config.NoirmConfig.NPC_HEALTH.get()); // config: difficulty.npcHealth
        }
        setHealth(getMaxHealth());
        CombatStates.get(this).setMaxPosture(150f + 25f * clamped);
        refreshDimensions();
    }

    public float scale() {
        return isShukaku() ? shukakuScale() : scaleFor(tails());
    }

    /** The One-Tail has its own model and shape: an upright tanuki, narrower and taller than the placeholder. */
    public boolean isShukaku() {
        return tails() == 1;
    }

    /** {@code creatures.shukakuScale} (config), read when the size is refreshed, not every tick. */
    public static float shukakuScale() {
        return com.noirm.config.NoirmConfig.f(com.noirm.config.NoirmConfig.SHUKAKU_SCALE);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (isShukaku()) {
            return EntityDimensions.scalable(SHUKAKU_WIDTH, SHUKAKU_HEIGHT).scale(shukakuScale());
        }
        return super.getDimensions(pose).scale(scale());
    }

    // ---------------------------------------------------------------- mood and actions (synced, for the renderer)

    public Mood mood() {
        return Mood.of(entityData.get(MOOD));
    }

    public byte action() {
        return entityData.get(ACTION_ID);
    }

    /** Game time the current action started. */
    public int actionStart() {
        return entityData.get(ACTION_START);
    }

    public void setAction(byte action) {
        if (action() != action || action != ACTION_NONE) {
            entityData.set(ACTION_ID, action);
            entityData.set(ACTION_START, (int) level().getGameTime());
        }
    }

    public void clearAction(byte action) {
        if (action() == action) {
            entityData.set(ACTION_ID, ACTION_NONE);
        }
    }

    /** For tests: every idle habit is ready to start now. */
    public void readyIdleHabits() {
        goalSelector.getAvailableGoals().forEach(g -> {
            if (g.getGoal() instanceof BeastIdleGoal idle) {
                idle.readyNow();
            }
        });
    }

    /** Server, every tick (public for tests): picks the mood from what is happening (the brief's triggers), holding each for {@link #MOOD_HOLD}. */
    public void updateMood() {
        Mood now = mood();
        Mood next = wantedMood(now);
        if (moodHeld > 0) {
            moodHeld--;
        }
        if (next != now && (moodHeld <= 0 || next == Mood.SUBDUED)) {
            entityData.set(MOOD, (byte) next.ordinal());
            moodHeld = MOOD_HOLD;
            if (next == Mood.ANGRY && now != Mood.MANIC && action() == ACTION_NONE) {
                setAction(ACTION_ROAR); // the intimidation display; BeastIdleGoal-free, it just plays once
            }
        }
        if (action() == ACTION_ROAR && level().getGameTime() - actionStart() > 30) {
            clearAction(ACTION_ROAR);
        }
    }

    private Mood wantedMood(Mood now) {
        if (isSubdued()) {
            return Mood.SUBDUED;
        }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            quietTicks = 0;
            if (target.getHealth() < target.getMaxHealth() * 0.3f) {
                return Mood.MANIC; // it smells blood
            }
            boolean hurtLately = getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < 60;
            if (hurtLately || distanceTo(target) < getBbWidth() / 2 + 5.5) {
                return Mood.ANGRY;
            }
            return getHealth() < getMaxHealth() * 0.35f ? Mood.TIRED : Mood.ALERT;
        }
        if (getHealth() < getMaxHealth() * 0.35f) {
            return Mood.TIRED;
        }
        if (level().getNearestPlayer(this, 12) != null) {
            quietTicks = 0;
            return Mood.ALERT;
        }
        if (now == Mood.CALM || ++quietTicks > CALM_AFTER) {
            return Mood.CALM;
        }
        return now == Mood.ANGRY || now == Mood.MANIC ? Mood.ALERT : now;
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
            if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
                com.noirm.progress.BeastTrigger.subdued(player, tails()); // "Path of the Shinobi"
            }
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
        if (isShukaku() && tickCount % 100 == 7 && Math.abs(getBbWidth() - SHUKAKU_WIDTH * shukakuScale()) > 0.01f) {
            refreshDimensions(); // creatures.shukakuScale changed (config reload)
        }
        if (level().isClientSide()) {
            chakraAura();
            return;
        }
        Threat.tick(this); // turn on whoever is hurting it most
        if (!poofed) {
            poofed = true;
            summonPoof();
        }
        updateMood();
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

    /** It arrives in a puff of smoke (sand-coloured for Shukaku), sized to the beast. */
    private void summonPoof() {
        if (level() instanceof ServerLevel level) {
            int colour = isShukaku() ? 0xD8C090 : 0xE8E8E8;
            float size = getBbWidth();
            com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.smoke(colour, 1.2f + size * 0.6f, 30), position().add(0, getBbHeight() * 0.4, 0),
                    (int) (16 + 10 * size), getBbWidth() * 0.6, 0.08);
        }
    }

    private void chakraAura() {
        if (isShukaku()) {
            sandAura();
            return;
        }
        if (isSubdued() || random.nextInt(2) != 0) {
            return;
        }
        float hue = tails() / 9f;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.9f, 0.5f - 0.35f * hue, 0.1f), 1.6f);
        double w = getBbWidth() * 0.6;
        level().addParticle(dust, getX() + (random.nextDouble() - 0.5) * w * 2, getY() + random.nextDouble() * getBbHeight(),
                getZ() + (random.nextDouble() - 0.5) * w * 2, 0, 0.02, 0);
    }

    /** Shukaku's aura: sand dusting off it, thicker when angry; sand pours while it moves, swipes and shakes; a cloud when it lands. */
    private void sandAura() {
        boolean landed = onGround() && !wasOnGround;
        wasOnGround = onGround();
        if (isSubdued()) {
            return;
        }
        if (landed || action() == ACTION_SHAKE && random.nextInt(2) == 0 || attackAnimation > 6) {
            sandBurst(landed ? 24 : 6);
        }
        Mood mood = mood();
        int chance = mood == Mood.ANGRY || mood == Mood.MANIC ? 1 : 3;
        double w = getBbWidth() * 0.6;
        if (random.nextInt(chance) == 0) {
            DustParticleOptions sand = new DustParticleOptions(new Vector3f(0.82f, 0.68f, 0.45f), 1.2f);
            level().addParticle(sand, getX() + (random.nextDouble() - 0.5) * w * 2, getY() + random.nextDouble() * getBbHeight() * 0.6,
                    getZ() + (random.nextDouble() - 0.5) * w * 2, 0, -0.01, 0);
        }
        if (walkAnimation.speed() > 0.3f && random.nextInt(2) == 0) {
            net.minecraft.core.particles.BlockParticleOption grains = new net.minecraft.core.particles.BlockParticleOption(
                    ParticleTypes.FALLING_DUST, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
            level().addParticle(grains, getX() + (random.nextDouble() - 0.5) * w * 2, getY() + random.nextDouble() * getBbHeight() * 0.7,
                    getZ() + (random.nextDouble() - 0.5) * w * 2, 0, 0, 0);
        }
    }

    /** A puff of sand around the body (client). */
    private void sandBurst(int count) {
        net.minecraft.core.particles.BlockParticleOption sand = new net.minecraft.core.particles.BlockParticleOption(
                ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
        double w = getBbWidth() * 0.8;
        for (int i = 0; i < count; i++) {
            level().addParticle(sand, getX() + (random.nextDouble() - 0.5) * w * 2, getY() + random.nextDouble() * getBbHeight() * 0.5,
                    getZ() + (random.nextDouble() - 0.5) * w * 2, (random.nextDouble() - 0.5) * 0.3, 0.1, (random.nextDouble() - 0.5) * 0.3);
        }
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
        return isShukaku() && nature == Nature.WIND ? 1f : 0f; // Shukaku: the Drilling Air Bullet
    }

    @Override
    public void onCombatStateChanged(CombatState state) {
        if (level().isClientSide()) {
            return;
        }
        if (state.phase() == CombatPhase.WINDUP) {
            setAction(ACTION_CLAW); // the renderer raises the claws for the whole wind-up
        } else if (state.phase() == CombatPhase.IDLE || state.phase() == CombatPhase.STAGGER) {
            clearAction(ACTION_CLAW);
        }
        if (state.phase() == CombatPhase.ACTIVE) {
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
        tag.putBoolean("Poofed", true);
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
        poofed = tag.getBoolean("Poofed"); // reloaded from the save: no arrival puff
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

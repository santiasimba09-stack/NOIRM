package com.noirm.entity;

import com.noirm.combat.CombatListener;
import com.noirm.combat.CombatMove;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatStats;
import com.noirm.player.ShinobiData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A sparring partner that fights with the same combat system as the player: telegraphed
 * wind-ups, combos, guard and dodges. It challenges players who enter combat mode nearby and
 * fights back when hit. Its speed and taijutsu (default 10, like a new player) can be set with
 * {@code /summon noirm:sparring_ninja ~ ~ ~ {Speed:15,Taijutsu:15}} to test perception.
 */
public class SparringNinja extends PathfinderMob implements CombatStats, CombatListener {
    /** What the model should show. Synced to clients. */
    public enum Pose { IDLE, WINDUP_LIGHT, WINDUP_HEAVY, ACTIVE, GUARD, STAGGER }

    private static final EntityDataAccessor<Byte> POSE = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BYTE);

    private float speed = ShinobiData.DEFAULT_STAT;
    private float taijutsu = ShinobiData.DEFAULT_STAT;

    public SparringNinja(EntityType<? extends SparringNinja> type, Level level) {
        super(type, level);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.ATTACK_DAMAGE, 1);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(POSE, (byte) Pose.IDLE.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new NinjaFightGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // A challenge: anyone who enters combat mode nearby gets a fight.
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, player -> {
            CombatState state = CombatStates.getIfPresent(player);
            return state != null && state.isCombatMode();
        }));
    }

    public Pose ninjaPose() {
        int index = entityData.get(POSE);
        Pose[] poses = Pose.values();
        return index >= 0 && index < poses.length ? poses[index] : Pose.IDLE;
    }

    @Override
    public void onCombatStateChanged(CombatState state) {
        Pose pose;
        if (state.isGuarding()) {
            pose = Pose.GUARD;
        } else {
            CombatMove move = state.currentMove();
            pose = switch (state.phase()) {
                case WINDUP -> move != null && move.hit().isHeavy() ? Pose.WINDUP_HEAVY : Pose.WINDUP_LIGHT;
                case ACTIVE -> Pose.ACTIVE;
                case STAGGER -> Pose.STAGGER;
                default -> Pose.IDLE;
            };
        }
        entityData.set(POSE, (byte) pose.ordinal());
    }

    @Override
    public float combatSpeed() {
        return speed;
    }

    @Override
    public float combatTaijutsu() {
        return taijutsu;
    }

    public void setCombatStats(float speed, float taijutsu) {
        this.speed = Mth.clamp(speed, 1f, ShinobiData.MAX_STAT);
        this.taijutsu = Mth.clamp(taijutsu, 1f, ShinobiData.MAX_STAT);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Speed", speed);
        tag.putFloat("Taijutsu", taijutsu);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCombatStats(tag.contains("Speed") ? tag.getFloat("Speed") : ShinobiData.DEFAULT_STAT,
                tag.contains("Taijutsu") ? tag.getFloat("Taijutsu") : ShinobiData.DEFAULT_STAT);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }
}

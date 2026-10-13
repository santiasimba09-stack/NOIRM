package com.noirm.entity;

import com.noirm.clan.WorldClans;
import com.noirm.player.ShinobiCapability;
import com.noirm.training.TrainingSystem.Stat;
import com.noirm.training.TrainingSystem;
import com.noirm.vfx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * Shadow Clone (影分身, Kage Bunshin): a real, solid copy of its owner. It fights whatever the
 * owner fights, using the owner's clan, jutsu and stats, and vanishes in a puff of smoke the
 * moment it's hit. When it goes, what it learned (taijutsu XP from its hits) returns to the owner.
 */
public class ShadowClone extends SparringNinja {
    public static final int MAX_PER_OWNER = 4;

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ShadowClone.class, EntityDataSerializers.OPTIONAL_UUID);

    private LivingEntity owner;
    private int life = 600;
    private float storedXp;

    public ShadowClone(EntityType<? extends ShadowClone> type, Level level) {
        super(type, level);
        xpReward = 0;
        sealBeast(0); // a clone is never a jinchūriki of its own
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        setCustomName(owner.getDisplayName());
        if (owner instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(data -> {
                setCombatStats(data.getSpeed(), data.getTaijutsu());
                if (data.identity().isAssigned() && player.getServer() != null) {
                    WorldClans.get(player.getServer()).find(data.identity().clanId()).ifPresent(this::joinClan);
                }
            });
        } else if (owner instanceof SparringNinja ninja) {
            setCombatStats(ninja.combatSpeed(), ninja.combatTaijutsu());
            if (owner.getServer() != null) {
                WorldClans.get(owner.getServer()).find(ninja.clanId()).ifPresent(this::joinClan);
            }
        }
        setCombatStyle(com.noirm.combat.CombatSystem.styleFor(owner)); // fights the way you do
        sealBeast(0);
    }

    public void setLife(int ticks) {
        life = ticks > 0 ? ticks : 600;
    }

    public Optional<UUID> ownerId() {
        return entityData.get(OWNER);
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return ownerId().map(entity.getUUID()::equals).orElse(false);
    }

    /** XP its hits earned, handed to the owner when it's dispelled. */
    public void storeXp(float xp) {
        storedXp += xp;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new NinjaFightGoal(this));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (owner == null || !owner.isAlive() || owner.level() != level() || --life <= 0) {
            dispel();
            return;
        }
        // Fight what the owner fights; otherwise stay close.
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || isFriend(target)) {
            LivingEntity pick = owner.getLastHurtMob();
            if (pick == null || !pick.isAlive() || isFriend(pick)) {
                pick = owner.getLastHurtByMob();
            }
            setTarget(pick != null && pick.isAlive() && !isFriend(pick) && pick.distanceTo(this) < 24 ? pick : null);
        }
        if (getTarget() == null && distanceTo(owner) > 5) {
            getNavigation().moveTo(owner, 1.1);
        }
    }

    private boolean isFriend(LivingEntity entity) {
        return entity == owner || entity instanceof ShadowClone clone && owner != null && clone.isOwnedBy(owner);
    }

    /** Any real hit dispels it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isInvulnerableTo(source) || amount <= 0) {
            return false;
        }
        dispel();
        return true;
    }

    public void dispel() {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel level) {
            Vec3 at = position().add(0, 1, 0);
            level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 30, 0.3, 0.6, 0.3, 0.04);
            Vfx.burst(level, Vfx.smoke(0xE8E8E8, 0.9f, 30), at, 10, 0.4, 0.04);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHICKEN_EGG, getSoundSource(), 1f, 0.6f);
        }
        if (owner instanceof Player player && storedXp > 0) {
            TrainingSystem.grant(player, Stat.TAIJUTSU, storedXp);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}

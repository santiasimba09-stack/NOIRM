package com.noirm.entity;

import com.noirm.combat.CombatListener;
import com.noirm.combat.CombatMove;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatStats;
import com.noirm.player.ShinobiData;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.clan.WorldClans;
import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuUser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * A sparring partner that fights with the same combat system as the player: telegraphed
 * wind-ups, combos, guard and dodges. It challenges players who enter combat mode nearby and
 * fights back when hit. It belongs to a clan (rolled like a player's) and casts that clan's
 * jutsu; Uchiha ninjas fight with an active Sharingan. Speed, taijutsu and clan can be set with
 * {@code /summon noirm:sparring_ninja ~ ~ ~ {Speed:15,Taijutsu:15,Clan:"uchiha"}}.
 */
public class SparringNinja extends PathfinderMob implements CombatStats, CombatListener, JutsuUser {
    /** What the model should show. Synced to clients. */
    public enum Pose { IDLE, WINDUP_LIGHT, WINDUP_HEAVY, ACTIVE, GUARD, STAGGER }

    private static final EntityDataAccessor<Byte> POSE = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> CLAN_NAME = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> SHARINGAN = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BYTE);

    /** Clan id, chosen when first needed (or from the {@code Clan} NBT tag). */
    private String clanId = "";
    private final EnumMap<Nature, Float> natures = new EnumMap<>(Nature.class);
    private final List<ResourceLocation> jutsu = new ArrayList<>();

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
        entityData.define(CLAN_NAME, "");
        entityData.define(SHARINGAN, (byte) 0);
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide() && clanId.isEmpty() && level().getServer() != null) {
            joinClan(WorldClans.get(level().getServer()).rollForNewcomer(getRandom()));
        }
        super.aiStep();
    }

    /** Takes on a clan's natures, jutsu and (for the Uchiha) an active Sharingan. */
    public void joinClan(Clan clan) {
        clanId = clan.id();
        entityData.set(CLAN_NAME, clan.name());
        natures.clear();
        natures.putAll(clan.natures());
        jutsu.clear();
        for (Nature nature : natures.keySet()) {
            jutsu.addAll(JutsuData.startersFor(nature));
        }
        jutsu.addAll(JutsuData.startersFor(null));
        entityData.set(SHARINGAN, (byte) (clan.hasSharingan() ? 1 + getRandom().nextInt(3) : 0));
        if (clan.hasSharingan() && entityData.get(SHARINGAN) >= 2 && JutsuData.get(Sharingan.GENJUTSU) != null) {
            jutsu.add(Sharingan.GENJUTSU);
        }
    }

    public String clanId() {
        return clanId;
    }

    public List<ResourceLocation> knownJutsu() {
        return jutsu;
    }

    @Override
    public float jutsuAffinity(Nature nature) {
        return natures.getOrDefault(nature, 0f);
    }

    @Override
    public int activeSharinganStage() {
        return entityData.get(SHARINGAN);
    }

    /** Sets the active Sharingan (0–3 tomoe); only meaningful for clans that carry it. */
    public void setSharinganStage(int tomoe) {
        entityData.set(SHARINGAN, (byte) Mth.clamp(tomoe, 0, 3));
        if (tomoe >= 2 && JutsuData.get(Sharingan.GENJUTSU) != null && !jutsu.contains(Sharingan.GENJUTSU)) {
            jutsu.add(Sharingan.GENJUTSU);
        }
    }

    @Override
    public Component getName() {
        String clan = entityData.get(CLAN_NAME);
        return hasCustomName() || clan.isEmpty() ? super.getName()
                : Component.translatable("entity.noirm.sparring_ninja.of_clan", super.getName(), clan);
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
        tag.putString("Clan", clanId);
        tag.putByte("Sharingan", entityData.get(SHARINGAN));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCombatStats(tag.contains("Speed") ? tag.getFloat("Speed") : ShinobiData.DEFAULT_STAT,
                tag.contains("Taijutsu") ? tag.getFloat("Taijutsu") : ShinobiData.DEFAULT_STAT);
        String savedClan = tag.getString("Clan");
        if (!savedClan.isEmpty() && level().getServer() != null) {
            WorldClans.get(level().getServer()).find(savedClan).ifPresent(clan -> {
                joinClan(clan);
                if (tag.contains("Sharingan")) {
                    entityData.set(SHARINGAN, tag.getByte("Sharingan"));
                }
            });
        }
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

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
import com.noirm.biju.BijuData;
import com.noirm.biju.BijuSystem;
import com.noirm.dojutsu.Byakugan;
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
import net.minecraft.world.entity.LivingEntity;
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
    private static final EntityDataAccessor<Boolean> BYAKUGAN = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> TAILS = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> CLOAK = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BOOLEAN);
    /** A jinchūriki ninja calls on its beast below this fraction of its health. */
    public static final float CLOAK_HEALTH = 0.35f;

    private static final net.minecraft.core.particles.DustParticleOptions CLOAK_AURA =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.45f, 0.05f), 1.4f);

    /** Clan id, chosen when first needed (or from the {@code Clan} NBT tag). */
    private String clanId = "";
    private final EnumMap<Nature, Float> natures = new EnumMap<>(Nature.class);
    private final List<ResourceLocation> jutsu = new ArrayList<>();

    /** Whether the jinchūriki roll has happened (or Tails came from NBT). */
    private boolean tailsDecided;
    private boolean beastReleased;
    /** The player whose crew this ninja is in, or null. */
    private java.util.UUID leader;
    /** Beaten: waiting for {@code yieldedTo} to invite them, until {@code yieldedUntil}. */
    private java.util.UUID yieldedTo;
    private long yieldedUntil;
    /** A crew mate told to wait here. */
    private boolean waiting;
    /** Outfit: {@code "<folder>/<n>"} under {@code textures/entity/ninja/}, picked for the clan. */
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.STRING);
    /** How many outfits each folder has (mirrors {@code textures/entity/ninja/catalog.json}). */
    public static final java.util.Map<String, Integer> OUTFITS = java.util.Map.of("uchiha", 5, "senju", 4, "hyuga", 4, "uzumaki", 4, "generic", 6);
    private static final EntityDataAccessor<Boolean> IN_CREW = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BOOLEAN);
    /** Fighting style; chosen when first needed (or from the {@code Style} NBT tag). */
    private ResourceLocation style;
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
        entityData.define(BYAKUGAN, false);
        entityData.define(TAILS, (byte) 0);
        entityData.define(CLOAK, false);
        entityData.define(IN_CREW, false);
        entityData.define(SKIN, "");
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide() && clanId.isEmpty() && level().getServer() != null) {
            joinClan(WorldClans.get(level().getServer()).rollForNewcomer(getRandom()));
        }
        if (!level().isClientSide() && !tailsDecided) {
            tailsDecided = true;
            if (getRandom().nextFloat() < BijuSystem.NPC_JINCHURIKI_CHANCE) {
                sealBeast(1 + getRandom().nextInt(BijuData.MAX_TAILS));
            }
        }
        if (!level().isClientSide() && tails() > 0) {
            tickJinchuriki();
        }
        if (!level().isClientSide()) {
            tickCrew();
        }
        super.aiStep();
    }

    // ---------------------------------------------------------------- crew (allies)

    public java.util.UUID leaderId() {
        return leader;
    }

    /** The outfit texture path (under {@code textures/entity/ninja/}), or "" before a clan is known. */
    public String outfit() {
        return entityData.get(SKIN);
    }

    protected void setOutfit(String outfit) {
        entityData.set(SKIN, outfit);
    }

    public boolean inCrew() {
        return entityData.get(IN_CREW);
    }

    public boolean isYielded() {
        return yieldedTo != null && level().getGameTime() < yieldedUntil;
    }

    public void setYielded(java.util.UUID to, long until) {
        yieldedTo = to;
        yieldedUntil = until;
        setPose(net.minecraft.world.entity.Pose.CROUCHING); // down on one knee
    }

    public void joinCrew(java.util.UUID player) {
        leader = player;
        yieldedTo = null;
        waiting = false;
        entityData.set(IN_CREW, true);
        setTarget(null);
        setPose(net.minecraft.world.entity.Pose.STANDING);
        setPersistenceRequired();
    }

    public boolean isWaiting() {
        return waiting;
    }

    private net.minecraft.world.entity.player.Player leaderPlayer() {
        return leader == null ? null : level().getPlayerByUUID(leader);
    }

    private void tickCrew() {
        if (yieldedTo != null && !isYielded()) {
            yieldedTo = null; // nobody answered: back on their feet
            setPose(net.minecraft.world.entity.Pose.STANDING);
        }
        if (isYielded()) {
            setTarget(null);
            getNavigation().stop();
            return;
        }
        var player = leaderPlayer();
        if (player == null || !player.isAlive()) {
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && com.noirm.ally.Crew.sameSide(this, target)) {
            setTarget(null);
            target = null;
        }
        // Fight what the leader fights, and whoever attacks them.
        if (target == null || !target.isAlive()) {
            LivingEntity pick = player.getLastHurtMob();
            if (pick == null || !pick.isAlive() || com.noirm.ally.Crew.sameSide(this, pick) || player.tickCount - player.getLastHurtMobTimestamp() > 200) {
                pick = player.getLastHurtByMob();
            }
            if (pick != null && pick.isAlive() && !com.noirm.ally.Crew.sameSide(this, pick) && pick.distanceTo(this) < 24) {
                setTarget(pick);
            }
        }
        if (getTarget() == null) {
            double distance = distanceTo(player);
            if (!waiting && distance > com.noirm.ally.Crew.TELEPORT_DISTANCE && player.onGround()) {
                teleportTo(player.getX(), player.getY(), player.getZ());
                getNavigation().stop();
            } else if (!waiting && distance > 5) {
                getNavigation().moveTo(player, distance > 12 ? 1.25 : 1.0);
            }
            if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) {
                heal(com.noirm.ally.Crew.REGEN_PER_SECOND);
            }
        }
    }

    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        if (other instanceof LivingEntity living && leader != null && com.noirm.ally.Crew.sameSide(this, living)) {
            return true;
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && source.getEntity() instanceof LivingEntity attacker && leader != null
                && com.noirm.ally.Crew.sameSide(this, attacker)) {
            return false; // a stray hit from your own side
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && isAlive() && canYield() && source.getEntity() instanceof Player player
                && getHealth() < getMaxHealth() * com.noirm.ally.Crew.YIELD_HEALTH && !isYielded()) {
            com.noirm.ally.Crew.yieldTo(this, player);
        } else if (hurt && isYielded() && source.getEntity() != null) {
            yieldedTo = null; // attacked while yielding: they fight on
            setPose(net.minecraft.world.entity.Pose.STANDING);
        }
        return hurt;
    }

    /** Whether this ninja can be beaten into joining (not clones, not cloaked jinchūriki, not crew). */
    protected boolean canYield() {
        return leader == null && !isCloaked();
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }
        if (level().isClientSide()) {
            return isYielded() || player.getUUID().equals(leader) ? net.minecraft.world.InteractionResult.SUCCESS : super.mobInteract(player, hand);
        }
        if (isYielded() && player.getUUID().equals(yieldedTo)) {
            com.noirm.ally.Crew.recruit(player, this);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (player.getUUID().equals(leader)) {
            if (player.isShiftKeyDown()) {
                waiting = !waiting;
                getNavigation().stop();
                player.displayClientMessage(Component.translatable(waiting ? "message.noirm.crew.wait" : "message.noirm.crew.follow",
                        getDisplayName()), true);
            } else {
                player.displayClientMessage(Component.translatable("message.noirm.crew.status", getDisplayName(),
                        Math.round(getHealth()), Math.round(getMaxHealth())), true);
            }
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    /** Takes on a clan's natures, jutsu and (for the Uchiha) an active Sharingan. */
    public void joinClan(Clan clan) {
        clanId = clan.id();
        entityData.set(CLAN_NAME, clan.name());
        String folder = OUTFITS.containsKey(clan.id()) ? clan.id() : "generic";
        if (!entityData.get(SKIN).startsWith(folder + "/")) {
            entityData.set(SKIN, folder + "/" + getRandom().nextInt(OUTFITS.get(folder)));
        }
        natures.clear();
        natures.putAll(clan.natures());
        jutsu.clear();
        for (Nature nature : natures.keySet()) {
            jutsu.addAll(JutsuData.allFor(nature)); // ninjas have mastered their clan's natures
        }
        jutsu.addAll(JutsuData.startersFor(null));
        entityData.set(SHARINGAN, (byte) (clan.hasSharingan() ? 1 + getRandom().nextInt(3) : 0));
        if (clan.hasSharingan() && entityData.get(SHARINGAN) >= 2 && JutsuData.get(Sharingan.GENJUTSU) != null) {
            jutsu.add(Sharingan.GENJUTSU);
        }
        // Hyūga fight with the Byakugan open and know the Eight Trigrams.
        entityData.set(BYAKUGAN, clan.hasByakugan());
        if (clan.hasByakugan()) {
            Byakugan.JUTSU.stream().filter(id -> JutsuData.get(id) != null).forEach(jutsu::add);
        }
        if (tails() > 0) {
            sealBeast(tails()); // keeps the Tailed Beast Ball
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

    // ---------------------------------------------------------------- jinchūriki

    public int tails() {
        return entityData.get(TAILS);
    }

    /** Seals a tailed beast into this ninja: it will use the cloak (and the Tailed Beast Ball) when hurt. */
    public void sealBeast(int tails) {
        tailsDecided = true;
        entityData.set(TAILS, (byte) Mth.clamp(tails, 0, BijuData.MAX_TAILS));
        if (tails > 0 && JutsuData.get(BijuSystem.BEAST_BALL) != null && !jutsu.contains(BijuSystem.BEAST_BALL)) {
            jutsu.add(BijuSystem.BEAST_BALL);
        }
    }

    public boolean isCloaked() {
        return entityData.get(CLOAK);
    }

    @Override
    public int bijuCloakStage() {
        return isCloaked() ? 1 : 0;
    }

    private void tickJinchuriki() {
        if (!isCloaked() && getTarget() != null && getHealth() < getMaxHealth() * CLOAK_HEALTH) {
            entityData.set(CLOAK, true);
            playSound(SoundEvents.RAVAGER_ROAR, 1.5f, 1.3f);
        }
        if (isCloaked() && tickCount % 2 == 0 && level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(CLOAK_AURA, getX(), getY() + 1, getZ(), 4, 0.35, 0.7, 0.35, 0);
        }
    }

    /** When a jinchūriki dies, its beast breaks free, and it remembers who did it. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) {
            com.noirm.ally.Crew.onDeath(this);
        }
        if (beastReleased || level().isClientSide() || tails() == 0 || !(level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        beastReleased = true;
        TailedBeast beast = com.noirm.registry.NoirmEntities.TAILED_BEAST.get().create(level);
        if (beast == null) {
            return;
        }
        beast.setTails(tails());
        beast.moveTo(getX(), getY(), getZ(), getYRot(), 0f);
        if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity killer) {
            beast.setTarget(killer);
        }
        level.addFreshEntity(beast);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.HOSTILE, 3f, 0.5f);
        for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(48))) {
            player.sendSystemMessage(Component.translatable("message.noirm.beast.breaks_free", BijuSystem.beastName(tails()))
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    @Override
    public boolean byakuganActive() {
        return entityData.get(BYAKUGAN);
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
        if (inCrew() && !hasCustomName()) {
            Component base = clan.isEmpty() ? super.getName() : Component.translatable("entity.noirm.sparring_ninja.of_clan", super.getName(), clan);
            return Component.translatable("entity.noirm.sparring_ninja.crew", base);
        }
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
            return leader == null && !isYielded() && state != null && state.isCombatMode();
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

    @Override
    public ResourceLocation combatStyle() {
        if (style == null && !level().isClientSide()) {
            style = com.noirm.combat.StyleSystem.pickForNpc(getRandom(), taijutsu, byakuganActive());
        }
        return style;
    }

    public void setCombatStyle(ResourceLocation style) {
        this.style = style;
    }

    public void setCombatStats(float speed, float taijutsu) {
        this.speed = Mth.clamp(speed, 1f, ShinobiData.MAX_STAT);
        this.taijutsu = Mth.clamp(taijutsu, 1f, ShinobiData.MAX_STAT);
        var movement = getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.setBaseValue(0.32 * com.noirm.player.Movement.runMultiplier(this.speed));
        }
        // Stronger ninjas have more health too (on top of their durability).
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            boolean full = getHealth() >= getMaxHealth();
            health.setBaseValue(Mth.clamp(40 + ((this.speed + this.taijutsu) / 2f - ShinobiData.DEFAULT_STAT), 20, 1024));
            if (full || getHealth() > getMaxHealth()) {
                setHealth(getMaxHealth());
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Speed", speed);
        tag.putFloat("Taijutsu", taijutsu);
        tag.putString("Clan", clanId);
        tag.putByte("Sharingan", entityData.get(SHARINGAN));
        tag.putInt("Tails", tails());
        if (style != null) {
            tag.putString("Style", style.toString());
        }
        tag.putString("Skin", outfit());
        if (leader != null) {
            tag.putUUID("Leader", leader);
            tag.putBoolean("Waiting", waiting);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCombatStats(tag.contains("Speed") ? tag.getFloat("Speed") : ShinobiData.DEFAULT_STAT,
                tag.contains("Taijutsu") ? tag.getFloat("Taijutsu") : ShinobiData.DEFAULT_STAT);
        if (tag.contains("Tails")) {
            sealBeast(tag.getInt("Tails"));
        }
        if (!tag.getString("Skin").isEmpty()) {
            entityData.set(SKIN, tag.getString("Skin"));
        }
        if (tag.hasUUID("Leader")) {
            joinCrew(tag.getUUID("Leader"));
            waiting = tag.getBoolean("Waiting");
        }
        String savedClan = tag.getString("Clan");
        ResourceLocation savedStyle = ResourceLocation.tryParse(tag.getString("Style"));
        if (!savedClan.isEmpty() && level().getServer() != null) {
            WorldClans.get(level().getServer()).find(savedClan).ifPresent(clan -> {
                joinClan(clan);
                if (tag.contains("Sharingan")) {
                    entityData.set(SHARINGAN, tag.getByte("Sharingan"));
                }
            });
        }
        if (!tag.getString("Style").isEmpty() && savedStyle != null) {
            style = savedStyle;
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

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
public class SparringNinja extends PathfinderMob implements CombatStats, CombatListener, JutsuUser, com.noirm.combat.CombatAnimated,
        com.noirm.chakra.WaterWalker {
    /** What the model should show. Synced to clients. */
    public enum Pose { IDLE, WINDUP_LIGHT, WINDUP_HEAVY, ACTIVE, GUARD, STAGGER }

    private static final EntityDataAccessor<Byte> POSE = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> CLAN_NAME = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> SHARINGAN = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> BYAKUGAN = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> TAILS = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> CLOAK = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BOOLEAN);
    /** The keyframe animation the client should play (same files as players), its length, and a counter that restarts it. */
    private static final EntityDataAccessor<String> ANIMATION = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIMATION_TICKS = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIMATION_SEQ = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.INT);
    /** Fighting style id (for the stance loop) and whether they're in a fight right now. */
    private static final EntityDataAccessor<String> STYLE = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> FIGHTING = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BOOLEAN);
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
    /** Their look: a {@link NinjaLook} key ({@code "<clan folder>:<hex seed>"}), picked for the clan. */
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.STRING);
    /** Whether their clan gear was handed out already (see {@code gear/ClanOutfits}). */
    private boolean gearRolled;
    private static final EntityDataAccessor<Boolean> IN_CREW = SynchedEntityData.defineId(SparringNinja.class, EntityDataSerializers.BOOLEAN);
    /** Fighting style; chosen when first needed (or from the {@code Style} NBT tag). */
    private ResourceLocation style;
    private float speed = ShinobiData.DEFAULT_STAT;
    private float taijutsu = ShinobiData.DEFAULT_STAT;
    /** Below 0: "same as their speed/taijutsu average" (until they train it on their own). */
    private float vitality = -1;
    private float reserves = -1;
    /** Training XP per stat (crew mates grow; see {@code AllyTraining}). */
    private final float[] statXp = new float[com.noirm.training.TrainingSystem.Stat.values().length];
    private com.noirm.ally.Order order = com.noirm.ally.Order.FOLLOW;
    /** Jutsu their leader taught them (kept on top of their clan's). */
    private final List<ResourceLocation> taught = new ArrayList<>();
    /** Respect for their leader, 0–100 (see {@code AllyMind}). */
    private float respect = 50f;
    private com.noirm.ally.Trait trait;
    /** Jutsu mastery XP (crew mates master what they use). */
    private final java.util.Map<ResourceLocation, Integer> jutsuXp = new java.util.HashMap<>();
    /** The jutsu a crew mate has equipped (up to 4); empty = they pick from what they know. */
    private final List<ResourceLocation> loadout = new ArrayList<>();
    public static final int LOADOUT_SLOTS = 4;
    /**
     * Lives in a village (placed by world generation). Residents never challenge players just for
     * entering combat mode; they fight back when attacked, or when their village is attacked.
     */
    private boolean resident;
    /** What a resident does in their village ({@code VillagePlan.Role} name; "" = just lives there). */
    private String villageRole = "";
    /** A resident's route: rooftops for runners, the wall walk for patrols (see {@code VillageRoutineGoal}). */
    private final List<net.minecraft.core.BlockPos> route = new ArrayList<>();

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
        entityData.define(ANIMATION, "");
        entityData.define(ANIMATION_TICKS, 0);
        entityData.define(ANIMATION_SEQ, 0);
        entityData.define(STYLE, "");
        entityData.define(FIGHTING, false);
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide() && clanId.isEmpty() && rollsClan() && level().getServer() != null) {
            Clan rolled = WorldClans.get(level().getServer()).rollForNewcomer(getRandom());
            joinClan(rolled);
            com.noirm.dojutsu.SharinganAwakening.rollNpcDormant(this, rolled); // some Uchiha haven't awakened yet
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
            if (!isYielded()) {
                Threat.tick(this); // turn on whoever is hurting them most
            }
            tickChakra();
            com.noirm.ally.AllyFood.tickSelfFeed(this);
            syncAnimationState();
        }
        super.aiStep();
    }

    // ---------------------------------------------------------------- chakra (NPCs pay for their jutsu too)

    /** Chakra regained per tick, and how much faster out of a fight. */
    public static final float CHAKRA_REGEN_PER_TICK = 0.25f;
    public static final float CHAKRA_REGEN_RESTING = 2f;
    /** The least max chakra a ninja has, however low their reserves. */
    public static final float MIN_MAX_CHAKRA = 40f;

    /** Current chakra; below 0 = full (not spent yet). */
    private float chakra = -1f;
    /** How many jutsu they've cast (this session; for tests and tuning). */
    private int jutsuCasts;

    /** Max chakra, from reserves like a player's: 100 at 10, +5 per point above. */
    public float maxChakra() {
        return Math.max(MIN_MAX_CHAKRA, ShinobiData.DEFAULT_MAX_CHAKRA
                + (combatReserves() - ShinobiData.DEFAULT_STAT) * com.noirm.training.TrainingSystem.CHAKRA_PER_RESERVES);
    }

    public float chakra() {
        return chakra < 0 ? maxChakra() : Math.min(chakra, maxChakra());
    }

    public void setChakra(float value) {
        chakra = Mth.clamp(value, 0f, maxChakra());
    }

    /** Spends {@code cost} chakra if they have it. */
    public boolean tryUseChakra(float cost) {
        if (chakra() < cost) {
            return false;
        }
        setChakra(chakra() - cost);
        return true;
    }

    public int jutsuCasts() {
        return jutsuCasts;
    }

    /** Called by {@code JutsuCaster.npcCast} after each cast. */
    public void onJutsuCast() {
        jutsuCasts++;
    }

    private void tickChakra() {
        if (chakra >= 0 && chakra < maxChakra()) {
            setChakra(chakra + CHAKRA_REGEN_PER_TICK * (getTarget() == null ? CHAKRA_REGEN_RESTING : 1f));
        }
    }

    // ---------------------------------------------------------------- crew (allies)

    public java.util.UUID leaderId() {
        return leader;
    }

    /** Their look key ({@link NinjaLook}), or "" before a clan is known. */
    public String outfit() {
        return entityData.get(SKIN);
    }

    public void changeOutfit(String outfit) {
        setOutfit(NinjaLook.normalize(outfit));
    }

    public boolean gearRolled() {
        return gearRolled;
    }

    public void setGearRolled(boolean rolled) {
        gearRolled = rolled;
    }

    /** Leaves their leader's crew (dismissed): back to being their own ninja, no hard feelings. */
    public void leaveCrew() {
        leader = null;
        order = com.noirm.ally.Order.FOLLOW;
        entityData.set(IN_CREW, false);
        setTarget(null);
        setPose(net.minecraft.world.entity.Pose.STANDING);
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
        if (resident) {
            resident = false; // they leave the village to follow you
            clearRestriction();
        }
        yieldedTo = null;
        order = com.noirm.ally.Order.FOLLOW;
        // From now on they train their own toughness and chakra.
        vitality = combatVitality();
        reserves = combatReserves();
        entityData.set(IN_CREW, true);
        setTarget(null);
        setPose(net.minecraft.world.entity.Pose.STANDING);
        setPersistenceRequired();
    }

    public boolean isWaiting() {
        return order != com.noirm.ally.Order.FOLLOW;
    }

    public com.noirm.ally.Order order() {
        return order;
    }

    public void setOrder(com.noirm.ally.Order order) {
        this.order = order;
        getNavigation().stop();
        if (order != com.noirm.ally.Order.MEDITATE && getPose() == net.minecraft.world.entity.Pose.CROUCHING && !isYielded()) {
            setPose(net.minecraft.world.entity.Pose.STANDING);
        }
    }

    public List<ResourceLocation> taughtJutsu() {
        return taught;
    }

    // ---------------------------------------------------------------- mind (crew mates)

    public float respect() {
        return respect;
    }

    public void setRespect(float value) {
        respect = Mth.clamp(value, 0f, 100f);
    }

    public com.noirm.ally.Trait trait() {
        if (trait == null) {
            trait = com.noirm.ally.Trait.values()[getRandom().nextInt(com.noirm.ally.Trait.values().length)];
        }
        return trait;
    }

    public int jutsuMastery(ResourceLocation id) {
        return com.noirm.jutsu.JutsuBook.levelFor(jutsuXp.getOrDefault(id, 0));
    }

    public void addJutsuXp(ResourceLocation id, int amount) {
        if (leader == null) {
            return; // only crew mates grow
        }
        int before = jutsuMastery(id);
        jutsuXp.merge(id, amount, Integer::sum);
        if (jutsuMastery(id) > before) {
            var player = leaderPlayer();
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.noirm.ally.mastery", getDisplayName(),
                        Component.translatable(Sharingan.jutsuKey(id)), jutsuMastery(id)).withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        }
    }

    public List<ResourceLocation> loadout() {
        return loadout;
    }

    /** Equips {@code id} (or unequips it if it's already equipped). Returns whether it's equipped now. */
    public boolean toggleLoadout(ResourceLocation id) {
        if (loadout.remove(id)) {
            return false;
        }
        if (loadout.size() < LOADOUT_SLOTS && jutsu.contains(id)) {
            loadout.add(id);
            return true;
        }
        return false;
    }

    /** What they actually cast: their equipped jutsu, or everything they know if none are. */
    public List<ResourceLocation> castableJutsu() {
        if (leader != null && !loadout.isEmpty()) {
            return loadout;
        }
        return jutsu;
    }

    /** Their leader teaches them a jutsu. */
    public void teach(ResourceLocation id) {
        if (!taught.contains(id)) {
            taught.add(id);
        }
        if (!jutsu.contains(id)) {
            jutsu.add(id);
        }
    }

    // ---------------------------------------------------------------- stats (crew mates train)

    public float stat(com.noirm.training.TrainingSystem.Stat stat) {
        return switch (stat) {
            case SPEED -> speed;
            case TAIJUTSU -> taijutsu;
            case VITALITY -> combatVitality();
            case RESERVES -> combatReserves();
        };
    }

    public void setStat(com.noirm.training.TrainingSystem.Stat stat, float value) {
        switch (stat) {
            case SPEED -> setCombatStats(value, taijutsu);
            case TAIJUTSU -> setCombatStats(speed, value);
            case VITALITY -> {
                vitality = Mth.clamp(value, 1f, ShinobiData.MAX_STAT);
                setCombatStats(speed, taijutsu);
            }
            case RESERVES -> reserves = Mth.clamp(value, 1f, ShinobiData.MAX_STAT);
        }
    }

    public float xp(com.noirm.training.TrainingSystem.Stat stat) {
        return statXp[stat.ordinal()];
    }

    public void setXp(com.noirm.training.TrainingSystem.Stat stat, float value) {
        statXp[stat.ordinal()] = Math.max(0, value);
    }

    @Override
    public float combatVitality() {
        return vitality >= 0 ? vitality : (speed + taijutsu) / 2f;
    }

    @Override
    public float combatReserves() {
        return reserves >= 0 ? reserves : (speed + taijutsu) / 2f;
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
        if (tickCount % 100 == 0) {
            com.noirm.ally.AllyMind.drift(this, player);
        }
        com.noirm.ally.AllyMind.support(this, player);
        com.noirm.ally.NatureTeaching.tick(this, player);
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
        if (order == com.noirm.ally.Order.TRAIN && (getTarget() == null || getTarget() instanceof TrainingDummy)) {
            if (getTarget() == null && tickCount % 20 == 0) {
                level().getEntitiesOfClass(TrainingDummy.class, getBoundingBox().inflate(24)).stream()
                        .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).ifPresent(this::setTarget);
            }
        }
        if (order == com.noirm.ally.Order.MEDITATE && getTarget() == null) {
            getNavigation().stop();
            setPose(net.minecraft.world.entity.Pose.CROUCHING);
            if (tickCount % 20 == 0) {
                com.noirm.ally.AllyTraining.onMeditate(this);
                heal(com.noirm.ally.Crew.REGEN_PER_SECOND * 2);
                if (level() instanceof net.minecraft.server.level.ServerLevel level) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, getX(), getY() + 1.2, getZ(), 6, 0.4, 0.4, 0.4, 0.4);
                }
            }
            return;
        }
        if (getTarget() == null) {
            double distance = distanceTo(player);
            boolean follows = order == com.noirm.ally.Order.FOLLOW;
            if (follows && distance > com.noirm.ally.Crew.TELEPORT_DISTANCE && player.onGround()) {
                teleportTo(player.getX(), player.getY(), player.getZ());
                getNavigation().stop();
            } else if (follows && distance > 5) {
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
        if (resident && com.noirm.world.village.VillageResidents.isResident(other)) {
            return true; // neighbours don't feud over a stray hit
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && source.getEntity() instanceof LivingEntity attacker && leader != null
                && com.noirm.ally.Crew.sameSide(this, attacker)) {
            if (attacker instanceof Player && tickCount % 10 == 0) {
                com.noirm.ally.AllyMind.respect(this, -3f, "message.noirm.ally.hurt_by_you");
            }
            return false; // a stray hit from your own side
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide()) {
            com.noirm.ally.AllyTraining.onDamageTaken(this, source.getEntity(), amount);
            if (resident) {
                com.noirm.world.village.VillageResidents.raiseAlarm(this, source.getEntity());
            }
        }
        if (hurt && !level().isClientSide() && isAlive() && canYield() && source.getEntity() instanceof Player player
                && getHealth() < getMaxHealth() * com.noirm.ally.Crew.YIELD_HEALTH && !isYielded()) {
            com.noirm.ally.Crew.yieldTo(this, player);
        } else if (hurt && isYielded() && source.getEntity() != null) {
            yieldedTo = null; // attacked while yielding: they fight on
            setPose(net.minecraft.world.entity.Pose.STANDING);
        }
        return hurt;
    }

    /** Whether a clan is rolled for this ninja when it has none (masters keep their own look and skills). */
    protected boolean rollsClan() {
        return true;
    }

    /** Whether this ninja can ever join a player's crew (masters can't). */
    public boolean recruitable() {
        return true;
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
            // (The client doesn't know the leader: a crew mate offered food is being fed, not eaten by you.)
            boolean feeding = inCrew() && !player.isShiftKeyDown() && com.noirm.ally.AllyFood.isFood(player.getItemInHand(hand), this);
            return feeding || isYielded() || player.getUUID().equals(leader) ? net.minecraft.world.InteractionResult.SUCCESS
                    : super.mobInteract(player, hand);
        }
        if (isYielded() && player.getUUID().equals(yieldedTo)) {
            com.noirm.ally.Crew.recruit(player, this);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (player.getUUID().equals(leader)) {
            // Food in hand: feed them (sneak still gives orders, anything else opens their screen).
            if (!player.isShiftKeyDown() && com.noirm.ally.AllyFood.isFood(player.getItemInHand(hand), this)) {
                com.noirm.ally.AllyFood.feed(this, player, hand);
                return net.minecraft.world.InteractionResult.CONSUME;
            }
            if (player.isShiftKeyDown()) {
                setOrder(order == com.noirm.ally.Order.FOLLOW ? com.noirm.ally.Order.WAIT : com.noirm.ally.Order.FOLLOW);
                player.displayClientMessage(Component.translatable(isWaiting() ? "message.noirm.crew.wait" : "message.noirm.crew.follow",
                        getDisplayName()), true);
            } else if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.noirm.ally.AllyMenu.open(serverPlayer, this);
            }
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    /** Takes on a clan's natures, jutsu and (for the Uchiha) an active Sharingan. */
    public void joinClan(Clan clan) {
        clanId = clan.id();
        entityData.set(CLAN_NAME, clan.name());
        if (!NinjaLook.fits(entityData.get(SKIN), clan.id())) {
            entityData.set(SKIN, NinjaLook.random(clan.id(), getRandom()));
        }
        if (!level().isClientSide() && !(this instanceof ShadowClone)) com.noirm.gear.ClanOutfits.equip(this, clan, getRandom());
        natures.clear();
        natures.putAll(clan.natures());
        jutsu.clear();
        for (Nature nature : natures.keySet()) {
            jutsu.addAll(JutsuData.allFor(nature)); // ninjas have mastered their clan's natures
        }
        allyState.learned().forEach(natures::putIfAbsent); // natures their leader taught them
        jutsu.addAll(JutsuData.startersFor(null));
        taught.stream().filter(id -> JutsuData.get(id) != null && !jutsu.contains(id)).forEach(jutsu::add);
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

    /** Forces the beast's cloak on or off (a shadow clone of a cloaked jinchūriki). */
    public void setCloaked(boolean cloaked) {
        entityData.set(CLOAK, cloaked);
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
        goalSelector.addGoal(2, new NinjaThrowGoal(this)); // throwables from range, alongside the fight
        // Residents walk back home after a fight (does nothing for ninjas without a home).
        // Village life: patrols, rooftop running, practice (does nothing for ninjas without a role).
        goalSelector.addGoal(3, new com.noirm.world.village.VillageRoutineGoal(this));
        goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // A challenge: anyone who enters combat mode nearby gets a fight.
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::wouldChallenge));
        // Village residents keep monsters out of their village.
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.monster.Monster.class, 10, true, false,
                this::wouldDefendAgainst));
    }

    /**
     * Whether this resident goes after a monster that came into the village: any vanilla monster
     * but a creeper (they'd blow the houses up), and never this mod's own creatures.
     */
    public boolean wouldDefendAgainst(LivingEntity monster) {
        if (!resident || isYielded() || monster instanceof net.minecraft.world.entity.monster.Creeper
                || monster instanceof net.minecraft.world.entity.NeutralMob || !isWithinRestriction(monster.blockPosition())) {
            return false; // (not creepers, not endermen and the like, and only inside the patch they look after)
        }
        var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(monster.getType());
        return key != null && "minecraft".equals(key.getNamespace());
    }

    /**
     * Whether this ninja picks a fight with {@code target} on its own: free ninjas challenge anyone
     * in combat mode nearby. Crew mates, beaten ninjas and village residents don't.
     */
    public boolean wouldChallenge(LivingEntity target) {
        CombatState state = CombatStates.getIfPresent(target);
        return leader == null && !resident && !isYielded() && state != null && state.isCombatMode();
    }

    // ---------------------------------------------------------------- village residents

    public boolean isResident() {
        return resident;
    }

    /**
     * Makes this ninja live in a village: it stays within {@code radius} blocks of {@code home},
     * never despawns, and only fights when it or its village is attacked.
     */
    public void makeResident(net.minecraft.core.BlockPos home, int radius) {
        resident = true;
        restrictTo(home, radius);
        setPersistenceRequired();
    }

    public String villageRole() {
        return villageRole;
    }

    public void setVillageRole(String role) {
        villageRole = role == null ? "" : role;
    }

    /** The waypoints of this resident's routine (empty for most). */
    public List<net.minecraft.core.BlockPos> route() {
        return route;
    }

    public void setRoute(List<net.minecraft.core.BlockPos> points) {
        route.clear();
        route.addAll(points);
    }

    // ---------------------------------------------------------------- animation (synced to the client model)

    @Override
    public void playCombatAnimation(ResourceLocation animation, int durationTicks) {
        entityData.set(ANIMATION, animation.toString());
        entityData.set(ANIMATION_TICKS, Math.max(1, durationTicks));
        entityData.set(ANIMATION_SEQ, entityData.get(ANIMATION_SEQ) + 1);
        entityData.set(FIGHTING, true);
    }

    /** The last combat animation started (empty if none yet). */
    public String animationId() {
        return entityData.get(ANIMATION);
    }

    public int animationTicks() {
        return entityData.get(ANIMATION_TICKS);
    }

    /** Goes up by one each time an animation starts, so the same move twice restarts it. */
    public int animationSeq() {
        return entityData.get(ANIMATION_SEQ);
    }

    /** The style as the client sees it (for the stance), e.g. {@code noirm:brawler}. */
    public String syncedStyle() {
        return entityData.get(STYLE);
    }

    /** In a fight: has a live target, or is mid-move. The client holds the style's stance then. */
    public boolean isFighting() {
        return entityData.get(FIGHTING);
    }

    private void syncAnimationState() {
        ResourceLocation current = combatStyle();
        entityData.set(STYLE, current == null ? "" : current.toString());
        LivingEntity target = getTarget();
        CombatState state = CombatStates.getIfPresent(this);
        boolean busy = state != null && state.phase() != com.noirm.combat.CombatPhase.IDLE;
        entityData.set(FIGHTING, (target != null && target.isAlive()) || busy || (state != null && state.isGuarding()));
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
            health.setBaseValue(Mth.clamp(40 + (combatVitality() - ShinobiData.DEFAULT_STAT), 20, 1024));
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
        tag.putBoolean("GearRolled", gearRolled);
        if (chakra >= 0) {
            tag.putFloat("Chakra", chakra);
        }
        if (resident) {
            tag.putBoolean("Resident", true);
            if (hasRestriction()) {
                tag.putLong("Home", getRestrictCenter().asLong());
                tag.putInt("HomeRadius", (int) getRestrictRadius());
            }
            if (!villageRole.isEmpty()) {
                tag.putString("VillageRole", villageRole);
            }
            if (!route.isEmpty()) {
                tag.putLongArray("Route", route.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
            }
        }
        tag.put("Ally", allyState.save());
        if (leader != null) {
            tag.putUUID("Leader", leader);
            tag.putString("Order", order.name());
            tag.putFloat("Vitality", vitality);
            tag.putFloat("Reserves", reserves);
            net.minecraft.nbt.CompoundTag xpTag = new net.minecraft.nbt.CompoundTag();
            for (var stat : com.noirm.training.TrainingSystem.Stat.values()) {
                xpTag.putFloat(stat.key(), xp(stat));
            }
            tag.put("StatXp", xpTag);
            net.minecraft.nbt.ListTag taughtTag = new net.minecraft.nbt.ListTag();
            taught.forEach(id -> taughtTag.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
            tag.put("Taught", taughtTag);
            tag.putFloat("Respect", respect);
            tag.putString("Trait", trait().name());
            net.minecraft.nbt.CompoundTag masteryTag = new net.minecraft.nbt.CompoundTag();
            jutsuXp.forEach((id, xp) -> masteryTag.putInt(id.toString(), xp));
            tag.put("JutsuXp", masteryTag);
            net.minecraft.nbt.ListTag loadoutTag = new net.minecraft.nbt.ListTag();
            loadout.forEach(id -> loadoutTag.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
            tag.put("Loadout", loadoutTag);
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
            entityData.set(SKIN, NinjaLook.normalize(tag.getString("Skin"))); // old "clan/n" outfits too
        }
        // Ninjas saved before clan gear existed get theirs once, when they rejoin their clan below.
        gearRolled = tag.getBoolean("GearRolled");
        chakra = tag.contains("Chakra") ? Math.max(0f, tag.getFloat("Chakra")) : -1f;
        allyState.load(tag.getCompound("Ally"));
        if (tag.getBoolean("Resident")) {
            resident = true;
            villageRole = tag.getString("VillageRole");
            route.clear();
            for (long point : tag.getLongArray("Route")) {
                route.add(net.minecraft.core.BlockPos.of(point));
            }
            if (tag.contains("Home")) {
                restrictTo(net.minecraft.core.BlockPos.of(tag.getLong("Home")), Math.max(2, tag.getInt("HomeRadius")));
            }
        }
        if (tag.hasUUID("Leader")) {
            joinCrew(tag.getUUID("Leader"));
            if (tag.contains("Order")) {
                try {
                    order = com.noirm.ally.Order.valueOf(tag.getString("Order"));
                } catch (IllegalArgumentException ignored) {
                    order = com.noirm.ally.Order.FOLLOW;
                }
            } else if (tag.getBoolean("Waiting")) {
                order = com.noirm.ally.Order.WAIT;
            }
            if (tag.contains("Vitality")) {
                vitality = tag.getFloat("Vitality");
                reserves = tag.getFloat("Reserves");
            }
            net.minecraft.nbt.CompoundTag xpTag = tag.getCompound("StatXp");
            for (var stat : com.noirm.training.TrainingSystem.Stat.values()) {
                setXp(stat, xpTag.getFloat(stat.key()));
            }
            taught.clear();
            tag.getList("Taught", net.minecraft.nbt.Tag.TAG_STRING).forEach(t -> {
                ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
                if (id != null) {
                    taught.add(id);
                }
            });
            if (tag.contains("Respect")) {
                respect = tag.getFloat("Respect");
            }
            try {
                trait = tag.contains("Trait") ? com.noirm.ally.Trait.valueOf(tag.getString("Trait")) : null;
            } catch (IllegalArgumentException ignored) {
                trait = null;
            }
            jutsuXp.clear();
            net.minecraft.nbt.CompoundTag masteryTag = tag.getCompound("JutsuXp");
            for (String key : masteryTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    jutsuXp.put(id, masteryTag.getInt(key));
                }
            }
            loadout.clear();
            tag.getList("Loadout", net.minecraft.nbt.Tag.TAG_STRING).forEach(t -> {
                ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
                if (id != null && loadout.size() < LOADOUT_SLOTS) {
                    loadout.add(id);
                }
            });
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

    // ---------------------------------------------------------------- tactics and nature lessons (ally/AllyState)

    /** Tactics, the nature being taught and natures learned (see {@code ally/NatureTeaching}). */
    private final com.noirm.ally.AllyState allyState = new com.noirm.ally.AllyState();

    public com.noirm.ally.AllyState allyState() {
        return allyState;
    }

    /** Gains a nature from a teacher (kept across saves and clan re-joins). */
    public void learnNature(Nature nature, float affinity) {
        allyState.learned().merge(nature, affinity, Math::max);
        natures.merge(nature, affinity, Math::max);
    }

    /** Every nature they have, with its affinity. */
    public java.util.Map<Nature, Float> natures() {
        return java.util.Collections.unmodifiableMap(natures);
    }

    // ---------------------------------------------------------------- water walking (chakra/WaterWalking)

    /** Ninjas walk on water, so their crew can follow you over a lake; a beaten ninja sinks. */
    @Override
    public boolean canWaterWalk() {
        return isAlive() && !isPassenger() && !isYielded();
    }
}

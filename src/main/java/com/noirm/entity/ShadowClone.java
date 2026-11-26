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
 * moment it takes a solid hit.
 *
 * <p>Wave "clones" (2026-11-26), closer to canon:
 * <ul>
 *   <li><b>Chakra is split evenly</b> between you and your clones; each clone casts from its own share and gets no
 *       more ({@link #chakraShare}). What it hasn't spent comes back to you when it pops.</li>
 *   <li><b>One solid hit</b> ({@link #SOLID_HIT} damage or more) dispels it; only a graze leaves it standing.</li>
 *   <li><b>Memories return</b>: XP from its hits, the chakra it spent, the jutsu it used and what it saw go back to you
 *       when it pops, and tire your mind ({@code training.CloneTraining}). Idle clones near you train alongside you.</li>
 *   <li><b>It thinks</b>: picks its own fights near you, scouts (an enemy it sees far from you is reported when it
 *       pops), trains on a training dummy when there's nothing to fight, and casts your jutsu.</li>
 *   <li><b>Combos</b>: a clone beside you helps shape the Rasengan ({@link #RASENGAN_CLONE_BONUS}); a clone that's
 *       struck down is a <b>feint</b> that leaves the attacker open ({@link #FEINT_STUN_TICKS}); and Clone Swap trades
 *       places with one of your clones.</li>
 * </ul>
 */
public class ShadowClone extends SparringNinja {
    public static final int MAX_PER_OWNER = 4;
    /** A hit at least this strong dispels a clone at once. */
    public static final float SOLID_HIT = 1.5f;
    /** A clone struck down with you this close leaves its attacker reeling for this long (a feint). */
    public static final int FEINT_STUN_TICKS = 16;
    public static final double FEINT_RANGE = 10;
    /** The Rasengan is this much stronger with a clone of yours this close helping to shape it. */
    public static final float RASENGAN_CLONE_BONUS = 1.5f;
    public static final double COMBO_RANGE = 4;
    /** The Multi Shadow Clone Jutsu: at most this many clones at once (a ×100 pool; half at ×10). */
    public static final int MULTI_MAX = 24;
    /** Each clone needs at least this much chakra after the split, or fewer clones come out. */
    public static final float MIN_CHAKRA_SHARE = 10f;
    /** A clone with nothing to fight trains on a training dummy this close to its owner. */
    public static final double DUMMY_RANGE = 12;
    /** Big pools make more clones: +{@link #BONUS_PER_TENFOLD} per cast (and in all) per tenfold pool, at most {@link #MAX_BONUS}. */
    public static final float BONUS_PER_TENFOLD = 2f;
    public static final int MAX_BONUS = 4;

    /**
     * Extra clones a caster's chakra pool allows: 0 for a normal pool, +2 at ×10, +4 at ×100 (an Uzumaki).
     * Players only; NPC clones use the base count.
     */
    public static int bonusClones(LivingEntity caster) {
        if (!(caster instanceof net.minecraft.world.entity.player.Player player)) {
            return 0;
        }
        float pool = com.noirm.player.ShinobiCapability.get(player).map(com.noirm.traits.Perks::poolMultiplier).orElse(1f);
        return pool <= 1f ? 0 : Math.min(MAX_BONUS, (int) Math.floor(Math.log10(pool) * BONUS_PER_TENFOLD + 1e-4));
    }

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ShadowClone.class, EntityDataSerializers.OPTIONAL_UUID);

    private LivingEntity owner;
    private int life = 600;
    private final com.noirm.training.CloneTraining.Memories memories = new com.noirm.training.CloneTraining.Memories();
    /** Chakra the clone was made with (its whole pool: it doesn't regenerate). Below 0: an NPC's normal pool. */
    private float chakraShare = -1f;
    private boolean dispelling;

    public ShadowClone(EntityType<? extends ShadowClone> type, Level level) {
        super(type, level);
        xpReward = 0;
        sealBeast(0); // a clone is never a jinchūriki of its own
    }

    /** Clones copy their owner's jutsu instead of rolling a set. */
    @Override
    protected boolean rollsPersonalJutsu() {
        return false;
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
        if (owner instanceof SparringNinja ninja) {
            setOutfit(ninja.outfit()); // looks exactly like them
        }
        // ...and wears and wields what they do (a chakra copy: nothing drops when it pops).
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            setItemSlot(slot, owner.getItemBySlot(slot).copy());
            setDropChance(slot, 0f);
        }
        setCombatStyle(com.noirm.combat.CombatSystem.styleFor(owner)); // fights the way you do
        copyPowers(owner);
        // A real body: a share of your health, at your toughness. It takes a beating before it pops.
        var health = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Math.max(MIN_HEALTH, owner.getMaxHealth() * HEALTH_SHARE));
        }
        setHealth(getMaxHealth());
    }

    /** Your jutsu, your eyes, your active mode, your beast's cloak. */
    private void copyPowers(LivingEntity owner) {
        knownJutsu().clear();
        int tails = 0;
        if (owner instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(data -> knownJutsu().addAll(data.jutsu().known()));
            tails = ShinobiCapability.get(player).map(d -> d.biju().tails()).orElse(0);
        } else if (owner instanceof SparringNinja ninja) {
            knownJutsu().addAll(ninja.knownJutsu());
            tails = ninja.tails();
        }
        knownJutsu().remove(SHADOW_CLONE_JUTSU); // clones don't make clones
        setSharinganStage(com.noirm.dojutsu.Sharingan.activeStage(owner));
        com.noirm.jutsu.ModeSystem.copyTo(owner, this);
        boolean cloaked = com.noirm.biju.BijuSystem.cloakStage(owner) > 0;
        sealBeast(cloaked ? tails : 0);
        setCloaked(cloaked);
    }

    /** A clone's health: this share of its owner's, at least {@link #MIN_HEALTH}. */
    public static final float HEALTH_SHARE = 0.4f;
    public static final float MIN_HEALTH = 12f;
    private static final net.minecraft.resources.ResourceLocation SHADOW_CLONE_JUTSU = com.noirm.Noirm.id("universal/shadow_clone");

    public void setLife(int ticks) {
        life = ticks > 0 ? ticks : 600;
    }

    public Optional<UUID> ownerId() {
        return entityData.get(OWNER);
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return ownerId().map(entity.getUUID()::equals).orElse(false);
    }

    /** Taijutsu XP its hits earned, handed to the owner when it's dispelled. */
    public void storeXp(float xp) {
        memories.add(Stat.TAIJUTSU, xp);
    }

    /** Everything it has learned so far (returned when it pops). */
    public com.noirm.training.CloneTraining.Memories memories() {
        return memories;
    }

    /** Gives it its share of the split chakra: that's all it will ever have. */
    public void setChakraShare(float share) {
        chakraShare = Math.max(0f, share);
        setChakra(chakraShare);
    }

    public float chakraShare() {
        return chakraShare;
    }

    @Override
    public float maxChakra() {
        return chakraShare >= 0 ? Math.max(1f, chakraShare) : super.maxChakra();
    }

    @Override
    protected boolean regeneratesChakra() {
        return chakraShare < 0;
    }

    /** Casting from its own share trains Reserves, like spending chakra trains yours. */
    @Override
    public boolean tryUseChakra(float cost) {
        boolean paid = super.tryUseChakra(cost);
        if (paid && cost > 0) {
            memories.add(Stat.RESERVES, cost * TrainingSystem.RESERVES_XP_PER_CHAKRA);
        }
        return paid;
    }

    @Override
    public void onJutsuCast(net.minecraft.resources.ResourceLocation id) {
        super.onJutsuCast(id);
        memories.usedJutsu(id); // practice that becomes your mastery when it pops
    }

    /** Not fighting anything real: free to train alongside its owner. */
    public boolean isTrainingIdle() {
        LivingEntity target = getTarget();
        return target == null || target instanceof TrainingDummy;
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
        if (target == null || !target.isAlive() || isFriend(target) || target instanceof TrainingDummy) {
            LivingEntity pick = owner.getLastHurtMob();
            if (pick == null || !pick.isAlive() || isFriend(pick)) {
                pick = owner.getLastHurtByMob();
            }
            if (pick != null && pick.isAlive() && !isFriend(pick) && pick.distanceTo(this) < 24) {
                setTarget(pick); // a real fight beats a dummy
            } else if (!(target instanceof TrainingDummy) || !target.isAlive()) {
                setTarget(null);
            }
        }
        LivingEntity now = getTarget();
        if (now != null && !(now instanceof TrainingDummy) && tickCount % 20 == 0) {
            memories.saw(now.getDisplayName(), now.blockPosition()); // scouting: where it last saw an enemy
        }
        if (now == null && tickCount % 20 == 0) {
            // Nothing to fight: train on a dummy near the owner, if there is one.
            level().getEntitiesOfClass(TrainingDummy.class, owner.getBoundingBox().inflate(DUMMY_RANGE), TrainingDummy::isAlive).stream()
                    .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).ifPresent(this::setTarget);
        }
        if (getTarget() == null && distanceTo(owner) > 5) {
            getNavigation().moveTo(owner, 1.1);
        }
    }

    /** The Rasengan's power with a clone of {@code caster}'s close by to help shape it. */
    public static float comboPower(LivingEntity caster, net.minecraft.resources.ResourceLocation id) {
        if (!id.equals(RASENGAN) || caster.level().isClientSide()) {
            return 1f;
        }
        boolean helper = !caster.level().getEntitiesOfClass(ShadowClone.class, caster.getBoundingBox().inflate(COMBO_RANGE),
                c -> c.isAlive() && c.isOwnedBy(caster)).isEmpty();
        return helper ? RASENGAN_CLONE_BONUS : 1f;
    }

    private static final net.minecraft.resources.ResourceLocation RASENGAN = com.noirm.Noirm.id("universal/rasengan");

    private boolean isFriend(LivingEntity entity) {
        return com.noirm.ally.Crew.sameSide(this, entity);
    }

    @Override
    protected boolean canYield() {
        return false;
    }

    /** It takes hits like a real body; the blow that would kill it dispels it instead. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isInvulnerableTo(source) || amount <= 0) {
            return false;
        }
        if (source.getEntity() instanceof LivingEntity attacker && com.noirm.ally.Crew.sameSide(this, attacker)) {
            return false;
        }
        float before = getHealth();
        boolean hurt = super.hurt(source, amount);
        if (hurt && (amount >= SOLID_HIT || getHealth() <= 1f || !isAlive() || getHealth() >= before && amount >= before)) {
            // One solid blow and it's gone. If it was struck down in a melee, the attacker swung at smoke: a feint.
            if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                    && owner != null && owner.distanceTo(this) <= FEINT_RANGE && attacker != owner) {
                com.noirm.combat.CombatSystem.stun(attacker, FEINT_STUN_TICKS);
                if (owner instanceof Player player) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.noirm.clone.feint",
                            attacker.getDisplayName()), true);
                }
            }
            dispel();
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        dispel(); // no body, no drops: just smoke
    }

    public void dispel() {
        if (isRemoved() || dispelling) {
            return;
        }
        dispelling = true;
        if (level() instanceof ServerLevel level) {
            dispelEffects(level, position().add(0, 1, 0));
        }
        if (owner instanceof Player player && !level().isClientSide()) {
            // What's left of its chakra share comes home, with its memories (and the fatigue they bring).
            if (chakraShare > 0 && !player.getAbilities().instabuild) {
                float left = chakra();
                ShinobiCapability.get(player).ifPresent(d -> d.setChakra(d.getChakra() + left));
            }
            com.noirm.training.CloneTraining.returnMemories(player, this, memories);
        }
        discard();
    }

    /** How it goes: a puff of smoke (a wood clone splinters instead). */
    protected void dispelEffects(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 30, 0.3, 0.6, 0.3, 0.04);
        Vfx.burst(level, Vfx.smoke(0xE8E8E8, 0.9f, 30), at, 10, 0.4, 0.04);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHICKEN_EGG, getSoundSource(), 1f, 0.6f);
    }

    /** The owner, if it's loaded (server side). */
    public LivingEntity ownerEntity() {
        return owner;
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

package com.noirm.entity;

import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.master.MasterData;
import com.noirm.master.MasterDefinition;
import com.noirm.master.MasterSystem;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.training.TrainingSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A master: a ninja (same body, same combat system) who teaches instead of fighting. Which master
 * comes from {@code data/noirm/masters/<id>.json} (the {@code Master} NBT tag). A master never
 * starts a fight, can't be recruited and can't be hurt, except in a spar: their test. In a spar
 * they hold back (see {@link MasterDefinition.Test}) and nobody dies: the rules are in
 * {@link MasterSystem}. Right-click one to talk (the master screen).
 */
public class MasterNinja extends SparringNinja {
    private static final EntityDataAccessor<String> MASTER_ID = SynchedEntityData.defineId(MasterNinja.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> MASTER_NAME = SynchedEntityData.defineId(MasterNinja.class, EntityDataSerializers.STRING);
    /** How far the student may stray before a spar is called off. */
    public static final double SPAR_LEASH = 32;
    /** How far a master wanders from where they were placed. */
    public static final int HOME_RADIUS = 6;

    /** The spar in progress (not saved: a reload ends it). */
    private Player partner;
    private long sparEnds;
    private int cleanHits;
    private int postureBreaks;
    private long lastBreakSeen;
    /** A spar just ended: restore full strength and health next tick. */
    private boolean restorePending;

    public MasterNinja(EntityType<? extends MasterNinja> type, Level level) {
        super(type, level);
        xpReward = 0;
        sealBeast(0); // no beast inside a master
        setPersistenceRequired();
    }

    /** A master spars with their body, not with a jutsu set. */
    @Override
    protected boolean rollsPersonalJutsu() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(MASTER_ID, "");
        entityData.define(MASTER_NAME, "");
    }

    // ---------------------------------------------------------------- who they are

    public ResourceLocation masterId() {
        return ResourceLocation.tryParse(entityData.get(MASTER_ID));
    }

    public MasterDefinition definition() {
        return MasterData.get(masterId());
    }

    /** Becomes the master {@code id}: their look, gear, style and full strength. */
    public void setMaster(ResourceLocation id) {
        entityData.set(MASTER_ID, id.toString());
        MasterDefinition def = MasterData.get(id);
        if (def == null) {
            return;
        }
        entityData.set(MASTER_NAME, def.name());
        if (!def.look().isEmpty()) {
            setOutfit(NinjaLook.normalize(def.look()));
        }
        def.style().ifPresent(this::setCombatStyle);
        for (var entry : def.gear().entrySet()) {
            EquipmentSlot slot = slotFor(entry.getKey());
            var item = ForgeRegistries.ITEMS.getValue(entry.getValue());
            if (slot != null && item != null) {
                setItemSlot(slot, new ItemStack(item));
                setDropChance(slot, 0f);
            }
        }
        setGearRolled(true);
        knownJutsu().clear(); // a master spars with their body, not with jutsu
        applyStrength(def, 1f, null);
        setHealth(getMaxHealth());
    }

    private static EquipmentSlot slotFor(String name) {
        return switch (name) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            case "mainhand" -> EquipmentSlot.MAINHAND;
            default -> null;
        };
    }

    /**
     * Sets their stats to {@code level} of the master's own. Against a student, never below the
     * student's own stats (an even fight), and never above full strength.
     */
    private void applyStrength(MasterDefinition def, float level, Player student) {
        MasterDefinition.Stats stats = def.stats();
        ShinobiData data = student == null ? null : ShinobiCapability.get(student).orElse(null);
        float speed = scaled(stats.speed(), level, data == null ? 0 : data.getSpeed());
        float taijutsu = scaled(stats.taijutsu(), level, data == null ? 0 : data.getTaijutsu());
        float vitality = scaled(stats.vitality(), level, data == null ? 0 : data.getVitality());
        setStat(TrainingSystem.Stat.VITALITY, vitality);
        setCombatStats(speed, taijutsu);
    }

    private static float scaled(float full, float level, float floor) {
        return Math.min(full, Math.max(full * level, floor));
    }

    @Override
    public Component getName() {
        String key = entityData.get(MASTER_NAME);
        return hasCustomName() || key.isEmpty() ? super.getName() : Component.translatable(key);
    }

    // ---------------------------------------------------------------- not a fighter (unless sparring)

    @Override
    protected boolean rollsClan() {
        return false;
    }

    @Override
    public boolean recruitable() {
        return false;
    }

    @Override
    protected boolean canYield() {
        return false;
    }

    @Override
    public boolean wouldChallenge(LivingEntity target) {
        return false;
    }

    @Override
    public boolean wouldDefendAgainst(LivingEntity monster) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (entityData.get(MASTER_ID).isEmpty() && MasterData.get(MasterData.MIGHT_GUY) != null) {
            setMaster(MasterData.MIGHT_GUY); // from a plain spawn egg: the first master
        }
        if (!hasRestriction()) {
            restrictTo(blockPosition(), HOME_RADIUS); // a master keeps to their spot, so you can find them
        }
        if (restorePending) {
            restoreAfterSpar();
        }
        if (partner != null) {
            tickSpar();
        } else if (getTarget() != null) {
            setTarget(null); // a master only fights in a spar
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) {
            return super.hurt(source, amount);
        }
        boolean fromPartner = partner != null && source.getEntity() == partner;
        if (!fromPartner && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (source.getEntity() instanceof Player player && tickCount % 10 == 0) {
                player.displayClientMessage(Component.translatable("message.noirm.master.not_like_that", getDisplayName()), true);
            }
            return false; // outside a spar, a master simply isn't hit
        }
        CombatState state = CombatStates.getIfPresent(this);
        boolean clean = state == null || CombatSystem.classify(this, state, source.getEntity()) == CombatSystem.Outcome.HIT;
        boolean hurt = super.hurt(source, amount);
        if (hurt && clean && fromPartner && partner != null) {
            cleanHits++;
            MasterDefinition def = definition();
            if (def != null && def.test().hits() > 0 && cleanHits >= def.test().hits()) {
                endSpar(true, "message.noirm.master.passed_hits");
            }
        }
        return hurt;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (partner != null) {
            player.displayClientMessage(Component.translatable("message.noirm.master.focus"), true);
        } else if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.noirm.master.MasterPackets.open(serverPlayer, this);
        }
        return InteractionResult.CONSUME;
    }

    // ---------------------------------------------------------------- the spar

    public boolean isSparring() {
        return partner != null;
    }

    public Player sparPartner() {
        return partner;
    }

    public int cleanHits() {
        return cleanHits;
    }

    public int postureBreaks() {
        return postureBreaks;
    }

    /** Ticks left in the spar. */
    public int sparTicksLeft() {
        return partner == null ? 0 : (int) Math.max(0, sparEnds - level().getGameTime());
    }

    /** Starts the test against {@code student} (see {@link MasterSystem#startSpar}). */
    public void beginSpar(Player student) {
        MasterDefinition def = definition();
        if (def == null) {
            return;
        }
        partner = student;
        restorePending = false;
        sparEnds = level().getGameTime() + def.test().seconds() * 20L;
        cleanHits = 0;
        postureBreaks = 0;
        lastBreakSeen = CombatSystem.lastPostureBreak(this);
        applyStrength(def, def.test().level(), student);
        setHealth(getMaxHealth());
        setTarget(student);
    }

    private void tickSpar() {
        MasterDefinition def = definition();
        if (def == null || !partner.isAlive() || partner.isRemoved() || partner.level() != level() || partner.distanceTo(this) > SPAR_LEASH) {
            endSpar(false, "message.noirm.master.called_off");
            return;
        }
        long broke = CombatSystem.lastPostureBreak(this);
        if (broke > lastBreakSeen) {
            lastBreakSeen = broke;
            postureBreaks++;
            if (def.test().postureBreaks() > 0 && postureBreaks >= def.test().postureBreaks()) {
                endSpar(true, "message.noirm.master.passed_posture");
                return;
            }
        }
        if (level().getGameTime() >= sparEnds) {
            endSpar(false, "message.noirm.master.time_up");
            return;
        }
        setTarget(partner);
        if (tickCount % 10 == 0) {
            partner.displayClientMessage(Component.translatable("message.noirm.master.progress",
                    cleanHits, def.test().hits(), postureBreaks, def.test().postureBreaks(), sparTicksLeft() / 20), true);
        }
    }

    /**
     * Ends the spar; a pass is recorded on the student. They're back to full strength and health
     * on their next tick (this can run in the middle of a hit, before its damage is applied).
     */
    public void endSpar(boolean passed, String messageKey) {
        Player student = partner;
        partner = null;
        restorePending = true;
        setTarget(null);
        CombatSystem.setGuard(this, false);
        if (student != null) {
            MasterSystem.onSparEnded(student, this, passed, messageKey);
        }
    }

    /** Back to full strength and full health after a spar. */
    public void restoreAfterSpar() {
        restorePending = false;
        MasterDefinition def = definition();
        if (def != null) {
            applyStrength(def, 1f, null);
        }
        setHealth(getMaxHealth());
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Master", entityData.get(MASTER_ID));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Master"));
        if (id != null && !tag.getString("Master").isEmpty()) {
            setMaster(id);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}

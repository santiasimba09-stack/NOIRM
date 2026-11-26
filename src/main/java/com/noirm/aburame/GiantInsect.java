package com.noirm.aburame;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * The Giant Insect (docs/ABURAME.md, 2.10): a red, yellow and black flying beetle big enough to sit on,
 * summoned at Clan Master from a Queen you raised. It flies where you look (slower than an elytra,
 * steadier), carries three, and dives into and out of water. Sneak and use it to send it away.
 */
public class GiantInsect extends Mob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(GiantInsect.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final int SEATS = 3;
    public static final float SPEED = 0.42f;

    public GiantInsect(EntityType<? extends GiantInsect> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FLYING_SPEED, 0.6);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
    }

    public void setOwner(Player player) {
        entityData.set(OWNER, Optional.of(player.getUUID()));
    }

    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        boolean mine = owner().map(player.getUUID()::equals).orElse(true);
        if (player.isShiftKeyDown() && mine) {
            ejectPassengers();
            discard(); // back to its rest
            return InteractionResult.CONSUME;
        }
        if (getPassengers().size() < SEATS) {
            player.startRiding(this);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < SEATS;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity first = getFirstPassenger();
        return first instanceof Player p && owner().map(p.getUUID()::equals).orElse(true) ? p : null;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        int i = getPassengers().indexOf(passenger);
        double back = 0.6 - i * 0.8;
        Vec3 off = new Vec3(0, 0, back).yRot(-getYRot() * ((float) Math.PI / 180f));
        move.accept(passenger, getX() + off.x, getY() + getBbHeight() * 0.75, getZ() + off.z);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 input) {
        float forward = player.zza <= 0 ? player.zza * 0.25f : player.zza;
        return new Vec3(player.xxa * 0.5f, 0, forward);
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        setRot(player.getYRot(), player.getXRot() * 0.5f);
        yRotO = yBodyRot = yHeadRot = getYRot();
        Vec3 dm = getDeltaMovement();
        if (player.zza > 0) {
            double climb = -Math.sin(Math.toRadians(player.getXRot())) * 0.45;
            setDeltaMovement(dm.x, climb, dm.z); // it flies where you look
        } else {
            setDeltaMovement(dm.x, dm.y * 0.7, dm.z);
        }
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return SPEED;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isVehicle()) {
            setDeltaMovement(getDeltaMovement().multiply(0.8, 0.6, 0.8)); // hovers when no one rides it
        }
        fallDistance = 0;
    }

    @Override
    public boolean causeFallDamage(float distance, float mul, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true; // it dives in and out of water (the Boruto scene)
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        owner().ifPresent(id -> tag.putUUID("Owner", id));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        }
    }
}

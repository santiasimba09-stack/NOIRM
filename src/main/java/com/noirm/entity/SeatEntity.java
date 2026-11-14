package com.noirm.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;

/**
 * An invisible seat on a bench, stool or step: a villager sits by riding it (the trick other village
 * mods use). It goes away as soon as nobody is sitting on it, or if the block under it is broken.
 * (It's saved with the world like any vehicle, so whoever sits on it is saved too.)
 */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        var under = level().getBlockState(BlockPos.containing(getX(), getY() + 0.3, getZ()));
        boolean seat = under.getBlock() instanceof StairBlock || under.getBlock() instanceof SlabBlock;
        if (getPassengers().isEmpty() || !seat || tickCount > 20 * 120) {
            ejectPassengers();
            discard();
        }
    }

    /** Riders sit with their hips at the top of a half-block seat. */
    @Override
    public double getPassengersRidingOffset() {
        return -0.25;
    }
}

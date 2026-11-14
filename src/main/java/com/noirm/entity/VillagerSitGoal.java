package com.noirm.entity;

import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

/**
 * Now and then, by day, a villager finds a seat nearby (a bench, a stool at a table, a step: any
 * bottom-half stair at their own level with room above it), walks over and sits down for half a
 * minute or so, facing out from the seat. Hurt, called to trade or night falling: they get up.
 */
public class VillagerSitGoal extends Goal {
    private static final int REACH = 8;
    private final VillageMerchant self;
    private BlockPos seat;
    private SeatEntity riding;
    private int ticks, cooldown, sitFor;

    public VillagerSitGoal(VillageMerchant self) {
        this.self = self;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (self.isTrading() || self.chatPartner() != null || self.isBaby() || self.getRandom().nextInt(240) != 0 || !daytime()) {
            return false;
        }
        seat = findSeat();
        return seat != null;
    }

    private boolean daytime() {
        long time = self.level().getDayTime() % 24000;
        return time < 12000 && !self.level().isRaining();
    }

    /** The nearest free seat this villager can walk to, or null. */
    BlockPos findSeat() {
        BlockPos at = self.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-REACH, -1, -REACH), at.offset(REACH, 1, REACH))) {
            if (!isSeat(pos) || !self.isWithinRestriction(pos)) {
                continue;
            }
            double d = pos.distSqr(at);
            if (d < bestDistance && self.level().getEntitiesOfClass(SeatEntity.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) {
                Path path = self.getNavigation().createPath(pos, 1);
                if (path != null && path.canReach()) {
                    bestDistance = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    /** A bottom-half stair with two blocks of air above it. */
    boolean isSeat(BlockPos pos) {
        BlockState state = self.level().getBlockState(pos);
        return state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM
                && self.level().getBlockState(pos.above()).isAir() && self.level().getBlockState(pos.above(2)).isAir();
    }

    @Override
    public void start() {
        ticks = 0;
        self.getNavigation().moveTo(seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5, 0.5);
    }

    @Override
    public boolean canContinueToUse() {
        if (self.isTrading() || self.getLastHurtByMob() != null && self.tickCount - self.getLastHurtByMobTimestamp() < 100 || !daytime()) {
            return false;
        }
        if (riding != null) {
            return ticks < sitFor && self.getVehicle() == riding;
        }
        return ticks < 200 && isSeat(seat);
    }

    @Override
    public void tick() {
        ticks++;
        if (riding != null) {
            return;
        }
        if (self.distanceToSqr(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5) < 2.2) {
            sitDown();
        } else if (self.getNavigation().isDone()) {
            self.getNavigation().moveTo(seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5, 0.5);
        }
    }

    private void sitDown() {
        riding = sit(self, seat);
        if (riding != null) {
            ticks = 0;
            sitFor = 400 + self.getRandom().nextInt(500);
        }
    }

    /**
     * Sits {@code villager} on the stair at {@code seat}, facing out from it: puts an invisible
     * {@link SeatEntity} there and rides it. Returns the seat, or null if they couldn't sit.
     */
    public static SeatEntity sit(VillageMerchant villager, BlockPos seat) {
        BlockState state = villager.level().getBlockState(seat);
        if (!(state.getBlock() instanceof StairBlock)) {
            return null;
        }
        SeatEntity entity = NoirmEntities.SEAT.get().create(villager.level());
        if (entity == null) {
            return null;
        }
        entity.moveTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5);
        villager.level().addFreshEntity(entity);
        villager.getNavigation().stop();
        if (!villager.startRiding(entity, true)) {
            entity.discard();
            return null;
        }
        // Face out from the seat: away from the stair's back.
        Direction facing = state.getValue(StairBlock.FACING).getOpposite();
        villager.setYRot(facing.toYRot());
        villager.setYHeadRot(facing.toYRot());
        villager.yBodyRot = facing.toYRot();
        return entity;
    }

    @Override
    public void stop() {
        if (riding != null && self.getVehicle() == riding) {
            self.stopRiding();
        }
        riding = null;
        seat = null;
        cooldown = 600 + self.getRandom().nextInt(600);
    }

}

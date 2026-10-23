package com.noirm.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;

import java.util.EnumSet;

/**
 * A monster in the village: a villager runs home (or to their stall) and stays put until it's
 * gone; the village's ninjas deal with it. (Vanilla's flee goal looks for a spot away from the
 * monster inside the villager's home area, and a shopkeeper's is only a few blocks wide.)
 */
public class VillagerShelterGoal extends Goal {
    /** How close a monster must be to send villagers running (blocks). */
    private static final double DANGER = 12;
    private final VillageMerchant self;
    private int check;

    public VillagerShelterGoal(VillageMerchant self) {
        this.self = self;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (++check % 10 != 0 || self.home() == null) {
            return false;
        }
        return monsterNear() && !atHome();
    }

    private boolean monsterNear() {
        return !self.level().getEntitiesOfClass(Monster.class, self.getBoundingBox().inflate(DANGER),
                m -> m.isAlive() && !(m instanceof NeutralMob)).isEmpty();
    }

    private boolean atHome() {
        BlockPos home = self.home();
        return self.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) < 4;
    }

    @Override
    public void start() {
        BlockPos home = self.home();
        self.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.75);
    }

    @Override
    public boolean canContinueToUse() {
        return !atHome() && !self.getNavigation().isDone();
    }

    @Override
    public void stop() {
        self.getNavigation().stop();
    }
}

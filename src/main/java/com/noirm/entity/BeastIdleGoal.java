package com.noirm.entity;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * One idle habit of a tailed beast (docs/entities/shukaku.md, "Idle life"): scratching its belly, sifting sand,
 * laughing, yawning. It runs only when the beast is calm or alert with no target, waits a random cooldown between
 * {@code minGap} and {@code maxGap} ticks, sets the beast's {@link TailedBeast#action()} for {@code duration} ticks
 * (the renderer animates it) and clears it at the end. {@code holdStill} stops the beast walking meanwhile.
 */
final class BeastIdleGoal extends Goal {
    private final TailedBeast beast;
    private final byte action;
    private final int duration, minGap, maxGap;
    private int cooldown;
    private long endsAt;

    BeastIdleGoal(TailedBeast beast, byte action, int duration, int minGap, int maxGap, boolean holdStill) {
        this.beast = beast;
        this.action = action;
        this.duration = duration;
        this.minGap = minGap;
        this.maxGap = maxGap;
        this.cooldown = minGap / 2 + beast.getRandom().nextInt(Math.max(1, maxGap - minGap / 2)); // desynchronised from the start
        setFlags(holdStill ? EnumSet.of(Flag.MOVE, Flag.LOOK) : EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        TailedBeast.Mood mood = beast.mood();
        return (mood == TailedBeast.Mood.CALM || mood == TailedBeast.Mood.ALERT) && beast.getTarget() == null
                && !beast.isSubdued() && beast.action() == TailedBeast.ACTION_NONE;
    }

    @Override
    public boolean canContinueToUse() {
        return beast.level().getGameTime() < endsAt && beast.getTarget() == null && !beast.isSubdued() && beast.action() == action;
    }

    @Override
    public void start() {
        endsAt = beast.level().getGameTime() + duration; // game time: vanilla ticks most goals only every other tick
        beast.setAction(action);
        if (getFlags().contains(Flag.MOVE)) {
            beast.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        beast.clearAction(action);
        cooldown = minGap + beast.getRandom().nextInt(Math.max(1, maxGap - minGap));
    }

    /** For tests: ready now. */
    void readyNow() {
        cooldown = 0;
    }
}

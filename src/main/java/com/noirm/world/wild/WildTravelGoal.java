package com.noirm.world.wild;

import com.noirm.entity.SparringNinja;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * How a wild group travels when it isn't fighting: the leader walks a route of waypoints across
 * open land (now and then stopping to look around, and waiting for anyone who falls behind), and
 * the others keep their place in the formation behind it (one beside for a pair, a wedge for a
 * squad). A follower left far behind catches up with the Body Flicker. Does nothing for ninjas
 * that aren't wild.
 */
public final class WildTravelGoal extends Goal {
    /** Walking pace for the leader, catching-up pace for the others. */
    public static final double LEAD_SPEED = 0.6;
    public static final double FOLLOW_SPEED = 0.75;
    public static final double CATCH_UP_SPEED = 1.0;
    /** Waypoints are this far apart (blocks). */
    public static final int LEG_MIN = 16;
    public static final int LEG_MAX = 32;
    /** A follower this far from the leader flickers to it. */
    public static final double FLICKER_DISTANCE = 32;
    /** Formation: side gap and row gap (blocks). */
    public static final double SIDE_GAP = 1.8;
    public static final double ROW_GAP = 2.2;

    private final SparringNinja ninja;
    private BlockPos waypoint;
    private float heading = Float.NaN;
    private int legTicks;
    private int pause;
    private int repath;

    public WildTravelGoal(SparringNinja ninja) {
        this.ninja = ninja;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return WildNinjas.isWild(ninja) && ninja.getTarget() == null && !ninja.isYielded() && !ninja.isPassenger();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        ninja.getNavigation().stop();
        waypoint = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        SparringNinja leader = WildNinjas.leaderOf(ninja);
        if (leader == null || leader == ninja) {
            lead();
        } else {
            follow(leader);
        }
    }

    // ------------------------------------------------------------ the leader

    private void lead() {
        if (pause > 0) {
            pause--;
            return;
        }
        if (WildNinjas.straggling(ninja)) {
            ninja.getNavigation().stop(); // wait for the others
            return;
        }
        legTicks++;
        boolean arrived = waypoint != null && ninja.blockPosition().distSqr(waypoint) < 4;
        if (waypoint == null || arrived || legTicks > 20 * 30) {
            if (arrived && ninja.getRandom().nextFloat() < 0.35f) {
                pause = 40 + ninja.getRandom().nextInt(100); // stop and look around
                waypoint = null;
                return;
            }
            waypoint = nextWaypoint();
            legTicks = 0;
            if (waypoint == null) {
                heading += 90f;
                pause = 20;
                return;
            }
            ninja.getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, LEAD_SPEED);
            return;
        }
        if (--repath <= 0 && ninja.getNavigation().isDone()) {
            repath = 20;
            if (!ninja.getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, LEAD_SPEED)) {
                waypoint = null; // no way there: pick another
            }
        }
    }

    /** The next stop: ahead (give or take 60°), on open land outside villages; null if none is found. */
    private BlockPos nextWaypoint() {
        if (!(ninja.level() instanceof ServerLevel level)) {
            return null;
        }
        if (Float.isNaN(heading)) {
            heading = ninja.getYRot();
        }
        for (int i = 0; i < 4; i++) {
            float yaw = heading + (ninja.getRandom().nextFloat() - 0.5f) * 120f + (i > 1 ? 180f : 0f);
            double distance = LEG_MIN + ninja.getRandom().nextInt(LEG_MAX - LEG_MIN + 1);
            int x = Mth.floor(ninja.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance);
            int z = Mth.floor(ninja.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance);
            BlockPos column = new BlockPos(x, ninja.getBlockY(), z);
            if (!level.hasChunkAt(column) || !level.isPositionEntityTicking(column)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(pos.getY() - ninja.getBlockY()) <= 12 && WildSpawner.isGoodSpot(level, pos)) {
                heading = yaw;
                return pos;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ the others

    private void follow(SparringNinja leader) {
        WildNinjas.Member member = WildNinjas.member(ninja);
        int slot = member == null ? 1 : Math.max(1, member.slot);
        double distance = ninja.distanceTo(leader);
        if (distance > FLICKER_DISTANCE && leader.onGround()) {
            flicker(leader.position());
            return;
        }
        Vec3 place = slot(leader, slot);
        double off = ninja.position().distanceTo(place);
        if (off < 1.4) {
            ninja.getNavigation().stop();
            if (leader.getNavigation().isDone()) {
                ninja.getLookControl().setLookAt(leader.getX() - Mth.sin(leader.getYRot() * Mth.DEG_TO_RAD) * 8, leader.getEyeY(),
                        leader.getZ() + Mth.cos(leader.getYRot() * Mth.DEG_TO_RAD) * 8);
            }
            return;
        }
        if (--repath <= 0 || ninja.getNavigation().isDone()) {
            repath = 10;
            if (!ninja.getNavigation().moveTo(place.x, place.y, place.z, off > 8 ? CATCH_UP_SPEED : FOLLOW_SPEED)) {
                ninja.getNavigation().moveTo(leader, CATCH_UP_SPEED); // no way to the place: go to the leader
            }
        }
    }

    /**
     * Where follower {@code slot} (1, 2, 3…) stands: alternately left and right of the leader, a row
     * further back every two (slot 1 of a pair walks just beside and behind).
     */
    public static Vec3 slot(SparringNinja leader, int slot) {
        int row = (slot + 1) / 2;
        double side = (slot % 2 == 1 ? -1 : 1) * SIDE_GAP;
        float yaw = leader.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw); // forward
        double rx = -fz, rz = fx;                      // sideways
        double back = ROW_GAP * row * (slot == 1 ? 0.5 : 1);
        return new Vec3(leader.getX() + rx * side - fx * back, leader.getY(), leader.getZ() + rz * side - fz * back);
    }

    private void flicker(Vec3 target) {
        if (ninja.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, ninja.getX(), ninja.getY() + 1, ninja.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            level.sendParticles(ParticleTypes.POOF, target.x, target.y + 1, target.z, 12, 0.3, 0.5, 0.3, 0.02);
        }
        ninja.getNavigation().stop();
        ninja.teleportTo(target.x, target.y, target.z);
        ninja.setDeltaMovement(Vec3.ZERO);
    }
}

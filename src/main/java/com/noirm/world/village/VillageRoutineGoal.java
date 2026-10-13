package com.noirm.world.village;

import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * What a village ninja does when nobody is fighting: the thing that makes a village feel lived in.
 * <ul>
 *   <li><b>Runners</b> travel across the village by leaping from rooftop to rooftop, the way ninjas
 *       do in the series ({@link #leapVelocity} aims each jump).</li>
 *   <li><b>Patrols</b> walk the wall walk back and forth.</li>
 *   <li><b>Trainees</b> take turns practising on the training ground's dummies.</li>
 * </ul>
 * Fighting always comes first: the goal stands down the moment the ninja has a target.
 */
public class VillageRoutineGoal extends Goal {
    /** Air drag per tick for a mob in the air (horizontal), and the vertical drag and gravity. */
    private static final double AIR_DRAG = 0.91, Y_DRAG = 0.98, GRAVITY = 0.08;

    private final SparringNinja ninja;
    private int index;
    private int step = 1;
    private int cooldown;
    private boolean leaping;
    private int airTicks;
    private int stuckTicks;
    private long practiceUntil;
    private long nextPractice;

    public VillageRoutineGoal(SparringNinja ninja) {
        this.ninja = ninja;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    private String role() {
        return ninja.villageRole();
    }

    @Override
    public boolean canUse() {
        if (!ninja.isResident() || role().isEmpty()) {
            return false;
        }
        LivingEntity target = ninja.getTarget();
        if (target instanceof TrainingDummy && ninja.level().getGameTime() > practiceUntil) {
            ninja.setTarget(null); // practice is over
            target = null;
        }
        if (target != null) {
            return false;
        }
        return switch (role()) {
            case "RUNNER", "PATROL" -> !ninja.route().isEmpty();
            case "TRAINEE" -> ninja.level().getGameTime() >= nextPractice;
            default -> false;
        };
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        leaping = false;
        ninja.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
        }
        switch (role()) {
            case "RUNNER" -> runner();
            case "PATROL" -> patrol();
            case "TRAINEE" -> practice();
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ rooftop runners

    private void runner() {
        List<BlockPos> route = ninja.route();
        if (leaping) {
            ninja.fallDistance = 0;
            airTicks++;
            if ((ninja.onGround() && airTicks > 3) || airTicks > 80) {
                leaping = false;
                cooldown = 15 + ninja.getRandom().nextInt(40);
            }
            return;
        }
        if (cooldown > 0 || !ninja.onGround()) {
            return;
        }
        BlockPos goal = route.get(Mth.clamp(index, 0, route.size() - 1));
        Vec3 here = ninja.position();
        double dx = goal.getX() + 0.5 - here.x;
        double dz = goal.getZ() + 0.5 - here.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.5 && Math.abs(goal.getY() - here.y) < 2) {
            advance(route.size());
            cooldown = 10 + ninja.getRandom().nextInt(30);
            return;
        }
        if (flat > 24) {
            // Lost the route (knocked off, or after a fight): walk back towards it.
            if (ninja.getNavigation().isDone()) {
                ninja.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
            }
            return;
        }
        leap(new Vec3(goal.getX() + 0.5, goal.getY() + 0.1, goal.getZ() + 0.5));
    }

    private void leap(Vec3 target) {
        Vec3 velocity = leapVelocity(ninja.position(), target);
        ninja.getNavigation().stop();
        ninja.setDeltaMovement(velocity);
        ninja.hasImpulse = true;
        float yaw = (float) (Mth.atan2(target.z - ninja.getZ(), target.x - ninja.getX()) * Mth.RAD_TO_DEG) - 90f;
        ninja.setYRot(yaw);
        ninja.setYBodyRot(yaw);
        ninja.setYHeadRot(yaw);
        leaping = true;
        airTicks = 0;
        if (ninja.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, ninja.getX(), ninja.getY() + 0.1, ninja.getZ(), 4, 0.2, 0.05, 0.2, 0.02);
        }
    }

    /**
     * The launch velocity that carries a mob from {@code from} to {@code to}: pick a flight time
     * from the distance, then solve the game's own motion (drag each tick, gravity) for it.
     */
    public static Vec3 leapVelocity(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        int ticks = Mth.clamp((int) Math.round(6 + flat * 1.1), 8, 26);
        double spread = (1 - Math.pow(AIR_DRAG, ticks)) / (1 - AIR_DRAG);
        double horizontal = flat / spread;
        // Height after `ticks` is linear in the starting vertical speed: y = vy0 * a + b.
        double a = 0, b = 0, vy = 0, k = 1;
        for (int i = 0; i < ticks; i++) {
            a += k;
            k *= Y_DRAG;
            b += vy;
            vy = (vy - GRAVITY) * Y_DRAG;
        }
        double vy0 = Mth.clamp((dy - b) / a, -1, 2.5);
        double nx = flat < 1e-4 ? 0 : dx / flat, nz = flat < 1e-4 ? 0 : dz / flat;
        return new Vec3(nx * horizontal, vy0, nz * horizontal);
    }

    // ------------------------------------------------------------ patrols

    private void patrol() {
        List<BlockPos> route = ninja.route();
        BlockPos goal = route.get(Mth.clamp(index, 0, route.size() - 1));
        double d = ninja.position().distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);
        if (d < 4 || stuckTicks > 200) {
            advance(route.size());
            stuckTicks = 0;
            cooldown = 20 + ninja.getRandom().nextInt(60); // a look around before walking on
            return;
        }
        if (cooldown > 0) {
            return;
        }
        stuckTicks++;
        if (ninja.getNavigation().isDone()) {
            ninja.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 0.75);
        }
    }

    private void advance(int size) {
        if (size <= 1) {
            index = 0;
            return;
        }
        if (index + step < 0 || index + step >= size) {
            step = -step;
        }
        index += step;
    }

    // ------------------------------------------------------------ trainees

    private void practice() {
        nextPractice = ninja.level().getGameTime() + 300 + ninja.getRandom().nextInt(500);
        List<TrainingDummy> dummies = ninja.level().getEntitiesOfClass(TrainingDummy.class, ninja.getBoundingBox().inflate(12));
        if (dummies.isEmpty()) {
            return;
        }
        TrainingDummy dummy = dummies.get(ninja.getRandom().nextInt(dummies.size()));
        practiceUntil = ninja.level().getGameTime() + 100 + ninja.getRandom().nextInt(140);
        ninja.setTarget(dummy);
    }
}

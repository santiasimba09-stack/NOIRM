package com.noirm.player;

import com.noirm.Noirm;
import com.noirm.combat.CombatStats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How the speed stat moves you. A fast ninja runs faster, jumps higher (and lands softer), steps
 * up blocks without jumping, and a jump while sprinting becomes a long forward leap. All of it
 * grows gently up to speed 35, a little more up to 50, then stops (the curve below).
 *
 * <p>Jumping runs on the client for your own player, so both sides compute the same boost (the
 * client reads its speed from the last stat sync).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Movement {
    /*
     * The speed curve (owner, APX 0.1: "ninjas are way too fast"). Speed turns into movement
     * gently, then less, then not at all; what movement stops taking goes into perception
     * (see Perception.perceptionSpeed).
     *
     *   speed:     1     10     20     30     35     40     50    100+
     *   running: ×0.87  ×1.00  ×1.14  ×1.28  ×1.35  ×1.37  ×1.41  ×1.41
     *   (v0.13:  ×0.70  ×1.00  ×1.41  ×1.73  ×1.87  ×2.00  ×2.24  ×3.16, up to ×5)
     *
     * - up to GENTLE_UNTIL (35): +1.4% running per point;
     * - from 35 to MOVEMENT_CAP_AT (50): only +0.4% per point (the rest goes into perception);
     * - past 50: movement stops growing (running, jumps, leaps and step-ups all stay as at 50).
     * Players, NPC ninjas and crew mates all use this (SparringNinja's movement attribute).
     */
    /** Up to this speed every point is fully movement. */
    public static final float GENTLE_UNTIL = 35f;
    /** Past this speed movement stops growing. */
    public static final float MOVEMENT_CAP_AT = 50f;
    /** Running bonus per speed point up to {@link #GENTLE_UNTIL}. */
    public static final float RUN_PER_POINT = 0.014f;
    /** Running bonus per speed point from {@link #GENTLE_UNTIL} to {@link #MOVEMENT_CAP_AT}. */
    public static final float RUN_PER_POINT_LATE = 0.004f;
    /** Never slower than this (very low speed stats). */
    public static final float MIN_RUN_MULTIPLIER = 0.7f;
    /** The most speed can add: ×1.41 at {@link #MOVEMENT_CAP_AT} and beyond. */
    public static final float MAX_RUN_MULTIPLIER = 1f + RUN_PER_POINT * (GENTLE_UNTIL - ShinobiData.DEFAULT_STAT)
            + RUN_PER_POINT_LATE * (MOVEMENT_CAP_AT - GENTLE_UNTIL);
    /** Extra jump speed per doubling of the movement stat above 10 (vanilla jump is 0.42; +0.22 at speed 50+). */
    public static final double JUMP_PER_DOUBLING = 0.11;
    public static final double MAX_JUMP_BONUS = 0.7;
    /** Forward push of a sprint-jump per doubling of speed. */
    public static final double LEAP_PER_DOUBLING = 0.22;
    public static final double MAX_LEAP = 1.3;

    /** The local player's speed stat, set from the stat sync (client side only). */
    public static volatile float clientSpeed = ShinobiData.DEFAULT_STAT;

    private Movement() {}

    /**
     * The speed stat as far as movement sees it: the stat itself up to 35, then each point counts
     * {@code RUN_PER_POINT_LATE / RUN_PER_POINT} (≈0.29) up to 50, then nothing more (≈39.3 from 50).
     * Running, jumps and leaps all grow from this, so they follow the same curve.
     */
    public static float movementStat(float speed) {
        if (speed <= GENTLE_UNTIL) {
            return speed;
        }
        return GENTLE_UNTIL + (Math.min(speed, MOVEMENT_CAP_AT) - GENTLE_UNTIL) * (RUN_PER_POINT_LATE / RUN_PER_POINT);
    }

    /** Running speed multiplier (the curve above). */
    public static float runMultiplier(float speed) {
        float bonus = (movementStat(speed) - ShinobiData.DEFAULT_STAT) * RUN_PER_POINT;
        return Mth.clamp(1f + bonus, MIN_RUN_MULTIPLIER, MAX_RUN_MULTIPLIER);
    }

    private static double doublings(float speed) {
        return Math.max(0, Math.log(movementStat(speed) / ShinobiData.DEFAULT_STAT) / Math.log(2));
    }

    public static double jumpBonus(float speed) {
        return Math.min(MAX_JUMP_BONUS, JUMP_PER_DOUBLING * doublings(speed));
    }

    public static double leap(float speed) {
        return Math.min(MAX_LEAP, LEAP_PER_DOUBLING * doublings(speed));
    }

    /** Extra step height: half a block at speed 25, a full block from {@link #MOVEMENT_CAP_AT} (50). */
    public static double stepBonus(float speed) {
        return speed >= MOVEMENT_CAP_AT ? 1.0 : speed >= 25 ? 0.5 : 0.0;
    }

    public static float speedOf(LivingEntity entity) {
        if (entity instanceof Player player) {
            if (player.level().isClientSide()) {
                return clientSpeed;
            }
            return ShinobiCapability.get(player).map(ShinobiData::getSpeed).orElse(ShinobiData.DEFAULT_STAT);
        }
        return entity instanceof CombatStats stats ? stats.combatSpeed() : ShinobiData.DEFAULT_STAT;
    }

    @SubscribeEvent
    public static void jump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player) && !(entity instanceof CombatStats)) {
            return;
        }
        float speed = speedOf(entity);
        double up = jumpBonus(speed);
        Vec3 motion = entity.getDeltaMovement();
        Vec3 push = Vec3.ZERO;
        if (entity.isSprinting()) {
            double leap = leap(speed);
            Vec3 facing = Vec3.directionFromRotation(0, entity.getYRot());
            push = new Vec3(facing.x * leap, 0, facing.z * leap);
            up *= 0.6; // a leap goes long rather than high
        }
        entity.setDeltaMovement(motion.x + push.x, motion.y + up, motion.z + push.z);
    }

    /** Fast ninjas land like ninjas: the height their legs can take doesn't hurt. */
    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        float speed = speedOf(event.getEntity());
        double safe = 10 * jumpBonus(speed) + 2 * leap(speed);
        if (safe > 0) {
            event.setDistance((float) Math.max(0, event.getDistance() - safe));
        }
    }
}

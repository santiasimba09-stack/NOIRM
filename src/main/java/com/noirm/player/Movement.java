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
 * up blocks without jumping, and a jump while sprinting becomes a long forward leap. Everything
 * grows fast at first and then levels off, so the world can still keep up with legends.
 *
 * <p>Jumping runs on the client for your own player, so both sides compute the same boost (the
 * client reads its speed from the last stat sync).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Movement {
    /** Running speed: ×√(speed/10), so ×1.4 at 20, ×3.2 at 100, capped at ×5. */
    public static final float MAX_RUN_MULTIPLIER = 5f;
    /** Extra jump speed per doubling of speed above 10 (vanilla jump is 0.42). */
    public static final double JUMP_PER_DOUBLING = 0.11;
    public static final double MAX_JUMP_BONUS = 0.7;
    /** Forward push of a sprint-jump per doubling of speed. */
    public static final double LEAP_PER_DOUBLING = 0.22;
    public static final double MAX_LEAP = 1.3;

    /** The local player's speed stat, set from the stat sync (client side only). */
    public static volatile float clientSpeed = ShinobiData.DEFAULT_STAT;

    private Movement() {}

    public static float runMultiplier(float speed) {
        return Mth.clamp((float) Math.sqrt(speed / ShinobiData.DEFAULT_STAT), 0.7f, MAX_RUN_MULTIPLIER);
    }

    private static double doublings(float speed) {
        return Math.max(0, Math.log(speed / ShinobiData.DEFAULT_STAT) / Math.log(2));
    }

    public static double jumpBonus(float speed) {
        return Math.min(MAX_JUMP_BONUS, JUMP_PER_DOUBLING * doublings(speed));
    }

    public static double leap(float speed) {
        return Math.min(MAX_LEAP, LEAP_PER_DOUBLING * doublings(speed));
    }

    /** Extra step height: half a block at speed 25, a full block from 60. */
    public static double stepBonus(float speed) {
        return speed >= 60 ? 1.0 : speed >= 25 ? 0.5 : 0.0;
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

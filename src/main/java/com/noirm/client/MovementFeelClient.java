package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.movement.MovementFeel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client side of {@link MovementFeel}: your own movement is worked out on your computer
 * first, so the slide, the pivot and the landing recovery happen here, where they feel instant.
 *
 * <p>Each tick, in the order the game runs it:
 * <ol>
 *   <li>tick start: a sudden speed-up since last tick means something pushed you (a dodge, a lunge,
 *       a hit), so any slide stops; meditating (or pressing its key) stops it too;</li>
 *   <li>the movement keys are read: landing recovery and a pivot weaken them;</li>
 *   <li>the game moves you and applies ground grip;</li>
 *   <li>tick end: while sliding or pivoting, the grip is eased to {@link MovementFeel#SLIDE_KEEP}.</li>
 * </ol>
 * It stays out of the way in the air, in water or lava, on ladders, swimming, gliding, flying,
 * riding, sneaking (so edges still hold you) and right after being hit.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class MovementFeelClient {
    private static int slideTicks;
    private static int pivotTicks;
    /** A pivot already happened for this turn: no second one until you face your momentum again. */
    private static boolean pivotUsed;
    private static boolean pivotThisTick;
    private static boolean inputThisTick;
    private static double lastEndSpeed;

    private MovementFeelClient() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof LocalPlayer player) || !player.isLocalPlayer()) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            double speed = player.getDeltaMovement().horizontalDistance();
            boolean pushed = speed > lastEndSpeed + MovementFeel.IMPULSE_TOLERANCE;
            if (pushed || meditating() || PowerKeys.MEDITATE.isDown() || CombatKeys.DODGE.isDown()) {
                slideTicks = 0;
                pivotTicks = 0;
            }
            pivotThisTick = false;
            inputThisTick = false;
            return;
        }
        // Tick end: the game has moved the player and applied the ground's grip.
        if (!MovementFeel.enabled(player.level()) || !canSlide(player)) {
            slideTicks = 0;
            pivotTicks = 0;
        } else if ((!inputThisTick && slideTicks > 0) || pivotThisTick) {
            if (!inputThisTick) {
                slideTicks--;
            }
            float grip = groundGrip(player);
            if (grip < MovementFeel.SLIDE_KEEP) { // on ice the ground is already slipperier: leave it
                Vec3 motion = player.getDeltaMovement();
                double scale = MovementFeel.SLIDE_KEEP / grip;
                player.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
            }
        }
        lastEndSpeed = player.getDeltaMovement().horizontalDistance();
    }

    /** The movement keys, just read: remember them, and weaken them for a landing or a pivot. */
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        Input input = event.getInput();
        inputThisTick = input.forwardImpulse != 0 || input.leftImpulse != 0;
        if (!MovementFeel.enabled(player.level())) {
            return;
        }
        if (inputThisTick) {
            slideTicks = MovementFeel.SLIDE_TICKS;
        }
        if (MovementFeel.recovering(player)) {
            input.forwardImpulse *= MovementFeel.RECOVERY_INPUT_SCALE;
            input.leftImpulse *= MovementFeel.RECOVERY_INPUT_SCALE;
            input.jumping = false;
        }
        if (inputThisTick && player.onGround() && canSlide(player)) {
            Vec3 motion = player.getDeltaMovement();
            double speed = motion.horizontalDistance();
            Vec3 wish = wishDirection(player, input);
            double dot = speed < 1.0e-4 ? 1 : (wish.x * motion.x + wish.z * motion.z) / speed;
            if (speed >= MovementFeel.PIVOT_MIN_SPEED && dot < MovementFeel.PIVOT_DOT) {
                if (!pivotUsed) {
                    pivotUsed = true;
                    pivotTicks = MovementFeel.PIVOT_TICKS;
                }
            } else {
                pivotUsed = false;
            }
        } else if (!inputThisTick) {
            pivotUsed = false;
        }
        if (pivotTicks > 0) {
            pivotTicks--;
            pivotThisTick = true;
            input.forwardImpulse *= MovementFeel.PIVOT_INPUT_SCALE;
            input.leftImpulse *= MovementFeel.PIVOT_INPUT_SCALE;
        }
    }

    /** Where the keys push, in the world (the game's own turn of the keys by your facing), normalised. */
    private static Vec3 wishDirection(LocalPlayer player, Input input) {
        float sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
        float cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
        double x = input.leftImpulse * cos - input.forwardImpulse * sin;
        double z = input.forwardImpulse * cos + input.leftImpulse * sin;
        double length = Math.sqrt(x * x + z * z);
        return length < 1.0e-4 ? Vec3.ZERO : new Vec3(x / length, 0, z / length);
    }

    /** Plain footing: on the ground, not in a fluid, on a ladder, swimming, gliding, flying, riding, sneaking or just hit. */
    private static boolean canSlide(LocalPlayer player) {
        return player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable()
                && !player.isSwimming() && !player.isFallFlying() && !player.getAbilities().flying && !player.isPassenger()
                && !player.isShiftKeyDown() && !player.isSpectator() && player.hurtTime == 0 && !player.isSleeping();
    }

    /** The share of speed the ground keeps each tick (block friction × 0.91, as the game does it). */
    private static float groundGrip(LocalPlayer player) {
        BlockPos below = BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.5000001, player.getZ());
        return player.level().getBlockState(below).getFriction(player.level(), below, player) * 0.91f;
    }

    private static boolean meditating() {
        var powers = ClientPowers.get();
        return powers != null && powers.meditating();
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        slideTicks = 0;
        pivotTicks = 0;
        pivotUsed = false;
        lastEndSpeed = 0;
        MovementFeel.clientEnabled = true;
    }
}

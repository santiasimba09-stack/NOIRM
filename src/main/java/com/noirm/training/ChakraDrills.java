package com.noirm.training;

import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chakra control drills, the canon ones (wave "clones", 2026-11-26). They train CTL (chakra control) up to the drill
 * cap ({@link TrainingSystem#DRILL_CAP}); past it, CTL comes from real use (Supplementary).
 * <ul>
 *   <li><b>Leaf on the forehead</b>: hold any leaves, crouch and keep still. Every {@link #LEAF_INTERVAL} ticks of
 *       stillness: {@link #LEAF_XP} CTL XP.</li>
 *   <li><b>Tree climbing</b>: crouch and walk into a tree trunk to climb it with chakra in your feet (it drains a
 *       little chakra). From CTL {@link #WALL_CONTROL} any solid wall works. Each second of climbing: {@link #CLIMB_XP}
 *       CTL XP.</li>
 *   <li>Water walking (chakra/WaterWalking) and the leaf-cutting nature drill (nature/NatureDrills) already existed.</li>
 * </ul>
 */
public final class ChakraDrills {
    public static final int LEAF_INTERVAL = 100;
    public static final float LEAF_XP = 2f;
    /** Moving more than this between checks breaks the leaf drill's focus. */
    public static final double LEAF_STILL = 0.05;
    /** Chakra needed to stick to a wall, and drained per tick while climbing. */
    public static final float CLIMB_MIN_CHAKRA = 5f;
    public static final float CLIMB_CHAKRA_PER_TICK = 0.1f;
    public static final float CLIMB_XP = 1f;
    /** From this CTL any solid wall can be climbed, not only tree trunks. */
    public static final float WALL_CONTROL = 30f;

    /** The client's own chakra and CTL (set by ClientShinobiData), so climbing predicts smoothly. */
    public static volatile float clientChakra;
    public static volatile float clientControl = ShinobiData.DEFAULT_STAT;

    private static final Map<Player, Vec3> STILL_AT = new WeakHashMap<>();
    private static final Map<Player, Integer> STILL_TICKS = new WeakHashMap<>();

    private ChakraDrills() {}

    // ---------------------------------------------------------------- climbing (called from the onClimbable mixin)

    /** True if {@code entity} is a player sticking to a trunk (or wall) with chakra right now. */
    public static boolean climbing(LivingEntity entity) {
        if (!(entity instanceof Player player) || !player.isShiftKeyDown() || player.isSpectator() || player.getAbilities().flying
                || !player.horizontalCollision) {
            return false;
        }
        float chakra;
        float control;
        if (player.level().isClientSide()) {
            chakra = clientChakra;
            control = clientControl;
        } else {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data == null) {
                return false;
            }
            chakra = player.getAbilities().instabuild ? CLIMB_MIN_CHAKRA : data.getChakra();
            control = data.getControl();
        }
        return chakra >= CLIMB_MIN_CHAKRA && touchingClimbable(player, control >= WALL_CONTROL);
    }

    private static boolean touchingClimbable(Player player, boolean anyWall) {
        BlockPos feet = player.blockPosition();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int up = 0; up <= 1; up++) {
                BlockPos at = feet.above(up).relative(side);
                BlockState state = player.level().getBlockState(at);
                if (state.is(BlockTags.LOGS) || anyWall && state.isFaceSturdy(player.level(), at, side.getOpposite())) {
                    // Close enough to touch: within a hair of that face.
                    double gap = side.getAxis() == Direction.Axis.X
                            ? Math.abs((side.getStepX() > 0 ? at.getX() : at.getX() + 1) - (player.getX() + side.getStepX() * player.getBbWidth() / 2))
                            : Math.abs((side.getStepZ() > 0 ? at.getZ() : at.getZ() + 1) - (player.getZ() + side.getStepZ() * player.getBbWidth() / 2));
                    if (gap < 0.1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- server tick

    public static void tick(Player player, ShinobiData data) {
        if (player.level().isClientSide()) {
            return;
        }
        if (climbing(player) && !player.onGround()) {
            if (!player.getAbilities().instabuild) {
                data.tryConsumeChakra(CLIMB_CHAKRA_PER_TICK);
            }
            if (player.tickCount % 20 == 0 && data.getControl() < TrainingSystem.DRILL_CAP) {
                Supplementary.grant(player, data, Supplementary.Stat.CONTROL, CLIMB_XP);
            }
            if (player.tickCount % 4 == 0 && player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.05, player.getZ(), 1, 0.15, 0.02, 0.15, 0.005);
            }
        }
        tickLeaf(player, data);
    }

    private static void tickLeaf(Player player, ShinobiData data) {
        boolean leaf = player.getMainHandItem().is(ItemTags.LEAVES) || player.getOffhandItem().is(ItemTags.LEAVES);
        Vec3 last = STILL_AT.get(player);
        if (!leaf || !player.isShiftKeyDown() || last == null || last.distanceTo(player.position()) > LEAF_STILL) {
            STILL_AT.put(player, player.position());
            STILL_TICKS.put(player, 0);
            return;
        }
        int ticks = STILL_TICKS.merge(player, 1, Integer::sum);
        if (ticks % LEAF_INTERVAL == 0 && data.getControl() < TrainingSystem.DRILL_CAP) {
            Supplementary.grant(player, data, Supplementary.Stat.CONTROL, LEAF_XP);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.COMPOSTER, player.getX(), player.getEyeY() + 0.25, player.getZ(), 4, 0.1, 0.05, 0.1, 0);
            }
        }
    }
}

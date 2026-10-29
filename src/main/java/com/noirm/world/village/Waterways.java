package com.noirm.world.village;

import com.noirm.world.village.VillagePlan.Bridge;
import com.noirm.world.village.VillagePlan.Landing;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Water through the village: the river the planner routed across it ({@link VillagePlan#isWater}),
 * five blocks wide and two or three deep between stone banks (mossy and cracked here and there),
 * steps down to the water every ~25 blocks, flat stone bridges with parapets where the avenues
 * cross, and red arched bridges, lanterns on their end posts, where the streets cross. Drawn after
 * the streets, for the part of the village inside the canvas's clip.
 *
 * <p>The water's surface is one block below the ground, so the river reads as a canal.
 */
final class Waterways {
    /** The top water block (one below the ground, which is at y = -1). */
    static final int SURFACE = -2;

    private static final Materials.Mix BANK = Materials.Mix.of(Blocks.STONE_BRICKS, 50, Blocks.MOSSY_STONE_BRICKS, 35, Blocks.CRACKED_STONE_BRICKS, 15);
    private static final Materials.Mix BED = Materials.Mix.of(Blocks.GRAVEL, 45, Blocks.CLAY, 25, Blocks.SAND, 20, Blocks.MOSSY_COBBLESTONE, 10);
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    private Waterways() {}

    static void draw(VillageCanvas c, VillagePlan plan) {
        if (!plan.hasRiver()) {
            return;
        }
        int[] box = c.localBounds();
        long seed = plan.seed ^ 0x7A7E_4B0DL;
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                if (plan.isWater(x, z)) {
                    channel(c, plan, x, z, seed);
                } else if (besideWater(plan, x, z)) {
                    // The bank: a stone wall from the bed up to the ground (a street keeps its paving on top).
                    for (int y = SURFACE - 3; y <= -2; y++) {
                        c.set(x, y, z, BANK.at(x, y, z, seed));
                    }
                    if (!plan.isRoad(x, z)) {
                        c.set(x, -1, z, BANK.at(x, -1, z, seed));
                    }
                }
            }
        }
        for (Landing landing : plan.landings) {
            if (inBox(box, landing.x(), landing.z(), 1)) {
                steps(c, landing);
            }
        }
        for (Bridge bridge : plan.bridges) {
            int reach = bridge.length() / 2 + 2;
            if (bridge.arched() && inBox(box, bridge.x(), bridge.z(), reach)) {
                arched(c, bridge);
            }
        }
    }

    /** One column of river: water two deep at the edges and three in the middle over a gravel and clay bed; a flat stone deck where an avenue (or a street by the wall) crosses. */
    private static void channel(VillageCanvas c, VillagePlan plan, int x, int z, long seed) {
        int depth = deep(plan, x, z) ? 3 : 2;
        c.set(x, -1, z, AIR);
        for (int y = SURFACE; y > SURFACE - depth; y--) {
            c.set(x, y, z, WATER);
        }
        c.set(x, SURFACE - depth, z, BED.at(x, SURFACE - depth, z, seed));
        if (plan.paving(x, z) != null && onFlatBridge(plan, x, z)) {
            c.set(x, -1, z, BANK.at(x, -1, z, seed + 1));
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                if (plan.isWater(nx, nz) && (plan.paving(nx, nz) == null || !onFlatBridge(plan, nx, nz))) {
                    c.set(x, 0, z, Blocks.STONE_BRICK_WALL); // a parapet along the bridge's side
                    break;
                }
            }
        }
    }

    /** Whether (x, z) is on the deck of a flat stone bridge (an avenue's, or a street's by the wall). */
    private static boolean onFlatBridge(VillagePlan plan, int x, int z) {
        for (Bridge bridge : plan.bridges) {
            if (bridge.arched()) {
                continue;
            }
            // (Round the crossing rather than along one axis, so a street crossing at a slant gets a whole deck.)
            if (Math.hypot(x - bridge.x(), z - bridge.z()) <= bridge.length() / 2.0 + 1) {
                return true;
            }
        }
        return false;
    }

    /** Whether (x, z) is well inside the river (the middle three blocks are deeper). */
    private static boolean deep(VillagePlan plan, int x, int z) {
        int wet = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (plan.isWater(x + dx, z + dz)) {
                    wet++;
                }
            }
        }
        return wet >= 20;
    }

    private static boolean besideWater(VillagePlan plan, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (plan.isWater(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inBox(int[] box, int x, int z, int margin) {
        return x >= box[0] - margin && x <= box[2] + margin && z >= box[1] - margin && z <= box[3] + margin;
    }

    /** Steps down from the bank into the water. */
    private static void steps(VillageCanvas c, Landing landing) {
        Direction down = landing.facing();
        BlockState step = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, down.getOpposite());
        c.set(landing.x(), -1, landing.z(), step);
        c.set(landing.x() + down.getStepX(), SURFACE, landing.z() + down.getStepZ(), step.setValue(StairBlock.WATERLOGGED, true));
    }

    /**
     * A red arched footbridge: a three-wide deck of mangrove stairs and planks rising two blocks over
     * the water, a curved soffit underneath, fence rails along both sides, and a lantern on each of
     * the four end posts.
     */
    private static void arched(VillageCanvas c, Bridge bridge) {
        boolean alongX = bridge.axis() == Direction.Axis.X;
        int ax = alongX ? 1 : 0, az = 1 - ax;
        int h = bridge.length() / 2;
        Block planks = Blocks.MANGROVE_PLANKS;
        Block rail = Blocks.MANGROVE_FENCE;
        for (int k = -h; k <= h; k++) {
            int e = h - Math.abs(k);
            Direction up = alongX ? (k < 0 ? Direction.EAST : Direction.WEST) : (k < 0 ? Direction.SOUTH : Direction.NORTH);
            BlockState stair = Blocks.MANGROVE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up);
            int top = e == 0 ? 1 : 2; // the height of the walking surface
            for (int j = -2; j <= 2; j++) {
                int x = bridge.x() + ax * k + az * j, z = bridge.z() + az * k + ax * j;
                if (Math.abs(j) == 2) {
                    if (e == 0) {
                        c.set(x, 0, z, rail);
                        c.set(x, 1, z, rail);
                        c.set(x, 2, z, Blocks.LANTERN);
                    } else {
                        c.set(x, top - 1, z, planks);
                        c.set(x, top, z, rail);
                    }
                    continue;
                }
                if (e == 0) {
                    c.set(x, 0, z, stair);
                } else if (e == 1) {
                    c.set(x, 0, z, planks);
                    c.set(x, 1, z, stair);
                } else {
                    c.set(x, 1, z, planks);
                    // The arch: an upturned step where it springs from the bank, open water beneath.
                    c.set(x, 0, z, e == 2 ? stair.setValue(StairBlock.FACING, up.getOpposite()).setValue(StairBlock.HALF, Half.TOP) : AIR);
                }
                for (int y = top; y <= top + 2; y++) {
                    c.set(x, y, z, AIR);
                }
            }
        }
    }
}

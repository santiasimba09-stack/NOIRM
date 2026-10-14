package com.noirm.world.village;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.SlabType;

import static com.noirm.world.village.Buildings.slab;
import static com.noirm.world.village.Buildings.stairs;

/**
 * The open ground between buildings, so a village has no idle lawns: the small yards the planner
 * fills every leftover patch with (kitchen gardens and fields, laundry, woodpiles, sheds, koi
 * ponds, bamboo, roadside shrines, wells), the park round a giant tree, and the cemetery.
 *
 * <p>Yards come in every size from 3×3 up, so each kind scales down to something sensible.
 */
final class Yards {
    private Yards() {}

    /** Draws a {@link VillagePlan.LotType#YARD} by its style (see {@code VillagePlanner.yardStyle}). */
    static void yard(LotFrame f) {
        switch (f.lot.style() % 8) {
            case 0 -> kitchenGarden(f);
            case 1 -> laundry(f);
            case 2 -> woodpile(f);
            case 3 -> shed(f);
            case 4 -> koiPond(f);
            case 5 -> bamboo(f);
            case 6 -> roadsideShrine(f);
            default -> well(f);
        }
    }

    // ================================================================ yards

    private static final Block[] CROPS = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};

    /**
     * Rows of crops on farmland with a water channel every few rows (so the farmland stays wet),
     * a low fence round the edge and, on a big field, a scarecrow.
     */
    static void kitchenGarden(LotFrame f) {
        Block crop = CROPS[f.rnd.nextInt(CROPS.length)];
        boolean fenced = f.w >= 5 && f.d >= 5;
        int inset = fenced ? 1 : 0;
        boolean alongU = f.w >= f.d;
        for (int u = inset; u < f.w - inset; u++) {
            for (int v = inset; v < f.d - inset; v++) {
                int row = alongU ? v - inset : u - inset;
                if (row % 4 == 2) {
                    f.set(u, -1, v, Blocks.WATER);
                    f.set(u, -2, v, Blocks.CLAY);
                    continue;
                }
                f.set(u, -1, v, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                Block here = f.rnd.nextInt(9) == 0 ? CROPS[f.rnd.nextInt(CROPS.length)] : crop;
                BlockState state = here.defaultBlockState();
                if (here instanceof CropBlock cropBlock) {
                    state = cropBlock.getStateForAge(Math.min(cropBlock.getMaxAge(), 2 + f.rnd.nextInt(cropBlock.getMaxAge())));
                }
                f.set(u, 0, v, state);
            }
        }
        if (fenced) {
            int gate = f.w / 2;
            for (int u = 0; u < f.w; u++) {
                for (int v = 0; v < f.d; v++) {
                    boolean edge = u == 0 || v == 0 || u == f.w - 1 || v == f.d - 1;
                    if (edge && !(v == f.d - 1 && u == gate)) {
                        f.set(u, 0, v, f.p.fence);
                        f.set(u, -1, v, Blocks.COARSE_DIRT);
                    } else if (edge) {
                        f.set(u, -1, v, Blocks.DIRT_PATH);
                    }
                }
            }
            f.set(1, 0, f.d - 2, Blocks.COMPOSTER);
        }
        if (f.w * f.d >= 70) {
            // A scarecrow: a fence post, a hay body and a pumpkin head.
            int u = f.w / 2 + 1, v = f.d / 2;
            f.set(u, -1, v, Blocks.COARSE_DIRT);
            f.set(u, 0, v, f.p.fence);
            f.set(u, 1, v, Blocks.HAY_BLOCK);
            f.set(u, 2, v, Blocks.CARVED_PUMPKIN);
        }
    }

    private static final Block[] WASHING = {Blocks.WHITE_WALL_BANNER, Blocks.LIGHT_BLUE_WALL_BANNER, Blocks.WHITE_WALL_BANNER,
            Blocks.PINK_WALL_BANNER, Blocks.YELLOW_WALL_BANNER, Blocks.LIGHT_GRAY_WALL_BANNER, Blocks.BLUE_WALL_BANNER};

    /** A washing line between two posts with the laundry out, a washtub and a basket. */
    static void laundry(LotFrame f) {
        gravel(f);
        int v = f.d / 2;
        int u1 = f.w >= 5 ? 1 : 0, u2 = f.w >= 5 ? f.w - 2 : f.w - 1;
        f.fill(u1, 0, v, u1, 2, v, f.p.fence);
        f.fill(u2, 0, v, u2, 2, v, f.p.fence);
        BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS, Direction.Axis.X);
        for (int u = u1 + 1; u < u2; u++) {
            f.set(u, 3, v, chain);
            if (f.rnd.nextInt(4) != 0) {
                f.set(u, 2, v, WASHING[f.rnd.nextInt(WASHING.length)].defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
            }
        }
        f.fill(u1, 3, v, u1, 3, v, f.p.fence);
        f.fill(u2, 3, v, u2, 3, v, f.p.fence);
        if (f.d >= 4) {
            f.set(u1, 0, v + 1 < f.d ? v + 1 : v - 1, Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 2));
            f.set(u2, 0, v - 1 >= 0 ? v - 1 : v + 1, Blocks.BARREL);
        }
    }

    /** Split logs stacked under a lean-to roof, a chopping stump and a few hay bales. */
    static void woodpile(LotFrame f) {
        gravel(f);
        BlockState log = f.p.log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        int u2 = Math.min(f.w - 1, 5);
        for (int u = 0; u <= u2; u++) {
            f.set(u, 0, 0, log);
            if (u != 0 && u != u2) {
                f.set(u, 1, 0, log);
            }
            if (f.d >= 4) {
                f.set(u, 2, 1, slab(f.p.slab, SlabType.BOTTOM)); // the lean-to roof
            }
        }
        if (f.d >= 4) {
            f.fill(0, 0, 1, 0, 1, 1, f.p.fence);
            f.fill(u2, 0, 1, u2, 1, 1, f.p.fence);
            f.fill(0, 2, 0, u2, 2, 0, slab(f.p.slab, SlabType.TOP));
        }
        int su = Math.min(f.w - 1, 2), sv = Math.min(f.d - 1, 3);
        f.set(su, 0, sv, Blocks.STRIPPED_OAK_LOG); // the chopping stump
        if (f.w >= 5 && f.d >= 5) {
            f.set(f.w - 2, 0, f.d - 2, Blocks.HAY_BLOCK);
            f.set(f.w - 2, 1, f.d - 2, Blocks.HAY_BLOCK);
            f.set(f.w - 3, 0, f.d - 2, Blocks.HAY_BLOCK);
        }
    }

    /** A little storage shed (a door, a window, a lean roof) with barrels and tools beside it. */
    static void shed(LotFrame f) {
        gravel(f);
        if (f.w < 4 || f.d < 4) {
            f.set(0, 0, 0, Blocks.BARREL);
            f.set(f.w - 1, 0, 0, Blocks.COMPOSTER);
            return;
        }
        Materials.Colourway cw = f.p.colourway(f.lot.colour());
        int u2 = Math.min(f.w - 1, 5), v2 = Math.min(f.d - 2, 4);
        f.fill(0, -1, 0, u2, -1, v2, f.p.planks);
        f.walls(0, 0, 0, u2, 2, v2, cw.accent());
        for (int u : new int[] {0, u2}) {
            for (int v : new int[] {0, v2}) {
                f.fill(u, 0, v, u, 2, v, cw.trim());
            }
        }
        f.fill(1, 0, 1, u2 - 1, 2, v2 - 1, Blocks.AIR);
        Buildings.door(f, u2 / 2, 0, v2, Direction.SOUTH);
        f.set(0, 1, v2 / 2 + 1, Blocks.GLASS_PANE);
        // A lean roof in the colourway's tiles, falling to the back.
        for (int u = -1; u <= u2 + 1; u++) {
            f.set(u, 3, v2 + 1, slab(cw.roofSlab(), SlabType.BOTTOM));
            for (int v = 0; v <= v2; v++) {
                f.set(u, 3, v, v >= v2 / 2 ? cw.roofFlat().defaultBlockState() : slab(cw.roofSlab(), SlabType.TOP));
            }
        }
        f.set(1, 0, 1, Blocks.BARREL);
        f.set(u2 - 1, 0, 1, Blocks.SMITHING_TABLE);
        f.set(1, 0, v2 - 1, Blocks.GRINDSTONE);
        if (u2 + 1 < f.w) {
            f.set(u2 + 1, 0, v2, Blocks.BARREL);
            f.set(u2 + 1, 1, v2, Blocks.BARREL);
        }
    }

    /**
     * A koi pond: an oval of water two deep over clay with lily pads, a stepping-stone path, a stone
     * lantern and a maple (a cherry, or an azalea on a small pond). The koi swim in the middle.
     */
    static void koiPond(LotFrame f) {
        int cu = (f.w - 1) / 2, cv = (f.d - 1) / 2;
        double ru = Math.max(1, (f.w - 2) / 2.0), rv = Math.max(1, (f.d - 2) / 2.0);
        for (int u = 0; u < f.w; u++) {
            for (int v = 0; v < f.d; v++) {
                double e = sq((u - cu) / ru) + sq((v - cv) / rv);
                if (e <= 1.0) {
                    boolean deep = e <= 0.45;
                    f.set(u, -1, v, Blocks.WATER);
                    f.set(u, -2, v, deep ? Blocks.WATER : Blocks.CLAY);
                    if (deep) {
                        f.set(u, -3, v, Blocks.CLAY);
                    }
                } else if (e <= 1.6) {
                    f.set(u, -1, v, (u + v) % 3 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
                } else {
                    f.set(u, -1, v, Blocks.MOSS_BLOCK);
                }
            }
        }
        f.set(cu, -1, cv, Blocks.WATER);
        if (f.w >= 5) {
            f.set(Math.max(0, cu - 1), 0, Math.min(f.d - 1, cv + 1), Blocks.LILY_PAD);
        }
        if (f.w >= 7 && f.d >= 7) {
            f.set(cu + 1, 0, cv - 1, Blocks.LILY_PAD);
            Buildings.stoneLantern(f, f.w - 1, f.d - 1);
            Block[] tree = f.p == VillagePalette.SAND ? f.p.tree(0) : new Block[] {Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES};
            treeOf(f, 0, 0, 4, tree);
        } else if (f.w >= 4) {
            f.set(f.w - 1, 0, 0, Blocks.FLOWERING_AZALEA);
        }
    }

    /** A grove of bamboo, tall and leafy, on coarse ground with a stepping-stone path through it. */
    static void bamboo(LotFrame f) {
        if (f.p == VillagePalette.SAND) {
            woodpile(f); // no bamboo in the desert
            return;
        }
        int path = f.w / 2;
        for (int u = 0; u < f.w; u++) {
            for (int v = 0; v < f.d; v++) {
                if (u == path) {
                    f.set(u, -1, v, (v & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.COARSE_DIRT);
                    continue;
                }
                f.set(u, -1, v, (u * 3 + v) % 4 == 0 ? Blocks.PODZOL : Blocks.COARSE_DIRT);
                if (f.rnd.nextInt(5) < 3) {
                    int h = 5 + f.rnd.nextInt(7);
                    for (int y = 0; y < h; y++) {
                        BambooLeaves leaves = y >= h - 2 ? BambooLeaves.LARGE : y >= h - 4 ? BambooLeaves.SMALL : BambooLeaves.NONE;
                        f.set(u, y, v, Blocks.BAMBOO.defaultBlockState().setValue(BambooStalkBlock.AGE, 1)
                                .setValue(BambooStalkBlock.LEAVES, leaves).setValue(BambooStalkBlock.STAGE, 1));
                    }
                }
            }
        }
    }

    /**
     * A roadside shrine: a little roofed box on a stone base with a lantern and offerings, a red
     * gate in front where there's room, and a sacred rope (a chain) across it.
     */
    static void roadsideShrine(LotFrame f) {
        gravel(f);
        int cu = f.w / 2;
        int back = 0;
        f.fill(cu - 1, 0, back, cu + 1, 0, back + 1, Blocks.STONE_BRICKS);
        f.set(cu, 1, back, Blocks.SPRUCE_PLANKS);
        f.set(cu - 1, 1, back, Blocks.STRIPPED_SPRUCE_LOG);
        f.set(cu + 1, 1, back, Blocks.STRIPPED_SPRUCE_LOG);
        f.set(cu, 1, back + 1, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3)
                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        f.set(cu - 1, 1, back + 1, Blocks.POTTED_RED_TULIP);
        f.set(cu + 1, 1, back + 1, Blocks.POTTED_WHITE_TULIP);
        f.fill(cu - 1, 2, back, cu + 1, 2, back + 1, Blocks.SPRUCE_PLANKS);
        for (int u = cu - 2; u <= cu + 2; u++) {
            f.set(u, 3, back, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            f.set(u, 3, back + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        }
        f.set(cu, 2, back + 2 < f.d ? back + 2 : back + 1, Buildings.HANGING_LANTERN);
        int gv = f.d - 1;
        if (f.w >= 5 && gv >= back + 4) {
            torii(f, cu, gv, 1, Blocks.RED_TERRACOTTA);
        }
    }

    /** A stone well with a little roof, a rope (chain) and a bucket rest, and a bench beside it. */
    static void well(LotFrame f) {
        gravel(f);
        int cu = Math.max(1, (f.w - 1) / 2), cv = Math.max(1, (f.d - 1) / 2);
        Materials.Mix stone = f.p.stoneMix();
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                if (du == 0 && dv == 0) {
                    f.fill(cu, -4, cv, cu, -1, cv, Blocks.WATER);
                    continue;
                }
                int u = cu + du, v = cv + dv;
                f.set(u, 0, v, stone.at(f.x(u, v), 0, f.z(u, v), f.lot.seed()));
            }
        }
        f.fill(cu - 1, 1, cv, cu - 1, 2, cv, f.p.fence);
        f.fill(cu + 1, 1, cv, cu + 1, 2, cv, f.p.fence);
        Materials.Colourway cw = f.p.colourway(f.lot.colour());
        for (int du = -1; du <= 1; du++) {
            f.set(cu + du, 3, cv - 1, slab(cw.roofSlab(), SlabType.BOTTOM));
            f.set(cu + du, 3, cv, cw.roofFlat());
            f.set(cu + du, 3, cv + 1, slab(cw.roofSlab(), SlabType.BOTTOM));
        }
        f.set(cu, 2, cv, Blocks.CHAIN);
        if (f.w >= 6) {
            f.set(cu + 3 < f.w ? cu + 3 : cu - 3, 0, cv, stairs(f.p.stairs, Direction.WEST));
        }
    }

    // ================================================================ the giant tree

    /**
     * A park round one giant tree (the kind the village children climb): a thick trunk with roots,
     * a wide crown, a pond on one side, a swing on a low branch, a sandpit and benches.
     */
    static void giantTree(LotFrame f) {
        int cu = f.w / 2, cv = f.d / 2;
        Block[] kind = f.p.tree(f.lot.style() % 4);
        Block log = kind[0];
        BlockState leaves = Buildings.leaves(kind[1]);
        boolean spruce = kind[1] == Blocks.SPRUCE_LEAVES;
        int height = 16 + f.rnd.nextInt(5);
        // Paths round and across the park.
        for (int u = 0; u < f.w; u++) {
            for (int v = 0; v < f.d; v++) {
                double d = Shapes.dist(u - cu, v - cv);
                if (d > 6.5 && d <= 7.5 || u == cu && v > cv + 6 || v == cv && u > cu + 6) {
                    f.set(u, -1, v, f.p.pavingMix(VillagePlan.Paving.PATH).at(f.x(u, v), -1, f.z(u, v), f.lot.seed()));
                }
            }
        }
        // The trunk: 3×3 at the base, 2×2 above, with roots spreading out over the ground.
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                int top = Math.abs(du) + Math.abs(dv) == 2 ? 3 : du <= 0 && dv <= 0 ? height : 6 + f.rnd.nextInt(4);
                f.fill(cu + du, -1, cv + dv, cu + du, top, cv + dv, log);
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int len = 2 + f.rnd.nextInt(3);
            for (int k = 2; k <= len + 1; k++) {
                BlockState root = log.defaultBlockState().hasProperty(RotatedPillarBlock.AXIS)
                        ? log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, side.getAxis()) : log.defaultBlockState();
                f.set(cu + side.getStepX() * k, k <= 3 ? 0 : -1, cv + side.getStepZ() * k, root);
            }
        }
        // Branches, then the crown (a few overlapping blobs so it isn't a ball).
        int[][] blobs = {{0, 0, height, 7}, {-4, -3, height - 3, 5}, {4, 2, height - 2, 5}, {2, -4, height - 4, 4}, {-3, 4, height - 4, 4}};
        for (int[] b : blobs) {
            int bu = cu + b[0], bv = cv + b[1], by = b[2], r = spruce ? b[3] - 1 : b[3];
            if (b[0] != 0 || b[1] != 0) {
                branch(f, cu, height - 6, cv, bu, by - 1, bv, log);
            }
            for (int du = -r; du <= r; du++) {
                for (int dv = -r; dv <= r; dv++) {
                    for (int dy = -r / 2; dy <= r / 2 + 1; dy++) {
                        double d = du * du + dv * dv + dy * dy * 3.0;
                        if (d <= r * r + 0.5 && (d < (r - 1) * (r - 1) || f.rnd.nextInt(5) != 0)) {
                            int u = bu + du, v = bv + dv, y = by + dy;
                            if (f.get(u, y, v).isAir()) {
                                f.set(u, y, v, leaves);
                            }
                        }
                    }
                }
            }
        }
        // The swing on a low branch, over the front path.
        int sv = Math.min(f.d - 3, cv + 5);
        BlockState beam = log.defaultBlockState().hasProperty(RotatedPillarBlock.AXIS)
                ? log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z) : log.defaultBlockState();
        f.fill(cu + 2, 6, cv + 1, cu + 2, 6, sv + 1, beam);
        f.fill(cu + 2, 3, sv, cu + 2, 5, sv, Blocks.CHAIN);
        f.set(cu + 2, 2, sv, slab(f.p.slab, SlabType.BOTTOM));
        // A pond at the back left, a sandpit at the back right, benches by the front path.
        if (f.w >= 18 && f.d >= 18) {
            for (int du = -3; du <= 3; du++) {
                for (int dv = -2; dv <= 2; dv++) {
                    if (du * du * 4 + dv * dv * 9 <= 40) {
                        f.set(3 + du, -1, 3 + dv, Blocks.WATER);
                        f.set(3 + du, -2, 3 + dv, Blocks.CLAY);
                    }
                }
            }
            f.set(3, 0, 3, Blocks.LILY_PAD);
            for (int u = f.w - 7; u <= f.w - 3; u++) {
                for (int v = 2; v <= 5; v++) {
                    boolean edge = u == f.w - 7 || u == f.w - 3 || v == 2 || v == 5;
                    f.set(u, -1, v, edge ? f.p.planks : Blocks.SAND);
                }
            }
            for (int s = -1; s <= 1; s += 2) {
                f.set(cu + s * 3, 0, f.d - 3, stairs(f.p.stairs, s < 0 ? Direction.EAST : Direction.WEST));
                Buildings.stoneLantern(f, cu + s * 4, f.d - 2);
            }
        }
    }

    /** A thick branch (a line of logs) from (u1, y1, v1) to (u2, y2, v2). */
    private static void branch(LotFrame f, int u1, int y1, int v1, int u2, int y2, int v2, Block log) {
        int n = Math.max(Math.abs(u2 - u1), Math.max(Math.abs(v2 - v1), Math.abs(y2 - y1)));
        for (int k = 0; k <= n; k++) {
            double t = n == 0 ? 0 : k / (double) n;
            f.set((int) Math.round(u1 + (u2 - u1) * t), (int) Math.round(y1 + (y2 - y1) * t), (int) Math.round(v1 + (v2 - v1) * t),
                    log.defaultBlockState());
        }
    }

    // ================================================================ the cemetery

    /**
     * The cemetery on the edge of the village: rows of headstones on gravel, a hedge with a gate, a
     * path to the memorial stone at the back (a dark stone with a flame burning before it), and
     * trees in the corners.
     */
    static void cemetery(LotFrame f) {
        BlockState hedge = Buildings.leaves(f.p.tree(1)[1]);
        int gate = f.w / 2;
        for (int u = 0; u < f.w; u++) {
            for (int v = 0; v < f.d; v++) {
                boolean edge = u == 0 || v == 0 || u == f.w - 1 || v == f.d - 1;
                if (edge) {
                    if (!(v == f.d - 1 && Math.abs(u - gate) <= 1)) {
                        f.set(u, 0, v, hedge);
                    }
                } else if (Math.abs(u - gate) <= 1) {
                    f.set(u, -1, v, Blocks.GRAVEL);
                } else {
                    f.set(u, -1, v, (u + v) % 5 == 0 ? Blocks.COARSE_DIRT : f.p.ground);
                }
            }
        }
        f.fill(gate - 2, 0, f.d - 1, gate - 2, 2, f.d - 1, f.p.log);
        f.fill(gate + 2, 0, f.d - 1, gate + 2, 2, f.d - 1, f.p.log);
        f.fill(gate - 3, 3, f.d - 1, gate + 3, 3, f.d - 1, slab(f.p.slab, SlabType.BOTTOM));
        // Rows of graves either side of the path.
        Block[] stones = {Blocks.STONE_BRICK_WALL, Blocks.ANDESITE_WALL, Blocks.COBBLESTONE_WALL, Blocks.MOSSY_STONE_BRICK_WALL};
        for (int v = 9; v <= f.d - 4; v += 3) {
            for (int u = 2; u <= f.w - 3; u += 2) {
                if (Math.abs(u - gate) <= 2) {
                    continue;
                }
                f.set(u, 0, v, stones[f.rnd.nextInt(stones.length)]);
                f.set(u, -1, v + 1, Blocks.GRAVEL);
                if (f.rnd.nextInt(4) == 0) {
                    f.set(u, 0, v + 1, f.rnd.nextBoolean() ? Blocks.POTTED_WHITE_TULIP : Blocks.POTTED_LILY_OF_THE_VALLEY);
                } else if (f.rnd.nextInt(6) == 0) {
                    f.set(u, 0, v + 1, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
                }
            }
        }
        // The memorial stone: a dark polished stone on a base, a flame in a basin before it.
        int mv = 3;
        f.fill(gate - 3, -1, mv - 1, gate + 3, -1, mv + 3, Blocks.POLISHED_ANDESITE);
        f.fill(gate - 2, 0, mv, gate + 2, 0, mv, Blocks.POLISHED_DEEPSLATE);
        f.fill(gate - 1, 1, mv, gate + 1, 3, mv, Blocks.POLISHED_BLACKSTONE);
        f.set(gate, 4, mv, Blocks.POLISHED_BLACKSTONE);
        f.set(gate - 2, 1, mv, Blocks.POLISHED_BLACKSTONE_WALL);
        f.set(gate + 2, 1, mv, Blocks.POLISHED_BLACKSTONE_WALL);
        f.set(gate, 0, mv + 2, Blocks.CAMPFIRE); // the flame that keeps burning
        f.set(gate - 1, 0, mv + 2, Blocks.STONE_BRICK_SLAB);
        f.set(gate + 1, 0, mv + 2, Blocks.STONE_BRICK_SLAB);
        for (int s = -1; s <= 1; s += 2) {
            f.set(gate + s * 3, 0, mv + 2, Blocks.POTTED_WHITE_TULIP);
        }
        if (f.w >= 12) {
            treeOf(f, 2, 2, 5, f.p.tree(2));
            treeOf(f, f.w - 3, 2, 5, f.p.tree(2));
        }
    }

    // ================================================================ helpers

    /** A red gate (torii) across v = {@code v}, centred on {@code cu}, posts {@code half + 1} blocks from the middle. */
    static void torii(LotFrame f, int cu, int v, int half, Block red) {
        int a = cu - half - 1, b = cu + half + 1;
        f.fill(a, 0, v, a, 3, v, red);
        f.fill(b, 0, v, b, 3, v, red);
        f.fill(a, 3, v, b, 3, v, red);
        f.fill(a - 1, 4, v, b + 1, 4, v, Blocks.DARK_OAK_SLAB);
        f.set(cu, 2, v, Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS, Direction.Axis.Y));
    }

    private static void treeOf(LotFrame f, int u, int v, int height, Block[] kind) {
        BlockState leaves = Buildings.leaves(kind[1]);
        for (int du = -2; du <= 2; du++) {
            for (int dv = -2; dv <= 2; dv++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (du * du + dv * dv + dy * dy <= 5 && f.get(u + du, height + dy, v + dv).isAir()) {
                        f.set(u + du, height + dy, v + dv, leaves);
                    }
                }
            }
        }
        f.fill(u, 0, v, u, height, v, kind[0]);
    }

    /** Gravel and coarse dirt over the whole yard (a working yard, trodden bare). */
    private static void gravel(LotFrame f) {
        for (int u = 0; u < f.w; u++) {
            for (int v = 0; v < f.d; v++) {
                long h = Materials.hash(f.x(u, v), -1, f.z(u, v), f.lot.seed());
                f.set(u, -1, v, h % 5 == 0 ? Blocks.GRAVEL : h % 5 == 1 ? f.p.ground : Blocks.COARSE_DIRT);
            }
        }
    }

    private static double sq(double x) {
        return x * x;
    }
}

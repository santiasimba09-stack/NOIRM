package com.noirm.world.village;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import static com.noirm.world.village.Buildings.slab;
import static com.noirm.world.village.Buildings.stairs;

/**
 * The slender and round buildings that give a hidden village its skyline: spires (a cone, a
 * "bottle", a bell turret) that poke up over the low carpet of roofs, and the jōnin standby
 * station, a round plank-clad lounge. Built from the {@link Shapes} drums and flared ring eaves,
 * painted in the lot's colourway.
 */
final class RoundBuildings {
    private RoundBuildings() {}

    // ================================================================ spires

    /** A spire by the lot's style: 0 a cone, 1 a bottle, 2 a bell turret. */
    static void spire(LotFrame f) {
        Materials.Colourway cw = f.p.colourway(f.lot.colour());
        int cu = (f.w - 1) / 2, cv = (f.d - 1) / 2;
        int r = Math.max(2, (Math.min(f.w, f.d) - 1) / 2);
        int h = 13 + f.rnd.nextInt(6);
        switch (f.lot.style() % 3) {
            case 0 -> coneSpire(f, cw, cu, cv, r, h);
            case 1 -> bottleSpire(f, cw, cu, cv, r, h);
            default -> bellTurret(f, cw, cu, cv, r, h - 3);
        }
    }

    /** A round tower with a band at every storey, a flared eave and a tall pointed cap. */
    private static void coneSpire(LotFrame f, Materials.Colourway cw, int cu, int cv, int r, int h) {
        tower(f, cw, cu, cv, r, h);
        Shapes.ringEave(f, cu, cv, r, h, cw.roofStairs(), cw.roofSlab(), true);
        int levels = (r + 1) * 2 + 1;
        for (int k = 0; k < levels; k++) {
            int rr = r - k / 2;
            if (rr < 0) {
                break;
            }
            Shapes.disk(f, cu, cv, rr, h + 1 + k, cw.roofFlat().defaultBlockState());
        }
        f.set(cu, h + 1 + levels, cv, Blocks.LIGHTNING_ROD);
    }

    /** A round tower ending in a bulb (a wider drum with a rounded top), a little eave and a finial. */
    private static void bottleSpire(LotFrame f, Materials.Colourway cw, int cu, int cv, int r, int h) {
        tower(f, cw, cu, cv, r, h);
        int b = r + 1;
        Shapes.disk(f, cu, cv, b, h, cw.accent().defaultBlockState());
        Shapes.drum(f, cu, cv, b, h + 1, h + 3, cw.roofFlat());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            f.set(cu + side.getStepX() * b, h + 2, cv + side.getStepZ() * b, Blocks.YELLOW_STAINED_GLASS_PANE); // lit windows in the bulb
        }
        Shapes.disk(f, cu, cv, b, h + 4, cw.roofFlat().defaultBlockState());
        Shapes.disk(f, cu, cv, Math.max(1, b - 1), h + 5, cw.roofFlat().defaultBlockState());
        Shapes.ringEave(f, cu, cv, Math.max(0, r - 2), h + 6, cw.roofStairs(), cw.roofSlab(), false);
        f.set(cu, h + 6, cv, cw.accent());
        f.set(cu, h + 7, cv, Blocks.LIGHTNING_ROD);
        f.set(cu, h + 2, cv, Blocks.LANTERN); // a light in the bulb
    }

    /** A square timber turret with an open belfry under a pyramid roof. */
    private static void bellTurret(LotFrame f, Materials.Colourway cw, int cu, int cv, int r, int h) {
        int u1 = cu - r, u2 = cu + r, v1 = cv - r, v2 = cv + r;
        f.fill(u1, -1, v1, u2, -1, v2, f.p.planks);
        f.walls(u1, 0, v1, u2, h - 1, v2, cw.wall());
        for (int u : new int[] {u1, u2}) {
            for (int v : new int[] {v1, v2}) {
                f.fill(u, 0, v, u, h + 3, v, cw.trim());
            }
        }
        f.fill(u1 + 1, 0, v1 + 1, u2 - 1, h - 1, v2 - 1, Blocks.AIR);
        for (int y = 3; y < h; y += 4) {
            f.walls(u1, y, v1, u2, y, v2, cw.accent());
        }
        Buildings.door(f, cu, 0, v2, Direction.SOUTH);
        ladder(f, cu, v1 + 1, 0, h);
        // The belfry: a floor, railings between the posts, the bell hanging from the roof.
        f.fill(u1, h, v1, u2, h, v2, f.p.planks);
        f.set(cu, h, v1 + 1, Blocks.AIR);
        for (int u = u1 + 1; u < u2; u++) {
            f.set(u, h + 1, v1, f.p.fence);
            f.set(u, h + 1, v2, f.p.fence);
        }
        for (int v = v1 + 1; v < v2; v++) {
            f.set(u1, h + 1, v, f.p.fence);
            f.set(u2, h + 1, v, f.p.fence);
        }
        f.fill(u1, h + 4, v1, u2, h + 4, v2, cw.trim());
        f.set(cu, h + 3, cv, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        // The pyramid roof, overhanging by one.
        for (int k = 0; k <= r + 1; k++) {
            int a = r + 1 - k;
            int y = h + 5 + k;
            if (a == 0) {
                f.set(cu, y, cv, slab(cw.roofSlab(), SlabType.BOTTOM));
                f.set(cu, y + 1, cv, Blocks.LIGHTNING_ROD);
                break;
            }
            for (int d = -a; d <= a; d++) {
                f.set(cu + d, y, cv - a, stairs(cw.roofStairs(), Direction.SOUTH));
                f.set(cu + d, y, cv + a, stairs(cw.roofStairs(), Direction.NORTH));
                f.set(cu - a, y, cv + d, stairs(cw.roofStairs(), Direction.EAST));
                f.set(cu + a, y, cv + d, stairs(cw.roofStairs(), Direction.WEST));
            }
            f.fill(cu - a + 1, y, cv - a + 1, cu + a - 1, y, cv + a - 1, cw.roofFlat());
        }
    }

    /** The round body of a spire: a drum with windows and a band at each storey, a door and a ladder inside. */
    private static void tower(LotFrame f, Materials.Colourway cw, int cu, int cv, int r, int h) {
        Shapes.disk(f, cu, cv, r, -1, f.p.planks.defaultBlockState());
        Shapes.drum(f, cu, cv, r, 0, h - 1, cw.wall());
        for (int y = 3; y < h; y += 4) {
            Shapes.drum(f, cu, cv, r, y, y, cw.accent());
            Shapes.ringEave(f, cu, cv, r, y + 1, cw.roofStairs(), cw.roofSlab(), false);
            // (A slim pent eave: only its inner ring of stairs, cleared again past one block.)
            for (int du = -r - 2; du <= r + 2; du++) {
                for (int dv = -r - 2; dv <= r + 2; dv++) {
                    double d = Shapes.dist(du, dv);
                    if (d > r + 1.5 && d <= r + 2.5) {
                        f.set(cu + du, y + 1, cv + dv, Blocks.AIR);
                    }
                }
            }
        }
        for (int y = 5; y < h - 1; y += 4) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                f.set(cu + side.getStepX() * r, y, cv + side.getStepZ() * r, Blocks.GLASS_PANE);
            }
        }
        Shapes.disk(f, cu, cv, r, h, cw.accent().defaultBlockState());
        Buildings.door(f, cu, 0, cv + r, Direction.SOUTH);
        ladder(f, cu, cv - r + 1, 0, h);
        f.set(cu, h - 1, cv - r + 1, Blocks.AIR);
        f.set(cu, h - 2, cv, Buildings.HANGING_LANTERN);
    }

    private static void ladder(LotFrame f, int u, int v, int y1, int y2) {
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = y1; y < y2; y++) {
            f.set(u, y, v, ladder);
        }
    }

    // ================================================================ the jōnin station

    /**
     * The jōnin standby station: a round lounge clad in planks between timber posts, a ribbon of
     * windows, a flared eave, and a small round lookout on the roof. Inside, benches round the wall,
     * low tables, a tea counter and the mission board.
     */
    static void joninStation(LotFrame f) {
        Materials.Colourway cw = f.p.colourway(f.lot.colour());
        int cu = (f.w - 1) / 2, cv = (f.d - 2) / 2;
        int r = Math.max(4, Math.min(f.w, f.d - 1) / 2 - 2);
        int h = 5;
        Block clad = f.p == VillagePalette.SAND ? Blocks.SMOOTH_SANDSTONE : Blocks.SPRUCE_PLANKS;
        Block post = f.p == VillagePalette.SAND ? Blocks.CUT_SANDSTONE : Blocks.STRIPPED_SPRUCE_LOG;
        Shapes.disk(f, cu, cv, r, -1, f.p.planks.defaultBlockState());
        Shapes.drum(f, cu, cv, r, 0, h - 1, clad);
        for (int du = -r; du <= r; du++) {
            for (int dv = -r; dv <= r; dv++) {
                double d = Shapes.dist(du, dv);
                if (d <= r + 0.5 && d > r - 0.5) {
                    int u = cu + du, v = cv + dv;
                    if (((du * 3 + dv * 5) & 3) == 0) {
                        f.fill(u, 0, v, u, h - 1, v, post);
                    } else {
                        f.fill(u, 1, v, u, 2, v, Blocks.GLASS_PANE); // the window ribbon
                    }
                } else if (d <= r - 0.5) {
                    f.fill(cu + du, 0, cv + dv, cu + du, h - 1, cv + dv, Blocks.AIR);
                }
            }
        }
        Shapes.disk(f, cu, cv, r, h, cw.roofFlat().defaultBlockState());
        Shapes.ringEave(f, cu, cv, r, h, cw.roofStairs(), cw.roofSlab(), true);
        // The door, with a lantern either side.
        f.fill(cu, 0, cv + r, cu, 1, cv + r, Blocks.AIR);
        Buildings.door(f, cu, 0, cv + r, Direction.SOUTH);
        f.set(cu - 1, 2, cv + r + 1, Buildings.HANGING_LANTERN);
        f.set(cu + 1, 2, cv + r + 1, Buildings.HANGING_LANTERN);
        // Benches round the inside wall, two low tables, a tea counter, the mission board.
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            int du = (int) Math.round(Math.sin(a) * (r - 1)), dv = (int) Math.round(Math.cos(a) * (r - 1));
            if (dv >= r - 2 && Math.abs(du) <= 1) {
                continue; // the doorway
            }
            BlockState seat = stairs(f.p.stairs, Shapes.towardsCentre(du, dv).getOpposite());
            if (f.get(cu + du, 0, cv + dv).isAir()) {
                f.set(cu + du, 0, cv + dv, seat);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            f.set(cu + s * 2, 0, cv, f.p.fence);
            f.set(cu + s * 2, 1, cv, Blocks.SPRUCE_PRESSURE_PLATE);
        }
        f.set(cu - 1, 0, cv - r + 1, Blocks.BARREL);
        f.set(cu, 0, cv - r + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        f.set(cu + 1, 0, cv - r + 1, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH));
        f.fill(cu - 1, 1, cv - r, cu + 1, 2, cv - r, Blocks.BIRCH_PLANKS);
        f.set(cu, h - 1, cv, Buildings.HANGING_LANTERN);
        // The lookout on the roof, reached by a ladder.
        int lr = Math.max(2, r / 2 - 1);
        Shapes.drum(f, cu, cv, lr, h + 1, h + 3, clad);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            f.set(cu + side.getStepX() * lr, h + 2, cv + side.getStepZ() * lr, Blocks.GLASS_PANE);
        }
        f.fill(cu, h + 1, cv + lr, cu, h + 2, cv + lr, Blocks.AIR);
        Shapes.disk(f, cu, cv, lr, h + 4, cw.roofFlat().defaultBlockState());
        Shapes.ringEave(f, cu, cv, lr, h + 4, cw.roofStairs(), cw.roofSlab(), false);
        f.set(cu, h + 5, cv, Blocks.LIGHTNING_ROD);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
        for (int y = 0; y <= h; y++) {
            f.set(cu + r - 1, y, cv, ladder); // against the east wall, up through a hatch in the roof
        }
        // An upturned-stair sill under the window ribbon outside, for depth.
        for (Direction side : new Direction[] {Direction.EAST, Direction.WEST}) {
            int u = cu + side.getStepX() * (r + 1);
            for (int dv = -1; dv <= 1; dv++) {
                f.set(u, 0, cv + dv, Blocks.SPRUCE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, side.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
            }
        }
    }
}

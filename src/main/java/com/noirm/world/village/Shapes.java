package com.noirm.world.village;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Round shapes shared by homes and landmarks: drums (round walls), the flared ring eaves that are
 * the Hidden Leaf's signature on round buildings, flat disks, and round medallion signs with a
 * glyph. All work in a {@link LotFrame} around a centre (cu, cv).
 */
public final class Shapes {
    private Shapes() {}

    /** The distance from the centre, as used by every shape here. */
    public static double dist(int du, int dv) {
        return Math.sqrt(du * du + dv * dv);
    }

    /** A round wall of radius {@code r} (one block thick) from y1 to y2. */
    public static void drum(LotFrame f, int cu, int cv, int r, int y1, int y2, Block wall) {
        for (int du = -r; du <= r; du++) {
            for (int dv = -r; dv <= r; dv++) {
                double d = dist(du, dv);
                if (d <= r + 0.5 && d > r - 0.5) {
                    f.fill(cu + du, y1, cv + dv, cu + du, y2, cv + dv, wall);
                }
            }
        }
    }

    /** A filled disk of radius {@code r} at height y. */
    public static void disk(LotFrame f, int cu, int cv, int r, int y, BlockState state) {
        for (int du = -r; du <= r; du++) {
            for (int dv = -r; dv <= r; dv++) {
                if (dist(du, dv) <= r + 0.5) {
                    f.set(cu + du, y, cv + dv, state);
                }
            }
        }
    }

    /**
     * A flared ring eave round a drum of radius {@code r} at height {@code y}: a ring of stairs
     * sloping down and out (just outside the wall), then a ring of slabs beyond it. With
     * {@code thick}, a ring of upside-down slabs under the stairs makes the eave look heavier.
     */
    public static void ringEave(LotFrame f, int cu, int cv, int r, int y, Block stairs, Block slab, boolean thick) {
        for (int du = -r - 2; du <= r + 2; du++) {
            for (int dv = -r - 2; dv <= r + 2; dv++) {
                double d = dist(du, dv);
                if (d > r + 0.5 && d <= r + 1.5) {
                    f.set(cu + du, y, cv + dv, stairs.defaultBlockState()
                            .setValue(StairBlock.FACING, towardsCentre(du, dv))
                            .setValue(StairBlock.HALF, Half.BOTTOM));
                    if (thick) {
                        f.set(cu + du, y - 1, cv + dv, Buildings.slab(slab, SlabType.TOP));
                    }
                } else if (d > r + 1.5 && d <= r + 2.5) {
                    f.set(cu + du, y, cv + dv, Buildings.slab(slab, SlabType.BOTTOM));
                }
            }
        }
    }

    /** The horizontal direction from (du, dv) back towards the centre (frame directions). */
    public static Direction towardsCentre(int du, int dv) {
        if (Math.abs(du) >= Math.abs(dv)) {
            return du > 0 ? Direction.WEST : Direction.EAST;
        }
        return dv > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * A round medallion sign standing in the plane v = {@code v} (facing +v, the front): a disk of
     * {@code size} (7, 9 or 11) with the corners cut, a 1-block {@code ring}, a {@code field}, and
     * the 5×5 {@code glyph} in {@code ink} in the middle (scaled up for bigger medallions).
     * {@code cu, cy} is the centre.
     */
    public static void medallion(LotFrame f, int cu, int cy, int v, VillagePalette.Glyph glyph, Block ring, Block field, Block ink, int size) {
        int half = size / 2;
        int scale = Math.max(1, (size - 2) / 5);
        int glyphHalf = (5 * scale) / 2;
        for (int du = -half; du <= half; du++) {
            for (int dy = -half; dy <= half; dy++) {
                double d = dist(du, dy);
                if (d > half + 0.5) {
                    continue;
                }
                Block block = d > half - 0.5 ? ring : field;
                int gx = du + glyphHalf;
                int gy = glyphHalf - dy;
                if (gx >= 0 && gy >= 0 && gx < 5 * scale && gy < 5 * scale && glyph.painted(gx / scale, gy / scale)) {
                    block = ink;
                }
                f.set(cu + du, cy + dy, v, block);
            }
        }
    }
}

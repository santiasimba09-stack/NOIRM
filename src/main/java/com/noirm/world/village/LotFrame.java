package com.noirm.world.village;

import com.noirm.world.village.VillagePlan.Lot;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws one lot as if its front faced south: {@code u} runs across the front (0 to
 * {@link #w} - 1), {@code v} from the back (0) to the front ({@link #d} - 1), {@code y} up from the
 * village floor. The frame turns positions and blocks so the front ends up on the lot's
 * {@link Lot#facing() facing} side, then hands them to the {@link VillageCanvas} (which turns them
 * again with the whole village).
 *
 * <p>So every building is written once, front to the south, and works on any side of any street.
 */
public final class LotFrame {
    public final VillageCanvas c;
    public final VillagePalette p;
    public final Lot lot;
    /** Width across the front. */
    public final int w;
    /** Depth from back to front. */
    public final int d;
    /** This lot's own randomness (the same whichever chunk draws it). */
    public final RandomSource rnd;
    private final Rotation turn;

    public LotFrame(VillageCanvas canvas, VillagePalette palette, Lot lot) {
        this.c = canvas;
        this.p = palette;
        this.lot = lot;
        this.rnd = RandomSource.create(lot.seed());
        boolean sideways = lot.facing().getAxis() == Direction.Axis.X;
        this.w = sideways ? lot.depth() : lot.width();
        this.d = sideways ? lot.width() : lot.depth();
        this.turn = switch (lot.facing()) {
            case NORTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.CLOCKWISE_90;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** The village-local x of lot position (u, v). */
    public int x(int u, int v) {
        return switch (lot.facing()) {
            case NORTH -> lot.x2() - u;
            case WEST -> lot.x2() - v;
            case EAST -> lot.x1() + v;
            default -> lot.x1() + u;
        };
    }

    /** The village-local z of lot position (u, v). */
    public int z(int u, int v) {
        return switch (lot.facing()) {
            case NORTH -> lot.z2() - v;
            case WEST -> lot.z1() + u;
            case EAST -> lot.z2() - u;
            default -> lot.z1() + v;
        };
    }

    public void set(int u, int y, int v, Block block) {
        set(u, y, v, block.defaultBlockState());
    }

    /** Places a block; directional blocks are given as if the front faced south. */
    public void set(int u, int y, int v, BlockState state) {
        c.set(x(u, v), y, z(u, v), state.rotate(turn));
    }

    public BlockState get(int u, int y, int v) {
        return c.get(x(u, v), y, z(u, v));
    }

    public void fill(int u1, int y1, int v1, int u2, int y2, int v2, Block block) {
        fill(u1, y1, v1, u2, y2, v2, block.defaultBlockState());
    }

    public void fill(int u1, int y1, int v1, int u2, int y2, int v2, BlockState state) {
        for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) {
                    set(u, y, v, state);
                }
            }
        }
    }

    /** Hollow box walls (no floor or ceiling) from (u1, v1) to (u2, v2). */
    public void walls(int u1, int y1, int v1, int u2, int y2, int v2, Block block) {
        fill(u1, y1, v1, u2, y2, v1, block);
        fill(u1, y1, v2, u2, y2, v2, block);
        fill(u1, y1, v1, u1, y2, v2, block);
        fill(u2, y1, v1, u2, y2, v2, block);
    }

    /** The village-local direction of a frame direction (frame south = the lot's front). */
    public Direction toVillage(Direction frameDirection) {
        return turn.rotate(frameDirection);
    }

    /**
     * A smaller lot inside this one (frame coordinates), e.g. a house in a clan compound, with its
     * front on frame side {@code front}. Its seed comes from this lot's randomness.
     */
    public VillagePlan.Lot sub(VillagePlan.LotType type, int u1, int v1, int u2, int v2, Direction front, int floors, int style) {
        int xa = x(u1, v1), za = z(u1, v1), xb = x(u2, v2), zb = z(u2, v2);
        return new VillagePlan.Lot(type, Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb),
                toVillage(front), floors, style, rnd.nextLong(), lot.colour());
    }

    public <T> T pick(java.util.List<T> list) {
        return list.get(rnd.nextInt(list.size()));
    }
}

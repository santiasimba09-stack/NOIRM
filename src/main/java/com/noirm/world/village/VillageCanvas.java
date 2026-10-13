package com.noirm.world.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where the village builder draws. It works in <b>local</b> coordinates: (0, 0, 0) is the village
 * centre at ground level (the first air block above the ground), +z is "south" (towards the main
 * gate) before the village is turned. The canvas turns every position and block by the village's
 * rotation, and only writes inside {@link #clip}.
 *
 * <p>Why the clip: world generation builds a structure one chunk at a time, and may only write
 * into the chunk being generated. The builder runs the whole plan for every chunk the village
 * touches, and the canvas silently drops everything outside it. In a game test the clip is just
 * the test area.
 */
public final class VillageCanvas {
    /** Send to clients, but don't make neighbours re-check their shape (so nothing pops off mid-build). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public final WorldGenLevel level;
    public final BoundingBox clip;
    public final BlockPos origin;
    public final Rotation rotation;

    public VillageCanvas(WorldGenLevel level, BoundingBox clip, BlockPos origin, Rotation rotation) {
        this.level = level;
        this.clip = clip;
        this.origin = origin;
        this.rotation = rotation;
    }

    /** The world position of a local position. */
    public BlockPos world(int x, int y, int z) {
        int wx, wz;
        switch (rotation) {
            case CLOCKWISE_90 -> { wx = -z; wz = x; }
            case CLOCKWISE_180 -> { wx = -x; wz = -z; }
            case COUNTERCLOCKWISE_90 -> { wx = z; wz = -x; }
            default -> { wx = x; wz = z; }
        }
        return origin.offset(wx, y, wz);
    }

    /** The local (x, z) of a world column (the inverse of {@link #world}). */
    public int[] local(int worldX, int worldZ) {
        int dx = worldX - origin.getX();
        int dz = worldZ - origin.getZ();
        return switch (rotation) {
            case CLOCKWISE_90 -> new int[] {dz, -dx};
            case CLOCKWISE_180 -> new int[] {-dx, -dz};
            case COUNTERCLOCKWISE_90 -> new int[] {-dz, dx};
            default -> new int[] {dx, dz};
        };
    }

    /** The clip as a local box: {min x, min z, max x, max z}. */
    public int[] localBounds() {
        int[] a = local(clip.minX(), clip.minZ());
        int[] b = local(clip.maxX(), clip.maxZ());
        return new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    public boolean inside(int x, int y, int z) {
        return clip.isInside(world(x, y, z));
    }

    public void set(int x, int y, int z, Block block) {
        set(x, y, z, block.defaultBlockState());
    }

    /** Places a block (turned with the village) if it's inside the clip. */
    public void set(int x, int y, int z, BlockState state) {
        BlockPos pos = world(x, y, z);
        if (!clip.isInside(pos)) {
            return;
        }
        BlockState turned = state.rotate(rotation);
        level.setBlock(pos, turned, FLAGS);
        Block block = turned.getBlock();
        // During world generation, fences, panes, walls and stairs connect up when the chunk
        // finishes (like vanilla's structures do).
        if (!(level instanceof ServerLevel)
                && (block instanceof FenceBlock || block instanceof IronBarsBlock || block instanceof WallBlock || block instanceof StairBlock)) {
            level.getChunk(pos).markPosForPostprocessing(pos);
        }
    }

    public BlockState get(int x, int y, int z) {
        return level.getBlockState(world(x, y, z));
    }

    /** Fills the box between two local corners (inclusive). */
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        fill(x1, y1, z1, x2, y2, z2, block.defaultBlockState());
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** Whether any part of a local box (in x/z) reaches into the clip; lets the builder skip far work. */
    public boolean touches(int x1, int z1, int x2, int z2) {
        BlockPos a = world(x1, 0, z1);
        BlockPos b = world(x2, 0, z2);
        return clip.intersects(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()));
    }
}

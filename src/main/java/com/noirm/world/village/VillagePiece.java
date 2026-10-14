package com.noirm.world.village;

import com.noirm.registry.NoirmStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A village, or one chunk of one. It remembers everything needed to rebuild the same village
 * (size, look, centre, rotation, seed; saved with the chunk), and {@link #postProcess} runs the
 * {@link VillageBuilder} for every chunk its box covers.
 *
 * <p>Towns and outposts are one piece covering the whole village. A hidden village is too big for
 * one structure, so it's many pieces of one chunk each: the core start's piece covers the anchor
 * chunk, and one "ward" start covers each other chunk (see {@link VillageWardStructure}). So every
 * chunk is drawn exactly once.
 *
 * <p>The box starts at ground level, so the terrain adaptation in the structure JSON
 * ({@code beard_thin}) smooths the land up to the village floor.
 */
public class VillagePiece extends StructurePiece {
    private final VillageKind kind;
    private final VillagePalette palette;
    private final BlockPos origin;
    private final Rotation rotation;
    private final long seed;
    /** How high above the ground to clear (enough to take off the hills on the site). */
    private final int clearHeight;

    /** The whole village in one piece. */
    public VillagePiece(VillageKind kind, VillagePalette palette, BlockPos origin, Rotation rotation, long seed, int clearHeight) {
        this(kind, palette, origin, rotation, seed, clearHeight, box(kind, origin));
    }

    /** Only the part of the village inside {@code box} (one chunk of a hidden village). */
    public VillagePiece(VillageKind kind, VillagePalette palette, BlockPos origin, Rotation rotation, long seed, int clearHeight, BoundingBox box) {
        super(NoirmStructures.VILLAGE_PIECE.get(), 0, box);
        this.clearHeight = clearHeight;
        this.kind = kind;
        this.palette = palette;
        this.origin = origin;
        this.rotation = rotation;
        this.seed = seed;
    }

    /** Loads a piece saved with a chunk (the base class saves the box). */
    public VillagePiece(CompoundTag tag) {
        super(NoirmStructures.VILLAGE_PIECE.get(), tag);
        this.kind = VillageKind.byOrdinal(tag.getInt("Kind"));
        this.palette = VillagePalette.byOrdinal(tag.getInt("Palette"));
        this.origin = BlockPos.of(tag.getLong("Origin"));
        Rotation[] rotations = Rotation.values();
        int turn = tag.getInt("Rotation");
        this.rotation = turn >= 0 && turn < rotations.length ? rotations[turn] : Rotation.NONE;
        this.seed = tag.getLong("Seed");
        this.clearHeight = tag.contains("Clear") ? tag.getInt("Clear") : kind.height;
    }

    private static BoundingBox box(VillageKind kind, BlockPos origin) {
        int r = kind.radius + 1;
        return new BoundingBox(origin.getX() - r, origin.getY(), origin.getZ() - r,
                origin.getX() + r, origin.getY() + kind.height, origin.getZ() + r);
    }

    /** The box of one chunk of a village whose floor is at {@code ground}: the chunk's columns, from the floor up. */
    public static BoundingBox chunkBox(ChunkPos chunk, VillageKind kind, int ground) {
        return new BoundingBox(chunk.getMinBlockX(), ground, chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), ground + kind.height, chunk.getMaxBlockZ());
    }

    public VillageKind kind() {
        return kind;
    }

    public VillagePalette palette() {
        return palette;
    }

    /** The village centre: the first air block above the finished ground. */
    public BlockPos origin() {
        return origin;
    }

    public Rotation rotation() {
        return rotation;
    }

    public long seed() {
        return seed;
    }

    public int clearHeight() {
        return clearHeight;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Kind", kind.ordinal());
        tag.putInt("Palette", palette.ordinal());
        tag.putLong("Origin", origin.asLong());
        tag.putInt("Rotation", rotation.ordinal());
        tag.putLong("Seed", seed);
        tag.putInt("Clear", clearHeight);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        VillageBuilder.build(level, chunkBox, kind, palette, origin, rotation, seed, clearHeight);
    }
}

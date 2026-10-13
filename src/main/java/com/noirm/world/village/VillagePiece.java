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
 * The one piece a village is made of. It remembers everything needed to rebuild the same village
 * (size, look, centre, rotation, seed; saved with the chunk), and {@link #postProcess} runs the
 * {@link VillageBuilder} once for every chunk the village covers.
 *
 * <p>Its bounding box starts at ground level, so the terrain adaptation in the structure JSON
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

    public VillagePiece(VillageKind kind, VillagePalette palette, BlockPos origin, Rotation rotation, long seed, int clearHeight) {
        super(NoirmStructures.VILLAGE_PIECE.get(), 0, box(kind, origin));
        this.clearHeight = clearHeight;
        this.kind = kind;
        this.palette = palette;
        this.origin = origin;
        this.rotation = rotation;
        this.seed = seed;
    }

    /** Loads a piece saved with a chunk. */
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

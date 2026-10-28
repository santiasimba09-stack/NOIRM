package com.noirm.world.sanctuary;

import com.noirm.registry.NoirmStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The whole sanctuary in one piece. It remembers its centre (the floor) and seed (saved with the
 * chunk); {@link #postProcess} runs {@link SanctuaryBuilder} once per chunk it covers, clipped to
 * that chunk. The box starts one block under the floor, so the terrain adaptation in the structure
 * JSON ({@code beard_thin}) brings the land up to the platform.
 */
public class SanctuaryPiece extends StructurePiece {
    private final BlockPos origin;
    private final long seed;

    public SanctuaryPiece(BlockPos origin, long seed) {
        super(NoirmStructures.SANCTUARY_PIECE.get(), 0, SanctuaryBuilder.box(origin));
        this.origin = origin;
        this.seed = seed;
    }

    public SanctuaryPiece(CompoundTag tag) {
        super(NoirmStructures.SANCTUARY_PIECE.get(), tag);
        this.origin = BlockPos.of(tag.getLong("Origin"));
        this.seed = tag.getLong("Seed");
    }

    public BlockPos origin() {
        return origin;
    }

    public long seed() {
        return seed;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putLong("Origin", origin.asLong());
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        SanctuaryBuilder.build(level, chunkBox, origin, seed, true);
    }
}

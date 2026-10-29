package com.noirm.world.sanctuary;

import com.mojang.serialization.Codec;
import com.noirm.registry.NoirmStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * The Stone Hermit's hidden sanctuary: a small shrine by a waterfall and a sacred pool, ringed by
 * mossy stone pillars, where the sage master lives (see {@code senjutsu.Sage}). Rare (one try every
 * 64 chunks, {@code worldgen/structure_set/sage_sanctuaries.json}), only in mountain, jungle and old
 * misty forest biomes ({@code tags/worldgen/biome/has_structure/sage_sanctuary.json}), and never
 * within {@value #MIN_DISTANCE} blocks of the world's centre. Built in code by
 * {@link SanctuaryBuilder}, one {@link SanctuaryPiece}. {@code /locate structure noirm:sage_sanctuary}
 * finds it; {@code /noirm sage rumour} gives a vague hint.
 */
public class SanctuaryStructure extends Structure {
    public static final Codec<SanctuaryStructure> CODEC = simpleCodec(SanctuaryStructure::new);

    /** Never this close to the world's centre (where players start). */
    public static final int MIN_DISTANCE = 1000;
    /** The land under the site may rise and fall at most this much (the builder levels the rest). */
    public static final int MAX_SLOPE = 14;
    /** Where the ground is sampled (around the centre). */
    private static final int[][] SAMPLES = {{0, 0}, {10, 0}, {-10, 0}, {0, 10}, {0, -10}, {7, 7}, {-7, -7}, {7, -7}, {-7, 7}};

    public SanctuaryStructure(StructureSettings settings) {
        super(settings);
    }

    /** Far enough from the world's centre? */
    public static boolean farEnough(int x, int z) {
        return (long) x * x + (long) z * z >= (long) MIN_DISTANCE * MIN_DISTANCE;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX(), cz = chunk.getMiddleBlockZ();
        if (!farEnough(cx, cz)) {
            return Optional.empty();
        }
        int sea = context.chunkGenerator().getSeaLevel();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, centre = 0;
        for (int i = 0; i < SAMPLES.length; i++) {
            int ground = ground(context, cx + SAMPLES[i][0], cz + SAMPLES[i][1]);
            if (ground == Integer.MIN_VALUE || ground <= sea) {
                return Optional.empty(); // under water, or below the sea
            }
            if (i == 0) {
                centre = ground;
            }
            min = Math.min(min, ground);
            max = Math.max(max, ground);
            if (max - min > MAX_SLOPE) {
                return Optional.empty();
            }
        }
        // The floor sits a little above the lowest ground: the builder digs into the slope behind
        // (the waterfall's cliff) and the terrain is bearded up to the platform in front.
        int floor = Math.max(min + 2, Math.min(centre, (min + max) / 2));
        BlockPos origin = new BlockPos(cx, floor, cz);
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(origin, pieces -> pieces.addPiece(new SanctuaryPiece(origin, seed))));
    }

    /** The first air block above solid ground at (x, z), or {@link Integer#MIN_VALUE} if water is on top. */
    private static int ground(GenerationContext context, int x, int z) {
        var height = context.heightAccessor();
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, height, context.randomState());
        int surface = Integer.MIN_VALUE;
        for (int y = height.getMaxBuildHeight() - 1; y >= height.getMinBuildHeight(); y--) {
            BlockState state = column.getBlock(y);
            if (surface == Integer.MIN_VALUE && !state.isAir()) {
                surface = y + 1;
            }
            if (state.blocksMotion()) {
                return surface > y + 1 ? Integer.MIN_VALUE : y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public StructureType<?> type() {
        return NoirmStructures.SAGE_SANCTUARY.get();
    }
}

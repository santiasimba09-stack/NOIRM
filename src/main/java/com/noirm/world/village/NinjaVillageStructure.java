package com.noirm.world.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.registry.NoirmStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A ninja village in the world: a hidden village capital or a small outpost (the {@code size}
 * field of {@code data/noirm/worldgen/structure/*.json}).
 *
 * <p>World generation asks this class, for candidate chunks, "is there a village here?". We
 * measure the land under the whole site; if it's too steep or under water we say no, otherwise
 * we pick the ground height, the look (from the biome), a rotation and a random seed, and hand
 * them to a single {@link VillagePiece} that does the building.
 */
public class NinjaVillageStructure extends Structure {
    public static final Codec<NinjaVillageStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            VillageKind.CODEC.fieldOf("size").forGetter(s -> s.kind)
    ).apply(instance, NinjaVillageStructure::new));

    private final VillageKind kind;

    public NinjaVillageStructure(StructureSettings settings, VillageKind kind) {
        super(settings);
        this.kind = kind;
    }

    public VillageKind kind() {
        return kind;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        int r = kind.radius - 2;
        int[] offsets = kind == VillageKind.CAPITAL ? new int[] {-r, -r / 2, 0, r / 2, r} : new int[] {-r, 0, r};
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int samples = 0;
        int wet = 0;
        for (int dx : offsets) {
            for (int dz : offsets) {
                int floor = generator.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                int surface = generator.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                if (surface > floor) {
                    wet++; // water on top
                }
                min = Math.min(min, floor);
                max = Math.max(max, floor);
                sum += floor;
                samples++;
            }
        }
        // Not on a lake or the sea, not on a mountainside.
        if (wet > samples / 6 || max - min > kind.maxSlope) {
            return Optional.empty();
        }
        int ground = (int) Math.round((double) sum / samples);
        if (ground <= generator.getSeaLevel()) {
            return Optional.empty();
        }
        var biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(cx), QuartPos.fromBlock(ground), QuartPos.fromBlock(cz),
                context.randomState().sampler());
        VillagePalette palette = VillagePalette.forBiome(biome);
        Rotation rotation = Rotation.getRandom(context.random());
        long seed = context.random().nextLong();
        BlockPos origin = new BlockPos(cx, ground, cz);
        return Optional.of(new GenerationStub(origin, pieces -> pieces.addPiece(new VillagePiece(kind, palette, origin, rotation, seed))));
    }

    @Override
    public StructureType<?> type() {
        return NoirmStructures.NINJA_VILLAGE.get();
    }
}

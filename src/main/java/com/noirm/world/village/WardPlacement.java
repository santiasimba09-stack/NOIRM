package com.noirm.world.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.registry.NoirmStructures;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/**
 * Where a hidden village's "wards" go: every chunk around a hidden village's anchor, out to the
 * village's radius plus one chunk ({@code noirm:village_wards} in a structure set's
 * {@code placement}).
 *
 * <h2>Why</h2>
 * A structure can only reach 8 chunks (~128 blocks) from the chunk it starts in, but a hidden
 * village is ~545 blocks across. So the village's core start (the {@code anchors} structure set,
 * {@code noirm:hidden_villages}) only builds its own chunk, and every other chunk of the village
 * gets its own little "ward" start that builds just that chunk from the same plan. (The same trick
 * as Large Structure Lib's grid placement.)
 *
 * <p>The anchors are worked out with the anchor set's own {@code random_spread} placement (its
 * JSON stays the one place the spacing is decided). The anchor chunk itself is left to the core.
 */
public class WardPlacement extends StructurePlacement {
    public static final Codec<WardPlacement> CODEC = RecordCodecBuilder.create(instance -> placementCodec(instance)
            .and(ResourceKey.codec(Registries.STRUCTURE_SET).fieldOf("anchors").forGetter(p -> p.anchors))
            .apply(instance, WardPlacement::new));

    /** How far past the village's radius the wards reach (blocks): one chunk. */
    public static final int MARGIN = 16;

    private final ResourceKey<StructureSet> anchors;
    /** The anchors found for the last world asked about (worlds rarely change, so one is enough). */
    private volatile Found found;

    private record Found(ChunkGeneratorStructureState state, Holder<StructureSet> set, RandomSpreadStructurePlacement spread, int reach) {}

    public WardPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt,
                         Optional<ExclusionZone> exclusionZone, ResourceKey<StructureSet> anchors) {
        super(locateOffset, method, frequency, salt, exclusionZone);
        this.anchors = anchors;
    }

    /** The structure set whose starts are the villages these wards belong to. */
    public ResourceKey<StructureSet> anchors() {
        return anchors;
    }

    /** How far from an anchor's centre a chunk may be and still get a ward (blocks). */
    public static int reach(VillageKind kind) {
        return kind.radius + MARGIN;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        Found f = find(state);
        if (f == null) {
            return false;
        }
        ChunkPos anchor = anchorCovering(f.spread(), state.getLevelSeed(), chunkX, chunkZ, f.reach());
        return anchor != null && (anchor.x != chunkX || anchor.z != chunkZ) && f.spread().isStructureChunk(state, anchor.x, anchor.z);
    }

    private Found find(ChunkGeneratorStructureState state) {
        Found f = found;
        if (f != null && f.state() == state) {
            return f;
        }
        f = null;
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            if (set.is(anchors) && set.value().placement() instanceof RandomSpreadStructurePlacement spread) {
                VillageKind kind = coreKind(set.value());
                if (kind != null) {
                    f = new Found(state, set, spread, reach(kind));
                }
                break;
            }
        }
        if (f != null) {
            found = f;
        }
        return f;
    }

    /** The size of the villages a structure set places, or null if it doesn't place ninja villages. */
    static VillageKind coreKind(StructureSet set) {
        if (set.structures().isEmpty()) {
            return null;
        }
        return set.structures().get(0).structure().value() instanceof NinjaVillageStructure core ? core.kind() : null;
    }

    /**
     * The anchor (the chunk a {@code random_spread} placement picked in its region) whose centre is
     * within {@code reach} blocks of some part of chunk ({@code chunkX}, {@code chunkZ}), or null.
     * The placement's {@code separation} keeps anchors far enough apart that there's at most one.
     */
    public static ChunkPos anchorCovering(RandomSpreadStructurePlacement spread, long seed, int chunkX, int chunkZ, int reach) {
        int spacing = spread.spacing();
        int r = (reach >> 4) + 1;
        for (int rx = Math.floorDiv(chunkX - r, spacing); rx <= Math.floorDiv(chunkX + r, spacing); rx++) {
            for (int rz = Math.floorDiv(chunkZ - r, spacing); rz <= Math.floorDiv(chunkZ + r, spacing); rz++) {
                ChunkPos anchor = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                if (covers(anchor, chunkX, chunkZ, reach)) {
                    return anchor;
                }
            }
        }
        return null;
    }

    /** Whether chunk ({@code chunkX}, {@code chunkZ}) comes within {@code reach} blocks of the centre of the village anchored in {@code anchor}. */
    public static boolean covers(ChunkPos anchor, int chunkX, int chunkZ, int reach) {
        return reaches(anchor.getMiddleBlockX(), anchor.getMiddleBlockZ(), chunkX, chunkZ, reach);
    }

    /** Whether any column of chunk ({@code chunkX}, {@code chunkZ}) is within {@code reach} blocks of (x, z). */
    public static boolean reaches(int x, int z, int chunkX, int chunkZ, int reach) {
        int minX = chunkX << 4, minZ = chunkZ << 4;
        long dx = Math.max(0, Math.max(minX - x, x - (minX + 15)));
        long dz = Math.max(0, Math.max(minZ - z, z - (minZ + 15)));
        return dx * dx + dz * dz <= (long) reach * reach;
    }

    @Override
    public StructurePlacementType<?> type() {
        return NoirmStructures.WARD_PLACEMENT.get();
    }
}

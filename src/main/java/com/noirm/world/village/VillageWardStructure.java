package com.noirm.world.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.registry.NoirmStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.Optional;

/**
 * One chunk of a hidden village ("ward"), anywhere but the anchor chunk (the core start builds
 * that one). Placed by {@link WardPlacement} in every chunk around an anchor of the {@code anchors}
 * structure set.
 *
 * <p>It works out which village it belongs to (the anchor whose village reaches this chunk), asks
 * the core structure for that village ({@link NinjaVillageStructure#capitalAt}: the same site
 * check, biome check, rotation and seed as the core, remembered per anchor), and returns a
 * {@link VillagePiece} covering just this chunk, from the village floor up. No village there (bad
 * site, wrong biome) or this chunk is beyond its edge: no ward.
 *
 * <p>Whether a ward is valid depends only on its anchor, never on the biome of the ward's own chunk
 * (a hidden village may well take in a river), so the per-chunk biome check is skipped.
 */
public class VillageWardStructure extends Structure {
    public static final Codec<VillageWardStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            ResourceKey.codec(Registries.STRUCTURE_SET).fieldOf("anchors").forGetter(s -> s.anchors)
    ).apply(instance, VillageWardStructure::new));

    /** A ward is only made for chunks that come within this many blocks past the village's radius (what the builder can touch). */
    public static final int EDGE = 2;

    private final ResourceKey<StructureSet> anchors;

    public VillageWardStructure(StructureSettings settings, ResourceKey<StructureSet> anchors) {
        super(settings);
        this.anchors = anchors;
    }

    /** The structure set whose starts are the villages these wards belong to. */
    public ResourceKey<StructureSet> anchors() {
        return anchors;
    }

    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context) {
        return findGenerationPoint(context); // valid wherever the village is: no biome check of this chunk
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        StructureSet set = context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(anchors);
        if (set == null || set.structures().isEmpty() || !(set.placement() instanceof RandomSpreadStructurePlacement spread)
                || !(set.structures().get(0).structure().value() instanceof NinjaVillageStructure core)) {
            return Optional.empty();
        }
        VillageKind kind = core.kind();
        ChunkPos chunk = context.chunkPos();
        ChunkPos anchor = WardPlacement.anchorCovering(spread, context.seed(), chunk.x, chunk.z, WardPlacement.reach(kind));
        if (anchor == null || anchor.equals(chunk)) {
            return Optional.empty(); // not near a village, or the core's own chunk
        }
        return core.capitalAt(context, anchor)
                .filter(site -> WardPlacement.reaches(site.origin().getX(), site.origin().getZ(), chunk.x, chunk.z, kind.radius + EDGE))
                .map(site -> {
                    int ground = site.origin().getY();
                    BlockPos position = new BlockPos(chunk.getMiddleBlockX(), ground, chunk.getMiddleBlockZ());
                    return new GenerationStub(position, pieces -> pieces.addPiece(new VillagePiece(kind, site.palette(), site.origin(),
                            site.rotation(), site.seed(), site.clear(), VillagePiece.chunkBox(chunk, kind, ground))));
                });
    }

    @Override
    public StructureType<?> type() {
        return NoirmStructures.VILLAGE_WARD.get();
    }
}

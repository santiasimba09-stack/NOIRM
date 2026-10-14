package com.noirm.world.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.registry.NoirmStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A ninja village in the world: a hidden village, a town or an outpost (the {@code size} field of
 * {@code data/noirm/worldgen/structure/*.json}).
 *
 * <p>World generation asks this class, for candidate chunks, "is there a village here?". We
 * measure the land under the whole site ({@link #site}); if it's too steep or under water we say
 * no, otherwise we pick the ground height, the look (from the biome), a rotation and a random seed.
 *
 * <ul>
 *   <li><b>Towns and outposts</b> are one {@link VillagePiece} covering the whole village. They keep
 *   clear of hidden villages: a site too close to one ({@code avoid}: the hidden villages' structure
 *   set) is turned down.</li>
 *   <li><b>A hidden village</b> is bigger than one structure can reach, so this "core" start only
 *   builds its own (anchor) chunk; {@link VillageWardStructure} builds every other chunk. Both work
 *   the village out the same way, through {@link #capitalAt}, which remembers the answer per
 *   anchor (measuring a 545-block site takes a few hundred height samples).</li>
 * </ul>
 */
public class NinjaVillageStructure extends Structure {
    public static final Codec<NinjaVillageStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            VillageKind.CODEC.fieldOf("size").forGetter(s -> s.kind),
            ResourceKey.codec(Registries.STRUCTURE_SET).optionalFieldOf("avoid").forGetter(s -> s.avoid)
    ).apply(instance, NinjaVillageStructure::new));

    /** Room kept between a town or outpost's ground and a hidden village's (blocks). */
    public static final int CAPITAL_GAP = 32;
    /** The share of the highest and of the lowest ground samples left out when judging how flat a site is (a lone hill or hollow). */
    public static final double TRIM = 0.1;

    /** Hidden villages worked out so far, by anchor (whether there's one, and where and how). */
    private static final VillagePlan.Cache<SiteKey, Optional<Site>> CAPITALS = new VillagePlan.Cache<>(64);

    private final VillageKind kind;
    private final Optional<ResourceKey<StructureSet>> avoid;

    /** What a village needs to be built: its centre (on the new ground), look, rotation, seed and how high to clear. */
    public record Site(BlockPos origin, VillagePalette palette, Rotation rotation, long seed, int clear) {}

    /** One world (its terrain noise), one capital structure, one anchor. */
    private record SiteKey(int world, int structure, long seed, long anchor) {}

    public NinjaVillageStructure(StructureSettings settings, VillageKind kind, Optional<ResourceKey<StructureSet>> avoid) {
        super(settings);
        this.kind = kind;
        this.avoid = avoid;
    }

    public VillageKind kind() {
        return kind;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (kind == VillageKind.CAPITAL) {
            // The core: just the anchor chunk. The wards build the rest.
            ChunkPos chunk = context.chunkPos();
            return capitalAt(context, chunk).map(site -> new GenerationStub(site.origin(), pieces -> pieces.addPiece(
                    new VillagePiece(kind, site.palette(), site.origin(), site.rotation(), site.seed(), site.clear(),
                            VillagePiece.chunkBox(chunk, kind, site.origin().getY())))));
        }
        Optional<Site> found = site(context);
        if (found.isEmpty() || nearHiddenVillage(context, found.get().origin())) {
            return Optional.empty();
        }
        Site site = found.get();
        return Optional.of(new GenerationStub(site.origin(), pieces -> pieces.addPiece(
                new VillagePiece(kind, site.palette(), site.origin(), site.rotation(), site.seed(), site.clear()))));
    }

    // ================================================================ hidden villages

    /**
     * The hidden village whose core starts in chunk {@code anchor}, if there really is one there
     * (the site is good and the biome is one of this structure's): the same answer world
     * generation gives the core start, because it's worked out from a fresh context for the anchor
     * (the same world seed and chunk give the same random rotation and village seed). Remembered
     * per anchor, so the ~1,000 wards of a village and its neighbours' checks measure it once.
     *
     * @param like any context of the same world (its seed, generator and terrain noise are used)
     */
    public Optional<Site> capitalAt(GenerationContext like, ChunkPos anchor) {
        SiteKey key = new SiteKey(System.identityHashCode(like.randomState()), System.identityHashCode(this), like.seed(), anchor.toLong());
        return CAPITALS.get(key, k -> {
            GenerationContext at = new GenerationContext(like.registryAccess(), like.chunkGenerator(), like.biomeSource(), like.randomState(),
                    like.structureTemplateManager(), like.seed(), anchor, like.heightAccessor(), biomes()::contains);
            return site(at).filter(site -> biomes().contains(at.chunkGenerator().getBiomeSource().getNoiseBiome(
                    QuartPos.fromBlock(site.origin().getX()), QuartPos.fromBlock(site.origin().getY()), QuartPos.fromBlock(site.origin().getZ()),
                    at.randomState().sampler())));
        });
    }

    /**
     * Whether this village (a town or outpost) centred on {@code origin} would come too close to a
     * hidden village of the {@code avoid} set: closer than both radii plus {@link #CAPITAL_GAP}.
     * Only hidden villages that really generate count (same check as theirs, remembered per anchor).
     */
    public boolean nearHiddenVillage(GenerationContext context, BlockPos origin) {
        if (avoid.isEmpty()) {
            return false;
        }
        StructureSet set = context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(avoid.get());
        if (set == null || set.structures().isEmpty() || !(set.placement() instanceof RandomSpreadStructurePlacement spread)
                || !(set.structures().get(0).structure().value() instanceof NinjaVillageStructure capital)) {
            return false;
        }
        int keep = capital.kind.radius + kind.radius + CAPITAL_GAP;
        int spacing = spread.spacing();
        int reach = (keep >> 4) + 1;
        int chunkX = origin.getX() >> 4, chunkZ = origin.getZ() >> 4;
        for (int rx = Math.floorDiv(chunkX - reach, spacing); rx <= Math.floorDiv(chunkX + reach, spacing); rx++) {
            for (int rz = Math.floorDiv(chunkZ - reach, spacing); rz <= Math.floorDiv(chunkZ + reach, spacing); rz++) {
                ChunkPos anchor = spread.getPotentialStructureChunk(context.seed(), rx * spacing, rz * spacing);
                long dx = anchor.getMiddleBlockX() - origin.getX();
                long dz = anchor.getMiddleBlockZ() - origin.getZ();
                if (dx * dx + dz * dz < (long) keep * keep && capital.capitalAt(context, anchor).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ================================================================ the site

    /**
     * Measures the land for a village centred on the middle of the context's chunk and, if it's
     * good, picks everything else. Draws the rotation and the village seed from the context's random
     * (so the same chunk always gives the same village).
     *
     * <p>The quick looks first: the biomes over the site (a 9×9 grid for a hidden village, 5×5 for a
     * town, 3×3 for an outpost), turning down a site with too much sea or river; then the ground in
     * the middle, turning down a site whose middle isn't one of the structure's biomes. Then the
     * ground over the rest of the grid and, for a hidden village, the middle of each grid cell too
     * (each sample works out a whole column of terrain, a few milliseconds, so a bad site is turned
     * down as soon as it shows: too much water, or dry ground spanning more than twice
     * {@code maxSlope}). Finally the dry ground, leaving out its highest and lowest tenth (a lone
     * hill or hollow), may rise and fall at most {@code maxSlope}, and the floor (their average)
     * must be above sea level. The builder clears the hills and fills the water and hollows.
     */
    public Optional<Site> site(GenerationContext context) {
        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        int sea = generator.getSeaLevel();
        int r = kind.radius - 2;
        int cells = switch (kind) {
            case CAPITAL -> 8;
            case TOWN -> 4;
            default -> 2;
        };
        List<int[]> grid = grid(r, cells, false);
        List<int[]> points = new ArrayList<>(grid);
        if (kind == VillageKind.CAPITAL) {
            points.addAll(grid(r, cells, true));
        }
        int maxWet = points.size() / (kind == VillageKind.CAPITAL ? 3 : 6);

        // Biomes are quick to look up: not on the sea, not on a river.
        int wetBiomes = 0;
        for (int[] p : grid) {
            var biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(cx + p[0]), QuartPos.fromBlock(sea + 8), QuartPos.fromBlock(cz + p[1]),
                    context.randomState().sampler());
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
                wetBiomes++;
            }
        }
        if (wetBiomes * points.size() > maxWet * grid.size()) {
            return Optional.empty();
        }

        // The ground (the middle first): not on a lake or the sea, not on a mountainside.
        Survey survey = new Survey(context, cx, cz);
        for (int[] p : points) {
            survey.sample(p[0], p[1]);
            if (survey.samples() == 1 && survey.dry == 1 && !biomes().contains(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(cx),
                    QuartPos.fromBlock(survey.heights.getInt(0)), QuartPos.fromBlock(cz), context.randomState().sampler()))) {
                return Optional.empty(); // the middle isn't a place for this village
            }
            if (survey.wet > maxWet || survey.range(0) > 2 * kind.maxSlope) {
                return Optional.empty();
            }
        }
        if (survey.dry == 0 || survey.range(TRIM) > kind.maxSlope) {
            return Optional.empty();
        }
        int ground = survey.average(TRIM);
        if (ground <= sea) {
            return Optional.empty();
        }
        // Clear high enough to take the hills off the site.
        int clear = Math.max(kind.height, survey.max() - ground + 8);
        var biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(cx), QuartPos.fromBlock(ground), QuartPos.fromBlock(cz),
                context.randomState().sampler());
        VillagePalette palette = VillagePalette.forBiome(biome);
        Rotation rotation = Rotation.getRandom(context.random());
        long seed = context.random().nextLong();
        return Optional.of(new Site(new BlockPos(cx, ground, cz), palette, rotation, seed, clear));
    }

    /**
     * The points of a {@code cells}×{@code cells} grid over a round site of radius {@code r} (or the
     * middles of its cells), inside the circle, centre first and the rest in a fixed scattered
     * order, so a bad site shows its high and low points early.
     */
    private static List<int[]> grid(int r, int cells, boolean middles) {
        List<int[]> points = new ArrayList<>();
        int n = middles ? cells : cells + 1;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int dx = (int) Math.round(-r + (i + (middles ? 0.5 : 0)) * 2.0 * r / cells);
                int dz = (int) Math.round(-r + (j + (middles ? 0.5 : 0)) * 2.0 * r / cells);
                if (dx * dx + dz * dz <= r * r + r) {
                    points.add(new int[] {dx, dz});
                }
            }
        }
        points.sort(java.util.Comparator.comparingLong(p -> p[0] == 0 && p[1] == 0 ? Long.MIN_VALUE : VillageBuilder.hash(p[0], p[1], 0x5EEDL)));
        return points;
    }

    /** The ground sampled over a site: the dry ground's height range and average, and how many samples were under water. */
    private static final class Survey {
        private final GenerationContext context;
        private final int cx, cz;
        /** The dry samples' ground heights (the first air block above the ground), in sampling order. */
        final it.unimi.dsi.fastutil.ints.IntArrayList heights = new it.unimi.dsi.fastutil.ints.IntArrayList();
        int dry, wet;
        private int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;

        Survey(GenerationContext context, int cx, int cz) {
            this.context = context;
            this.cx = cx;
            this.cz = cz;
        }

        void sample(int dx, int dz) {
            var height = context.heightAccessor();
            NoiseColumn column = context.chunkGenerator().getBaseColumn(cx + dx, cz + dz, height, context.randomState());
            int surface = Integer.MIN_VALUE, floor = height.getMinBuildHeight();
            for (int y = height.getMaxBuildHeight() - 1; y >= height.getMinBuildHeight(); y--) {
                BlockState state = column.getBlock(y);
                if (surface == Integer.MIN_VALUE && !state.isAir()) {
                    surface = y + 1;
                }
                if (state.blocksMotion()) {
                    floor = y + 1;
                    break;
                }
            }
            if (surface > floor) {
                wet++; // water (or lava) on top: the builder fills it in
                return;
            }
            heights.add(floor);
            min = Math.min(min, floor);
            max = Math.max(max, floor);
            dry++;
        }

        int samples() {
            return dry + wet;
        }

        int max() {
            return max;
        }

        /** How far the dry ground rises and falls, leaving out the highest and lowest {@code trim} of it. */
        int range(double trim) {
            if (dry == 0) {
                return 0;
            }
            if (trim <= 0) {
                return max - min;
            }
            int[] sorted = sorted();
            int cut = (int) (sorted.length * trim);
            return sorted[sorted.length - 1 - cut] - sorted[cut];
        }

        /** The average height of the dry ground, leaving out the highest and lowest {@code trim} of it. */
        int average(double trim) {
            int[] sorted = sorted();
            int cut = (int) (sorted.length * trim);
            long sum = 0;
            for (int i = cut; i < sorted.length - cut; i++) {
                sum += sorted[i];
            }
            return (int) Math.round((double) sum / (sorted.length - 2 * cut));
        }

        private int[] sorted() {
            int[] sorted = heights.toIntArray();
            java.util.Arrays.sort(sorted);
            return sorted;
        }
    }

    @Override
    public StructureType<?> type() {
        return NoirmStructures.NINJA_VILLAGE.get();
    }
}

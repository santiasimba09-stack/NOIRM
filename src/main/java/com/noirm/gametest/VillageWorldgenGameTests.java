package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.world.village.NinjaVillageStructure;
import com.noirm.world.village.VillageBuildQueue;
import com.noirm.world.village.VillageBuilder;
import com.noirm.world.village.VillageCanvas;
import com.noirm.world.village.VillageKind;
import com.noirm.world.village.VillagePalette;
import com.noirm.world.village.VillagePiece;
import com.noirm.world.village.VillagePlan;
import com.noirm.world.village.VillageWardStructure;
import com.noirm.world.village.WardPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Tests for how villages get into the world at full size: the hidden village's "ward" starts
 * cover every chunk around its anchor (and nothing else), every ward works out the same village
 * as the core start, world generation really builds the village's far edge, towns keep clear of
 * hidden villages, the plan's per-chunk index finds exactly what a full search would, and
 * {@code /noirm village} builds a few chunks per tick.
 *
 * <p>The game test world is an ordinary world (its seed is picked when {@code run/world} is first
 * made), so the tests look for a hidden village that really generates in it, nearest the centre.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageWorldgenGameTests {
    private VillageWorldgenGameTests() {}

    /** A hidden village that generates in the test world: its anchor chunk and its core piece. */
    private record Capital(ChunkPos anchor, VillagePiece core) {}

    // ------------------------------------------------------------ wards

    @GameTest(template = "empty", batch = "noirm_worldgen_wards")
    public static void wardsCoverTheHiddenVillage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var sets = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        RandomSpreadStructurePlacement capitals = capitals(level);
        StructurePlacement wards = sets.get(Noirm.id("hidden_village_wards")).placement();
        ChunkGeneratorStructureState state = state(level);
        long seed = level.getSeed();
        ChunkPos anchor = capitals.getPotentialStructureChunk(seed, 0, 0);
        helper.assertTrue(capitals.isStructureChunk(state, anchor.x, anchor.z), "a hidden village may be anchored at " + anchor);
        helper.assertFalse(wards.isStructureChunk(state, anchor.x, anchor.z), "the anchor chunk is the core's, not a ward's");

        // Every anchor that could reach the area we look at.
        List<ChunkPos> anchors = new ArrayList<>();
        for (int rx = -1; rx <= 1; rx++) {
            for (int rz = -1; rz <= 1; rz++) {
                anchors.add(capitals.getPotentialStructureChunk(seed, rx * capitals.spacing(), rz * capitals.spacing()));
            }
        }
        int reach = WardPlacement.reach(VillageKind.CAPITAL);
        int ring = 0;
        for (int dx = -40; dx <= 40; dx++) {
            for (int dz = -40; dz <= 40; dz++) {
                int x = anchor.x + dx, z = anchor.z + dz;
                boolean expected = false;
                for (ChunkPos a : anchors) {
                    if (!(a.x == x && a.z == z) && nearest(a, x, z) <= reach) {
                        expected = true;
                    }
                }
                boolean placed = wards.isStructureChunk(state, x, z);
                if (placed != expected) {
                    throw new GameTestAssertException("ward at chunk " + x + ", " + z + ": expected " + expected + ", got " + placed);
                }
                if (placed && nearest(anchor, x, z) <= reach) {
                    ring++;
                }
            }
        }
        // π (288 / 16)² ≈ 1,018 chunks, plus the part-chunks along the edge.
        helper.assertTrue(ring > 950 && ring < 1250, "a whole village's worth of wards around the anchor, got " + ring);
        helper.assertFalse(wards.isStructureChunk(state, anchor.x + 19, anchor.z) || wards.isStructureChunk(state, anchor.x, anchor.z - 19),
                "and none past its edge");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_worldgen_wards", timeoutTicks = 200)
    public static void wardsBuildTheSameVillageAsTheCore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NinjaVillageStructure core = core(level);
        VillageWardStructure ward = ward(level);
        Capital capital = findCapital(level, 0);
        helper.assertTrue(capital != null, "a hidden village generates somewhere near the test world's centre");
        ChunkPos anchor = capital.anchor();
        VillagePiece corePiece = capital.core();
        int ground = corePiece.origin().getY();
        helper.assertTrue(corePiece.origin().equals(new BlockPos(anchor.getMiddleBlockX(), ground, anchor.getMiddleBlockZ())),
                "centred on its anchor chunk: " + corePiece.origin());
        helper.assertTrue(sameColumns(corePiece.getBoundingBox(), anchor) && corePiece.getBoundingBox().minY() == ground,
                "the core start builds only its own chunk: " + corePiece.getBoundingBox());

        // A fresh look at the same anchor picks the same rotation and seed (it's how wards find their village).
        NinjaVillageStructure.Site fresh = core.site(context(level, anchor, core)).orElseThrow();
        helper.assertTrue(fresh.seed() == corePiece.seed() && fresh.rotation() == corePiece.rotation() && fresh.origin().equals(corePiece.origin()),
                "the same anchor always gives the same village");

        // Two different wards of the same village.
        for (ChunkPos chunk : new ChunkPos[] {new ChunkPos(anchor.x + 3, anchor.z), new ChunkPos(anchor.x - 9, anchor.z + 12)}) {
            VillagePiece piece = piece(ward.findValidGenerationPoint(context(level, chunk, ward)));
            helper.assertTrue(piece != null, "a ward at " + chunk);
            helper.assertTrue(piece.seed() == corePiece.seed() && piece.rotation() == corePiece.rotation() && piece.origin().equals(corePiece.origin())
                    && piece.palette() == corePiece.palette() && piece.kind() == VillageKind.CAPITAL && piece.clearHeight() == corePiece.clearHeight(),
                    "the ward at " + chunk + " builds the core's village (seed " + piece.seed() + " vs " + corePiece.seed() + ", "
                            + piece.rotation() + " vs " + corePiece.rotation() + ")");
            helper.assertTrue(sameColumns(piece.getBoundingBox(), chunk) && piece.getBoundingBox().minY() == ground,
                    "and only its own chunk, from the village floor up: " + piece.getBoundingBox());
        }

        // Every chunk of the village is built exactly once: by the core (the anchor) or by its own ward.
        int reach = VillageKind.CAPITAL.radius + VillageWardStructure.EDGE;
        int r = reach / 16 + 2;
        int built = 0;
        for (int x = anchor.x - r; x <= anchor.x + r; x++) {
            for (int z = anchor.z - r; z <= anchor.z + r; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                Structure structure = chunk.equals(anchor) ? core : ward;
                VillagePiece piece = piece(structure.findValidGenerationPoint(context(level, chunk, structure)));
                boolean inside = WardPlacement.reaches(corePiece.origin().getX(), corePiece.origin().getZ(), x, z, reach);
                helper.assertTrue((piece != null) == inside, "chunk " + chunk + (inside ? " is part of the village" : " is beyond its edge"));
                if (piece != null) {
                    helper.assertTrue(sameColumns(piece.getBoundingBox(), chunk), "chunk " + chunk + " builds only itself");
                    built++;
                }
            }
        }
        helper.assertTrue(ward.findValidGenerationPoint(context(level, anchor, ward)).isEmpty(), "no ward in the core's chunk");
        // π (274 / 16)² ≈ 921 chunks, plus the part-chunks along the edge.
        helper.assertTrue(built > 900 && built < 1100, "the whole village is covered: " + built + " chunks");
        helper.succeed();
    }

    /**
     * The real thing: world generation builds a stretch of a hidden village's great wall, far beyond
     * the 8 chunks one structure can reach, from the ward started in that chunk; and
     * {@code /locate} finds the village's core. (Uses a village away from the other tests.)
     */
    @GameTest(template = "empty", batch = "noirm_worldgen_far", timeoutTicks = 1200)
    public static void worldGenerationBuildsTheFarEdge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NinjaVillageStructure core = core(level);
        VillageWardStructure ward = ward(level);
        Capital capital = findCapital(level, 1);
        helper.assertTrue(capital != null, "a hidden village generates somewhere in the test world");
        ChunkPos anchor = capital.anchor();
        BlockPos origin = capital.core().origin();

        long started = System.nanoTime();
        VillagePlan.of(VillageKind.CAPITAL, capital.core().palette(), capital.core().seed());
        Noirm.LOGGER.info("Planned a hidden village in {} ms", (System.nanoTime() - started) / 1_000_000);

        // /locate finds the core.
        var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(
                ResourceKey.create(Registries.STRUCTURE, Noirm.id("hidden_village")));
        var located = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), origin, 100, false);
        helper.assertTrue(located != null && new ChunkPos(located.getFirst()).equals(anchor),
                "/locate finds the hidden village at " + anchor + ", got " + (located == null ? null : located.getFirst()));

        // A spot on the great wall, 45° south-east of the centre (no gate there), 264 blocks out.
        int w = VillageKind.CAPITAL.wall;
        int d = (int) Math.round(w / Math.sqrt(2));
        BlockPos onWall = new VillageCanvas(level, BoundingBox.infinite(), origin, capital.core().rotation()).world(d, 0, d);
        ChunkPos wallChunk = new ChunkPos(onWall);
        helper.assertTrue(Math.max(Math.abs(wallChunk.x - anchor.x), Math.abs(wallChunk.z - anchor.z)) > 8,
                "the wall is further from the core than one structure could reach");
        started = System.nanoTime();
        ChunkAccess chunk = level.getChunk(wallChunk.x, wallChunk.z);
        Noirm.LOGGER.info("Generated a chunk of a hidden village's wall in {} ms", (System.nanoTime() - started) / 1_000_000);
        StructureStart start = chunk.getStartForStructure(ward);
        helper.assertTrue(start != null && start.isValid() && start.getPieces().size() == 1
                        && start.getPieces().get(0) instanceof VillagePiece piece && piece.seed() == capital.core().seed(),
                "the wall's chunk has its own ward of the village");
        int wall = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = wallChunk.getMinBlockX(); x <= wallChunk.getMaxBlockX(); x++) {
            for (int z = wallChunk.getMinBlockZ(); z <= wallChunk.getMaxBlockZ(); z++) {
                if (!level.getBlockState(at.set(x, origin.getY() + 5, z)).isAir()) {
                    wall++;
                }
            }
        }
        helper.assertTrue(wall >= 16, "the great wall stands there (everything else was cleared): " + wall + " blocks");
        helper.assertTrue(level.getChunk(anchor.x, anchor.z).getStartForStructure(core) != null, "and the core started in the anchor chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_worldgen_wards", timeoutTicks = 200)
    public static void settlementsKeepClearOfHiddenVillages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        NinjaVillageStructure town = (NinjaVillageStructure) structures.get(Noirm.id("ninja_town"));
        NinjaVillageStructure outpost = (NinjaVillageStructure) structures.get(Noirm.id("ninja_outpost"));
        Capital capital = findCapital(level, 0);
        helper.assertTrue(capital != null, "a hidden village generates somewhere near the test world's centre");
        BlockPos centre = capital.core().origin();
        var context = context(level, capital.anchor(), town);
        // A town keeps 272 + 72 + 32 = 376 blocks from a hidden village's centre; an outpost 272 + 15 + 32 = 319.
        helper.assertTrue(town.nearHiddenVillage(context, centre.offset(320, 0, 0)), "no town 320 blocks from a hidden village");
        helper.assertTrue(outpost.nearHiddenVillage(context, centre.offset(0, 0, -300)), "no outpost 300 blocks from one");
        helper.assertFalse(outpost.nearHiddenVillage(context, centre.offset(0, 0, -340)), "but one 340 blocks away is fine");
        helper.assertFalse(town.nearHiddenVillage(context, centre.offset(-400, 0, 0)), "and a town 400 blocks away");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_worldgen_debug", timeoutTicks = 2000)
    public static void debugCapitalRate(GameTestHelper helper) { // DEBUG (temporary)
        ServerLevel level = helper.getLevel();
        NinjaVillageStructure core = core(level);
        RandomSpreadStructurePlacement capitals = capitals(level);
        var towns = (NinjaVillageStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(Noirm.id("ninja_town"));
        int valid = 0, checked = 0;
        long worst = 0, total = 0;
        for (int rx = -4; rx <= 4; rx++) {
            for (int rz = -4; rz <= 4; rz++) {
                ChunkPos anchor = capitals.getPotentialStructureChunk(level.getSeed() + 1, rx * capitals.spacing(), rz * capitals.spacing());
                var ctx = context(level, anchor, core);
                long t = System.nanoTime();
                boolean ok = core.site(ctx).isPresent();
                long took = System.nanoTime() - t;
                worst = Math.max(worst, took);
                total += took;
                checked++;
                if (ok) {
                    valid++;
                }
            }
        }
        int townsOk = 0;
        long townTime = 0;
        for (int i = 0; i < 50; i++) {
            ChunkPos at = new ChunkPos(i * 37 - 900, i * 53 - 1300);
            long t = System.nanoTime();
            if (towns.site(context(level, at, towns)).isPresent()) {
                townsOk++;
            }
            townTime += System.nanoTime() - t;
        }
        Noirm.LOGGER.info("DEBUG capital sites: {} of {} good; average {} ms, worst {} ms. Towns: {} of 50 good, average {} ms", valid, checked,
                total / checked / 1_000_000, worst / 1_000_000, townsOk, townTime / 50 / 1_000_000);
        helper.succeed();
    }

    // ------------------------------------------------------------ the plan

    @GameTest(template = "empty", batch = "noirm_worldgen_plan", timeoutTicks = 200)
    public static void planIndexFindsWhatAFullSearchFinds(GameTestHelper helper) {
        VillagePlan plan = VillagePlan.uncached(VillageKind.CAPITAL, VillagePalette.LEAF, 2024L);
        int r = VillageKind.CAPITAL.radius + 2;
        int spawns = 0;
        // Chunk-sized squares across the whole village, offset like a real chunk grid (the centre is a chunk's middle).
        for (int x1 = -r - 9; x1 <= r; x1 += 16) {
            for (int z1 = -r - 9; z1 <= r; z1 += 16) {
                int x2 = x1 + 15, z2 = z1 + 15;
                List<VillagePlan.Lot> lots = new ArrayList<>();
                for (VillagePlan.Lot lot : plan.lots) {
                    if (lot.overlaps(x1, z1, x2, z2, VillagePlan.LOT_MARGIN)) {
                        lots.add(lot);
                    }
                }
                helper.assertTrue(plan.lotsIn(x1, z1, x2, z2).equals(lots), "the lots near " + x1 + ", " + z1);
                int m = VillagePlan.PROP_MARGIN;
                List<VillagePlan.Prop> props = new ArrayList<>();
                for (VillagePlan.Prop prop : plan.props) {
                    if (prop.x() >= x1 - m && prop.x() <= x2 + m && prop.z() >= z1 - m && prop.z() <= z2 + m) {
                        props.add(prop);
                    }
                }
                helper.assertTrue(plan.propsIn(x1, z1, x2, z2).equals(props), "the props near " + x1 + ", " + z1);
                List<Integer> people = new ArrayList<>();
                for (int i = 0; i < plan.spawns.size(); i++) {
                    VillagePlan.Spawn s = plan.spawns.get(i);
                    if (s.x() >= x1 && s.x() <= x2 && s.z() >= z1 && s.z() <= z2) {
                        people.add(i);
                    }
                }
                helper.assertTrue(java.util.Arrays.equals(plan.spawnIndicesIn(x1, z1, x2, z2), people.stream().mapToInt(Integer::intValue).toArray()),
                        "the people in " + x1 + ", " + z1);
                spawns += people.size();
            }
        }
        helper.assertTrue(spawns == plan.spawns.size(), "every resident is placed by exactly one chunk: " + spawns + " of " + plan.spawns.size());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_worldgen_plan", timeoutTicks = 200)
    public static void planCacheIsSharedBetweenThreads(GameTestHelper helper) {
        long seed = 31337L + helper.getLevel().getGameTime();
        List<CompletableFuture<VillagePlan>> asks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            asks.add(CompletableFuture.supplyAsync(() -> VillagePlan.of(VillageKind.TOWN, VillagePalette.SNOW, seed)));
        }
        VillagePlan first = asks.get(0).join();
        for (CompletableFuture<VillagePlan> ask : asks) {
            helper.assertTrue(ask.join() == first, "every thread gets the one plan");
        }
        helper.assertTrue(VillagePlan.of(VillageKind.TOWN, VillagePalette.SNOW, seed) == first, "and it stays cached");
        // The cache stays small: plan a few more villages and the oldest is dropped.
        for (int i = 1; i <= 20; i++) {
            VillagePlan.of(VillageKind.OUTPOST, VillagePalette.SNOW, seed + i);
        }
        helper.assertTrue(VillagePlan.of(VillageKind.TOWN, VillagePalette.SNOW, seed) != first, "old plans are forgotten");
        helper.succeed();
    }

    // ------------------------------------------------------------ /noirm village

    /** The command's village is built over a few ticks, each chunk once: the same people as building it all at once. */
    @GameTest(template = "empty", batch = "noirm_worldgen_progressive", timeoutTicks = 300)
    public static void villageIsBuiltAFewChunksAtATime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        BoundingBox area = new BoundingBox(origin.getX() - 19, origin.getY() - 15, origin.getZ() - 19,
                origin.getX() + 19, origin.getY() + 24, origin.getZ() + 19);
        Snapshot snapshot = new Snapshot(level, area);
        String[] failure = {null};
        boolean[] finished = {false};
        VillageBuildQueue.Job job = VillageBuilder.buildNow(level, origin, VillageKind.OUTPOST, VillagePalette.LEAF, 42L, done -> {
            try {
                helper.assertTrue(level.getBlockState(origin).is(Blocks.CAMPFIRE), "the campfire in the middle");
                helper.assertTrue(level.getBlockState(origin.offset(0, 0, -VillageBuilder.OUTPOST_WALL)).is(Blocks.OAK_LOG), "the palisade");
                int queued = residents(level, area);
                snapshot.restore();
                // The same village, built in one go.
                VillageBuilder.build(level, area, VillageKind.OUTPOST, VillagePalette.LEAF, origin, Rotation.NONE, 42L);
                int direct = residents(level, area);
                helper.assertTrue(queued > 0 && queued == direct, "every chunk built once: " + queued + " ninjas, " + direct + " when built at once");
            } catch (GameTestAssertException e) {
                failure[0] = e.getMessage();
            } finally {
                snapshot.restore();
                finished[0] = true;
            }
        });
        helper.assertTrue(job.chunks() >= 4 && !job.isDone(), "queued, not built on the spot: " + job.chunks() + " chunks");
        helper.succeedWhen(() -> {
            helper.assertTrue(finished[0], "still building: " + job.built() + " of " + job.chunks() + " chunks");
            helper.assertTrue(failure[0] == null, String.valueOf(failure[0]));
        });
    }

    // ------------------------------------------------------------ helpers

    private static NinjaVillageStructure core(ServerLevel level) {
        return (NinjaVillageStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(Noirm.id("hidden_village"));
    }

    private static VillageWardStructure ward(ServerLevel level) {
        return (VillageWardStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(Noirm.id("hidden_village_ward"));
    }

    /** The hidden villages' placement (hidden_villages.json). */
    private static RandomSpreadStructurePlacement capitals(ServerLevel level) {
        return (RandomSpreadStructurePlacement) level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(Noirm.id("hidden_villages")).placement();
    }

    /**
     * The hidden village nearest the world's centre that really generates (good site, right biome),
     * looking at the anchors ring by ring from {@code firstRing} (ring 0 is the centre's own region).
     */
    private static Capital findCapital(ServerLevel level, int firstRing) {
        NinjaVillageStructure core = core(level);
        RandomSpreadStructurePlacement capitals = capitals(level);
        int spacing = capitals.spacing();
        long started = System.nanoTime();
        int checked = 0;
        for (int ring = firstRing; ring <= 6; ring++) {
            for (int rx = -ring; rx <= ring; rx++) {
                for (int rz = -ring; rz <= ring; rz++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) != ring) {
                        continue;
                    }
                    ChunkPos anchor = capitals.getPotentialStructureChunk(level.getSeed(), rx * spacing, rz * spacing);
                    checked++;
                    VillagePiece piece = piece(core.findValidGenerationPoint(context(level, anchor, core)));
                    if (piece != null) {
                        Noirm.LOGGER.info("Found a hidden village at {} after checking {} sites in {} ms", anchor, checked,
                                (System.nanoTime() - started) / 1_000_000);
                        return new Capital(anchor, piece);
                    }
                }
            }
        }
        return null;
    }

    /** A structure state for an ordinary world with every structure set. */
    private static ChunkGeneratorStructureState state(ServerLevel level) {
        var source = level.getChunkSource();
        return ChunkGeneratorStructureState.createForNormal(source.randomState(), level.getSeed(), source.getGenerator().getBiomeSource(),
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET));
    }

    /** What world generation hands a structure for {@code chunk}. */
    private static Structure.GenerationContext context(ServerLevel level, ChunkPos chunk, Structure structure) {
        var source = level.getChunkSource();
        return new Structure.GenerationContext(level.registryAccess(), source.getGenerator(), source.getGenerator().getBiomeSource(),
                source.randomState(), level.getStructureManager(), level.getSeed(), chunk, level, structure.biomes()::contains);
    }

    private static VillagePiece piece(Optional<Structure.GenerationStub> stub) {
        if (stub.isEmpty()) {
            return null;
        }
        List<StructurePiece> pieces = stub.get().getPiecesBuilder().build().pieces();
        return pieces.size() == 1 && pieces.get(0) instanceof VillagePiece piece ? piece : null;
    }

    private static boolean sameColumns(BoundingBox box, ChunkPos chunk) {
        return box.minX() == chunk.getMinBlockX() && box.maxX() == chunk.getMaxBlockX() && box.minZ() == chunk.getMinBlockZ() && box.maxZ() == chunk.getMaxBlockZ();
    }

    /** Distance (blocks) from the centre of the village anchored at {@code anchor} to the nearest column of chunk (x, z). */
    private static double nearest(ChunkPos anchor, int x, int z) {
        int cx = anchor.getMiddleBlockX(), cz = anchor.getMiddleBlockZ();
        int dx = Math.max(0, Math.max((x << 4) - cx, cx - ((x << 4) + 15)));
        int dz = Math.max(0, Math.max((z << 4) - cz, cz - ((z << 4) + 15)));
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    private static int residents(ServerLevel level, BoundingBox area) {
        return level.getEntitiesOfClass(SparringNinja.class, AABB.of(area).inflate(2), SparringNinja::isResident).size();
    }

    /** Remembers every block in an area (and clears its mobs on restore), so a test can build freely. */
    private static final class Snapshot {
        private final ServerLevel level;
        private final BoundingBox area;
        private final BlockState[] states;

        Snapshot(ServerLevel level, BoundingBox area) {
            this.level = level;
            this.area = area;
            this.states = new BlockState[area.getXSpan() * area.getYSpan() * area.getZSpan()];
            int i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
                states[i++] = level.getBlockState(pos);
            }
        }

        void restore() {
            level.getEntitiesOfClass(LivingEntity.class, AABB.of(area).inflate(2), e -> !(e instanceof Player)).forEach(LivingEntity::discard);
            int i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
                BlockState old = states[i++];
                if (level.getBlockState(pos) != old) {
                    level.setBlock(pos, old, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, AABB.of(area).inflate(2)).forEach(e -> e.discard());
        }
    }
}

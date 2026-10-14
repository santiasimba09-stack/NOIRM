package com.noirm.world.village;

import com.noirm.Noirm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds villages in a live world one chunk at a time, a few chunks every server tick
 * ({@code /noirm village}), so even a 545-block hidden village doesn't freeze the server.
 *
 * <p>Each chunk is built like world generation builds it (the same {@link VillageBuilder#build}
 * clipped to the chunk), from the middle outwards. Right after, its fences, panes, walls and stairs
 * are joined up, together with the edge of the chunks already built next to it (so they connect
 * across the border). The chunks just ahead are asked to load in the background (a short-lived
 * chunk ticket), so the server isn't kept waiting for them either.
 *
 * <p>Everything here runs on the server thread.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class VillageBuildQueue {
    /** How long building may take each server tick (nanoseconds): 20 ms of the 50 a tick has. */
    private static final long BUDGET = 20_000_000L;
    /** How many chunks ahead to ask the server to load. */
    private static final int LOOKAHEAD = 24;
    /** How long to wait for a chunk to load before loading it on the spot (ticks). */
    private static final int MAX_WAIT = 200;
    /** Keeps the chunks being built (and their neighbours) loaded for a few seconds. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("noirm_village", Comparator.comparingLong(ChunkPos::toLong), 100);

    private static final List<Job> JOBS = new ArrayList<>();

    private VillageBuildQueue() {}

    /** One village being built. */
    public static final class Job {
        private final ServerLevel level;
        private final BlockPos centre;
        private final VillageKind kind;
        private final VillagePalette palette;
        private final Rotation rotation;
        private final long seed;
        private final int minY, maxY;
        private final List<ChunkPos> chunks = new ArrayList<>();
        private final Consumer<Job> onDone;
        private final long startedAt = System.nanoTime();
        private int built;
        private int waited;
        private boolean done;
        private long tookNanos;

        private Job(ServerLevel level, BlockPos centre, VillageKind kind, VillagePalette palette, Rotation rotation, long seed, Consumer<Job> onDone) {
            this.level = level;
            this.centre = centre;
            this.kind = kind;
            this.palette = palette;
            this.rotation = rotation;
            this.seed = seed;
            this.onDone = onDone;
            this.minY = centre.getY() - 41;
            this.maxY = centre.getY() + kind.height + 12;
            // Every chunk the village reaches (the same chunks world generation would build), middle first.
            int reach = kind.radius + VillageWardStructure.EDGE;
            ChunkPos middle = new ChunkPos(centre);
            int r = (reach >> 4) + 1;
            for (int x = middle.x - r; x <= middle.x + r; x++) {
                for (int z = middle.z - r; z <= middle.z + r; z++) {
                    if (WardPlacement.reaches(centre.getX(), centre.getZ(), x, z, reach)) {
                        chunks.add(new ChunkPos(x, z));
                    }
                }
            }
            chunks.sort(Comparator.comparingLong(c -> (long) (c.x - middle.x) * (c.x - middle.x) + (long) (c.z - middle.z) * (c.z - middle.z)));
        }

        /** How many chunks the village covers. */
        public int chunks() {
            return chunks.size();
        }

        /** How many of them are built so far. */
        public int built() {
            return built;
        }

        public boolean isDone() {
            return done;
        }

        public VillageKind kind() {
            return kind;
        }

        public VillagePalette palette() {
            return palette;
        }

        /** How long the whole build took (once done), in seconds. */
        public double seconds() {
            return tookNanos / 1e9;
        }

        /**
         * Builds the next chunk, if it's loaded (or has been waited for long enough, or {@code wait}
         * is false: then it's loaded on the spot). False when it had to wait, or there's nothing left.
         */
        private boolean step(boolean wait) {
            if (built >= chunks.size()) {
                return false;
            }
            var chunks = level.getChunkSource();
            for (int i = built; i < Math.min(this.chunks.size(), built + LOOKAHEAD); i++) {
                ChunkPos ahead = this.chunks.get(i);
                chunks.addRegionTicket(TICKET, ahead, 2, ahead); // (lots near the chunk's edge look a little way into the next)
            }
            ChunkPos pos = this.chunks.get(built);
            if (wait && !neighbourhoodLoaded(pos) && ++waited < MAX_WAIT) {
                return false;
            }
            waited = 0;
            BoundingBox clip = new BoundingBox(pos.getMinBlockX(), minY, pos.getMinBlockZ(), pos.getMaxBlockX(), maxY, pos.getMaxBlockZ());
            VillageBuilder.build(level, clip, kind, palette, centre, rotation, seed, kind.height);
            joinUp(pos);
            built++;
            return true;
        }

        private boolean neighbourhoodLoaded(ChunkPos pos) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.getChunkSource().getChunkNow(pos.x + dx, pos.z + dz) == null) {
                        return false; // (hasChunk only says a chunk is wanted, not that it's ready)
                    }
                }
            }
            return true;
        }

        /**
         * Joins up the fences, panes, walls and stairs of a freshly built chunk and of the block
         * next to it all round (the edge of the neighbours built before it), the way world
         * generation does when a chunk is finished. Skips what lies in unloaded chunks.
         */
        private void joinUp(ChunkPos pos) {
            boolean[] loaded = new boolean[9];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    loaded[(dx + 1) * 3 + dz + 1] = level.getChunkSource().getChunkNow(pos.x + dx, pos.z + dz) != null;
                }
            }
            int x0 = pos.getMinBlockX(), z0 = pos.getMinBlockZ();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int x = x0 - 1; x <= x0 + 16; x++) {
                for (int z = z0 - 1; z <= z0 + 16; z++) {
                    // This column and the ones beside it must be loaded (don't load chunks just to look).
                    if (!loaded(loaded, x0, z0, x, z) || !loaded(loaded, x0, z0, x - 1, z) || !loaded(loaded, x0, z0, x + 1, z)
                            || !loaded(loaded, x0, z0, x, z - 1) || !loaded(loaded, x0, z0, x, z + 1)) {
                        continue;
                    }
                    for (int y = centre.getY() - 3; y <= maxY; y++) {
                        at.set(x, y, z);
                        BlockState state = level.getBlockState(at);
                        Block block = state.getBlock();
                        if (block instanceof FenceBlock || block instanceof IronBarsBlock || block instanceof WallBlock || block instanceof StairBlock) {
                            BlockState joined = Block.updateFromNeighbourShapes(state, level, at);
                            if (joined != state) {
                                level.setBlock(at, joined, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            }
                        }
                    }
                }
            }
        }

        private static boolean loaded(boolean[] loaded, int x0, int z0, int x, int z) {
            int dx = Math.floorDiv(x - x0, 16), dz = Math.floorDiv(z - z0, 16);
            return dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1 && loaded[(dx + 1) * 3 + dz + 1];
        }

        private void finish() {
            done = true;
            tookNanos = System.nanoTime() - startedAt;
            Noirm.LOGGER.info("Built a {} village ({} chunks) in {} s", kind.getSerializedName(), chunks.size(), String.format("%.1f", seconds()));
            if (onDone != null) {
                onDone.accept(this);
            }
        }
    }

    /**
     * Queues a village to be built a few chunks per tick, centred on {@code centre} (the first air
     * block above the village floor). {@code onDone} runs on the server thread when it's finished.
     */
    public static Job submit(ServerLevel level, BlockPos centre, VillageKind kind, VillagePalette palette, Rotation rotation, long seed,
                             Consumer<Job> onDone) {
        Job job = new Job(level, centre, kind, palette, rotation, seed, onDone);
        JOBS.add(job);
        return job;
    }

    /** Builds a whole village right now (loading chunks on the spot). Fine for an outpost or a town; a hidden village takes a while. */
    public static Job buildAll(ServerLevel level, BlockPos centre, VillageKind kind, VillagePalette palette, Rotation rotation, long seed) {
        Job job = new Job(level, centre, kind, palette, rotation, seed, null);
        while (job.step(false)) {
            // keep going
        }
        job.finish();
        return job;
    }

    @SubscribeEvent
    public static void onVillageBuildTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        long end = System.nanoTime() + BUDGET;
        List<Job> finished = new ArrayList<>();
        for (Job job : JOBS) {
            while (System.nanoTime() < end && job.step(true)) {
                // build until the budget runs out or the next chunk isn't loaded yet
            }
            if (job.built >= job.chunks.size()) {
                finished.add(job);
            }
            if (System.nanoTime() >= end) {
                break;
            }
        }
        JOBS.removeAll(finished);
        finished.forEach(Job::finish);
    }

    @SubscribeEvent
    public static void onVillageBuildServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }
}

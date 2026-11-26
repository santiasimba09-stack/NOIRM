package com.noirm.gametest;

import com.noirm.Noirm;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the game-test world fair (only on the game-test server: never in a real game).
 *
 * <p>The test framework only force-loads the chunk(s) under each test's 3×3 arena. A chunk next to
 * it is loaded but not <em>entity-ticking</em>: a mob standing there never moves, thinks or fights.
 * Tests spawn fighters a few blocks out (a wild group spreads around its leader, a squad is
 * scattered to the corners of a 13×13 floor, a village is built 18 blocks across), and whether
 * those blocks fell in the arena's chunk or the next one depended on where the test happened to
 * land in the grid, which changes from run to run: a ninja frozen in a neighbouring chunk made
 * tests like "the squad gathers round its leader" or "a recruit leaves the wild" fail now and then.
 * So every mob that a test brings into the world gets its chunk, and the chunks round it, kept
 * entity-ticking, the way a player standing nearby would.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class TestWorld {
    /** Chunks forced so far (so each is forced once). */
    private static final Set<Long> FORCED = new HashSet<>();
    /** Chunks to force at the end of this tick (never while an entity is being added: forcing loads the chunk). */
    private static final Map<ServerLevel, Set<Long>> PENDING = new HashMap<>();

    private TestWorld() {}

    /** Whether {@code server} is the headless game-test server (natural spawners stay quiet there). */
    public static boolean isTestServer(MinecraftServer server) {
        return server instanceof GameTestServer;
    }

    @SubscribeEvent
    public static void mobJoins(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)
                || !isTestServer(level.getServer())) {
            return;
        }
        ChunkPos centre = mob.chunkPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long chunk = ChunkPos.asLong(centre.x + dx, centre.z + dz);
                if (FORCED.add(chunk)) {
                    PENDING.computeIfAbsent(level, l -> new HashSet<>()).add(chunk);
                }
            }
        }
    }

    @SubscribeEvent
    public static void forcePending(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        Map<ServerLevel, Set<Long>> due = new HashMap<>(PENDING);
        PENDING.clear();
        due.forEach((level, chunks) -> chunks.forEach(c -> level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true)));
    }

    @SubscribeEvent
    public static void testServerStopped(ServerStoppedEvent event) {
        FORCED.clear();
        PENDING.clear();
    }
}

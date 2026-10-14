package com.noirm.world.village;

import com.noirm.world.village.VillagePlan.Lot;
import com.noirm.world.village.VillagePlan.LotType;
import com.noirm.world.village.VillagePlan.Paving;
import com.noirm.world.village.VillagePlan.Prop;
import com.noirm.world.village.VillagePlan.Road;
import com.noirm.world.village.VillagePlan.Spawn;
import com.noirm.world.village.VillagePlan.Wire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import static com.noirm.world.village.Buildings.HANGING_LANTERN;
import static com.noirm.world.village.Buildings.slab;
import static com.noirm.world.village.Buildings.stairs;

/**
 * Builds a ninja village (the part of it inside one chunk) from its {@link VillagePlan}.
 *
 * <p>Order: level the ground, pave the streets, raise the walls (or the Kage Rock, or the desert
 * cliffs), draw every lot that reaches into the chunk, then the street furniture and the power
 * lines, and finally put the people who live in this chunk into the world.
 *
 * <p>Local layout (before rotation): the village centre is (0, 0), the main gate is to the south
 * (+z), the Kage Rock to the north.
 */
public final class VillageBuilder {
    public static final int TOWER_RADIUS = 12;
    /** Height of the Kage tower's main drum; the balcony is on top of it. */
    public static final int TOWER_HEIGHT = 28;
    /** Height of each floor inside the tower. */
    public static final int TOWER_FLOOR = 7;
    /** Height of the capital's wall (blocks); the wall walk is on top. */
    public static final int WALL_HEIGHT = 14;
    /** The height of the wires between utility poles. */
    public static final int POLE_HEIGHT = 7;
    /** Radius of an outpost's palisade. */
    public static final int OUTPOST_WALL = VillageKind.OUTPOST.wall;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final VillageCanvas c;
    private final VillagePlan plan;
    private final VillagePalette p;
    /** The chunk being built, in local coordinates. */
    private final int lx1, lz1, lx2, lz2;

    private VillageBuilder(VillageCanvas canvas, VillagePlan plan) {
        this.c = canvas;
        this.plan = plan;
        this.p = plan.palette;
        int[] box = canvas.localBounds();
        this.lx1 = box[0];
        this.lz1 = box[1];
        this.lx2 = box[2];
        this.lz2 = box[3];
    }

    /**
     * Builds (the part inside {@code clip} of) a village centred on {@code origin}, where
     * {@code origin.getY()} is the first air block above the finished ground. Everything up to
     * {@code clearHeight} above the ground is cleared first.
     */
    public static void build(WorldGenLevel level, BoundingBox clip, VillageKind kind, VillagePalette palette,
                             BlockPos origin, Rotation rotation, long seed, int clearHeight) {
        VillagePlan plan = VillagePlan.of(kind, palette, seed);
        VillageBuilder builder = new VillageBuilder(new VillageCanvas(level, clip, origin, rotation), plan);
        builder.ground(kind.radius, Math.max(kind.height, clearHeight));
        builder.roads();
        Waterways.draw(builder.c, plan);
        Fortifications.draw(builder.c, plan);
        builder.lots();
        builder.props();
        builder.spawns();
    }

    public static void build(WorldGenLevel level, BoundingBox clip, VillageKind kind, VillagePalette palette,
                             BlockPos origin, Rotation rotation, long seed) {
        build(level, clip, kind, palette, origin, rotation, seed, kind.height);
    }

    /**
     * Builds a village in a live world, centred on {@code centre} (the ground under it becomes the
     * village floor), a few chunks every server tick so even a hidden village doesn't freeze the
     * server ({@link VillageBuildQueue}). Each chunk is built like world generation would, and its
     * fences, panes, walls and stairs joined up. {@code onDone} runs when the last chunk is built.
     * Used by {@code /noirm village}.
     */
    public static VillageBuildQueue.Job buildNow(net.minecraft.server.level.ServerLevel level, BlockPos centre, VillageKind kind, VillagePalette palette,
                                                 long seed, java.util.function.Consumer<VillageBuildQueue.Job> onDone) {
        return VillageBuildQueue.submit(level, centre, kind, palette, Rotation.NONE, seed, onDone);
    }

    /**
     * Builds a whole village in a live world right away, chunk by chunk, as {@link #buildNow(net.minecraft.server.level.ServerLevel,
     * BlockPos, VillageKind, VillagePalette, long, java.util.function.Consumer) the queued version} does. Fine for an outpost or a
     * town (and tests); a hidden village holds the server up for a while.
     */
    public static void buildNow(net.minecraft.server.level.ServerLevel level, BlockPos centre, VillageKind kind, VillagePalette palette, long seed) {
        VillageBuildQueue.buildAll(level, centre, kind, palette, Rotation.NONE, seed);
    }

    /** Builds only the Kage tower (and its Kage), centred on {@code origin}. Used by the game tests. */
    public static void buildKageTower(WorldGenLevel level, BoundingBox clip, VillagePalette palette, BlockPos origin, boolean withKage) {
        VillageCanvas canvas = new VillageCanvas(level, clip, origin, Rotation.NONE);
        Lot lot = new Lot(LotType.KAGE_TOWER, -15, -15, 15, 15, Direction.SOUTH, 4, 0, 0L);
        Landmarks.kageTower(new LotFrame(canvas, palette, lot), withKage);
        if (withKage) {
            VillagePlan plan = VillagePlan.of(VillageKind.CAPITAL, palette, 0L);
            VillageResidents.spawn(canvas, plan, new Spawn(VillagePlan.Role.KAGE, 0, TOWER_HEIGHT + 1, 0, 0, TOWER_HEIGHT + 1, 0, 5, 0, 0, 0,
                    java.util.List.of()), 0L);
        }
    }

    // ================================================================ ground and streets

    /**
     * Levels the site: clears everything above the ground (trees, hills) up to {@code height}, fills
     * the holes beneath with the palette's foundation, and lays the palette's ground on top.
     */
    private void ground(int radius, int height) {
        for (int x = lx1; x <= lx2; x++) {
            for (int z = lz1; z <= lz2; z++) {
                if (!inDisk(x, z, radius)) {
                    continue;
                }
                for (int y = 0; y <= height; y++) {
                    if (!c.get(x, y, z).isAir()) {
                        c.set(x, y, z, AIR);
                    }
                }
                for (int y = -2; y >= -40; y--) {
                    BlockState below = c.get(x, y, z);
                    boolean hole = below.isAir() || below.canBeReplaced() || !below.getFluidState().isEmpty()
                            || below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS);
                    if (!hole) {
                        break;
                    }
                    c.set(x, y, z, p.foundation);
                }
                c.set(x, -1, z, p.ground);
            }
        }
    }

    private void roads() {
        int edge = plan.kind == VillageKind.OUTPOST ? plan.kind.radius : plan.kind.radius;
        for (Road road : plan.roads) {
            if (road.x1() > lx2 || road.x2() < lx1 || road.z1() > lz2 || road.z2() < lz1) {
                continue;
            }
            Block block = p.paving(road.paving());
            for (int x = Math.max(lx1, road.x1()); x <= Math.min(lx2, road.x2()); x++) {
                for (int z = Math.max(lz1, road.z1()); z <= Math.min(lz2, road.z2()); z++) {
                    if (!road.contains(x, z) || !inDisk(x, z, edge)) {
                        continue;
                    }
                    BlockState here = c.get(x, -1, z);
                    // A plaza wins over the streets that run into it.
                    if (road.paving() != Paving.PLAZA && here.is(p.paving(Paving.PLAZA)) && p.paving(Paving.PLAZA) != block) {
                        continue;
                    }
                    if (road.paving() == Paving.PLAZA && ((x * 7 + z * 3) & 7) == 0) {
                        c.set(x, -1, z, Blocks.POLISHED_ANDESITE);
                    } else {
                        c.set(x, -1, z, block);
                    }
                }
            }
        }
    }

    // ================================================================ lots, props, people

    private void lots() {
        // Only the lots near this chunk (the plan's index), in plan order.
        for (Lot lot : plan.lotsIn(lx1, lz1, lx2, lz2)) {
            Buildings.draw(new LotFrame(c, p, lot));
        }
    }

    private void props() {
        for (Prop prop : plan.props) {
            if (prop.x() < lx1 - 4 || prop.x() > lx2 + 4 || prop.z() < lz1 - 4 || prop.z() > lz2 + 4) {
                continue;
            }
            int x = prop.x(), z = prop.z();
            switch (prop.type()) {
                case LANTERN -> {
                    c.set(x, 0, z, p.lanternPost());
                    c.set(x, 1, z, p.lanternPost());
                    c.set(x, 2, z, Blocks.LANTERN);
                }
                case POLE -> {
                    c.fill(x, 0, z, x, POLE_HEIGHT + 1, z, p.log);
                    Direction across = prop.facing() == Direction.SOUTH ? Direction.EAST : Direction.SOUTH;
                    c.set(x + across.getStepX(), POLE_HEIGHT + 1, z + across.getStepZ(), p.fence);
                    c.set(x - across.getStepX(), POLE_HEIGHT + 1, z - across.getStepZ(), p.fence);
                    c.set(x, POLE_HEIGHT + 2, z, Blocks.LIGHTNING_ROD);
                }
                case BENCH -> {
                    Direction side = prop.facing().getClockWise();
                    c.set(x, 0, z, stairs(p.stairs, prop.facing().getOpposite()));
                    c.set(x + side.getStepX(), 0, z + side.getStepZ(), stairs(p.stairs, prop.facing().getOpposite()));
                }
                case TREE, BIG_TREE -> {
                    Lot lot = new Lot(LotType.PARK, x, z, x, z, Direction.SOUTH, 0, 0, hash(x, z, plan.seed));
                    LotFrame frame = new LotFrame(c, p, lot);
                    c.set(x, -1, z, Blocks.GRASS_BLOCK);
                    Buildings.tree(frame, 0, 0, prop.type() == VillagePlan.PropType.BIG_TREE ? 7 : 5, prop.style());
                }
                case WELL -> {
                    c.fill(x - 1, 0, z - 1, x + 1, 0, z + 1, Blocks.COBBLESTONE_WALL);
                    c.set(x, 0, z, Blocks.AIR);
                    c.set(x, -1, z, Blocks.WATER);
                    c.set(x, -2, z, Blocks.WATER);
                    for (int dx : new int[] {-1, 1}) {
                        c.fill(x + dx, 1, z, x + dx, 2, z, p.fence);
                    }
                    c.fill(x - 1, 3, z - 1, x + 1, 3, z + 1, slab(p.roofSlabs().get(0), SlabType.BOTTOM));
                    c.set(x, 2, z, Blocks.CHAIN);
                }
                default -> {
                }
            }
        }
        for (Wire wire : plan.wires) {
            boolean alongZ = wire.x1() == wire.x2();
            int from = alongZ ? Math.min(wire.z1(), wire.z2()) : Math.min(wire.x1(), wire.x2());
            int to = alongZ ? Math.max(wire.z1(), wire.z2()) : Math.max(wire.x1(), wire.x2());
            BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, alongZ ? Direction.Axis.Z : Direction.Axis.X);
            for (int k = from + 1; k < to; k++) {
                int x = alongZ ? wire.x1() : k;
                int z = alongZ ? k : wire.z1();
                if (x >= lx1 && x <= lx2 && z >= lz1 && z <= lz2 && c.get(x, wire.y(), z).isAir()) {
                    c.set(x, wire.y(), z, chain);
                }
            }
        }
    }

    private void spawns() {
        for (int i = 0; i < plan.spawns.size(); i++) {
            Spawn spawn = plan.spawns.get(i);
            if (spawn.x() >= lx1 && spawn.x() <= lx2 && spawn.z() >= lz1 && spawn.z() <= lz2) {
                VillageResidents.spawn(c, plan, spawn, plan.seed + i * 31L);
            }
        }
    }

    // ================================================================ helpers

    /** Whether (x, z) is inside a circle of radius r (a slightly fuller circle than x²+z² ≤ r², which looks rounder in blocks). */
    static boolean inDisk(int x, int z, int r) {
        return x * x + z * z <= r * r + r;
    }

    static long hash(int x, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h & Long.MAX_VALUE;
    }

    /** Smooth value noise in 0..1 on a grid of {@code cell} blocks. */
    static double noise(int x, int z, int cell, long seed) {
        int gx = Math.floorDiv(x, cell), gz = Math.floorDiv(z, cell);
        double fx = (x - gx * cell) / (double) cell, fz = (z - gz * cell) / (double) cell;
        double a = lattice(gx, gz, seed), b = lattice(gx + 1, gz, seed);
        double d = lattice(gx, gz + 1, seed), e = lattice(gx + 1, gz + 1, seed);
        double sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
        return (a + (b - a) * sx) * (1 - sz) + (d + (e - d) * sx) * sz;
    }

    private static double lattice(int x, int z, long seed) {
        return (hash(x, z, seed) % 10000) / 10000.0;
    }
}

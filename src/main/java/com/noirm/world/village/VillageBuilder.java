package com.noirm.world.village;

import com.noirm.world.village.VillagePlan.Lot;
import com.noirm.world.village.VillagePlan.LotType;
import com.noirm.world.village.VillagePlan.Paving;
import com.noirm.world.village.VillagePlan.Prop;
import com.noirm.world.village.VillagePlan.Spawn;
import com.noirm.world.village.VillagePlan.Wire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
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

    /** Draws a single lot (its village-local coordinates measured from {@code origin}). Used by the game tests. */
    public static void buildLot(WorldGenLevel level, BoundingBox clip, VillagePalette palette, BlockPos origin, Lot lot) {
        Buildings.draw(new LotFrame(new VillageCanvas(level, clip, origin, Rotation.NONE), palette, lot));
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

    /**
     * Paves the streets from the plan's street raster: each paving as a mix of blocks (dirt paths
     * with gravel and coarse dirt, plazas with flagstones…), kerbs of smooth stone along the edges
     * of the avenues, and plain paving through the gateways in the wall. River crossings are left to
     * {@link Waterways} (it builds the bridges).
     */
    private void roads() {
        long seed = plan.seed ^ 0x51DE_57EEL;
        int gateway = plan.kind.wall - (plan.kind == VillageKind.CAPITAL ? 4 : 2);
        BlockState kerb = (p == VillagePalette.SAND ? Blocks.TERRACOTTA : Blocks.SMOOTH_STONE).defaultBlockState();
        for (int x = lx1; x <= lx2; x++) {
            for (int z = lz1; z <= lz2; z++) {
                Paving paving = plan.paving(x, z);
                if (paving == null || plan.isWater(x, z) || !inDisk(x, z, plan.kind.radius)) {
                    continue;
                }
                BlockState block;
                if (!inDisk(x, z, gateway)) {
                    block = p.paving(paving).defaultBlockState();
                } else if (paving == Paving.AVENUE && kerbAt(x, z)) {
                    block = kerb;
                } else {
                    block = p.pavingMix(paving).at(x, -1, z, seed).defaultBlockState();
                }
                c.set(x, -1, z, block);
            }
        }
    }

    /** Whether an avenue block is on the avenue's edge (a kerb): beside a building, a yard or a gravel alley. */
    private boolean kerbAt(int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            Paving next = plan.paving(nx, nz);
            if (!plan.isWater(nx, nz) && (next == null || next == Paving.PATH)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ lots, props, people

    private void lots() {
        // Only the lots near this chunk (the plan's index), in plan order.
        for (Lot lot : plan.lotsIn(lx1, lz1, lx2, lz2)) {
            Buildings.draw(new LotFrame(c, p, lot));
        }
    }

    private void props() {
        // Only the props near this chunk (the plan's index): lantern lines and street gates reach a few blocks.
        for (Prop prop : plan.propsIn(lx1, lz1, lx2, lz2)) {
            int x = prop.x(), z = prop.z();
            switch (prop.type()) {
                case LANTERN -> {
                    c.set(x, 0, z, p.lanternPost());
                    c.set(x, 1, z, p.lanternPost());
                    c.set(x, 2, z, Blocks.LANTERN);
                }
                case POLE -> pole(prop);
                case LAMP_POST -> lampPost(x, z);
                case BARRELS -> barrels(prop);
                case FLOWERS -> flowers(prop);
                case NOTICE_BOARD, SIGN_BOARD -> noticeBoard(prop);
                case LANTERN_LINE -> lanternLine(prop);
                case STREET_GATE -> streetGate(prop);
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
            wire(wire);
        }
    }

    // ---- street furniture

    /** Places a block only where there's air (props never cut into buildings), and only inside this chunk. */
    private void setIfAir(int x, int y, int z, BlockState state) {
        if (x >= lx1 && x <= lx2 && z >= lz1 && z <= lz2 && c.get(x, y, z).isAir()) {
            c.set(x, y, z, state);
        }
    }

    private void setIfAir(int x, int y, int z, Block block) {
        setIfAir(x, y, z, block.defaultBlockState());
    }

    /**
     * A wooden utility pole with a crossarm across the street. Flags in the style: 1 = a
     * transformer (a cauldron on the back of the pole, insulators on the crossarm tips), 2 = a
     * lantern on an arm over the street, 4 = the street is on the counter-clockwise side.
     */
    private void pole(Prop prop) {
        int x = prop.x(), z = prop.z(), h = POLE_HEIGHT;
        c.fill(x, 0, z, x, h + 1, z, p.log);
        Direction across = prop.facing().getClockWise();
        c.set(x + across.getStepX(), h + 1, z + across.getStepZ(), p.fence);
        c.set(x - across.getStepX(), h + 1, z - across.getStepZ(), p.fence);
        Direction street = (prop.style() & 4) != 0 ? prop.facing().getCounterClockWise() : prop.facing().getClockWise();
        if ((prop.style() & 1) != 0) {
            setIfAir(x + across.getStepX(), h + 2, z + across.getStepZ(), Blocks.LIGHTNING_ROD);
            setIfAir(x - across.getStepX(), h + 2, z - across.getStepZ(), Blocks.LIGHTNING_ROD);
            Direction back = street.getOpposite();
            setIfAir(x + back.getStepX(), h - 2, z + back.getStepZ(), Blocks.CAULDRON);
        } else {
            c.set(x, h + 2, z, Blocks.LIGHTNING_ROD);
        }
        if ((prop.style() & 2) != 0) {
            setIfAir(x + street.getStepX(), 5, z + street.getStepZ(), p.fence);
            setIfAir(x + street.getStepX(), 4, z + street.getStepZ(), HANGING_LANTERN);
        }
    }

    /** An avenue lamp: a redstone lamp under an inverted daylight detector, so it lights itself at dusk and in storms. */
    private void lampPost(int x, int z) {
        c.fill(x, 0, z, x, 2, z, p.lanternPost());
        c.set(x, 3, z, Blocks.REDSTONE_LAMP);
        c.set(x, 4, z, Blocks.DAYLIGHT_DETECTOR.defaultBlockState().setValue(DaylightDetectorBlock.INVERTED, true));
    }

    private void barrels(Prop prop) {
        int x = prop.x(), z = prop.z();
        BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
        setIfAir(x, 0, z, barrel);
        if (prop.style() % 2 == 0) {
            setIfAir(x, 1, z, barrel);
        }
        Direction side = prop.facing().getClockWise();
        if (prop.style() % 3 == 0 && plan.isRoad(x + side.getStepX(), z + side.getStepZ())) {
            setIfAir(x + side.getStepX(), 0, z + side.getStepZ(), barrel);
        }
    }

    private static final Block[] POTS = {Blocks.POTTED_RED_TULIP, Blocks.POTTED_AZALEA, Blocks.POTTED_FERN, Blocks.POTTED_CORNFLOWER,
            Blocks.POTTED_ORANGE_TULIP, Blocks.POTTED_BAMBOO};

    private void flowers(Prop prop) {
        int x = prop.x(), z = prop.z();
        Direction side = prop.facing().getClockWise();
        setIfAir(x, 0, z, POTS[prop.style() % POTS.length]);
        if (plan.isRoad(x + side.getStepX(), z + side.getStepZ())) {
            setIfAir(x + side.getStepX(), 0, z + side.getStepZ(), POTS[(prop.style() + 2) % POTS.length]);
        }
    }

    /** A notice board: a plank board on two legs under a little roof, with papers pinned on its front. */
    private void noticeBoard(Prop prop) {
        int x = prop.x(), z = prop.z();
        Direction front = prop.facing(), side = front.getClockWise();
        BlockState paper = Blocks.BIRCH_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, front).setValue(TrapDoorBlock.OPEN, true);
        for (int k = -1; k <= 1; k++) {
            int bx = x + side.getStepX() * k, bz = z + side.getStepZ() * k;
            if (k != 0) {
                setIfAir(bx, 0, bz, p.fence);
            }
            setIfAir(bx, 1, bz, Blocks.SPRUCE_PLANKS);
            setIfAir(bx, 2, bz, Blocks.SPRUCE_PLANKS);
            setIfAir(bx, 3, bz, slab(p.slab, SlabType.BOTTOM));
            if (k != 1) {
                setIfAir(bx + front.getStepX(), k == 0 ? 2 : 1, bz + front.getStepZ(), paper);
            }
        }
    }

    /**
     * Paper lanterns strung across a festival street: a chain 5 blocks up from one house front to
     * the other (or to a post where there's no house), with three lanterns hanging from it.
     */
    private void lanternLine(Prop prop) {
        int x = prop.x(), z = prop.z(), reach = prop.style();
        Direction a = prop.facing();
        BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, a.getAxis());
        Block[] lights = {Blocks.SHROOMLIGHT, Blocks.PEARLESCENT_FROGLIGHT, Blocks.OCHRE_FROGLIGHT};
        for (int k = -reach; k <= reach; k++) {
            int bx = x + a.getStepX() * k, bz = z + a.getStepZ() * k;
            setIfAir(bx, 5, bz, chain);
            if (k % 2 == 0 && Math.abs(k) < reach) {
                setIfAir(bx, 4, bz, lights[Math.floorMod(k / 2 + x + z, lights.length)]);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            int ex = x + a.getStepX() * (reach + 1) * s, ez = z + a.getStepZ() * (reach + 1) * s;
            if (ex < lx1 - 1 || ex > lx2 + 1 || ez < lz1 - 1 || ez > lz2 + 1 || !c.get(ex, 5, ez).isAir()) {
                continue; // tied to a house front (or out of reach of this chunk)
            }
            int bx = x + a.getStepX() * reach * s, bz = z + a.getStepZ() * reach * s;
            for (int y = 0; y < 5; y++) {
                setIfAir(bx, y, bz, p.fence);
            }
        }
    }

    /**
     * An arched gate over a festival street: two tall posts, a beam with a tiled roof over it, and a
     * signboard under the beam painted with the village's glyph.
     */
    private void streetGate(Prop prop) {
        int x = prop.x(), z = prop.z(), reach = prop.style();
        Direction along = prop.facing(), across = along.getClockWise();
        Block post = p == VillagePalette.LEAF ? Blocks.RED_TERRACOTTA : p.log;
        Block beam = p == VillagePalette.LEAF ? Blocks.RED_TERRACOTTA : p.planks;
        Block roofSlab = p.roofSlabs().get(0);
        for (int s = -1; s <= 1; s += 2) {
            int bx = x + across.getStepX() * reach * s, bz = z + across.getStepZ() * reach * s;
            c.fill(bx, 0, bz, bx, 5, bz, post);
        }
        // The beam and its little roof; past the posts they only reach out where there's no house.
        for (int k = -reach - 2; k <= reach + 2; k++) {
            int bx = x + across.getStepX() * k, bz = z + across.getStepZ() * k;
            boolean over = Math.abs(k) <= reach;
            if (over) {
                c.set(bx, 6, bz, beam);
            } else if (Math.abs(k) == reach + 1) {
                setIfAir(bx, 6, bz, beam);
            }
            for (int d = -1; d <= 1; d++) {
                BlockState tile = slab(roofSlab, SlabType.BOTTOM);
                if (over) {
                    c.set(bx + along.getStepX() * d, 7, bz + along.getStepZ() * d, tile);
                } else {
                    setIfAir(bx + along.getStepX() * d, 7, bz + along.getStepZ() * d, tile);
                }
            }
        }
        // The signboard: rows 1–3 of the glyph, on white.
        for (int k = -2; k <= 2; k++) {
            for (int row = 1; row <= 3; row++) {
                int bx = x + across.getStepX() * k, bz = z + across.getStepZ() * k;
                c.set(bx, 6 - row, bz, p.glyph.painted(k + 2, row) ? p.paint : Blocks.WHITE_CONCRETE);
            }
        }
    }

    /**
     * A wire as a line of chains (stepping when it runs at a slant), from (x1, y, z1) to
     * (x2, y2, z2). A level wire between poles sags a block in its middle third; a service drop
     * slopes down to its house and stops at the wall.
     */
    private void wire(Wire wire) {
        if (Math.max(wire.x1(), wire.x2()) < lx1 || Math.min(wire.x1(), wire.x2()) > lx2
                || Math.max(wire.z1(), wire.z2()) < lz1 || Math.min(wire.z1(), wire.z2()) > lz2) {
            return;
        }
        boolean drop = wire.y() != wire.y2();
        int dx = wire.x2() - wire.x1(), dz = wire.z2() - wire.z1();
        int ax = Math.abs(dx), az = Math.abs(dz), sx = Integer.signum(dx), sz = Integer.signum(dz);
        int n = ax + az;
        int x = wire.x1(), z = wire.z1(), ix = 0, iz = 0;
        for (int k = 0; k <= n; k++) {
            boolean stepX = k < n && (az == 0 || ax != 0 && (ix + 0.5) * az < (iz + 0.5) * ax);
            double f = n == 0 ? 0 : k / (double) n;
            int y = drop ? (int) Math.round(wire.y() + (wire.y2() - wire.y()) * f)
                    : wire.y() - (n > 6 && f > 1 / 3.0 && f < 2 / 3.0 ? 1 : 0);
            boolean inChunk = x >= lx1 && x <= lx2 && z >= lz1 && z <= lz2;
            if (drop && k >= 2 && inChunk && !c.get(x, y, z).isAir()) {
                break; // reached the house
            }
            Direction.Axis axis = k < n ? (stepX ? Direction.Axis.X : Direction.Axis.Z) : (ax >= az ? Direction.Axis.X : Direction.Axis.Z);
            setIfAir(x, y, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, axis));
            if (k < n) {
                if (stepX) {
                    x += sx;
                    ix++;
                } else {
                    z += sz;
                    iz++;
                }
            }
        }
    }

    private void spawns() {
        for (int i : plan.spawnIndicesIn(lx1, lz1, lx2, lz2)) {
            VillageResidents.spawn(c, plan, plan.spawns.get(i), plan.seed + i * 31L);
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

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
        switch (kind) {
            case CAPITAL -> {
                if (palette.hasKageRock()) {
                    builder.kageRock();
                }
                if (palette == VillagePalette.SAND) {
                    builder.cliffWall(kind.wall);
                } else {
                    builder.greatWall(kind.wall);
                }
            }
            case TOWN -> builder.townFence(kind.wall);
            case OUTPOST -> builder.outpost(kind.wall);
        }
        builder.lots();
        builder.props();
        builder.spawns();
    }

    public static void build(WorldGenLevel level, BoundingBox clip, VillageKind kind, VillagePalette palette,
                             BlockPos origin, Rotation rotation, long seed) {
        build(level, clip, kind, palette, origin, rotation, seed, kind.height);
    }

    /**
     * Builds a whole village at once in a live world, centred on {@code centre} (the ground under it
     * becomes the village floor), then joins up fences, panes and stairs the way world generation
     * would. Used by {@code /noirm village}.
     */
    public static void buildNow(net.minecraft.server.level.ServerLevel level, BlockPos centre, VillageKind kind, VillagePalette palette, long seed) {
        int r = kind.radius + 2;
        int top = kind.height + 12;
        BoundingBox box = new BoundingBox(centre.getX() - r, centre.getY() - 41, centre.getZ() - r,
                centre.getX() + r, centre.getY() + top, centre.getZ() + r);
        build(level, box, kind, palette, centre, Rotation.NONE, seed, kind.height);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), centre.getY() - 3, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof net.minecraft.world.level.block.FenceBlock || block instanceof net.minecraft.world.level.block.IronBarsBlock
                    || block instanceof net.minecraft.world.level.block.WallBlock || block instanceof net.minecraft.world.level.block.StairBlock) {
                BlockState joined = Block.updateFromNeighbourShapes(state, level, pos);
                if (joined != state) {
                    level.setBlock(pos, joined, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
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

    // ================================================================ the capital's walls and the Kage Rock

    private boolean inRock(int x, int z) {
        return p.hasKageRock() && z <= VillagePlanner.ROCK_Z + 1 && Math.abs(x) <= VillagePlanner.ROCK_HALF;
    }

    /**
     * The hidden village's wall: three blocks thick, {@link #WALL_HEIGHT} high, battlements and a
     * walk along the top, ladders up from inside, lookout pavilions, two side gates (tunnels under
     * the wall) and the great gate in the south.
     */
    private void greatWall(int w) {
        for (int x = lx1; x <= lx2; x++) {
            for (int z = lz1; z <= lz2; z++) {
                if (!inDisk(x, z, w) || inDisk(x, z, w - 3) || inRock(x, z)) {
                    continue;
                }
                if (z > 0 && Math.abs(x) <= 9) {
                    continue; // the great gate
                }
                boolean sideGate = Math.abs(z - VillagePlanner.CROSS_Z) <= 3 && Math.abs(x) > w / 2;
                c.fill(x, sideGate ? 9 : 0, z, x, WALL_HEIGHT - 1, z, p.wall);
                if (sideGate) {
                    c.fill(x, 0, z, x, 8, z, Blocks.AIR);
                }
                boolean outer = !inDisk(x, z, w - 1);
                if (outer && ((x + z) & 1) == 0) {
                    c.set(x, WALL_HEIGHT, z, p.wall); // battlements
                }
                if (outer) {
                    c.set(x, WALL_HEIGHT - 4, z, p.towerRoof); // a coloured band round the outside
                }
            }
        }
        greatGate(w);
        // Ladders up the inside, and lookout pavilions on top.
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(25 + k * 45);
            int x = (int) Math.round(Math.sin(a) * (w - 4));
            int z = (int) Math.round(Math.cos(a) * (w - 4));
            if (inRock(x, z - 6) || (z > 0 && Math.abs(x) < 16)) {
                continue;
            }
            Direction facing = Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
            int ax = x - facing.getStepX();
            int az = z - facing.getStepZ();
            c.fill(ax, 0, az, ax, WALL_HEIGHT - 1, az, p.wall);
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
            for (int y = 0; y < WALL_HEIGHT; y++) {
                c.set(x, y, z, ladder);
            }
        }
        for (int deg : new int[] {38, -38, 112, -112, 150, -150}) {
            double a = Math.toRadians(deg);
            int x = (int) Math.round(Math.sin(a) * (w - 1));
            int z = (int) Math.round(Math.cos(a) * (w - 1));
            if (inRock(x, z) || inRock(x, z - 4)) {
                continue;
            }
            pavilion(x, WALL_HEIGHT, z);
        }
    }

    /** A little roofed lookout on the wall walk (patrols walk straight through it). */
    private void pavilion(int cx, int y, int cz) {
        if (!c.touches(cx - 4, cz - 4, cx + 4, cz + 4)) {
            return;
        }
        c.fill(cx - 3, y - 1, cz - 3, cx + 3, y - 1, cz + 3, p.wall);
        for (int dx : new int[] {-3, 3}) {
            for (int dz : new int[] {-3, 3}) {
                c.fill(cx + dx, y, cz + dz, cx + dx, y + 2, cz + dz, p.log);
            }
        }
        Block roofSlab = p.roofSlabs().get(0);
        c.fill(cx - 4, y + 3, cz - 4, cx + 4, y + 3, cz + 4, slab(roofSlab, SlabType.BOTTOM));
        c.fill(cx - 2, y + 3, cz - 2, cx + 2, y + 3, cz + 2, p.towerRoof);
        c.fill(cx - 1, y + 4, cz - 1, cx + 1, y + 4, cz + 1, slab(roofSlab, SlabType.BOTTOM));
        c.set(cx, y + 2, cz, HANGING_LANTERN);
    }

    /**
     * The great gate: two massive pillars, a lintel and a tiled roof, the village symbol hanging in
     * the opening, and the two great doors standing open, each painted with a sign (the "a" and
     * "un" of the Hidden Leaf's gate).
     */
    private void greatGate(int w) {
        if (!c.touches(-13, w - 12, 13, w + 3)) {
            return;
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int ax = 7; ax <= 9; ax++) {
                c.fill(side * ax, 0, w - 3, side * ax, 21, w + 1, p.log);
            }
        }
        c.fill(-10, 17, w - 2, 10, 19, w, p.planks);
        c.fill(-11, 20, w - 3, 11, 20, w + 1, p.towerRoof);
        c.fill(-12, 21, w - 4, 12, 21, w + 2, slab(p.roofSlabs().get(0), SlabType.BOTTOM));
        c.fill(-9, 22, w - 2, 9, 22, w, slab(p.roofSlabs().get(0), SlabType.BOTTOM));
        // The plaque with the village symbol.
        for (int gx = -1; gx <= 5; gx++) {
            for (int gy = -1; gy <= 5; gy++) {
                boolean border = gx < 0 || gy < 0 || gx > 4 || gy > 4;
                c.set(gx - 2, 16 - gy, w + 1, border ? p.planks.defaultBlockState()
                        : (p.glyph.painted(gx, gy) ? p.paint : p.plaque).defaultBlockState());
            }
        }
        c.fill(-3, 17, w + 1, 3, 17, w + 1, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X));
        // The doors, folded back inside the gate.
        String[] a = {". # # # .", "# # # # #", ". # . # .", "# . # . #", ". # # . #"};
        String[] un = {". . # . .", ". # . . .", ". # # . .", "# . . # .", "# . . . #"};
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 6;
            c.fill(x, 0, w - 9, x, 12, w - 3, p.gateDoor());
            String[] glyph = side < 0 ? a : un;
            for (int r = 0; r < 5; r++) {
                String row = glyph[r].replace(" ", "");
                for (int col = 0; col < 5; col++) {
                    if (row.charAt(col) == '#') {
                        c.set(x, 9 - r, w - 8 + col, p.paint);
                    }
                }
            }
            c.set(side * 10, 2, w + 2, Blocks.LANTERN);
            c.fill(side * 10, 0, w + 2, side * 10, 1, w + 2, p.fence);
        }
    }

    /** The Sand village's wall: a ring of layered desert cliffs, with the only way in a narrow canyon in the south. */
    private void cliffWall(int w) {
        Block[] layers = {Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA, Blocks.CUT_SANDSTONE, Blocks.ORANGE_TERRACOTTA};
        for (int x = lx1; x <= lx2; x++) {
            for (int z = lz1; z <= lz2; z++) {
                if (!inDisk(x, z, w) || inDisk(x, z, w - 7)) {
                    continue;
                }
                if (z > 0 && Math.abs(x) <= 4) {
                    continue; // the canyon
                }
                double n = noise(x, z, 8, plan.seed);
                int top = 24 + (int) (n * 12) - (z > 0 && Math.abs(x) < 9 ? 6 : 0);
                for (int y = 0; y < top; y++) {
                    int band = Math.floorMod(y + (int) (n * 4), layers.length * 2) / 2;
                    c.set(x, y, z, layers[band]);
                }
            }
        }
    }

    /**
     * The Kage Rock: a mountain north of the village with a sheer cliff facing it, and the faces of
     * four Kage carved into the cliff. Trees grow on top.
     */
    private void kageRock() {
        int face = VillagePlanner.ROCK_Z;
        int half = VillagePlanner.ROCK_HALF;
        int r = plan.kind.radius;
        if (lz1 > face + 3) {
            return;
        }
        for (int x = Math.max(lx1, -half); x <= Math.min(lx2, half); x++) {
            for (int z = lz1; z <= Math.min(lz2, face); z++) {
                if (!inDisk(x, z, r)) {
                    continue;
                }
                if (z > face - recess(x)) {
                    continue; // the cliff isn't a straight line, except where the faces are carved
                }
                int top = rockHeight(x, z);
                for (int y = 0; y < top; y++) {
                    double n = noise(x + y * 3, z, 5, plan.seed + 7);
                    c.set(x, y, z, n < 0.3 ? Blocks.ANDESITE : n > 0.8 ? Blocks.TUFF : p.rock);
                }
                if (top > 2) {
                    c.set(x, top - 1, z, Blocks.GRASS_BLOCK);
                    if (Math.floorMod(hash(x, z, plan.seed), 23) == 0 && z < face - 6 && top > 12) {
                        rockTree(x, top, z);
                    }
                }
            }
        }
        int[] centres = {-51, -17, 17, 51};
        for (int i = 0; i < centres.length; i++) {
            carveFace(centres[i], face, i);
        }
    }

    /** How far the cliff steps back from its line at x: nothing at the carved faces, up to 4 blocks between them. */
    private int recess(int x) {
        for (int centre : new int[] {-51, -17, 17, 51}) {
            if (Math.abs(x - centre) <= 10) {
                return 0;
            }
        }
        return (int) Math.round(noise(x, 0, 7, plan.seed + 3) * 4);
    }

    private int rockHeight(int x, int z) {
        int half = VillagePlanner.ROCK_HALF;
        double ridge = 50 * Math.min(1, Math.max(0, (half - Math.abs(x)) / 26.0));
        double dist = Math.sqrt(x * (double) x + z * (double) z);
        double outer = Math.min(1, Math.max(0, (plan.kind.radius - dist) / 12.0));
        return (int) Math.max(0, ridge * outer + noise(x, z, 9, plan.seed) * 8 - 4);
    }

    private void rockTree(int x, int y, int z) {
        Lot lot = new Lot(LotType.PARK, x, z, x, z, Direction.SOUTH, 0, 0, hash(x, z, plan.seed));
        LotFrame frame = new LotFrame(new VillageCanvas(c.level, c.clip, c.world(0, y, 0), c.rotation), p, lot);
        Buildings.tree(frame, 0, 0, 5, 2);
    }

    /** One carved face: a head standing out of the cliff, with brows, eyes, a nose and a mouth; each a little different. */
    private void carveFace(int cx, int face, int which) {
        if (!c.touches(cx - 9, face - 2, cx + 9, face + 4)) {
            return;
        }
        int cy = 26;
        for (int dx = -8; dx <= 8; dx++) {
            for (int y = 12; y <= 40; y++) {
                double e = Math.pow(dx / 7.5, 2) + Math.pow((y - cy) / 11.0, 2);
                if (e <= 1) {
                    c.set(cx + dx, y, face + 1, p.carved);
                    c.set(cx + dx, y, face, p.carved);
                }
                // Hair above (spikier on the second and fourth faces).
                boolean hair = y >= cy + 6 && e <= 1.25 && e > 0.7 || y >= cy + 8 && Math.abs(dx) <= 7 && e <= 1.3;
                if (hair && (which % 2 == 0 || ((dx + y) & 1) == 0 || y < cy + 11)) {
                    c.set(cx + dx, y, face + 2, p.rock);
                }
            }
        }
        if (which == 1) {
            c.fill(cx - 6, cy + 5, face + 2, cx + 6, cy + 5, face + 2, Blocks.LIGHT_GRAY_CONCRETE); // a forehead protector
        }
        c.fill(cx - 5, cy + 3, face + 2, cx - 2, cy + 3, face + 2, p.rock);            // brows
        c.fill(cx + 2, cy + 3, face + 2, cx + 5, cy + 3, face + 2, p.rock);
        c.fill(cx - 4, cy + 1, face, cx - 3, cy + 1, face + 1, Blocks.AIR);             // eyes
        c.fill(cx + 3, cy + 1, face, cx + 4, cy + 1, face + 1, Blocks.AIR);
        c.fill(cx, cy - 4, face + 2, cx, cy, face + 2, p.carved);                       // nose
        c.set(cx, cy - 4, face + 3, p.carved);
        c.fill(cx - 3, cy - 7, face + 1, cx + 3, cy - 7, face + 1, Blocks.AIR);         // mouth
        if (which == 3) {
            c.fill(cx - 4, cy - 10, face + 2, cx + 4, cy - 9, face + 2, p.rock);        // a beard
        }
    }

    // ================================================================ a town's fence, an outpost's palisade

    /** A town's low fence and hedge, broken by four wooden gates where the streets leave. */
    private void townFence(int w) {
        BlockState hedge = Buildings.leaves(p.tree(1)[1]);
        for (int x = lx1; x <= lx2; x++) {
            for (int z = lz1; z <= lz2; z++) {
                if (!inDisk(x, z, w) || inDisk(x, z, w - 1)) {
                    continue;
                }
                if (Math.abs(x) <= 4 || Math.abs(z) <= 3) {
                    continue;
                }
                boolean post = Math.floorMod(x * 3 + z * 5, 7) == 0;
                if (post) {
                    c.fill(x, 0, z, x, 1, z, p.log);
                } else if (((x + z) / 6 & 1) == 0) {
                    c.fill(x, 0, z, x, 1, z, hedge);
                } else {
                    c.set(x, 0, z, p.fence);
                }
            }
        }
        townGate(0, w, true);
        townGate(0, -w, true);
        townGate(w, 0, false);
        townGate(-w, 0, false);
    }

    /** A wooden gate frame across a street: two posts, a beam, a little roof, lanterns. */
    private void townGate(int cx, int cz, boolean acrossX) {
        if (!c.touches(cx - 7, cz - 7, cx + 7, cz + 7)) {
            return;
        }
        for (int k = -5; k <= 5; k += 10) {
            int x = acrossX ? cx + k : cx;
            int z = acrossX ? cz : cz + k;
            c.fill(x, 0, z, x, 5, z, p.log);
            c.set(acrossX ? x + Integer.signum(-k) : x, 2, acrossX ? z : z + Integer.signum(-k), HANGING_LANTERN);
        }
        for (int k = -6; k <= 6; k++) {
            int x = acrossX ? cx + k : cx;
            int z = acrossX ? cz : cz + k;
            c.set(x, 5, z, p.planks);
            c.set(x, 6, z, slab(p.roofSlabs().get(0), SlabType.BOTTOM));
            if (Math.abs(k) <= 1) {
                c.set(x, 4, z, k == 0 ? p.paint : p.plaque);
            }
        }
    }

    /** An outpost: a log palisade with a gate, a campfire with log seats, training posts, lanterns. */
    private void outpost(int wall) {
        for (int x = Math.max(lx1, -wall); x <= Math.min(lx2, wall); x++) {
            for (int z = Math.max(lz1, -wall); z <= Math.min(lz2, wall); z++) {
                if (!inDisk(x, z, wall) || inDisk(x, z, wall - 1)) {
                    continue;
                }
                if (z > 0 && Math.abs(x) <= 1) {
                    continue;
                }
                c.fill(x, 0, z, x, (z > 0 && Math.abs(x) == 2) ? 4 : 2, z, p.log);
            }
        }
        c.fill(-3, 4, wall - 1, 3, 4, wall - 1, p.log);
        c.set(0, 4, wall - 1, p.paint);
        c.set(0, -1, 0, p.road);
        c.set(0, 0, 0, Blocks.CAMPFIRE);
        c.set(0, 0, -3, p.log);
        c.set(-3, 0, 0, p.log);
        c.set(3, 0, 0, p.log);
        c.fill(-2, 0, -11, -2, 1, -11, p.log);
        c.fill(1, 0, -11, 1, 1, -11, p.log);
        c.set(1, 2, -11, Blocks.TARGET);
        c.set(-2, 2, -11, Blocks.HAY_BLOCK);
        for (int x : new int[] {-2, 2}) {
            c.fill(x, 0, wall - 3, x, 1, wall - 3, p.fence);
            c.set(x, 2, wall - 3, Blocks.LANTERN);
        }
    }

    // ================================================================ lots, props, people

    private void lots() {
        for (Lot lot : plan.lots) {
            if (lot.overlaps(lx1, lz1, lx2, lz2, 4)) {
                Buildings.draw(new LotFrame(c, p, lot));
            }
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

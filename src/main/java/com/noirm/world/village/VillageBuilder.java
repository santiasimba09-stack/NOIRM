package com.noirm.world.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds a ninja village block by block, from code (no structure files).
 *
 * <p>The whole plan is decided by one random seed, so every chunk of a village asks for exactly
 * the same buildings in the same places; the {@link VillageCanvas} keeps only the blocks that
 * fall inside the chunk being generated. <b>Rule for editing:</b> make every random choice
 * <i>before</i> checking whether something is inside the canvas, or chunks will disagree and
 * buildings will be cut in half.
 *
 * <p>Local layout (before rotation): the village centre is (0, 0), the main gate is to the south
 * (+z), the Kage Rock (carved faces) to the north.
 */
public final class VillageBuilder {
    /** Radius of the capital's outer wall. */
    public static final int CAPITAL_WALL = 40;
    public static final int TOWER_RADIUS = 7;
    /** Height of the Kage tower's main drum; the balcony is on top of it. */
    public static final int TOWER_HEIGHT = 20;
    /** Height of each floor inside the tower. */
    private static final int FLOOR = 7;
    /** Radius of an outpost's palisade. */
    public static final int OUTPOST_WALL = 14;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState HANGING_LANTERN = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

    private final VillageCanvas c;
    private final VillagePalette p;
    private final RandomSource rnd;

    private VillageBuilder(VillageCanvas canvas, VillagePalette palette, RandomSource random) {
        this.c = canvas;
        this.p = palette;
        this.rnd = random;
    }

    /**
     * Builds (the part inside {@code clip} of) a village centred on {@code origin}, where
     * {@code origin.getY()} is the first air block above the finished ground.
     */
    public static void build(WorldGenLevel level, BoundingBox clip, VillageKind kind, VillagePalette palette,
                             BlockPos origin, Rotation rotation, long seed) {
        VillageBuilder builder = new VillageBuilder(new VillageCanvas(level, clip, origin, rotation), palette, RandomSource.create(seed));
        builder.ground(kind.radius, kind.height);
        if (kind == VillageKind.CAPITAL) {
            builder.capital();
        } else {
            builder.outpost();
        }
    }

    /** Builds only the Kage tower (and its Kage), centred on {@code origin}. Used by the game tests. */
    public static void buildKageTower(WorldGenLevel level, BoundingBox clip, VillagePalette palette, BlockPos origin, boolean withKage) {
        VillageBuilder builder = new VillageBuilder(new VillageCanvas(level, clip, origin, Rotation.NONE), palette, RandomSource.create(0));
        builder.kageTower(withKage);
    }

    // ================================================================ the hidden village

    private void capital() {
        int wall = CAPITAL_WALL;
        // Streets: the main avenue from the gate to the tower, the plaza round the tower, a
        // cross street, the avenue north to the Kage Rock, and a ring road inside the wall.
        roadDisk(0, 0, 12);
        roadRing(0, 0, 33, 36);
        roadRect(-2, 8, 2, wall + 2);
        roadRect(-2, -25, 2, -8);
        roadRect(-wall + 4, -1, -8, 1);
        roadRect(8, -1, wall - 4, 1);

        outerWall(wall);
        kageTower(true);
        kageRock(wall);
        trainingGround(13, -21, 26, -9, true);
        torii(31);
        for (int z : new int[] {14, 20, 26}) {
            stall(-7, z, Direction.EAST);
            stall(7, z, Direction.WEST);
        }
        for (int z = 11; z <= 29; z += 6) {
            lanternPost(-3, z);
            lanternPost(3, z);
        }
        for (int x : new int[] {12, 20, 28}) {
            lanternPost(x, 2);
            lanternPost(-x, 2);
        }

        List<int[]> plots = housePlots();
        for (int[] plot : plots) {
            house(plot[0], plot[1], 1f);
        }

        // ---- the people
        BlockPos home = c.world(0, 0, 0);
        // Gate and tower guards.
        guard(-1, 0, wall - 7, 5);
        guard(1, 0, wall - 7, 5);
        guard(-3, 0, TOWER_RADIUS + 3, 4);
        guard(3, 0, TOWER_RADIUS + 3, 4);
        // Ninjas out and about in the village.
        for (int i = 0; i < Math.min(4, plots.size()); i++) {
            int[] plot = plots.get(i);
            float speed = 10 + rnd.nextFloat() * 20;
            float taijutsu = 10 + rnd.nextFloat() * 20;
            spawn(plot[0] + 5, 0, plot[1], pos -> VillageResidents.ninja(c.level, pos, home, 30, speed, taijutsu));
        }
        // Training ground.
        for (int[] spot : new int[][] {{18, -14}, {22, -16}}) {
            float speed = 15 + rnd.nextFloat() * 15;
            float taijutsu = 15 + rnd.nextFloat() * 15;
            spawn(spot[0], 0, spot[1], pos -> VillageResidents.ninja(c.level, pos, pos, 7, speed, taijutsu));
        }
        spawn(20, 0, -12, pos -> VillageResidents.dummy(c.level, pos));
        spawn(24, 0, -13, pos -> VillageResidents.dummy(c.level, pos));
        // A merchant behind every stall counter.
        for (int z : new int[] {14, 20, 26}) {
            spawn(-7, 0, z, pos -> VillageResidents.merchant(c.level, pos, 3));
            spawn(7, 0, z, pos -> VillageResidents.merchant(c.level, pos, 3));
        }
    }

    private void guard(int x, int y, int z, int radius) {
        float speed = 20 + rnd.nextFloat() * 15;
        float taijutsu = 20 + rnd.nextFloat() * 15;
        spawn(x, y, z, pos -> VillageResidents.ninja(c.level, pos, pos, radius, speed, taijutsu));
    }

    /**
     * Where the capital's houses go: spots on a grid that keep clear of the streets, the tower,
     * the stalls, the training ground and the Kage Rock, at least 10 blocks apart.
     */
    private List<int[]> housePlots() {
        List<int[]> candidates = new ArrayList<>();
        for (int x = -28; x <= 28; x += 7) {
            for (int z = -28; z <= 28; z += 7) {
                candidates.add(new int[] {x + rnd.nextInt(3) - 1, z + rnd.nextInt(3) - 1});
            }
        }
        // Shuffle (deterministically, from the village seed).
        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int[] swap = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, swap);
        }
        List<int[]> plots = new ArrayList<>();
        for (int[] spot : candidates) {
            int x = spot[0];
            int z = spot[1];
            boolean blocked = x * x + z * z < 18 * 18           // tower plaza
                    || x * x + z * z > 28 * 28                   // ring road
                    || Math.abs(x) < 15 && z > 3                 // main avenue and stalls
                    || Math.abs(x) < 8 && z < -3                 // north avenue
                    || Math.abs(z) < 7                           // cross street
                    || z < -19 && Math.abs(x) < 27               // Kage Rock
                    || x >= 8 && x <= 31 && z >= -26 && z <= -4; // training ground
            if (blocked) {
                continue;
            }
            boolean crowded = plots.stream().anyMatch(o -> (o[0] - x) * (o[0] - x) + (o[1] - z) * (o[1] - z) < 10 * 10);
            if (!crowded) {
                plots.add(spot);
            }
        }
        return plots;
    }

    /** A house (or a garden) on a plot, its door facing the village centre. */
    private void house(int x, int z, float roofTankChance) {
        Direction door = Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
        Block wall = p.houseWalls.get(rnd.nextInt(p.houseWalls.size()));
        Block roof = p.roofs.get(rnd.nextInt(p.roofs.size()));
        boolean tank = rnd.nextFloat() < roofTankChance * 0.7f;
        float kind = rnd.nextFloat();
        int radius = 3 + rnd.nextInt(2);
        int height = 4 + rnd.nextInt(2);
        if (kind < 0.45f) {
            roundHouse(x, z, radius, height, door, wall, roof, tank);
        } else if (kind < 0.62f) {
            roundHouse(x, z, radius, height + 3, door, wall, roof, tank); // a two-storey tower house
        } else if (kind < 0.9f) {
            squareHouse(x, z, 3, height, door, wall, roof, tank);
        } else {
            pondGarden(x, z);
        }
    }

    /** The capital's wall: two blocks thick, seven high, with battlements and the great gate in the south. */
    private void outerWall(int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!inDisk(x, z, radius) || inDisk(x, z, radius - 2) || !c.touches(x, z, x, z)) {
                    continue;
                }
                boolean gate = z > 0 && Math.abs(x) <= 3;
                boolean pillar = z > 0 && (Math.abs(x) == 4 || Math.abs(x) == 5);
                if (pillar) {
                    c.fill(x, 0, z, x, 9, z, p.log);
                } else if (!gate) {
                    c.fill(x, 0, z, x, 6, z, p.wall);
                    if (((x + z) & 1) == 0) {
                        c.set(x, 7, z, p.wall); // battlements
                    }
                }
                if (z > 0 && Math.abs(x) <= 5) {
                    c.fill(x, 8, z, x, 9, z, p.towerRoof); // the great gate's lintel
                }
            }
        }
        // The gate's little roof.
        c.fill(-6, 10, radius - 3, 6, 10, radius + 1, p.slab);
        // The two gate doors stand open, folded back inside the wall, each painted with a sign
        // (like the "a" and "un" on the Hidden Leaf's gates).
        for (int x : new int[] {-3, 3}) {
            c.fill(x, 0, radius - 5, x, 6, radius - 2, p.planks);
            c.fill(x, 2, radius - 4, x, 4, radius - 3, p.paint);
        }
    }

    // ================================================================ the Kage tower

    /**
     * The Kage's tower at the village centre: a tall drum with three floors and a ladder, the
     * village symbol painted on its front, a wide balcony on top, and the Kage's office (a smaller
     * drum with a pointed roof) in the middle of the balcony.
     */
    private void kageTower(boolean withKage) {
        int r = TOWER_RADIUS;
        int h = TOWER_HEIGHT;
        for (int x = -r - 3; x <= r + 3; x++) {
            for (int z = -r - 3; z <= r + 3; z++) {
                boolean inTower = inDisk(x, z, r);
                boolean ring = inTower && !inDisk(x, z, r - 1);
                if (inTower) {
                    c.set(x, -1, z, Blocks.POLISHED_ANDESITE);
                }
                if (ring) {
                    c.fill(x, 0, z, x, h - 1, z, p.towerWall);
                    for (int band = FLOOR; band < h; band += FLOOR) {
                        c.set(x, band, z, p.towerRoof); // coloured bands at each floor
                    }
                } else if (inTower) {
                    c.fill(x, 0, z, x, h - 1, z, Blocks.AIR);
                    for (int floor = FLOOR; floor < h; floor += FLOOR) {
                        c.set(x, floor, z, p.planks);
                    }
                }
                if (inDisk(x, z, r + 3)) {
                    c.set(x, h, z, p.planks); // the balcony floor
                }
                if (inDisk(x, z, r + 3) && !inDisk(x, z, r + 2)) {
                    c.set(x, h + 1, z, p.fence); // balcony railing
                }
            }
        }
        // Front door (south) and windows on every floor.
        c.fill(-1, 0, r, 1, 2, r, Blocks.AIR);
        c.fill(-1, 0, r - 1, 1, 2, r - 1, Blocks.AIR);
        for (int y = 2; y < h; y += FLOOR) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (side == Direction.SOUTH) {
                    continue; // the front carries the door and the symbol
                }
                for (int k = -1; k <= 1; k++) {
                    int x = side.getStepX() * r + (side.getStepX() == 0 ? k : 0);
                    int z = side.getStepZ() * r + (side.getStepZ() == 0 ? k : 0);
                    c.set(x, y, z, Blocks.GLASS_PANE);
                }
            }
        }
        // Ladder up the back wall through every floor, onto the balcony.
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 0; y <= h; y++) {
            c.set(0, y, -(r - 1), ladder);
        }
        for (int floor = 0; floor < h; floor += FLOOR) {
            c.set(0, Math.min(floor + FLOOR, h) - 1, 0, HANGING_LANTERN);
        }
        // The village symbol, painted on a plaque over the door.
        for (int gx = 0; gx < 5; gx++) {
            for (int gy = 0; gy < 5; gy++) {
                c.set(gx - 2, h - 4 - gy, r + 1, p.glyph.painted(gx, gy) ? p.paint : p.plaque);
            }
        }
        // The Kage's office on the balcony, with a pointed roof.
        int office = 5;
        for (int x = -office - 1; x <= office + 1; x++) {
            for (int z = -office - 1; z <= office + 1; z++) {
                if (inDisk(x, z, office) && !inDisk(x, z, office - 1)) {
                    c.fill(x, h + 1, z, x, h + 5, z, p.towerWall);
                    c.set(x, h + 3, z, Blocks.GLASS_PANE);
                }
                for (int k = 0; k <= office; k++) {
                    if (inDisk(x, z, office + 1 - k)) {
                        c.set(x, h + 6 + k, z, p.towerRoof);
                    }
                }
            }
        }
        c.fill(-1, h + 1, office, 1, h + 2, office, Blocks.AIR); // office door, facing the gate
        c.set(0, h + 1, -3, Blocks.LECTERN);
        c.set(-2, h + 1, -3, Blocks.BOOKSHELF);
        c.set(2, h + 1, -3, Blocks.BOOKSHELF);
        c.set(0, h + 5, 0, HANGING_LANTERN);
        c.set(0, h + 6 + office + 1, 0, Blocks.LIGHTNING_ROD);
        // The Kage, at their desk. (Stats are rolled whether or not this chunk holds the office.)
        float kageSpeed = 120 + rnd.nextFloat() * 80;
        float kageTaijutsu = 120 + rnd.nextFloat() * 80;
        if (withKage) {
            spawn(0, h + 1, 0, pos -> VillageResidents.kage(c.level, pos, kageSpeed, kageTaijutsu, p.kageTitle));
        }
    }

    /** The Kage Rock: a cliff behind the tower with the faces of past Kage carved into it. */
    private void kageRock(int wall) {
        for (int x = -21; x <= 21; x++) {
            for (int z = -38; z <= -26; z++) {
                if (!inDisk(x, z, wall - 2)) {
                    continue;
                }
                int top = 16 - Math.max(0, Math.abs(x) - 15) * 3 - Math.max(0, z + 28) * 2;
                if (top > 0) {
                    c.fill(x, 0, z, x, top - 1, z, p.rock);
                }
            }
        }
        for (int face : new int[] {-12, -4, 4, 12}) {
            c.fill(face - 2, 6, -26, face + 2, 11, -26, p.carved);  // the face
            c.fill(face - 2, 12, -25, face + 2, 12, -25, p.rock);   // hair / brow
            c.set(face, 8, -25, p.carved);                          // nose
            c.set(face - 1, 9, -26, AIR);                           // eyes
            c.set(face + 1, 9, -26, AIR);
            c.fill(face - 1, 7, -26, face + 1, 7, -26, Blocks.AIR); // mouth
        }
    }

    // ================================================================ the outpost

    private void outpost() {
        int wall = OUTPOST_WALL;
        roadDisk(0, 0, 3);
        roadRect(-1, 0, 1, wall + 1);
        // Palisade with a gate in the south.
        for (int x = -wall; x <= wall; x++) {
            for (int z = -wall; z <= wall; z++) {
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
        watchtower(-7, -6);
        // Campfire with log seats in the middle.
        c.set(0, 0, 0, Blocks.CAMPFIRE);
        c.set(0, 0, -3, p.log);
        c.set(-3, 0, 0, p.log);
        c.set(3, 0, 0, p.log);
        // Training posts.
        c.fill(-2, 0, -11, -2, 1, -11, p.log);
        c.fill(1, 0, -11, 1, 1, -11, p.log);
        c.set(1, 2, -11, Blocks.TARGET);
        c.set(-2, 2, -11, Blocks.HAY_BLOCK);
        lanternPost(-2, wall - 3);
        lanternPost(2, wall - 3);
        // Two or three houses.
        int[][] plots = {{6, -7}, {7, 4}, {-8, 5}};
        int skip = rnd.nextInt(4); // 3 = keep them all
        for (int i = 0; i < plots.length; i++) {
            Direction door = i == 0 ? Direction.WEST : i == 1 ? Direction.WEST : Direction.EAST;
            Block wallBlock = p.houseWalls.get(rnd.nextInt(p.houseWalls.size()));
            Block roof = p.roofs.get(rnd.nextInt(p.roofs.size()));
            boolean round = rnd.nextBoolean();
            boolean tank = rnd.nextBoolean();
            if (i == skip) {
                continue;
            }
            if (round || i == 0) {
                roundHouse(plots[i][0], plots[i][1], 3, 4, door, wallBlock, roof, tank);
            } else {
                squareHouse(plots[i][0], plots[i][1], 3, 4, door, wallBlock, roof, tank);
            }
        }
        // The people: a gate guard, a ninja by the fire, a lookout, a merchant and a dummy.
        BlockPos home = c.world(0, 0, 0);
        float[] stats = new float[6];
        for (int i = 0; i < stats.length; i++) {
            stats[i] = 10 + rnd.nextFloat() * 18;
        }
        spawn(2, 0, wall - 4, pos -> VillageResidents.ninja(c.level, pos, pos, 5, stats[0], stats[1]));
        spawn(1, 0, 2, pos -> VillageResidents.ninja(c.level, pos, home, 11, stats[2], stats[3]));
        spawn(-7, 10, -7, pos -> VillageResidents.ninja(c.level, pos, pos, 1, stats[4], stats[5]));
        spawn(-2, 0, 2, pos -> VillageResidents.merchant(c.level, pos, 6));
        spawn(0, 0, -9, pos -> VillageResidents.dummy(c.level, pos));
    }

    /** A wooden lookout tower on four legs, with a ladder, a railed platform and a roof. */
    private void watchtower(int cx, int cz) {
        int top = 9;
        for (int dx : new int[] {-2, 2}) {
            for (int dz : new int[] {-2, 2}) {
                c.fill(cx + dx, 0, cz + dz, cx + dx, top + 2, cz + dz, p.log);
            }
        }
        c.fill(cx, 0, cz, cx, top - 1, cz, p.log);
        c.fill(cx - 2, top, cz - 2, cx + 2, top, cz + 2, p.planks);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && !(Math.abs(dx) == 2 && Math.abs(dz) == 2)) {
                    c.set(cx + dx, top + 1, cz + dz, p.fence);
                }
            }
        }
        c.fill(cx - 3, top + 3, cz - 3, cx + 3, top + 3, cz + 3, p.slab);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 0; y <= top; y++) {
            c.set(cx, y, cz + 1, ladder);
        }
    }

    // ================================================================ buildings

    /**
     * A round house: a drum of {@code wall} with an overhanging {@code roof} and a stepped top,
     * windows on three sides, a door, a lantern, and maybe a water tank on the roof (the Hidden
     * Leaf's rooftops are full of them).
     */
    private void roundHouse(int cx, int cz, int r, int h, Direction door, Block wall, Block roof, boolean tank) {
        if (!c.touches(cx - r - 2, cz - r - 2, cx + r + 2, cz + r + 2)) {
            return;
        }
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (inDisk(dx, dz, r)) {
                    c.set(x, -1, z, p.planks);
                    if (!inDisk(dx, dz, r - 1)) {
                        c.fill(x, 0, z, x, h - 1, z, wall);
                    }
                }
                if (inDisk(dx, dz, r + 1)) {
                    c.set(x, h, z, roof);
                }
                if (inDisk(dx, dz, r - 1)) {
                    c.set(x, h + 1, z, roof);
                }
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side != door) {
                for (int y = 1; y < h - 1; y += 3) {
                    c.set(cx + side.getStepX() * r, y, cz + side.getStepZ() * r, Blocks.GLASS_PANE);
                }
            }
        }
        doorway(cx + door.getStepX() * r, cz + door.getStepZ() * r, door);
        furnish(cx, cz, r - 1, h, door);
        if (tank) {
            waterTank(cx, h + 2, cz);
        }
    }

    /** A square house with a flat, overhanging roof and a low parapet. */
    private void squareHouse(int cx, int cz, int half, int h, Direction door, Block wall, Block roof, boolean tank) {
        if (!c.touches(cx - half - 2, cz - half - 2, cx + half + 2, cz + half + 2)) {
            return;
        }
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            for (int dz = -half - 1; dz <= half + 1; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                int edge = Math.max(Math.abs(dx), Math.abs(dz));
                if (edge <= half) {
                    c.set(x, -1, z, p.planks);
                }
                if (edge == half) {
                    boolean corner = Math.abs(dx) == half && Math.abs(dz) == half;
                    c.fill(x, 0, z, x, h - 1, z, corner ? p.log : wall);
                }
                c.set(x, h, z, roof);
                if (edge == half + 1) {
                    c.set(x, h + 1, z, p.slab);
                }
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side == door) {
                continue;
            }
            for (int k = -1; k <= 1; k += 2) {
                int x = cx + side.getStepX() * half + (side.getStepX() == 0 ? k : 0);
                int z = cz + side.getStepZ() * half + (side.getStepZ() == 0 ? k : 0);
                c.set(x, 1, z, Blocks.GLASS_PANE);
            }
        }
        doorway(cx + door.getStepX() * half, cz + door.getStepZ() * half, door);
        furnish(cx, cz, half - 1, h, door);
        if (tank) {
            waterTank(cx, h + 1, cz);
        }
    }

    /** A door (opening towards {@code outward}) with a path step outside it. */
    private void doorway(int x, int z, Direction outward) {
        BlockState lower = p.door.defaultBlockState().setValue(DoorBlock.FACING, outward.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        c.set(x, 0, z, lower);
        c.set(x, 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        c.set(x + outward.getStepX(), -1, z + outward.getStepZ(), p.road);
    }

    /** A crafting table and a barrel against the back wall, and a lantern from the ceiling. */
    private void furnish(int cx, int cz, int inner, int h, Direction door) {
        int bx = cx - door.getStepX() * inner;
        int bz = cz - door.getStepZ() * inner;
        c.set(bx, 0, bz, Blocks.CRAFTING_TABLE);
        c.set(bx + door.getStepZ(), 0, bz + door.getStepX(), Blocks.BARREL);
        c.set(cx, h - 1, cz, HANGING_LANTERN);
    }

    /** A round rooftop water tank on short legs. */
    private void waterTank(int x, int y, int z) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            c.set(x + side.getStepX(), y, z + side.getStepZ(), p.fence);
            c.set(x + side.getStepX(), y + 1, z + side.getStepZ(), p.tank);
            c.set(x + side.getStepX(), y + 2, z + side.getStepZ(), p.tank);
        }
        c.set(x, y + 1, z, p.tank);
        c.set(x, y + 2, z, p.tank);
        c.set(x, y + 3, z, Blocks.SMOOTH_STONE_SLAB);
    }

    /** A little round pond with a wooden footbridge across it and flowers round the edge. */
    private void pondGarden(int cx, int cz) {
        if (!c.touches(cx - 5, cz - 5, cx + 5, cz + 5)) {
            return;
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (inDisk(dx, dz, 3)) {
                    c.set(cx + dx, -2, cz + dz, p.foundation);
                    c.set(cx + dx, -1, cz + dz, Blocks.WATER);
                } else if (inDisk(dx, dz, 4) && ((dx + dz) & 1) == 0) {
                    c.set(cx + dx, 0, cz + dz, (dx & 2) == 0 ? Blocks.POPPY : Blocks.OXEYE_DAISY);
                }
            }
        }
        c.fill(cx - 4, 0, cz, cx + 4, 0, cz, p.planks);
        for (int dx = -3; dx <= 3; dx++) {
            c.set(cx + dx, 0, cz - 1, p.fence);
            c.set(cx + dx, 0, cz + 1, p.fence);
        }
        c.fill(cx - 4, 0, cz - 1, cx - 4, 0, cz + 1, p.planks);
        c.fill(cx + 4, 0, cz - 1, cx + 4, 0, cz + 1, p.planks);
    }

    /**
     * A food stall like the ramen stand: a counter facing the street ({@code front}), stools, a
     * striped awning, a smoker and barrels behind the counter, and a lantern.
     */
    private void stall(int cx, int cz, Direction front) {
        if (!c.touches(cx - 3, cz - 3, cx + 3, cz + 3)) {
            return;
        }
        int s = front.getStepX();
        for (int k = -1; k <= 1; k++) {
            for (int dz = -2; dz <= 2; dz++) {
                c.set(cx + k * s, -1, cz + dz, p.planks);
            }
        }
        for (int dz = -2; dz <= 2; dz++) {
            boolean corner = Math.abs(dz) == 2;
            c.set(cx + s, 0, cz + dz, corner ? p.log : p.planks);
            c.fill(cx + s, 1, cz + dz, cx + s, 2, cz + dz, corner ? p.fence : Blocks.AIR);
            if (corner) {
                c.fill(cx - s, 0, cz + dz, cx - s, 2, cz + dz, p.fence);
            }
        }
        c.set(cx - s, 0, cz - 1, Blocks.BARREL);
        c.set(cx - s, 0, cz, Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, front));
        c.set(cx - s, 0, cz + 1, Blocks.BARREL);
        for (int k = -1; k <= 2; k++) {
            for (int dz = -2; dz <= 2; dz++) {
                c.set(cx + k * s, 3, cz + dz, (dz & 1) == 0 ? p.awningA : p.awningB);
            }
        }
        BlockState stool = p.stairs.defaultBlockState().setValue(StairBlock.FACING, front);
        for (int dz = -1; dz <= 1; dz++) {
            c.set(cx + 2 * s, 0, cz + dz, stool);
        }
        c.set(cx, 2, cz, HANGING_LANTERN);
    }

    /** A red shrine gate over the avenue. */
    private void torii(int z) {
        c.fill(-4, 0, z, -4, 5, z, Blocks.RED_CONCRETE);
        c.fill(4, 0, z, 4, 5, z, Blocks.RED_CONCRETE);
        c.fill(-5, 4, z, 5, 4, z, Blocks.RED_CONCRETE);
        c.fill(-6, 6, z, 6, 6, z, Blocks.DARK_OAK_PLANKS);
        c.set(-6, 7, z, Blocks.DARK_OAK_SLAB);
        c.set(6, 7, z, Blocks.DARK_OAK_SLAB);
        c.set(0, 5, z, p.plaque);
    }

    /**
     * A training ground: packed earth, a fence with an opening, the three wooden posts, two
     * archery targets and a bench.
     */
    private void trainingGround(int x1, int z1, int x2, int z2, boolean big) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                c.set(x, -1, z, Blocks.COARSE_DIRT);
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                boolean opening = x == x1 && Math.abs(z - (z1 + z2) / 2) <= 1;
                if (edge && !opening) {
                    c.set(x, 0, z, p.fence);
                }
            }
        }
        int postZ = z1 + 3;
        for (int x = x1 + 4; x <= x2 - 3; x += 3) {
            c.fill(x, 0, postZ, x, 2, postZ, p.log); // the three posts
        }
        if (big) {
            c.fill(x1 + 5, 0, z2 - 3, x1 + 5, 1, z2 - 3, p.log);
            c.set(x1 + 5, 2, z2 - 3, Blocks.TARGET);
            c.fill(x2 - 3, 0, z2 - 3, x2 - 3, 1, z2 - 3, p.log);
            c.set(x2 - 3, 2, z2 - 3, Blocks.TARGET);
            BlockState bench = p.stairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            for (int x = x1 + 2; x <= x1 + 4; x++) {
                c.set(x, 0, z2 - 1, bench);
            }
        }
    }

    /** A fence post with a lantern on top. */
    private void lanternPost(int x, int z) {
        c.fill(x, 0, z, x, 1, z, p.fence);
        c.set(x, 2, z, Blocks.LANTERN);
    }

    // ================================================================ ground and roads

    /**
     * Levels the site: clears everything above the ground (trees, hills) up to {@code height}, fills
     * the holes beneath with the palette's foundation, and lays the palette's ground on top.
     */
    private void ground(int radius, int height) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!inDisk(x, z, radius) || !c.touches(x, z, x, z)) {
                    continue;
                }
                for (int y = 0; y <= height; y++) {
                    if (!c.get(x, y, z).isAir()) {
                        c.set(x, y, z, AIR);
                    }
                }
                for (int y = -2; y >= -14; y--) {
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

    private void roadRect(int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                c.set(x, -1, z, p.road);
            }
        }
    }

    private void roadDisk(int cx, int cz, int r) {
        roadRing(cx, cz, -1, r);
    }

    /** Road on the ring between radius {@code inner} (exclusive) and {@code outer}. */
    private void roadRing(int cx, int cz, int inner, int outer) {
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                if (inDisk(dx, dz, outer) && (inner < 0 || !inDisk(dx, dz, inner))) {
                    c.set(cx + dx, -1, cz + dz, p.road);
                }
            }
        }
    }

    /** Puts a resident at a local spot, if that spot is in the part being built. */
    private void spawn(int x, int y, int z, Consumer<BlockPos> what) {
        BlockPos pos = c.world(x, y, z);
        if (c.clip.isInside(pos)) {
            what.accept(pos);
        }
    }

    /** Whether (x, z) is inside a circle of radius r (a slightly fuller circle than x²+z² ≤ r², which looks rounder in blocks). */
    static boolean inDisk(int x, int z, int r) {
        return x * x + z * z <= r * r + r;
    }
}

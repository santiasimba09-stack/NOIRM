package com.noirm.world.village;

import com.noirm.world.village.VillagePlan.Lot;
import com.noirm.world.village.VillagePlan.LotType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import static com.noirm.world.village.Buildings.HANGING_LANTERN;
import static com.noirm.world.village.Buildings.slab;
import static com.noirm.world.village.VillageBuilder.WALL_HEIGHT;
import static com.noirm.world.village.VillageBuilder.hash;
import static com.noirm.world.village.VillageBuilder.inDisk;
import static com.noirm.world.village.VillageBuilder.noise;

/**
 * A village's defences and its mountain: the hidden village's great wall and gates (or the Sand
 * village's ring of cliffs), the Kage Rock with its carved faces, a town's fence and gates, and an
 * outpost's palisade. Drawn after the streets and before the lots, for the part of the village
 * inside the canvas's clip.
 */
final class Fortifications {
    private final VillageCanvas c;
    private final VillagePlan plan;
    private final VillagePalette p;
    /** The chunk being built, in local coordinates. */
    private final int lx1, lz1, lx2, lz2;

    private Fortifications(VillageCanvas canvas, VillagePlan plan) {
        this.c = canvas;
        this.plan = plan;
        this.p = plan.palette;
        int[] box = canvas.localBounds();
        this.lx1 = box[0];
        this.lz1 = box[1];
        this.lx2 = box[2];
        this.lz2 = box[3];
    }

    static void draw(VillageCanvas canvas, VillagePlan plan) {
        Fortifications f = new Fortifications(canvas, plan);
        switch (plan.kind) {
            case CAPITAL -> {
                if (plan.palette.hasKageRock()) {
                    f.kageRock();
                }
                switch (plan.palette) {
                    case SAND -> f.cliffWall(plan.kind.wall, new Block[] {Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA,
                            Blocks.CUT_SANDSTONE, Blocks.ORANGE_TERRACOTTA}, 24, 12);
                    case STONE -> f.cliffWall(plan.kind.wall, new Block[] {Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.STONE,
                            Blocks.PACKED_MUD, Blocks.GRAVEL}, 26, 16);
                    case CLOUD -> f.spireRing(plan.kind.wall);
                    default -> f.greatWall(plan.kind.wall);
                }
            }
            case TOWN -> f.townFence(plan.kind.wall);
            case OUTPOST -> f.outpost(plan.kind.wall);
        }
    }

    // ================================================================ the capital's walls and the Kage Rock

    private boolean inRock(int x, int z) {
        return p.hasKageRock() && z <= plan.rockZ + 1 && Math.abs(x) <= plan.rockHalf;
    }

    /**
     * The hidden village's wall: three blocks thick, {@link #WALL_HEIGHT} high, a walk along the top,
     * ladders up from inside, lookout pavilions, two side gates and the great gate in the south.
     * The Leaf village's is the one from the series: reddish-brown, no battlements but a tiled
     * coping, round bastions, red gates standing over the wall walk, and side gates with painted
     * doors. The others have battlements.
     */
    private void greatWall(int w) {
        boolean leaf = p == VillagePalette.LEAF;
        Block band = leaf ? Blocks.BROWN_TERRACOTTA : p.towerRoof;
        for (int x = lx1; x <= lx2; x++) {
            for (int z = lz1; z <= lz2; z++) {
                if (!inDisk(x, z, w) || inDisk(x, z, w - 3) || inRock(x, z)) {
                    continue;
                }
                if (z > 0 && Math.abs(x) <= 9) {
                    continue; // the great gate
                }
                boolean sideGate = Math.abs(z - plan.crossZ) <= 3 && Math.abs(x) > w / 2;
                c.fill(x, sideGate ? 9 : 0, z, x, WALL_HEIGHT - 1, z, p.wall);
                if (plan.isWater(x, z)) {
                    // A barred water gate: the river flows through, nobody swims in under the wall.
                    for (int y = Waterways.SURFACE - 3; y <= -1; y++) {
                        c.set(x, y, z, Blocks.IRON_BARS.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.IronBarsBlock.WATERLOGGED, y <= Waterways.SURFACE));
                    }
                }
                if (sideGate) {
                    c.fill(x, 0, z, x, 8, z, Blocks.AIR);
                }
                boolean outer = !inDisk(x, z, w - 1);
                if (outer && leaf) {
                    c.set(x, WALL_HEIGHT, z, slab(p.roofSlabs().get(0), SlabType.BOTTOM)); // the tiled coping
                } else if (outer && ((x + z) & 1) == 0) {
                    c.set(x, WALL_HEIGHT, z, p.wall); // battlements
                }
                if (outer) {
                    c.set(x, WALL_HEIGHT - 4, z, band); // a coloured band round the outside
                    if (leaf) {
                        c.set(x, 2, z, band);
                    }
                }
            }
        }
        greatGate(w);
        if (leaf) {
            for (int deg = -165; deg <= 165; deg += 30) {
                bastion(w, deg);
            }
            for (int deg = -150; deg <= 150; deg += 30) {
                wallTorii(w, deg);
            }
            sideGate(w, 1);
            sideGate(w, -1);
        }
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

    /** Where on the wall's centre line the bearing {@code deg} (degrees from south, + = east) meets it. */
    private int[] onWall(int w, double deg, double r) {
        double a = Math.toRadians(deg);
        return new int[] {(int) Math.round(Math.sin(a) * r), (int) Math.round(Math.cos(a) * r)};
    }

    /** Whether a feature at (x, z) would sit in a gateway or on the Kage Rock. */
    private boolean blocked(int w, int x, int z, int clear) {
        return inRock(x, z) || inRock(x, z - 6) || z > 0 && Math.abs(x) < 16 + clear
                || Math.abs(z - plan.crossZ) < 8 + clear && Math.abs(x) > w / 2;
    }

    /** A round bastion bulging out of the wall: as high as the wall and a little more, with a tiled coping and a lantern. */
    private void bastion(int w, int deg) {
        int[] at = onWall(w, deg, w - 1);
        int r = 6;
        if (blocked(w, at[0], at[1], r) || !c.touches(at[0] - r, at[1] - r, at[0] + r, at[1] + r)) {
            return;
        }
        for (int x = at[0] - r; x <= at[0] + r; x++) {
            for (int z = at[1] - r; z <= at[1] + r; z++) {
                double d = Math.hypot(x - at[0], z - at[1]);
                if (d > r + 0.5 || inDisk(x, z, w - 3)) {
                    continue;
                }
                c.fill(x, 0, z, x, WALL_HEIGHT, z, p.wall);
                c.set(x, 2, z, Blocks.BROWN_TERRACOTTA);
                c.set(x, WALL_HEIGHT - 4, z, Blocks.BROWN_TERRACOTTA);
                if (d > r - 0.5) {
                    c.set(x, WALL_HEIGHT + 1, z, slab(p.roofSlabs().get(0), SlabType.BOTTOM));
                }
            }
        }
        c.set(at[0], WALL_HEIGHT + 1, at[1], Blocks.LANTERN);
    }

    /** A red gate standing over the wall walk: a post on each side of the walk, two beams, a dark tiled top. */
    private void wallTorii(int w, int deg) {
        int[] in = onWall(w, deg, w - 2.5), out = onWall(w, deg, w - 0.5);
        int[] mid = onWall(w, deg, w - 1.5);
        if (blocked(w, mid[0], mid[1], 2) || !c.touches(mid[0] - 3, mid[1] - 3, mid[0] + 3, mid[1] + 3)) {
            return;
        }
        int y = WALL_HEIGHT;
        for (int[] post : new int[][] {in, out}) {
            c.fill(post[0], y, post[1], post[0], y + 3, post[1], Blocks.RED_TERRACOTTA);
        }
        int n = Math.max(Math.abs(out[0] - in[0]), Math.abs(out[1] - in[1]));
        for (int k = -1; k <= n + 1; k++) {
            double t = (double) k / n;
            int x = (int) Math.round(in[0] + (out[0] - in[0]) * t), z = (int) Math.round(in[1] + (out[1] - in[1]) * t);
            if (k >= 0 && k <= n) {
                c.set(x, y + 3, z, Blocks.RED_TERRACOTTA);
            }
            c.set(x, y + 4, z, Blocks.BLACKSTONE_SLAB);
        }
    }

    /**
     * A side gate (east or west): a tall gateway through the wall with red posts and a roof outside,
     * the village symbol over the opening, and two doors folded back inside, painted "a" and "un".
     */
    private void sideGate(int w, int side) {
        int z0 = plan.crossZ;
        int x0 = side * (int) Math.round(Math.sqrt(Math.max(0, (double) w * w - (double) z0 * z0)));
        if (!c.touches(x0 - 8, z0 - 8, x0 + 8, z0 + 8)) {
            return;
        }
        int out = x0 + side;
        for (int dz : new int[] {-4, 4}) {
            c.fill(out, 0, z0 + dz, out, 12, z0 + dz, Blocks.RED_TERRACOTTA);
        }
        c.fill(out, 10, z0 - 5, out, 11, z0 + 5, Blocks.RED_TERRACOTTA);
        c.fill(out, 12, z0 - 6, out + side, 12, z0 + 6, slab(p.roofSlabs().get(0), SlabType.BOTTOM));
        c.fill(out - side, 12, z0 - 6, out - side, 12, z0 + 6, slab(p.roofSlabs().get(0), SlabType.BOTTOM));
        for (int gz = 0; gz < 5; gz++) {
            for (int gy = 0; gy < 5; gy++) {
                boolean painted = p.glyph.painted(side > 0 ? 4 - gz : gz, gy);
                c.set(out + side, 9 - gy + 0, z0 - 2 + gz, (painted ? p.paint : p.plaque).defaultBlockState());
            }
        }
        // The doors, folded back against the sides of the gateway.
        String[][] signs = {{". # # # .", "# # # # #", ". # . # .", "# . # . #", ". # # . #"},
                {". . # . .", ". # . . .", ". # # . .", "# . . # .", "# . . . #"}};
        for (int s = 0; s < 2; s++) {
            int dz = s == 0 ? -3 : 3;
            c.fill(x0 - side * 6, 0, z0 + dz, x0, 8, z0 + dz, p.gateDoor());
            for (int r = 0; r < 5; r++) {
                String row = signs[s][r].replace(" ", "");
                for (int col = 0; col < 5; col++) {
                    if (row.charAt(col) == '#') {
                        c.set(x0 - side * (1 + col), 7 - r, z0 + dz, p.paint);
                    }
                }
            }
        }
        for (int dz : new int[] {-5, 5}) {
            c.set(out + side, 2, z0 + dz, Blocks.LANTERN);
            c.fill(out + side, 0, z0 + dz, out + side, 1, z0 + dz, p.fence);
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
        if (p != VillagePalette.LEAF) {
            for (int side = -1; side <= 1; side += 2) {
                c.fill(side * 6, 0, w - 9, side * 6, 12, w - 3, p.gateDoor());
                c.set(side * 10, 2, w + 2, Blocks.LANTERN);
                c.fill(side * 10, 0, w + 2, side * 10, 1, w + 2, p.fence);
            }
            return;
        }
        // The doors, folded back inside the gate, painted "a" and "un" (the Leaf's gate).
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

    /**
     * A ring of layered cliffs (the Sand village's desert cliffs, the Stone village's grey rock),
     * {@code base} to {@code base + rise} high, with the only way in a narrow canyon in the south.
     */
    private void cliffWall(int w, Block[] layers, int base, int rise) {
        for (int x = lx1; x <= lx2; x++) {
            for (int z = lz1; z <= lz2; z++) {
                if (!inDisk(x, z, w) || inDisk(x, z, w - 7)) {
                    continue;
                }
                if (z > 0 && Math.abs(x) <= 4) {
                    continue; // the canyon
                }
                double n = noise(x, z, 8, plan.seed);
                int top = base + (int) (n * rise) - (z > 0 && Math.abs(x) < 9 ? 6 : 0);
                for (int y = 0; y < top; y++) {
                    int band = Math.floorMod(y + (int) (n * 4), layers.length * 2) / 2;
                    c.set(x, y, z, layers[band]);
                }
            }
        }
    }

    /**
     * The Cloud village's ring of rock: a ridge of grey cliffs 12–20 high, out of which tall spires of
     * rock rise every so often (up to 60 high), wrapped in a band of white "cloud" at their middles.
     * The only way in is a canyon in the south.
     */
    private void spireRing(int w) {
        Block[] layers = {Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.STONE, Blocks.TUFF};
        for (int x = lx1; x <= lx2; x++) {
            for (int z = lz1; z <= lz2; z++) {
                if (!inDisk(x, z, w) || inDisk(x, z, w - 9) || z > 0 && Math.abs(x) <= 4) {
                    continue;
                }
                double ridge = noise(x, z, 9, plan.seed);
                double spire = noise(x, z, 5, plan.seed + 11);
                int top = 12 + (int) (ridge * 8);
                if (spire > 0.72) {
                    top += (int) ((spire - 0.72) / 0.28 * 44); // a spire
                }
                if (z > 0 && Math.abs(x) < 9) {
                    top = Math.min(top, 10);
                }
                for (int y = 0; y < top; y++) {
                    int band = Math.floorMod(y / 3 + (int) (ridge * 3), layers.length);
                    c.set(x, y, z, layers[band]);
                }
                if (top > 30 && Math.floorMod(hash(x, z, plan.seed), 3) == 0) {
                    c.set(x, 26 + (int) (ridge * 3), z, Blocks.WHITE_WOOL); // a wisp of cloud
                }
            }
        }
    }

    /**
     * The Kage Rock: a mountain north of the village with a sheer cliff facing it, and the faces of
     * four Kage carved into the cliff. Trees grow on top.
     */
    private void kageRock() {
        int face = plan.rockZ;
        int half = plan.rockHalf;
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
        if (p == VillagePalette.LEAF) {
            for (int side = -1; side <= 1; side += 2) {
                rockStairs(side * STAIRS_X, face);
                shelterTunnel(side * 34, face);
            }
        }
    }

    /** Where the zig-zag stairs climb the Kage Rock (either side of the faces). */
    private static final int STAIRS_X = 92;

    /**
     * Zig-zag stairs up the cliff face of the Kage Rock: flights of ten steps of stone-brick stairs
     * on a buttress built against the cliff, turning at landings, with a rail on the outside, up to
     * the forest on top.
     */
    private void rockStairs(int x0, int face) {
        if (!c.touches(x0 - 8, face - 2, x0 + 8, face + 4)) {
            return;
        }
        int top = rockHeight(x0, face - 2);
        int y = 0, x = x0 - 5, dir = 1;
        while (y < top - 1) {
            for (int k = 0; k < 10 && y < top - 1; k++) {
                c.fill(x, 0, face + 1, x, y - 1, face + 2, p.rock);
                c.set(x, y, face + 1, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.StairBlock.FACING, dir > 0 ? Direction.EAST : Direction.WEST));
                c.set(x, y, face + 2, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.StairBlock.FACING, dir > 0 ? Direction.EAST : Direction.WEST));
                c.set(x, y + 1, face + 3, Blocks.STONE_BRICK_WALL);
                c.fill(x, y + 1, face + 1, x, y + 3, face + 2, Blocks.AIR);
                x += dir;
                y++;
            }
            // A landing, then back the other way.
            c.fill(x, 0, face + 1, x + dir, y - 1, face + 2, p.rock);
            c.fill(x, y + 0, face + 1, x + dir, y + 2, face + 2, Blocks.AIR);
            c.set(x + dir, y, face + 3, Blocks.LANTERN);
            dir = -dir;
            x += dir;
        }
        c.fill(x0 - 6, top - 1, face - 3, x0 + 6, top - 1, face, Blocks.GRASS_BLOCK); // out onto the top
    }

    /** A shelter tunnel into the foot of the Kage Rock: a framed doorway and a passage lit by lanterns. */
    private void shelterTunnel(int x0, int face) {
        if (!c.touches(x0 - 3, face - 14, x0 + 3, face + 2)) {
            return;
        }
        c.fill(x0 - 1, 0, face - 12, x0 + 1, 3, face + 1, Blocks.AIR);
        c.fill(x0 - 2, -1, face - 12, x0 + 2, -1, face + 1, Blocks.POLISHED_ANDESITE);
        for (int dx : new int[] {-2, 2}) {
            c.fill(x0 + dx, 0, face + 1, x0 + dx, 4, face + 1, Blocks.POLISHED_BLACKSTONE);
        }
        c.fill(x0 - 2, 4, face + 1, x0 + 2, 4, face + 1, Blocks.POLISHED_BLACKSTONE);
        for (int z = face - 2; z >= face - 11; z -= 4) {
            c.set(x0, 3, z, HANGING_LANTERN);
        }
        c.fill(x0 - 1, 0, face - 12, x0 + 1, 2, face - 12, Blocks.IRON_BARS);
    }

    /** How far the cliff steps back from its line at x: nothing at the carved faces, up to 4 blocks between them. */
    private int recess(int x) {
        for (int centre : new int[] {-51, -17, 17, 51, -STAIRS_X, STAIRS_X}) {
            if (Math.abs(x - centre) <= (Math.abs(centre) == STAIRS_X ? 12 : 10)) {
                return 0;
            }
        }
        return (int) Math.round(noise(x, 0, 7, plan.seed + 3) * 4);
    }

    private int rockHeight(int x, int z) {
        int half = plan.rockHalf;
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

}

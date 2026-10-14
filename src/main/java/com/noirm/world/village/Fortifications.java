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
                if (plan.palette == VillagePalette.SAND) {
                    f.cliffWall(plan.kind.wall);
                } else {
                    f.greatWall(plan.kind.wall);
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
                boolean sideGate = Math.abs(z - plan.crossZ) <= 3 && Math.abs(x) > w / 2;
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

package com.noirm.world.village;

import com.noirm.world.village.VillagePlan.Bridge;
import com.noirm.world.village.VillagePlan.Landing;
import com.noirm.world.village.VillagePlan.Lot;
import com.noirm.world.village.VillagePlan.LotType;
import com.noirm.world.village.VillagePlan.Paving;
import com.noirm.world.village.VillagePlan.Prop;
import com.noirm.world.village.VillagePlan.PropType;
import com.noirm.world.village.VillagePlan.Road;
import com.noirm.world.village.VillagePlan.Role;
import com.noirm.world.village.VillagePlan.Spawn;
import com.noirm.world.village.VillagePlan.Street;
import com.noirm.world.village.VillagePlan.District;
import com.noirm.world.village.VillagePlan.Wire;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.noirm.world.village.VillagePlan.STOREY;

/**
 * Lays out a village, the way a real town grows: the streets first, then the landmarks, then plots
 * packed along every street (door to the street), then back plots, yards and alleys in whatever is
 * left, so there are no idle lawns. Finally the street furniture and the people.
 *
 * <p><b>The hidden village</b> (Leaf and Snow) is a fan that opens from the Kage Rock: the Kage
 * residence stands right under the carved faces, seven avenues leave its plaza (one to the great
 * gate, two to the side gates), street arcs ring the plaza every ~50 blocks, and narrow alleys run
 * between the arcs. Plots are small and packed wall to wall near the plaza, and get bigger, with
 * gardens, towards the wall. A river winds across the village with stone banks and bridges, and
 * the village is split into districts (market, shinobi quarter, clan district, temple hill, lantern
 * street, homes, the outer ring) that pick what gets built and in what colours. The Sand village
 * has eight spokes and two ring streets round a central plaza instead, and no river.
 *
 * <p><b>A town</b> is a post town: one main street of two-storey shopfronts with lanterns strung
 * across it and arched gates at its ends, a cross street, back lanes, the chief's house, an inn, a
 * shrine and a training ground.
 *
 * <p>Curved streets are drawn by stamping disks along their centrelines into the plan's street
 * raster; plots along them are ordinary rectangles whose front is snapped to the nearest side, so
 * every building can still be drawn in a {@link LotFrame}. Everything comes from one
 * {@link RandomSource} seeded with the village seed: the same seed always gives the same plan.
 */
final class VillagePlanner {
    // ---- the capital's layout (local coordinates; the main gate is to the south, +z)
    /** The Kage residence (the plaza's centre), right under the faces of the Kage Rock. */
    static final int TOWER_Z = -165;
    /** Radius of the plaza round the Kage residence. */
    static final int PLAZA = 34;
    /** About where the side gates are (the planner sets the exact {@link VillagePlan#crossZ}). */
    static final int CROSS_Z = -124;
    /** North of this line (and within {@link #ROCK_HALF} of the middle) stands the Kage Rock. */
    static final int ROCK_Z = -200;
    static final int ROCK_HALF = 150;
    /** Rings by distance from the plaza: party-wall plots in the core, bigger plots further out, gardens at the edge. */
    static final int CORE = 90, MIDDLE = 200;
    /** Apartments and round towers only stand this close to the plaza (the skyline stays a low carpet). */
    static final int TALL_RANGE = 120;
    /** The most people and animals a capital is given. */
    static final int CAPITAL_RESIDENTS = 140;

    /** Bearings (degrees from due south, + = east) of the avenues leaving the Kage plaza. */
    private static final int[] FAN_AVENUES = {0, 28, -28, 55, -55, 80, -80};
    /** Radii of the street arcs round the plaza. */
    private static final int[] FAN_ARCS = {70, 120, 170, 220, 275, 335};
    private static final int[] SPOKES = {0, 45, -45, 90, -90, 135, -135, 180};
    private static final int[] SPOKE_RINGS = {100, 175};

    // ---- what each block of the occupancy grid holds
    private static final byte FREE = 0, ROAD = 1, LOT = 2, BLOCKED = 3, WATER = 4, BANK = 5;

    private final VillagePlan plan;
    private final VillagePalette palette;
    private final RandomSource rnd;
    private final int half;
    private final byte[] occ;
    /** Lots must fit inside this radius. */
    private final int limit;
    /** The plaza's centre (the Kage residence in a capital, the square in a town). */
    private int px, pz;
    private int[] avenues = new int[0];
    /** Every street laid so far (plots are packed along these). */
    private final List<Line> lines = new ArrayList<>();
    /** Apartments and round towers so far, and how many there may be. */
    private int tall, tallCap = 40;
    /** Cells that already hold a prop. */
    private final Set<Long> propCells = new HashSet<>();

    private VillagePlanner(VillagePlan plan, int limit) {
        this.plan = plan;
        this.palette = plan.palette;
        this.rnd = RandomSource.create(plan.seed);
        this.half = plan.kind.radius;
        int size = half * 2 + 1;
        this.occ = new byte[size * size];
        this.limit = limit;
    }

    static VillagePlan plan(VillageKind kind, VillagePalette palette, long seed) {
        VillagePlan plan = new VillagePlan(kind, palette, seed);
        switch (kind) {
            // The Sand village's cliffs are thicker than the Leaf village's wall.
            case CAPITAL -> new VillagePlanner(plan, kind.wall - (palette.hasKageRock() ? 8 : 14)).capital();
            case TOWN -> new VillagePlanner(plan, kind.wall - 4).town();
            default -> new VillagePlanner(plan, kind.wall - 1).outpost();
        }
        return plan;
    }

    // ================================================================ the hidden village

    private void capital() {
        boolean fan = palette.hasKageRock();
        int w = plan.kind.wall;
        px = 0;
        pz = fan ? TOWER_Z : 0;
        plan.towerX = px;
        plan.towerZ = pz;
        avenues = fan ? FAN_AVENUES : SPOKES;
        if (fan) {
            plan.rockZ = ROCK_Z;
            plan.rockHalf = ROCK_HALF;
            mark(-ROCK_HALF - 2, -half, ROCK_HALF + 2, ROCK_Z + 2, BLOCKED);
        }
        // ---- the Kage residence in its plaza
        street(Road.ring(Paving.PLAZA, px, pz, -1, PLAZA));
        landmark(LotType.KAGE_TOWER, px - 30, pz - 22, px + 30, pz + 22, Direction.SOUTH, 4, 0);
        // ---- the gates (the main gate in the south; the Leaf village's side gates east and west)
        for (int bearing : avenues) {
            if (isGate(bearing)) {
                int[] at = wallPoint(bearing);
                plan.gates.add(new int[] {at[0], at[1], bearing == 0 ? (fan ? 13 : 9) : 9});
                if (bearing == 80) {
                    plan.crossZ = at[1];
                }
            }
        }
        if (!fan) {
            plan.crossZ = 0;
        }
        // ---- the river, the ring road inside the wall, the avenues
        if (fan) {
            river();
        }
        lay(arc(0, 0, limit + 2.5, Paving.STREET, 4), 12, half - 1);
        // The avenues to the gates, then (in the Leaf village) the clan district by the river, then the
        // other avenues, which stop at its wall rather than crowd it off the bank.
        Lot riverside = null;
        for (int pass = 0; pass < 2; pass++) {
            for (int bearing : avenues) {
                boolean gate = isGate(bearing);
                if (gate != (pass == 0)) {
                    continue;
                }
                int reach = gate ? half - 1 : limit + 3;
                // The ray runs on until it leaves the village; lay() keeps it within reach of the centre.
                lay(ray(px, pz, bearing, PLAZA - 2, 2 * half, Paving.AVENUE, 7, false), 4, reach);
            }
            if (pass == 0 && fan) {
                riverside = riversideCompound();
            }
        }
        // ---- the landmarks the streets go round
        Lot academy, compound, shrine;
        if (fan) {
            academy = placeNear(LotType.ACADEMY, 47, 36, Direction.SOUTH, 2, 0, 30, new int[] {px - 78, pz - 14}, new int[] {px - 120, pz + 5});
            placeNear(LotType.JONIN_STATION, 15, 15, Direction.SOUTH, 1, 0, 24, new int[] {px - 45, pz - 25},
                    academy == null ? new int[] {px - 60, pz + 10} : new int[] {academy.x1() - 10, academy.cz()});
            shrine = placeNear(LotType.SHRINE, 36, 41, Direction.SOUTH, 1, 0, 40, new int[] {px + 95, pz - 12}, new int[] {px + 120, pz + 10});
            compound = riverside;
        } else {
            academy = placeNear(LotType.ACADEMY, 47, 36, null, 2, 0, 24, polar(-112, 68), polar(112, 68), polar(-157, 140), polar(157, 140));
            placeNear(LotType.JONIN_STATION, 15, 15, null, 1, 0, 24,
                    academy == null ? polar(-67, 65) : new int[] {academy.cx() + (academy.cx() < px ? 34 : -34), academy.cz()}, polar(67, 65));
            shrine = placeNear(LotType.SHRINE, 36, 41, null, 1, 0, 30, polar(157, 68), polar(-157, 68), polar(180, 140));
            compound = null;
        }
        if (compound == null) {
            compound = placeNear(LotType.CLAN_COMPOUND, 61, 51, null, 1, rnd.nextInt(2), 40,
                    polar(112, 140), polar(-112, 140), polar(67, 150), polar(-67, 150), polar(22, 160));
        }
        if (compound != null) {
            plan.compoundClan = compound.style() == 0 ? "uchiha" : "hyuga";
        }
        // ---- the lantern street (restaurants and the hot spring) and the gate's guard post
        Line lanternStreet = lanternStreet(fan);
        placeNear(LotType.GUARD_POST, 8, 9, Direction.WEST, 1, 0, 10, new int[] {9, w - (fan ? 16 : 22)});
        // ---- the street arcs (or the Sand village's ring streets)
        int[] radii = fan ? FAN_ARCS : SPOKE_RINGS;
        int[] arcs = new int[radii.length];
        for (int k = 0; k < radii.length; k++) {
            arcs[k] = radii[k] + rnd.nextInt(7) - 3;
            lay(arc(px, pz, arcs[k], Paving.STREET, 5), 12, limit + 2);
        }
        // ---- landmarks that sit on the streets
        // (Between the Leaf village's first two arcs the avenues are too close together for it.)
        if (fan) {
            hospital(arcs[1], arcs[2], arcs[0]);
        } else {
            hospital(arcs[0], arcs[1]);
        }
        placeNear(LotType.RAMEN_SHOP, 9, 10, Direction.WEST, 1, 0, 14, new int[] {px + 10, pz + arcs[0] + 9}, new int[] {px - 10, pz + arcs[0] + 9});
        outerLandmarks(fan);
        // ---- the districts
        districts(fan, academy, shrine, compound, lanternStreet);
        // ---- alleys between the arcs
        alleys(arcs);
        // ---- the plots
        spires(8 + rnd.nextInt(5));
        packAll();
        backLots();
        ensureTall(6);
        yards();
        // ---- street furniture
        lampPosts();
        poles(PLAZA + 30);
        festivalProps();
        plazaProps();
        gateBoards();
        frontProps(45, 40);
        cleanLandings();
        // ---- the people
        capitalPeople(fan);
    }

    private boolean isGate(int bearing) {
        return palette.hasKageRock() ? bearing == 0 || Math.abs(bearing) == 80 : bearing == 0;
    }

    /** Where a ray from the plaza at {@code bearing} meets the wall. */
    private int[] wallPoint(int bearing) {
        double a = Math.toRadians(bearing), dx = Math.sin(a), dz = Math.cos(a);
        double w = plan.kind.wall;
        double b = px * dx + pz * dz;
        double t = -b + Math.sqrt(b * b - (px * (double) px + pz * (double) pz) + w * w);
        return new int[] {(int) Math.round(px + dx * t), (int) Math.round(pz + dz * t)};
    }

    /** A point {@code r} blocks from the plaza at {@code bearing} degrees. */
    private int[] polar(double bearing, double r) {
        double a = Math.toRadians(bearing);
        return new int[] {(int) Math.round(px + Math.sin(a) * r), (int) Math.round(pz + Math.cos(a) * r)};
    }

    /** The clan compound on a flank, by the river, its gate facing the water. */
    private Lot riversideCompound() {
        if (plan.river.isEmpty()) {
            return null;
        }
        int n = plan.river.size();
        int start = rnd.nextInt(n);
        int style = rnd.nextInt(2);
        for (int k = 0; k < n; k += 3) {
            int[] p = plan.river.get((start + k) % n);
            double d = Math.hypot(p[0] - px, p[1] - pz);
            if (Math.abs(p[0]) < 70 || d < 110 || d > 260) {
                continue;
            }
            int[] q = plan.river.get(Math.min(n - 1, (start + k) % n + 2));
            int[] o = plan.river.get(Math.max(0, (start + k) % n - 2));
            double tx = q[0] - o[0], tz = q[1] - o[1];
            double len = Math.hypot(tx, tz);
            if (len == 0) {
                continue;
            }
            tx /= len;
            tz /= len;
            for (int side = -1; side <= 1; side += 2) {
                double nx = -tz * side, nz = tx * side;
                Direction facing = cardinal(-nx, -nz);
                boolean sideways = facing.getAxis() == Direction.Axis.X;
                int wx = sideways ? 51 : 61, wz = sideways ? 61 : 51;
                int reach = (sideways ? wx : wz) / 2 + 8;
                for (int push = 0; push <= 4; push += 2) {
                    int cx = (int) Math.round(p[0] + nx * (reach + push)), cz = (int) Math.round(p[1] + nz * (reach + push));
                    int x1 = cx - wx / 2, z1 = cz - wz / 2;
                    if (free(x1 - 1, z1 - 1, x1 + wx, z1 + wz)) {
                        return landmark(LotType.CLAN_COMPOUND, x1, z1, x1 + wx - 1, z1 + wz - 1, facing, 1, style);
                    }
                }
            }
        }
        return null;
    }

    /**
     * The lantern street: a straight street of restaurants between the gate avenue and the next
     * avenue out, with lanterns strung across it and an arched gate at each end, and the hot spring
     * at its far end.
     */
    private Line lanternStreet(boolean fan) {
        int side = rnd.nextBoolean() ? 1 : -1;
        int z = fan ? 118 + rnd.nextInt(9) : 140 + rnd.nextInt(9);
        double tan = Math.tan(Math.toRadians(fan ? 28 : 45));
        int end = (int) Math.round((z - pz) * tan) - 5;
        Line line = segment(side * 4, z, side * end, z, Paving.PLAZA, 7, true);
        List<Line> pieces = lay(line, 20, limit);
        int onsenX = side * (end - 24);
        placeNear(LotType.ONSEN, 36, 32, Direction.NORTH, 1, 0, 30, new int[] {onsenX, z + 21}, new int[] {onsenX, z - 21},
                new int[] {side * (end / 2), z + 21});
        return pieces.isEmpty() ? null : pieces.get(0);
    }

    /** The hospital, fronting one of the street arcs (the first of {@code arcs} with room). */
    private void hospital(int... arcs) {
        int[] bearings = {-42, 42, -15, 15, -68, 68, -33, 33, -50, 50, -6, 6, -75, 75, -24, 24};
        int start = rnd.nextInt(2);
        for (int arc : arcs) {
            for (int k = 0; k < bearings.length; k++) {
                double bearing = bearings[(k + start) % bearings.length];
                for (int extra = 0; extra <= 12; extra += 2) {
                    int[] c = polar(bearing, arc + 2.5 + 1 + 17 + extra);
                    Direction facing = cardinal(px - c[0], pz - c[1]);
                    if (tryLandmark(LotType.HOSPITAL, 42, 32, facing, 3, 0, c[0], c[1]) != null) {
                        return;
                    }
                }
            }
        }
        placeNear(LotType.HOSPITAL, 42, 32, null, 3, 0, 40, polar(30, arcs[0] + 40), polar(-30, arcs[0] + 40));
    }

    /** The training glades, the cemetery, the giant-tree parks and groves of the outer ring. */
    private void outerLandmarks(boolean fan) {
        int[] bearings = {-150, -120, -95, -65, -40, -15, 15, 40, 65, 95, 120, 150};
        shuffle(bearings);
        double[] reach = fan ? new double[] {255, 300, 235} : new double[] {215, 200};
        int glades = 0;
        int want = fan ? 3 : 2;
        for (double r : reach) {
            for (int bearing : bearings) {
                if (glades >= want) {
                    break;
                }
                int[] c = polar(bearing, r);
                if (placeNear(LotType.TRAINING_GROUND, 42, 30, null, 0, glades % 2, 10, c) != null) {
                    glades++;
                }
            }
        }
        // The cemetery: between two street arcs (or the Sand village's outer ring and the wall), well out.
        shuffle(bearings);
        boolean cemetery = false;
        for (double r : fan ? new double[] {247, 196, 302} : new double[] {212}) {
            for (int bearing : bearings) {
                if (!cemetery && placeNear(LotType.CEMETERY, 25, 31, null, 0, 0, 12, polar(bearing, r)) != null) {
                    cemetery = true;
                }
            }
        }
        // The giant tree park: one in the middle of the village (two in the Leaf village).
        int trees = 0;
        for (int bearing : bearings) {
            if (trees >= (fan ? 2 : 1)) {
                break;
            }
            double r = trees == 0 ? (fan ? 150 : 140) : 250;
            if (placeNear(LotType.GIANT_TREE, 24, 24, null, 0, trees, 12, polar(bearing + 7, r)) != null) {
                trees++;
            }
        }
        // Groves: patches of woods in the outer ring.
        int groves = 0;
        for (int bearing : bearings) {
            if (groves >= 3) {
                break;
            }
            int s = 16 + rnd.nextInt(7);
            if (placeNear(LotType.PARK, s, s, null, 0, 1, 8, polar(bearing - 9, fan ? 320 : 225)) != null) {
                groves++;
            }
        }
    }

    // ================================================================ the river

    /**
     * One river across the village, wall to wall: a straight line plus a gentle sine, never near the
     * plaza, the rock or the gates, passing a flank where the clan district can sit by it. Five
     * blocks of water between stone banks, a walk along each bank, steps down every ~25 blocks.
     */
    private void river() {
        double edge = half - 4;
        List<double[]> route = null;
        for (int attempt = 0; attempt < 120 && route == null; attempt++) {
            double a1 = Math.toRadians(rnd.nextInt(360) - 180);
            double a2 = a1 + Math.toRadians(180 + rnd.nextInt(121) - 60);
            double amp = 8 + rnd.nextInt(5), wave = 60 + rnd.nextInt(31), phase = rnd.nextDouble() * Math.PI * 2;
            List<double[]> pts = riverLine(a1, a2, amp, wave, phase, edge);
            if (riverFits(pts)) {
                route = pts;
            }
        }
        if (route == null) {
            route = riverLine(Math.toRadians(-100), Math.toRadians(35), 10, 75, 0, edge);
        }
        dig(route);
    }

    /** River centreline points every half block, from one edge of the site to the other. */
    private static List<double[]> riverLine(double a1, double a2, double amp, double wave, double phase, double edge) {
        double x1 = Math.sin(a1) * edge, z1 = Math.cos(a1) * edge, x2 = Math.sin(a2) * edge, z2 = Math.cos(a2) * edge;
        double len = Math.hypot(x2 - x1, z2 - z1);
        double ux = (x2 - x1) / len, uz = (z2 - z1) / len;
        List<double[]> pts = new ArrayList<>();
        for (double s = 0; s <= len; s += 0.5) {
            double taper = Math.min(1, Math.min(s, len - s) / 30);
            double off = amp * taper * Math.sin(2 * Math.PI * s / wave + phase);
            double x = x1 + ux * s - uz * off, z = z1 + uz * s + ux * off;
            if (x * x + z * z <= edge * edge) {
                pts.add(new double[] {x, z});
            }
        }
        return pts;
    }

    private boolean riverFits(List<double[]> pts) {
        int inside = 0, flank = 0;
        for (double[] p : pts) {
            double x = p[0], z = p[1];
            double d = Math.hypot(x - px, z - pz);
            if (d < PLAZA + 40 || z <= ROCK_Z + 20 && Math.abs(x) <= ROCK_HALF + 20) {
                return false;
            }
            for (int[] gate : plan.gates) {
                if (Math.hypot(x - gate[0], z - gate[1]) < 30) {
                    return false;
                }
            }
            if (x * x + z * z <= limit * (double) limit) {
                inside++;
            }
            if (Math.abs(x) >= 70 && d >= 110 && d <= 230) {
                flank++;
            }
        }
        return inside >= 600 && flank >= 120;
    }

    private void dig(List<double[]> pts) {
        int edge = half - 4;
        // The water (5 wide), then the banks beside it.
        for (double[] p : pts) {
            forDisk(p[0], p[1], 2.5, (x, z) -> {
                int i = plan.cell(x, z);
                if (i >= 0 && VillageBuilder.inDisk(x, z, edge) && occ[i] != BLOCKED) {
                    plan.waterBits[i >> 6] |= 1L << (i & 63);
                    occ[i] = WATER;
                }
            });
        }
        for (double[] p : pts) {
            forDisk(p[0], p[1], 3.5, (x, z) -> {
                int i = plan.cell(x, z);
                if (i >= 0 && occ[i] == FREE && VillageBuilder.inDisk(x, z, edge + 1)) {
                    occ[i] = BANK;
                }
            });
        }
        int[] last = null;
        for (int k = 0; k < pts.size(); k += 2) {
            int[] c = {(int) Math.round(pts.get(k)[0]), (int) Math.round(pts.get(k)[1])};
            if (last == null || last[0] != c[0] || last[1] != c[1]) {
                plan.river.add(c);
                last = c;
            }
        }
        // A walk along each bank, and steps down to the water every ~25 blocks.
        for (int side = -1; side <= 1; side += 2) {
            Line walk = new Line(Paving.PATH, 0.95, false);
            walk.packSide = side;
            for (int k = 0; k < pts.size(); k += 2) {
                double[] t = tangent(pts, k);
                double nx = -t[1] * side, nz = t[0] * side;
                walk.add(pts.get(k)[0] + nx * 4.5, pts.get(k)[1] + nz * 4.5, t[0], t[1]);
                if (k % 100 == (side > 0 ? 0 : 50)) {
                    int bx = (int) Math.round(pts.get(k)[0] + nx * 3), bz = (int) Math.round(pts.get(k)[1] + nz * 3);
                    int i = plan.cell(bx, bz);
                    Direction down = cardinal(-nx, -nz);
                    if (i >= 0 && occ[i] == BANK && within(bx, bz) && plan.isWater(bx + down.getStepX(), bz + down.getStepZ())) {
                        plan.landings.add(new Landing(bx, bz, down));
                    }
                }
            }
            lay(walk, 8, limit);
        }
    }

    private static double[] tangent(List<double[]> pts, int k) {
        double[] a = pts.get(Math.max(0, k - 4)), b = pts.get(Math.min(pts.size() - 1, k + 4));
        double tx = b[0] - a[0], tz = b[1] - a[1];
        double len = Math.max(1e-6, Math.hypot(tx, tz));
        return new double[] {tx / len, tz / len};
    }

    /** Drops steps that a street ended up running over. */
    private void cleanLandings() {
        plan.landings.removeIf(l -> plan.isRoad(l.x(), l.z()) || occ[plan.cell(l.x(), l.z())] != BANK);
    }

    // ================================================================ a small village: a post town

    private void town() {
        int w = plan.kind.wall;
        px = 0;
        pz = 0;
        plan.towerX = 0;
        plan.towerZ = 0;
        plan.crossZ = 0;
        street(Road.ring(Paving.PLAZA, 0, 0, -1, 6));
        lay(segment(0, -half + 1, 0, half - 1, Paving.STREET, 7, true), 4, half - 1);   // the main street
        lay(segment(-half + 1, 0, half - 1, 0, Paving.STREET, 5, false), 4, half - 1);  // the cross street
        plan.gates.add(new int[] {0, w, 9});
        plan.gates.add(new int[] {0, -w, 9});
        plan.gates.add(new int[] {w, 0, 7});
        plan.gates.add(new int[] {-w, 0, 7});
        placeNear(LotType.INN, 19, 19, Direction.WEST, 2, 0, 10, new int[] {14, 18});
        Lot chief = placeNear(LotType.CHIEF_HOUSE, 21, 19, Direction.SOUTH, 2, 0, 12, new int[] {-26, -16});
        placeNear(LotType.SHRINE, 23, 23, Direction.WEST, 1, 1, 12, new int[] {32, -28});
        Lot ground = placeNear(LotType.TRAINING_GROUND, 21, 21, Direction.EAST, 0, 2, 12, new int[] {-32, 26});
        placeNear(LotType.GUARD_POST, 7, 7, Direction.WEST, 1, 0, 8, new int[] {9, 50});
        // Back lanes behind the shopfronts.
        lay(segment(18, -52, 18, 52, Paving.PATH, 3, false), 8, limit);
        lay(segment(-18, -52, -18, 52, Paving.PATH, 3, false), 8, limit);
        // Wards: the market down the main street, homes behind.
        for (int z : new int[] {-58, -38, -18, 18, 38, 58}) {
            district(District.MARKET, 0, z);
        }
        for (int[] c : new int[][] {{-40, -30}, {40, -30}, {-40, 30}, {40, 30}, {-52, 0}, {52, 0}}) {
            district(District.RESIDENTIAL, c[0], c[1]);
        }
        packAll();
        backLots();
        yards();
        // Street furniture: lanterns strung across the main street, arched gates at its ends, poles
        // along the cross street, the well on the square.
        festivalProps();
        lanterns(Paving.STREET, 11, false);
        poles(10);
        prop(PropType.WELL, 0, 0, Direction.SOUTH, 0);
        plazaBenches(0, 0, 5);
        prop(PropType.NOTICE_BOARD, 5, -5, Direction.SOUTH, 0);
        frontProps(8, 8);

        if (chief != null) {
            add(new Spawn(Role.CHIEF, chief.cx(), 0, chief.cz(), chief.cx(), 0, chief.cz(), 3, 0, 0, 0, List.of()));
        }
        add(Spawn.at(Role.GUARD, 6, 0, w - 2, 4));
        add(Spawn.at(Role.GUARD, -6, 0, -w + 2, 4));
        Lot post = plan.first(LotType.GUARD_POST);
        if (post != null) {
            add(Spawn.at(Role.GUARD, post.cx(), 0, post.cz(), 3));
        }
        if (ground != null) {
            add(Spawn.at(Role.TRAINEE, ground.cx(), 0, ground.cz(), 6));
            add(Spawn.at(Role.DUMMY, ground.cx() + 2, 0, ground.z1() + 4, 0));
        }
        add(Spawn.at(Role.NINJA, 0, 0, 10, 30));
        shopkeepers(8);
        townsfolk(10, 3, 0, 0, 1);
        animals(3, 3, 0, 48);
    }

    // ================================================================ an outpost

    private void outpost() {
        // The outpost is drawn by hand (palisade, campfire, posts); only its paths, houses and people are planned.
        street(Road.ring(Paving.STREET, 0, 0, -1, 3));
        street(Road.rect(Paving.STREET, -1, 0, 1, plan.kind.wall + 1));
        plan.gates.add(new int[] {0, plan.kind.wall, 3});
        landmark(LotType.WATCHTOWER, -10, -9, -4, -3, Direction.SOUTH, 1, 0);
        int[][] spots = {{6, -7}, {7, 4}, {-8, 5}};
        Direction[] doors = {Direction.WEST, Direction.WEST, Direction.EAST};
        int skip = rnd.nextInt(4); // 3 = keep them all
        for (int i = 0; i < spots.length; i++) {
            boolean round = rnd.nextBoolean();
            int style = rnd.nextInt(6);
            long seed = rnd.nextLong();
            if (i == skip) {
                continue;
            }
            LotType type = round || i == 0 ? LotType.ROUND_HOUSE : LotType.HOUSE;
            plan.lots.add(new Lot(type, spots[i][0] - 3, spots[i][1] - 3, spots[i][0] + 3, spots[i][1] + 3, doors[i], 1, style, seed));
        }
        int wall = plan.kind.wall;
        add(Spawn.at(Role.GUARD, 2, 0, wall - 4, 5));
        add(Spawn.at(Role.NINJA, 1, 0, 2, 11));
        add(Spawn.at(Role.GUARD, -7, 10, -7, 1));
        add(Spawn.at(Role.MERCHANT, -2, 0, 2, 6));
        add(Spawn.at(Role.DUMMY, 0, 0, -9, 0));
    }

    // ================================================================ streets

    /** A street's centreline, one sample per block: position and unit direction. */
    private static final class Line {
        final Paving paving;
        /** Half the street's width. */
        final double hw;
        final boolean festival;
        /** Which side gets plots: 0 = both, else only that side (-1 or 1). */
        int packSide;
        final List<double[]> pts = new ArrayList<>();

        Line(Paving paving, double hw, boolean festival) {
            this.paving = paving;
            this.hw = hw;
            this.festival = festival;
        }

        void add(double x, double z, double tx, double tz) {
            pts.add(new double[] {x, z, tx, tz});
        }

        int size() {
            return pts.size();
        }

        double x(int i) {
            return pts.get(i)[0];
        }

        double z(int i) {
            return pts.get(i)[1];
        }

        double tx(int i) {
            return pts.get(i)[2];
        }

        double tz(int i) {
            return pts.get(i)[3];
        }

        Line emptyCopy() {
            Line copy = new Line(paving, hw, festival);
            copy.packSide = packSide;
            return copy;
        }
    }

    /** A straight street leaving (cx, cz) at {@code bearing} degrees, from {@code r0} to {@code r1} blocks out. */
    private static Line ray(double cx, double cz, double bearing, double r0, double r1, Paving paving, double width, boolean festival) {
        double a = Math.toRadians(bearing), tx = Math.sin(a), tz = Math.cos(a);
        Line line = new Line(paving, width / 2, festival);
        for (double s = r0; s <= r1; s += 1) {
            line.add(cx + tx * s, cz + tz * s, tx, tz);
        }
        return line;
    }

    /** A full circle of street of radius {@code r} round (cx, cz), starting due north. */
    private static Line arc(double cx, double cz, double r, Paving paving, double width) {
        Line line = new Line(paving, width / 2, false);
        int steps = (int) Math.ceil(2 * Math.PI * r);
        for (int k = 0; k < steps; k++) {
            double a = -Math.PI + 2 * Math.PI * k / steps;
            line.add(cx + r * Math.sin(a), cz + r * Math.cos(a), Math.cos(a), -Math.sin(a));
        }
        return line;
    }

    private static Line segment(double ax, double az, double bx, double bz, Paving paving, double width, boolean festival) {
        double len = Math.hypot(bx - ax, bz - az);
        double tx = (bx - ax) / len, tz = (bz - az) / len;
        Line line = new Line(paving, width / 2, festival);
        for (double s = 0; s <= len; s += 1) {
            line.add(ax + tx * s, az + tz * s, tx, tz);
        }
        return line;
    }

    /**
     * Lays a street along {@code line} wherever its centre may be street (not a landmark, not the
     * rock) and is within {@code reach} of the village centre, in pieces of at least {@code minRun}
     * blocks. Records the pieces, and a bridge wherever one crosses the river.
     */
    private List<Line> lay(Line line, int minRun, int reach) {
        List<Line> pieces = new ArrayList<>();
        Line run = null;
        for (double[] p : line.pts) {
            int cx = (int) Math.round(p[0]), cz = (int) Math.round(p[1]);
            if (mayBeRoad(cx, cz) && VillageBuilder.inDisk(cx, cz, reach)) {
                if (run == null) {
                    run = line.emptyCopy();
                }
                run.pts.add(p);
            } else if (run != null) {
                if (run.size() >= minRun) {
                    pieces.add(run);
                }
                run = null;
            }
        }
        if (run != null && run.size() >= minRun) {
            pieces.add(run);
        }
        for (Line piece : pieces) {
            for (int i = 0; i < piece.size(); i++) {
                disk(piece.x(i), piece.z(i), piece.hw, piece.paving);
                if (i + 1 < piece.size()) {
                    disk((piece.x(i) + piece.x(i + 1)) / 2, (piece.z(i) + piece.z(i + 1)) / 2, piece.hw, piece.paving);
                }
            }
            bridges(piece);
            List<int[]> centre = new ArrayList<>();
            for (double[] p : piece.pts) {
                centre.add(new int[] {(int) Math.round(p[0]), (int) Math.round(p[1])});
            }
            plan.streets.add(new Street(piece.paving, (int) Math.round(piece.hw * 2), piece.festival, centre));
        }
        lines.addAll(pieces);
        return pieces;
    }

    /** A bridge wherever the street's centre crosses water. */
    private void bridges(Line line) {
        int start = -1;
        for (int i = 0; i <= line.size(); i++) {
            boolean wet = i < line.size() && plan.isWater((int) Math.round(line.x(i)), (int) Math.round(line.z(i)));
            if (wet && start < 0) {
                start = i;
            } else if (!wet && start >= 0) {
                int mid = (start + i - 1) / 2;
                int x = (int) Math.round(line.x(mid)), z = (int) Math.round(line.z(mid));
                Direction.Axis axis = Math.abs(line.tx(mid)) >= Math.abs(line.tz(mid)) ? Direction.Axis.X : Direction.Axis.Z;
                int dx = axis == Direction.Axis.X ? 1 : 0, dz = 1 - dx;
                int a = 0, b = 0;
                while (a < 24 && plan.isWater(x + dx * (a + 1), z + dz * (a + 1))) {
                    a++;
                }
                while (b < 24 && plan.isWater(x - dx * (b + 1), z - dz * (b + 1))) {
                    b++;
                }
                int shift = Math.floorDiv(a - b, 2);
                int reachOut = Math.max(a - shift, b + shift) + 2;
                // Red arched footbridges carry the streets, flat stone ones the avenues and the ring road by the wall.
                boolean arched = line.paving != Paving.AVENUE && VillageBuilder.inDisk(x, z, plan.kind.wall - 14);
                plan.bridges.add(new Bridge(x + dx * shift, z + dz * shift, axis, reachOut * 2 + 1,
                        arched ? 3 : (int) Math.round(line.hw * 2), arched));
                start = -1;
            }
        }
    }

    /** Lays a simple street shape (plaza, ring). */
    private void street(Road road) {
        plan.roads.add(road);
        for (int x = Math.max(-half, road.x1()); x <= Math.min(half, road.x2()); x++) {
            for (int z = Math.max(-half, road.z1()); z <= Math.min(half, road.z2()); z++) {
                if (road.contains(x, z)) {
                    road(x, z, road.paving());
                }
            }
        }
    }

    private void disk(double cx, double cz, double r, Paving paving) {
        forDisk(cx, cz, r, (x, z) -> road(x, z, paving));
    }

    private interface CellAction {
        void at(int x, int z);
    }

    private static void forDisk(double cx, double cz, double r, CellAction action) {
        double rr = r * r + 1e-6;
        for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++) {
            for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++) {
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= rr) {
                    action.at(x, z);
                }
            }
        }
    }

    /** Makes (x, z) street with this paving (a grander paving wins where streets meet). */
    private void road(int x, int z, Paving paving) {
        int i = plan.cell(x, z);
        if (i < 0 || !VillageBuilder.inDisk(x, z, half - 1)) {
            return;
        }
        byte o = occ[i];
        if (o == LOT || o == BLOCKED) {
            return;
        }
        int old = plan.streetGrid[i];
        if (old == 0 || rank(paving) > rank(Paving.values()[old - 1])) {
            plan.streetGrid[i] = (byte) (paving.ordinal() + 1);
        }
        if (o == FREE) {
            occ[i] = ROAD;
        }
    }

    private static int rank(Paving paving) {
        return switch (paving) {
            case PATH -> 1;
            case STREET -> 2;
            case AVENUE -> 3;
            case PLAZA -> 4;
        };
    }

    private boolean mayBeRoad(int x, int z) {
        int i = plan.cell(x, z);
        return i >= 0 && VillageBuilder.inDisk(x, z, half - 1) && occ[i] != LOT && occ[i] != BLOCKED;
    }

    /**
     * Narrow alleys between the arcs, every 3–4 plots, leaving one arc and (mostly) meeting the next
     * in a T; some stop halfway at a little roadside shrine or a well.
     */
    private void alleys(int[] arcs) {
        double[] inner = new double[arcs.length + 1];
        double[] outer = new double[arcs.length + 1];
        inner[0] = PLAZA;
        for (int k = 0; k < arcs.length; k++) {
            outer[k] = arcs[k] - 2.5;
            inner[k + 1] = arcs[k] + 2.5;
        }
        outer[arcs.length] = limit + 0.5;
        for (int band = 0; band <= arcs.length; band++) {
            double rin = inner[band], rout = outer[band];
            if (rout - rin < 14) {
                continue;
            }
            double mid = (rin + rout) / 2;
            double spacing = 26 + rnd.nextInt(10);
            double step = spacing / mid;
            double phase = rnd.nextDouble() * step;
            for (double a = -Math.PI + phase; a < Math.PI; a += step) {
                boolean deadEnd = rnd.nextFloat() < 0.3f;
                double stop = 0.45 + 0.15 * rnd.nextFloat();
                if (nearAvenue(a, rin, 12)) {
                    continue;
                }
                double tx = Math.sin(a), tz = Math.cos(a);
                // It must leave a street.
                if (!plan.isRoad((int) Math.round(px + tx * (rin - 1)), (int) Math.round(pz + tz * (rin - 1)))) {
                    continue;
                }
                double s1 = deadEnd ? rin + (rout - rin) * stop : rout + 1;
                Line alley = new Line(Paving.PATH, 1.2, false);
                boolean blocked = false;
                for (double s = rin - 0.5; s <= s1; s += 1) {
                    int cx = (int) Math.round(px + tx * s), cz = (int) Math.round(pz + tz * s);
                    int i = plan.cell(cx, cz);
                    if (i < 0 || !within(cx, cz) || occ[i] != FREE && occ[i] != ROAD) {
                        blocked = true;
                        break;
                    }
                    alley.add(px + tx * s, pz + tz * s, tx, tz);
                }
                if (alley.size() < 6) {
                    continue;
                }
                lay(alley, 6, limit);
                if (deadEnd || blocked) {
                    // A little yard closes the dead end: a roadside shrine or a well.
                    double[] end = alley.pts.get(alley.size() - 1);
                    int cx = (int) Math.round(end[0] + tx * 3), cz = (int) Math.round(end[1] + tz * 3);
                    if (free(cx - 1, cz - 1, cx + 2, cz + 2)) {
                        addLot(LotType.YARD, cx - 1, cz - 1, cx + 2, cz + 2, cardinal(-tx, -tz), 0, rnd.nextBoolean() ? 6 : 7,
                                colourFor(plan.district(cx, cz), rnd.nextInt(4), null));
                    }
                }
            }
        }
    }

    /** Whether bearing {@code a} (radians) is within {@code gap} blocks of an avenue at radius r. */
    private boolean nearAvenue(double a, double r, double gap) {
        for (int bearing : avenues) {
            double d = Math.abs(Math.IEEEremainder(a - Math.toRadians(bearing), 2 * Math.PI));
            if (d * r < gap + 3.5) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ districts

    private void district(District district, double x, double z) {
        plan.districtCentres.add(new int[] {(int) Math.round(x), (int) Math.round(z), district.ordinal()});
    }

    /** Splits the capital into districts round fixed centres (a place belongs to the nearest one). */
    private void districts(boolean fan, Lot academy, Lot shrine, Lot compound, Line lanternStreet) {
        district(District.SHINOBI, px, pz);
        if (academy != null) {
            district(District.SHINOBI, academy.cx(), academy.cz());
        }
        Lot hospital = plan.first(LotType.HOSPITAL);
        if (hospital != null) {
            district(District.SHINOBI, hospital.cx(), hospital.cz());
        }
        if (shrine != null) {
            district(District.TEMPLE, shrine.cx(), shrine.cz());
        }
        if (compound != null) {
            district(District.CLAN, compound.cx(), compound.cz());
            double dx = compound.cx() - px, dz = compound.cz() - pz, len = Math.max(1, Math.hypot(dx, dz));
            district(District.CLAN, compound.cx() + dx / len * 45, compound.cz() + dz / len * 45);
        }
        if (lanternStreet != null) {
            for (int k = 1; k <= 3; k++) {
                int i = lanternStreet.size() * k / 4;
                district(District.LANTERN, lanternStreet.x(i), lanternStreet.z(i));
            }
        }
        // The market all along the gate avenue.
        double toGate = plan.gates.isEmpty() ? plan.kind.wall : Math.hypot(plan.gates.get(0)[0] - px, plan.gates.get(0)[1] - pz);
        for (double r = 70; r < toGate - 20; r += 75) {
            district(District.MARKET, px, pz + r);
        }
        if (fan) {
            for (int s = -1; s <= 1; s += 2) {
                district(District.RESIDENTIAL, s * 105, -125);
                district(District.RESIDENTIAL, s * 120, -45);
                district(District.RESIDENTIAL, s * 95, 30);
                district(District.RESIDENTIAL, s * 175, -95);
                district(District.OUTER, s * 215, 25);
                district(District.OUTER, s * 170, 140);
                district(District.OUTER, s * 95, 205);
            }
        } else {
            for (int bearing = 45; bearing < 360; bearing += 45) {
                int[] c = polar(bearing, 115);
                district(District.RESIDENTIAL, c[0], c[1]);
            }
            for (int bearing = 22; bearing < 360; bearing += 45) {
                int[] c = polar(bearing, 220);
                district(District.OUTER, c[0], c[1]);
            }
        }
    }

    // ================================================================ lots

    /** Adds a lot and marks it taken. */
    private Lot addLot(LotType type, int x1, int z1, int x2, int z2, Direction facing, int floors, int style, int colour) {
        Lot lot = new Lot(type, x1, z1, x2, z2, facing, floors, style, rnd.nextLong(), colour);
        plan.lots.add(lot);
        mark(x1, z1, x2, z2, LOT);
        return lot;
    }

    /** A landmark, with a block of space round it. */
    private Lot landmark(LotType type, int x1, int z1, int x2, int z2, Direction facing, int floors, int style) {
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        Lot lot = new Lot(type, x1, z1, x2, z2, facing, floors, style, rnd.nextLong(), colourFor(plan.district(cx, cz), rnd.nextInt(4), null));
        plan.lots.add(lot);
        mark(x1 - 1, z1 - 1, x2 + 1, z2 + 1, LOT);
        return lot;
    }

    /** A landmark of {@code frontW} × {@code depth} (front width × depth) centred on (cx, cz), if the ground is free. */
    private Lot tryLandmark(LotType type, int frontW, int depth, Direction facing, int floors, int style, int cx, int cz) {
        boolean sideways = facing.getAxis() == Direction.Axis.X;
        int wx = sideways ? depth : frontW, wz = sideways ? frontW : depth;
        int x1 = cx - wx / 2, z1 = cz - wz / 2;
        if (!free(x1 - 1, z1 - 1, x1 + wx, z1 + wz)) {
            return null;
        }
        return landmark(type, x1, z1, x1 + wx - 1, z1 + wz - 1, facing, floors, style);
    }

    /**
     * Places a landmark as close as it fits to one of the {@code anchors} (tried in order), searching
     * outward up to {@code search} blocks. With {@code fixed} null, it faces the plaza.
     */
    private Lot placeNear(LotType type, int frontW, int depth, Direction fixed, int floors, int style, int search, int[]... anchors) {
        for (int[] anchor : anchors) {
            for (int r = 0; r <= search; r += 2) {
                for (int dx = -r; dx <= r; dx += 2) {
                    for (int dz = -r; dz <= r; dz += 2) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        int cx = anchor[0] + dx, cz = anchor[1] + dz;
                        Direction facing = fixed != null ? fixed : cardinal(px - cx, pz - cz);
                        Lot lot = tryLandmark(type, frontW, depth, facing, floors, style, cx, cz);
                        if (lot != null) {
                            return lot;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Plots along every street: the avenues first (the best frontage), then streets, then alleys. */
    private void packAll() {
        List<Line> order = new ArrayList<>(lines);
        order.sort(Comparator.comparingInt(l -> l.paving == Paving.AVENUE ? 0 : l.festival ? 1 : l.paving == Paving.STREET ? 2 : 3));
        for (Line line : order) {
            pack(line);
        }
    }

    /**
     * Packs plots along both sides of a street, door to the street, side by side: wall to wall in
     * the core, with a garden strip between them further out. Neighbours on a street block share a
     * wall colour and differ in roof colour.
     */
    private void pack(Line line) {
        for (int side = -1; side <= 1; side += 2) {
            if (line.packSide != 0 && side != line.packSide) {
                continue;
            }
            Lot prev = null;
            int blockWall = rnd.nextInt(4);
            for (int i = 0; i < line.size(); i++) {
                double nx = -line.tz(i) * side, nz = line.tx(i) * side;
                int qx = (int) Math.round(line.x(i) + nx * (line.hw + 0.5)), qz = (int) Math.round(line.z(i) + nz * (line.hw + 0.5));
                int ci = plan.cell(qx, qz);
                if (ci < 0 || !within(qx, qz) || occ[ci] != FREE && occ[ci] != ROAD) {
                    continue;
                }
                District district = plan.district(qx, qz);
                int ring = ring(qx, qz);
                LotType type = frontType(district, ring, qx, qz);
                int[] dims = dims(type, ring);
                int gap = gap(ring);
                int[] r = tryPlot(line, i, side, dims[0], dims[1], gap);
                if (r == null) {
                    continue;
                }
                boolean joined = prev != null && apart(prev, r) <= 2;
                if (!joined) {
                    blockWall = rnd.nextInt(4);
                }
                int wall = rnd.nextFloat() < 0.8f ? blockWall : rnd.nextInt(4);
                int colour = colourFor(district, wall, joined ? prev : null);
                Lot lot = addLot(type, r[0], r[1], r[2], r[3], Direction.from2DDataValue(r[4]), floors(type, ring, district), style(type, district, ring), colour);
                if (type == LotType.APARTMENT || type == LotType.ROUND_TOWER) {
                    tall++;
                }
                prev = lot;
            }
        }
    }

    /**
     * Fits a {@code wid} × {@code dep} plot beside sample {@code i} of {@code line} on {@code side}:
     * its front snapped to the side nearest the street, pushed back off the street, then pulled up
     * against it. Returns {x1, z1, x2, z2, facing} or null if it doesn't fit.
     */
    private int[] tryPlot(Line line, int i, int side, int wid, int dep, int gap) {
        double nx = -line.tz(i) * side, nz = line.tx(i) * side;
        Direction facing = cardinal(-nx, -nz);
        Direction along = facing.getAxis() == Direction.Axis.X ? (line.tz(i) >= 0 ? Direction.SOUTH : Direction.NORTH)
                : (line.tx(i) >= 0 ? Direction.EAST : Direction.WEST);
        int fx = facing.getStepX(), fz = facing.getStepZ(), ax = along.getStepX(), az = along.getStepZ();
        int qx = (int) Math.round(line.x(i) + nx * (line.hw + 0.5)), qz = (int) Math.round(line.z(i) + nz * (line.hw + 0.5));
        int[] r = null;
        for (int push = 0; push <= 5 && r == null; push++) {
            int[] c = rect(qx - fx * push, qz - fz * push, wid, dep, fx, fz, ax, az);
            int s = scan(c[0], c[1], c[2], c[3]);
            if (s == 2) {
                return null;
            }
            if (s == 0) {
                r = c;
            }
        }
        if (r == null) {
            return null;
        }
        for (int pull = 0; pull < 3; pull++) {
            int[] c = {r[0] + fx, r[1] + fz, r[2] + fx, r[3] + fz};
            if (scan(c[0], c[1], c[2], c[3]) != 0) {
                break;
            }
            r = c;
        }
        // The door (the middle of the front) must open onto the street.
        int doorX = fx > 0 ? r[2] : fx < 0 ? r[0] : (r[0] + r[2]) / 2;
        int doorZ = fz > 0 ? r[3] : fz < 0 ? r[1] : (r[1] + r[3]) / 2;
        boolean reaches = false;
        for (int k = 1; k <= 3 && !reaches; k++) {
            reaches = plan.isRoad(doorX + fx * k, doorZ + fz * k);
        }
        if (!reaches) {
            return null;
        }
        if (gap > 0) {
            int[] g = ax != 0 ? new int[] {r[0] - gap, r[1], r[2] + gap, r[3]} : new int[] {r[0], r[1] - gap, r[2], r[3] + gap};
            if (hasLot(g[0], g[1], g[2], g[3])) {
                return null;
            }
        }
        return new int[] {r[0], r[1], r[2], r[3], facing.get2DDataValue()};
    }

    /** The box of a plot whose front corner is (qx, qz): {@code wid} along (ax, az), {@code dep} back from the street (away from (fx, fz)). */
    private static int[] rect(int qx, int qz, int wid, int dep, int fx, int fz, int ax, int az) {
        int x1 = qx + Math.min(0, ax * (wid - 1)) + Math.min(0, -fx * (dep - 1));
        int x2 = qx + Math.max(0, ax * (wid - 1)) + Math.max(0, -fx * (dep - 1));
        int z1 = qz + Math.min(0, az * (wid - 1)) + Math.min(0, -fz * (dep - 1));
        int z2 = qz + Math.max(0, az * (wid - 1)) + Math.max(0, -fz * (dep - 1));
        return new int[] {x1, z1, x2, z2};
    }

    /** How many blocks apart a lot and a box are (0 = touching). */
    private static int apart(Lot lot, int[] r) {
        int dx = Math.max(0, Math.max(r[0] - lot.x2() - 1, lot.x1() - r[2] - 1));
        int dz = Math.max(0, Math.max(r[1] - lot.z2() - 1, lot.z1() - r[3] - 1));
        return Math.max(dx, dz);
    }

    /** 0 = near the plaza (small plots, party walls), 1 = the middle, 2 = the outer ring. */
    private int ring(int x, int z) {
        if (plan.kind == VillageKind.TOWN) {
            return Math.abs(x) <= 18 ? 0 : 1;
        }
        double d = Math.hypot(x - px, z - pz);
        return d <= CORE ? 0 : d <= MIDDLE ? 1 : 2;
    }

    /** What a street-front plot becomes, by district. */
    private LotType frontType(District district, int ring, int x, int z) {
        float r = rnd.nextFloat();
        LotType type = switch (district) {
            case MARKET -> r < 0.38f ? LotType.SHOP : r < 0.56f ? LotType.RESTAURANT : r < 0.68f ? LotType.STALL
                    : r < 0.94f ? LotType.HOUSE : LotType.WAREHOUSE;
            case LANTERN -> r < 0.45f ? LotType.RESTAURANT : r < 0.65f ? LotType.SHOP : r < 0.75f ? LotType.STALL : LotType.HOUSE;
            case SHINOBI -> r < 0.55f ? LotType.HOUSE : r < 0.70f ? LotType.SHOP : r < 0.84f ? LotType.APARTMENT
                    : r < 0.95f ? LotType.ROUND_HOUSE : LotType.RESTAURANT;
            case TEMPLE -> r < 0.55f ? LotType.HOUSE : r < 0.70f ? LotType.SHOP : r < 0.85f ? LotType.ROUND_HOUSE
                    : r < 0.95f ? LotType.GARDEN : LotType.RESTAURANT;
            case CLAN -> r < 0.75f ? LotType.HOUSE : r < 0.80f ? LotType.ROUND_HOUSE : r < 0.90f ? LotType.GARDEN : LotType.WAREHOUSE;
            case OUTER -> r < 0.55f ? LotType.HOUSE : r < 0.70f ? LotType.ROUND_HOUSE : r < 0.82f ? LotType.GARDEN
                    : r < 0.90f ? LotType.WAREHOUSE : LotType.YARD;
            case RESIDENTIAL -> r < 0.62f ? LotType.HOUSE : r < 0.74f ? LotType.ROUND_HOUSE : r < 0.80f ? LotType.SHOP
                    : r < 0.83f ? LotType.RESTAURANT : r < 0.87f ? LotType.WAREHOUSE : r < 0.95f ? LotType.APARTMENT
                    : r < 0.98f ? LotType.ROUND_TOWER : LotType.GARDEN;
        };
        boolean tallType = type == LotType.APARTMENT || type == LotType.ROUND_TOWER;
        if (tallType && (plan.kind != VillageKind.CAPITAL || tall >= tallCap || Math.hypot(x - px, z - pz) > TALL_RANGE)) {
            type = LotType.HOUSE;
        }
        if (type == LotType.WAREHOUSE && ring == 0) {
            type = LotType.SHOP;
        }
        if (type == LotType.STALL && plan.kind == VillageKind.TOWN) {
            type = LotType.SHOP; // a post town's shopfronts are continuous
        }
        if (type == LotType.ROUND_HOUSE && ring == 0 && rnd.nextBoolean()) {
            type = LotType.HOUSE;
        }
        return type;
    }

    /** Front width and depth of a plot. */
    private int[] dims(LotType type, int ring) {
        return switch (type) {
            case APARTMENT -> new int[] {9 + rnd.nextInt(4), 10 + rnd.nextInt(4)};
            case ROUND_TOWER -> {
                int s = 9 + rnd.nextInt(3);
                yield new int[] {s, s + 1};
            }
            case ROUND_HOUSE -> {
                int s = 8 + rnd.nextInt(3) + (ring == 2 ? 1 : 0);
                yield new int[] {s, s + rnd.nextInt(2)};
            }
            case STALL -> new int[] {5 + rnd.nextInt(3), 5 + rnd.nextInt(2)};
            case WAREHOUSE -> new int[] {9 + rnd.nextInt(4), 10 + rnd.nextInt(4)};
            case GARDEN -> new int[] {6 + rnd.nextInt(5), 7 + rnd.nextInt(5)};
            case YARD -> new int[] {9 + rnd.nextInt(4), 9 + rnd.nextInt(4)};
            default -> plan.kind == VillageKind.TOWN && ring == 0 ? new int[] {5 + rnd.nextInt(4), 9 + rnd.nextInt(3)}
                    : switch (ring) {
                        case 0 -> new int[] {5 + rnd.nextInt(4), 6 + rnd.nextInt(5)};
                        case 1 -> new int[] {7 + rnd.nextInt(6), 8 + rnd.nextInt(6)};
                        default -> new int[] {9 + rnd.nextInt(6), 10 + rnd.nextInt(6)};
                    };
        };
    }

    /** Blocks of garden between neighbouring plots. */
    private int gap(int ring) {
        return switch (ring) {
            case 0 -> 0;
            case 1 -> rnd.nextFloat() < 0.45f ? 1 : 0;
            default -> 1 + rnd.nextInt(2);
        };
    }

    /** Storeys: a low 2–3-storey carpet, apartments and round towers up to 5. */
    private int floors(LotType type, int ring, District district) {
        float r = rnd.nextFloat();
        return switch (type) {
            case APARTMENT, ROUND_TOWER -> 4 + rnd.nextInt(2);
            case HOUSE -> plan.kind == VillageKind.TOWN && ring == 0 ? 2
                    : ring == 0 ? (r < 0.2f ? 1 : r < 0.75f ? 2 : 3)
                    : ring == 1 ? (r < 0.35f ? 1 : r < 0.85f ? 2 : 3)
                    : (r < 0.6f ? 1 : 2);
            case SHOP, RESTAURANT -> ring == 0 || district == District.MARKET || district == District.LANTERN ? 2 : 1 + rnd.nextInt(2);
            case ROUND_HOUSE, WAREHOUSE -> 1 + rnd.nextInt(2);
            case STALL -> 1;
            default -> 0;
        };
    }

    /** The building's variant; shops lean to weapons in the shinobi quarter and flowers on temple hill. */
    private int style(LotType type, District district, int ring) {
        return switch (type) {
            case SHOP -> {
                int kind = district == District.SHINOBI && rnd.nextFloat() < 0.6f ? 0
                        : district == District.TEMPLE && rnd.nextFloat() < 0.5f ? 2 : rnd.nextInt(3);
                yield rnd.nextInt(4) * 3 + kind;
            }
            case YARD -> yardStyle(ring);
            default -> rnd.nextInt(12);
        };
    }

    /**
     * A yard's filler: 0 kitchen garden (fields in the outer ring), 1 laundry, 2 woodpile, 3 shed,
     * 4 koi pond, 5 bamboo, 6 roadside shrine, 7 well.
     */
    private int yardStyle(int ring) {
        if (ring == 2 && rnd.nextFloat() < 0.55f) {
            return 0;
        }
        int r = rnd.nextInt(100);
        return r < 15 ? 0 : r < 33 ? 1 : r < 48 ? 2 : r < 63 ? 3 : r < 71 ? 4 : r < 83 ? 5 : r < 90 ? 6 : 7;
    }

    /**
     * If the plots gave the capital fewer than {@code min} blocks of flats, turns the roomiest houses
     * nearest the plaza into some (well apart): the skyline needs a few mid-rise accents.
     */
    private void ensureTall(int min) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < plan.lots.size(); i++) {
            Lot lot = plan.lots.get(i);
            // (Not in the core, where the small party-wall houses are.)
            double d = Math.hypot(lot.cx() - px, lot.cz() - pz);
            boolean roomy = lot.type() == LotType.HOUSE && frameW(lot) >= 8 && frameD(lot) >= 9
                    || lot.type() == LotType.ROUND_HOUSE && Math.min(frameW(lot), frameD(lot)) >= 9;
            if (roomy && d >= CORE && d <= TALL_RANGE + 8) {
                candidates.add(i);
            }
        }
        candidates.sort(Comparator.comparingDouble(i -> Math.hypot(plan.lots.get(i).cx() - px, plan.lots.get(i).cz() - pz)));
        List<Lot> placed = new ArrayList<>(plan.lots.stream().filter(l -> l.type() == LotType.APARTMENT || l.type() == LotType.ROUND_TOWER).toList());
        for (int i : candidates) {
            if (tall >= min) {
                break;
            }
            Lot lot = plan.lots.get(i);
            if (placed.stream().anyMatch(o -> Math.hypot(o.cx() - lot.cx(), o.cz() - lot.cz()) < 24)) {
                continue;
            }
            LotType type = lot.type() == LotType.ROUND_HOUSE ? LotType.ROUND_TOWER : LotType.APARTMENT;
            Lot flats = new Lot(type, lot.x1(), lot.z1(), lot.x2(), lot.z2(), lot.facing(), 4 + rnd.nextInt(2), lot.style(), lot.seed(), lot.colour());
            plan.lots.set(i, flats);
            placed.add(flats);
            tall++;
        }
    }

    /** Slender spires that break the skyline, at street corners, well apart. */
    private void spires(int count) {
        List<Line> streets = lines.stream().filter(l -> l.paving == Paving.STREET || l.paving == Paving.AVENUE).toList();
        List<Lot> placed = new ArrayList<>();
        for (int attempt = 0; attempt < 1500 && placed.size() < count && !streets.isEmpty(); attempt++) {
            Line line = streets.get(rnd.nextInt(streets.size()));
            int i = rnd.nextInt(line.size());
            int side = rnd.nextBoolean() ? 1 : -1;
            int s = 5 + rnd.nextInt(3);
            int style = rnd.nextInt(3);
            double d = Math.hypot(line.x(i) - px, line.z(i) - pz);
            if (d < PLAZA + 20 || d > limit - 10) {
                continue;
            }
            int[] r = tryPlot(line, i, side, s, s, 0);
            if (r == null) {
                continue;
            }
            int cx = (r[0] + r[2]) / 2, cz = (r[1] + r[3]) / 2;
            if (placed.stream().anyMatch(o -> Math.hypot(o.cx() - cx, o.cz() - cz) < 25)) {
                continue;
            }
            placed.add(addLot(LotType.SPIRE, r[0], r[1], r[2], r[3], Direction.from2DDataValue(r[4]), 0, style,
                    colourFor(plan.district(cx, cz), rnd.nextInt(4), null)));
        }
    }

    /** Back plots in the space behind the street fronts: houses facing the nearest street, gardens, small parks. */
    private void backLots() {
        List<int[]> spots = new ArrayList<>();
        for (int x = -limit; x <= limit; x += 3) {
            for (int z = -limit; z <= limit; z += 3) {
                if (within(x, z)) {
                    spots.add(new int[] {x, z});
                }
            }
        }
        for (int i = spots.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int[] t = spots.get(i);
            spots.set(i, spots.get(j));
            spots.set(j, t);
        }
        for (int[] spot : spots) {
            int x1 = spot[0], z1 = spot[1];
            if (occ[plan.cell(x1, z1)] != FREE) {
                continue;
            }
            int ring = ring(x1, z1);
            int wid = ring == 0 ? 5 + rnd.nextInt(4) : ring == 1 ? 7 + rnd.nextInt(5) : 9 + rnd.nextInt(5);
            int dep = ring == 0 ? 6 + rnd.nextInt(5) : ring == 1 ? 8 + rnd.nextInt(5) : 9 + rnd.nextInt(5);
            float roll = rnd.nextFloat();
            int x2 = x1 + wid - 1, z2 = z1 + dep - 1;
            if (!free(x1, z1, x2, z2)) {
                continue;
            }
            Direction facing = towardsStreet(x1, z1, x2, z2, 10);
            District district = plan.district((x1 + x2) / 2, (z1 + z2) / 2);
            float houses = ring == 0 ? 0.85f : ring == 1 ? 0.75f : 0.55f;
            LotType type;
            if (facing == null) {
                type = roll < 0.6f ? LotType.GARDEN : ring == 2 ? LotType.YARD : LotType.PARK;
            } else if (roll < houses) {
                type = roll < houses * 0.82f || Math.min(wid, dep) < 8 ? LotType.HOUSE : LotType.ROUND_HOUSE;
            } else {
                type = roll < houses + 0.2f ? LotType.GARDEN : ring == 2 ? LotType.YARD : LotType.PARK;
            }
            addLot(type, x1, z1, x2, z2, facing == null ? Direction.SOUTH : facing, floors(type, ring, district), style(type, district, ring),
                    colourFor(district, rnd.nextInt(4), null));
        }
    }

    /**
     * Fills what's left: every free patch of 3×3 or more becomes a yard (kitchen garden, laundry,
     * woodpile, shed, koi pond, bamboo, roadside shrine, well; fields in the outer ring), and the
     * 1–2-block slivers between buildings become gravel alleys. No idle lawns.
     */
    private void yards() {
        for (int x = -limit; x <= limit; x++) {
            for (int z = -limit; z <= limit; z++) {
                if (!freeCell(x, z)) {
                    continue;
                }
                int ring = ring(x, z);
                int max = ring == 0 ? 6 : ring == 1 ? 8 : 12;
                int widest = x;
                while (widest + 1 - x < max && freeCell(widest + 1, z)) {
                    widest++;
                }
                // The widest patch from here that's at least 3 deep.
                int x2 = -1, z2 = -1;
                for (int right = widest; right >= x + 2 && x2 < 0; right--) {
                    int bottom = z;
                    while (bottom + 1 - z < max && rowFree(x, right, bottom + 1)) {
                        bottom++;
                    }
                    if (bottom - z >= 2) {
                        x2 = right;
                        z2 = bottom;
                    }
                }
                if (x2 >= 0) {
                    Direction facing = towardsStreet(x, z, x2, z2, 6);
                    addLot(LotType.YARD, x, z, x2, z2, facing == null ? Direction.SOUTH : facing, 0, yardStyle(ring),
                            colourFor(plan.district(x, z), rnd.nextInt(4), null));
                } else {
                    road(x, z, Paving.PATH); // a sliver between buildings: a gravel alley
                }
            }
        }
    }

    private boolean freeCell(int x, int z) {
        int i = plan.cell(x, z);
        return i >= 0 && occ[i] == FREE && within(x, z);
    }

    private boolean rowFree(int x1, int x2, int z) {
        for (int x = x1; x <= x2; x++) {
            if (!freeCell(x, z)) {
                return false;
            }
        }
        return true;
    }

    /** The side of a box that looks onto the nearest street within {@code reach} blocks, or null. */
    private Direction towardsStreet(int x1, int z1, int x2, int z2, int reach) {
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        Direction best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int x = side.getStepX() > 0 ? x2 : side.getStepX() < 0 ? x1 : cx;
            int z = side.getStepZ() > 0 ? z2 : side.getStepZ() < 0 ? z1 : cz;
            for (int k = 1; k <= reach && k < bestDistance; k++) {
                if (plan.isRoad(x + side.getStepX() * k, z + side.getStepZ() * k)) {
                    bestDistance = k;
                    best = side;
                    break;
                }
            }
        }
        return best;
    }

    // ================================================================ colourways

    /**
     * A colourway for a lot: the street block's wall plaster, a roof picked with the district's taste
     * (warm reds and oranges in the shinobi quarter, dark tiles in the clan district), re-rolled so
     * it differs from the neighbour's roof.
     */
    private int colourFor(District district, int wall, Lot neighbour) {
        int trim = rnd.nextInt(3);
        int roof = roofRow(district);
        int index = wall + 4 * (roof + 20 * trim);
        if (neighbour != null) {
            Object roofBlock = palette.colourway(neighbour.colour()).roofFlat();
            for (int k = 0; k < 4 && palette.colourway(index).roofFlat() == roofBlock; k++) {
                roof = roofRow(district);
                index = wall + 4 * (roof + 20 * trim);
            }
            for (int k = 1; k < 20 && palette.colourway(index).roofFlat() == roofBlock; k++) {
                index = wall + 4 * ((roof + k) % 20 + 20 * trim);
            }
        }
        return index;
    }

    /** A row of the palette's 20-row roof table, leaning the way the district likes. */
    private int roofRow(District district) {
        int[] rows = switch (palette) {
            case LEAF -> switch (district) {
                case SHINOBI -> new int[] {0, 1, 2, 3, 4, 5, 12, 13, 19, 19, 19};
                case CLAN -> new int[] {12, 13, 14, 15, 18, 19};
                case TEMPLE -> new int[] {19, 19, 18, 12, 13, 0, 1};
                default -> null;
            };
            case SAND -> switch (district) {
                case SHINOBI, TEMPLE -> new int[] {16, 17, 18, 19, 9, 10};
                case CLAN -> new int[] {9, 10, 11, 12, 13, 14, 15};
                default -> null;
            };
            case SNOW -> switch (district) {
                case SHINOBI, TEMPLE -> new int[] {8, 9, 10, 11, 12, 15, 16};
                case CLAN -> new int[] {0, 1, 2, 3, 4, 5, 6, 7};
                default -> null;
            };
        };
        return rows == null ? rnd.nextInt(20) : rows[rnd.nextInt(rows.length)];
    }

    // ================================================================ street furniture

    private void prop(PropType type, int x, int z, Direction facing, int style) {
        if (propCells.add(((long) x << 32) ^ (z & 0xFFFFFFFFL))) {
            plan.props.add(new Prop(type, x, z, facing, style));
        }
    }

    private boolean hasProp(int x, int z) {
        return propCells.contains(((long) x << 32) ^ (z & 0xFFFFFFFFL));
    }

    /**
     * The outermost street block beside sample i on {@code side}, if it's a plain stretch of this
     * street (not a junction, a bridge or near the plaza) with nothing on it yet.
     */
    private int[] edgeCell(Line line, int i, int side) {
        double nx = -line.tz(i) * side, nz = line.tx(i) * side;
        int x = (int) Math.round(line.x(i) + nx * (line.hw - 0.5)), z = (int) Math.round(line.z(i) + nz * (line.hw - 0.5));
        if (plan.paving(x, z) != line.paving || plan.isWater(x, z) || hasProp(x, z) || !within(x, z)) {
            return null;
        }
        if (plan.kind == VillageKind.CAPITAL && Math.hypot(x - px, z - pz) < PLAZA + 6) {
            return null;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Paving next = plan.paving(x + d.getStepX(), z + d.getStepZ());
            if (next != null && next != line.paving || plan.isWater(x + d.getStepX(), z + d.getStepZ())) {
                return null;
            }
        }
        return new int[] {x, z};
    }

    /** Street lamps down both sides of every avenue (they light up at dusk), with benches by some. */
    private void lampPosts() {
        for (Line line : lines) {
            if (line.paving != Paving.AVENUE) {
                continue;
            }
            int k = 0;
            for (int i = 4; i < line.size() - 3; i += 7, k++) {
                int side = (k & 1) == 0 ? 1 : -1;
                int[] edge = edgeCell(line, i, side);
                if (edge == null) {
                    continue;
                }
                Direction inward = cardinal(line.tz(i) * side, -line.tx(i) * side);
                prop(PropType.LAMP_POST, edge[0], edge[1], inward, 0);
                if (k % 3 == 1) {
                    int[] seat = edgeCell(line, Math.min(line.size() - 1, i + 2), side);
                    if (seat != null) {
                        prop(PropType.BENCH, seat[0], seat[1], inward, 0);
                    }
                }
            }
        }
    }

    /** Stone lanterns along streets of one paving (a town's cross street). */
    private void lanterns(Paving paving, int every, boolean festival) {
        for (Line line : lines) {
            if (line.paving != paving || line.festival != festival) {
                continue;
            }
            for (int i = every / 2; i < line.size(); i += every) {
                for (int side = -1; side <= 1; side += 2) {
                    int[] edge = edgeCell(line, i, side);
                    if (edge != null && Math.hypot(edge[0] - px, edge[1] - pz) > 9) {
                        prop(PropType.LANTERN, edge[0], edge[1], Direction.SOUTH, 0);
                    }
                }
            }
        }
    }

    /**
     * Wooden utility poles along the streets (not near the plaza, so the rock stays in view), every
     * 12 blocks on the outer side, with two wires between them that sag in the middle; every third
     * pole carries a transformer, every second a lamp, and service drops run down to the houses.
     */
    private void poles(int clear) {
        int height = VillageBuilder.POLE_HEIGHT;
        for (Line line : lines) {
            if (line.paving != Paving.STREET || line.festival) {
                continue;
            }
            int mid = line.size() / 2;
            double ox = line.x(mid) - px, oz = line.z(mid) - pz;
            int side = -line.tz(mid) * ox + line.tx(mid) * oz >= 0 ? 1 : -1;
            int[] last = null;
            int k = 0;
            for (int at = 6; at < line.size() - 3; at += 12) {
                // The planned spot, or the next free one a few blocks on (a gateway, a bench).
                int[] edge = null;
                int i = at;
                for (int t = 0; t < 4 && edge == null && at + t < line.size() - 3; t++) {
                    i = at + t;
                    edge = edgeCell(line, i, side);
                }
                if (edge == null || Math.hypot(edge[0] - px, edge[1] - pz) < clear) {
                    last = null;
                    continue;
                }
                int x = edge[0], z = edge[1];
                Direction facing = cardinal(line.tx(i), line.tz(i));
                Direction inward = cardinal(line.tz(i) * side, -line.tx(i) * side);
                boolean ccw = inward == facing.getCounterClockWise();
                int flags = (k % 3 == 0 ? 1 : 0) | (k % 2 == 0 ? 2 : 0) | (ccw ? 4 : 0);
                prop(PropType.POLE, x, z, facing, flags);
                Direction across = facing.getClockWise();
                int[] here = {x + across.getStepX(), z + across.getStepZ(), x - across.getStepX(), z - across.getStepZ()};
                if (last != null && Math.hypot(last[0] - here[0], last[1] - here[1]) <= 18) {
                    plan.wires.add(new Wire(last[0], last[1], here[0], here[1], height));
                    plan.wires.add(new Wire(last[2], last[3], here[2], here[3], height - 1));
                }
                last = here;
                if (k % 2 == 1) {
                    serviceDrop(x, z, here, height);
                }
                k++;
            }
        }
    }

    /** A wire from a pole's crossarm down to the nearest house wall within 8 blocks. */
    private void serviceDrop(int x, int z, int[] arms, int height) {
        Lot best = null;
        int bestDistance = 9;
        for (Lot lot : plan.lots) {
            boolean building = switch (lot.type()) {
                case HOUSE, SHOP, RESTAURANT, APARTMENT, ROUND_HOUSE, WAREHOUSE, INN -> true;
                default -> false;
            };
            if (!building) {
                continue;
            }
            int d = apart(lot, new int[] {x, z, x, z});
            if (d < bestDistance) {
                bestDistance = d;
                best = lot;
            }
        }
        if (best == null) {
            return;
        }
        boolean first = Math.hypot(arms[0] - best.cx(), arms[1] - best.cz()) <= Math.hypot(arms[2] - best.cx(), arms[3] - best.cz());
        int ax = first ? arms[0] : arms[2], az = first ? arms[1] : arms[3];
        int y = first ? height : height - 1;
        int y2 = Math.max(2, Math.min(y - 1, best.floors() * STOREY - 2));
        plan.wires.add(new Wire(ax, az, best.cx(), best.cz(), y, y2));
    }

    /** Lanterns strung across festival streets every 6 blocks, and an arched gate at each end. */
    private void festivalProps() {
        for (Line line : lines) {
            if (!line.festival) {
                continue;
            }
            int n = line.size();
            int reach = (int) Math.floor(line.hw);
            // The street's ends inside the village (a town's main street runs on out of the gates).
            int first = 3, last = n - 4;
            while (first < n && !VillageBuilder.inDisk((int) Math.round(line.x(first)), (int) Math.round(line.z(first)), limit - 4)) {
                first++;
            }
            while (last > first && !VillageBuilder.inDisk((int) Math.round(line.x(last)), (int) Math.round(line.z(last)), limit - 4)) {
                last--;
            }
            if (last - first < 12) {
                continue;
            }
            for (int i = first + 4; i < last - 3; i += 6) {
                int x = (int) Math.round(line.x(i)), z = (int) Math.round(line.z(i));
                if (plan.isWater(x, z) || plan.paving(x, z) != line.paving || hasProp(x, z)) {
                    continue;
                }
                prop(PropType.LANTERN_LINE, x, z, cardinal(line.tx(i), line.tz(i)).getClockWise(), reach);
            }
            for (int i : new int[] {first, last}) {
                int x = (int) Math.round(line.x(i)), z = (int) Math.round(line.z(i));
                if (!plan.isWater(x, z)) {
                    prop(PropType.STREET_GATE, x, z, cardinal(line.tx(i), line.tz(i)), reach);
                }
            }
        }
    }

    /** Benches and notice boards on the Kage plaza. */
    private void plazaProps() {
        plazaBenches(px, pz, PLAZA - 3);
        Lot tower = plan.first(LotType.KAGE_TOWER);
        int z = tower != null ? tower.z2() + 5 : pz + PLAZA - 6;
        for (int s = -1; s <= 1; s += 2) {
            int x = px + s * 11;
            if (plan.paving(x, z) == Paving.PLAZA) {
                prop(PropType.NOTICE_BOARD, x, z, Direction.SOUTH, 0);
            }
        }
    }

    private void plazaBenches(int cx, int cz, int r) {
        Lot tower = plan.first(LotType.KAGE_TOWER);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int x = cx + (int) Math.round(Math.sin(a) * r);
            int z = cz + (int) Math.round(Math.cos(a) * r);
            if (Math.abs(x - cx) <= 5 || plan.paving(x, z) != Paving.PLAZA || tower != null && tower.overlaps(x, z, x, z, 1)) {
                continue;
            }
            prop(PropType.BENCH, x, z, Math.abs(x - cx) > Math.abs(z - cz) ? (x > cx ? Direction.WEST : Direction.EAST)
                    : (z > cz ? Direction.NORTH : Direction.SOUTH), 0);
        }
    }

    /** A notice board just inside every gate. */
    private void gateBoards() {
        for (int[] gate : plan.gates) {
            double len = Math.max(1, Math.hypot(gate[0], gate[1]));
            double ix = -gate[0] / len, iz = -gate[1] / len;
            for (int s = -1; s <= 1; s += 2) {
                int x = (int) Math.round(gate[0] + ix * 16 - iz * s * 3), z = (int) Math.round(gate[1] + iz * 16 + ix * s * 3);
                if (plan.paving(x, z) == Paving.AVENUE && !hasProp(x, z)) {
                    prop(PropType.NOTICE_BOARD, x, z, cardinal(gate[0] + ix * 16 - x, gate[1] + iz * 16 - z), 0);
                    break;
                }
            }
        }
    }

    /** Barrels in front of some shops and flower pots in front of some homes. */
    private void frontProps(int maxBarrels, int maxFlowers) {
        int barrels = 0, flowers = 0;
        for (Lot lot : plan.lots) {
            boolean shop = switch (lot.type()) {
                case SHOP, RESTAURANT, STALL, RAMEN_SHOP -> true;
                default -> false;
            };
            boolean home = lot.type() == LotType.HOUSE;
            if (!shop && !home) {
                continue;
            }
            float roll = rnd.nextFloat();
            int style = rnd.nextInt(6);
            boolean wantBarrels = shop && barrels < maxBarrels && roll < 0.3f;
            boolean wantFlowers = home && flowers < maxFlowers && roll < 0.08f;
            if (!wantBarrels && !wantFlowers) {
                continue;
            }
            Direction f = lot.facing();
            // Just outside the front, at a corner.
            int x = f.getStepX() > 0 ? lot.x2() + 1 : f.getStepX() < 0 ? lot.x1() - 1 : lot.x1();
            int z = f.getStepZ() > 0 ? lot.z2() + 1 : f.getStepZ() < 0 ? lot.z1() - 1 : lot.z1();
            if (!plan.isRoad(x, z) || plan.isWater(x, z) || hasProp(x, z) || Math.hypot(x - px, z - pz) < PLAZA + 2) {
                continue;
            }
            prop(wantBarrels ? PropType.BARRELS : PropType.FLOWERS, x, z, f, style);
            if (wantBarrels) {
                barrels++;
            } else {
                flowers++;
            }
        }
    }

    // ================================================================ people

    private void capitalPeople(boolean fan) {
        int h = VillageBuilder.TOWER_HEIGHT;
        add(new Spawn(Role.KAGE, px, h + 1, pz, px, h + 1, pz, 5, px, pz, 0, List.of()));
        // The residence's door guards, then two guards just inside every gate.
        Lot tower = plan.first(LotType.KAGE_TOWER);
        int front = tower != null ? tower.z2() + 3 : pz + PLAZA - 6;
        add(Spawn.at(Role.GUARD, px - 4, 0, front, 4));
        add(Spawn.at(Role.GUARD, px + 4, 0, front, 4));
        int inward = fan ? 6 : 10;
        for (int[] gate : plan.gates) {
            double len = Math.max(1, Math.hypot(gate[0], gate[1]));
            double ix = -gate[0] / len, iz = -gate[1] / len;
            double across = gate[2] / 2 + 1;
            for (int s = -1; s <= 1; s += 2) {
                int x = (int) Math.round(gate[0] + ix * inward - iz * s * across);
                int z = (int) Math.round(gate[1] + iz * inward + ix * s * across);
                add(Spawn.at(Role.GUARD, x, 0, z, 5));
            }
        }
        Lot post = plan.first(LotType.GUARD_POST);
        if (post != null) {
            add(Spawn.at(Role.GUARD, post.cx(), 0, post.cz(), 4));
        }
        patrols(6, fan);
        runners(8);
        for (Lot ground : lots(LotType.TRAINING_GROUND)) {
            for (int i = 0; i < 3; i++) {
                add(Spawn.at(Role.TRAINEE, ground.cx() - 4 + i * 4, 0, ground.cz() + 2, 8));
            }
            add(Spawn.at(Role.DUMMY, ground.cx() + 1, 0, ground.cz() - 4, 0));
            add(Spawn.at(Role.DUMMY, ground.cx() - 3, 0, ground.cz() - 4, 0));
        }
        Lot compound = plan.first(LotType.CLAN_COMPOUND);
        if (compound != null) {
            for (int i = 0; i < 5; i++) {
                add(Spawn.at(Role.CLAN_NINJA, compound.x1() + 6 + rnd.nextInt(compound.width() - 12), 0,
                        compound.z1() + 6 + rnd.nextInt(compound.depth() - 12), 14));
            }
        }
        Lot academy = plan.first(LotType.ACADEMY);
        int[] school = academy != null ? front(academy) : new int[] {px, pz + PLAZA};
        if (academy != null) {
            int[] in = inside(academy, 4);
            add(Spawn.at(Role.TEACHER, in[0], 0, in[1], 10));
            add(Spawn.at(Role.TEACHER, school[0], 0, school[1], 8));
        }
        shopkeepers(28);
        townsfolk(36, 10, school[0], school[1], 4);
        animals(8, 4, 4, CAPITAL_RESIDENTS);
    }

    /** Patrols walking stretches of the wall walk (the ring road in a cliff-walled Sand village), away from the gates and the rock. */
    private void patrols(int count, boolean wallWalk) {
        int wallTop = wallWalk ? VillageBuilder.WALL_HEIGHT : 0;
        double walkR = wallWalk ? plan.kind.wall - 1 : limit + 2.5;
        for (int i = 0; i < count; i++) {
            double centre = Math.toRadians(30 + i * 360.0 / count);
            List<int[]> route = new ArrayList<>();
            for (int k = -5; k <= 5; k++) {
                double a = centre + Math.toRadians(k * 4);
                int x = (int) Math.round(Math.sin(a) * walkR);
                int z = (int) Math.round(Math.cos(a) * walkR);
                boolean rock = palette.hasKageRock() && z <= plan.rockZ + 4 && Math.abs(x) <= plan.rockHalf;
                boolean gate = plan.gates.stream().anyMatch(g -> Math.hypot(g[0] - x, g[1] - z) < 16);
                if (!rock && !gate) {
                    route.add(new int[] {x, wallTop, z});
                }
            }
            if (route.size() >= 3) {
                int[] first = route.get(0);
                add(new Spawn(Role.PATROL, first[0], first[1], first[2], first[0], first[1], first[2], 40, first[0], first[2], 0, route));
            }
        }
    }

    /** Ninjas who travel the village across its roofs: each a route hopping between nearby flat roofs it hasn't visited. */
    private void runners(int count) {
        List<Lot> roofs = new ArrayList<>(plan.lots.stream().filter(l -> l.roofTop() > 0).toList());
        for (int i = 0; i < count && !roofs.isEmpty(); i++) {
            Lot here = roofs.remove(rnd.nextInt(roofs.size()));
            List<int[]> route = new ArrayList<>();
            route.add(new int[] {here.cx(), here.roofTop(), here.cz()});
            Set<Lot> visited = new HashSet<>();
            visited.add(here);
            for (int step = 0; step < 12; step++) {
                Lot next = null;
                double best = Double.MAX_VALUE;
                for (Lot other : roofs) {
                    if (visited.contains(other)) {
                        continue;
                    }
                    double d = Math.hypot(other.cx() - here.cx(), other.cz() - here.cz());
                    int climb = other.roofTop() - here.roofTop();
                    if (d > 4 && d < 21 && climb < 9 && d < best) {
                        best = d;
                        next = other;
                    }
                }
                if (next == null) {
                    break;
                }
                route.add(new int[] {next.cx(), next.roofTop(), next.cz()});
                visited.add(next);
                here = next;
            }
            if (route.size() >= 3) {
                int[] first = route.get(0);
                add(new Spawn(Role.RUNNER, first[0], first[1], first[2], first[0], first[1], first[2], 60, first[0], first[2], 0, route));
            } else {
                i--; // try another roof
                if (roofs.isEmpty()) {
                    break;
                }
            }
        }
    }

    /** A merchant in up to {@code max} shops, restaurants and stalls, spread over the village (market and lantern street first). */
    private void shopkeepers(int max) {
        List<Lot> shops = new ArrayList<>();
        for (Lot lot : plan.lots) {
            switch (lot.type()) {
                case RAMEN_SHOP, RESTAURANT, STALL, SHOP, INN -> shops.add(lot);
                default -> {
                }
            }
        }
        for (int i = shops.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Lot t = shops.get(i);
            shops.set(i, shops.get(j));
            shops.set(j, t);
        }
        shops.sort(Comparator.comparingInt(l -> l.type() == LotType.RAMEN_SHOP || l.type() == LotType.INN ? 0
                : plan.district(l.cx(), l.cz()) == District.MARKET || plan.district(l.cx(), l.cz()) == District.LANTERN ? 1 : 2));
        int n = 0;
        for (Lot lot : shops) {
            if (n >= max) {
                break;
            }
            int shop = switch (lot.type()) {
                case RESTAURANT -> 1 + (lot.style() % 2) * 4;   // ramen or a teahouse
                case STALL -> lot.style() % 2 == 0 ? 1 : 5;
                case SHOP -> 2 + lot.style() % 3;              // weapons, scrolls, flowers
                case INN -> 5;
                default -> 1;                                  // the ramen shop
            };
            int[] in = inside(lot, 2);
            add(new Spawn(Role.MERCHANT, in[0], 0, in[1], in[0], 0, in[1], 3, in[0], in[1], shop, List.of()));
            n++;
        }
    }

    /**
     * Ordinary people: each lives in a house (they sleep there at night) and spends the day out in
     * the village, mostly at the market; some are children who spend the day at
     * {@code (schoolX, schoolZ)}.
     */
    private void townsfolk(int adults, int children, int schoolX, int schoolZ, int ninjas) {
        List<Lot> homes = new ArrayList<>(plan.lots.stream().filter(l -> l.type() == LotType.HOUSE || l.type() == LotType.ROUND_HOUSE
                || l.type() == LotType.APARTMENT).toList());
        List<Lot> outings = plan.lots.stream().filter(l -> l.type() == LotType.SHOP || l.type() == LotType.RESTAURANT
                || l.type() == LotType.PARK || l.type() == LotType.STALL || l.type() == LotType.RAMEN_SHOP).toList();
        List<Lot> market = outings.stream().filter(l -> {
            District w = plan.district(l.cx(), l.cz());
            return w == District.MARKET || w == District.LANTERN;
        }).toList();
        for (int i = 0; i < adults + children + ninjas && !homes.isEmpty(); i++) {
            Lot home = homes.remove(rnd.nextInt(homes.size()));
            int[] bed = inside(home, 0);
            Role role = i < children ? Role.CHILD : i < children + ninjas ? Role.NINJA : Role.CIVILIAN;
            int workX, workZ;
            if (role == Role.CHILD) {
                workX = schoolX;
                workZ = schoolZ;
            } else if (!outings.isEmpty()) {
                List<Lot> from = !market.isEmpty() && rnd.nextFloat() < 0.7f ? market : outings;
                int[] out = front(from.get(rnd.nextInt(from.size())));
                workX = out[0];
                workZ = out[1];
            } else {
                workX = px;
                workZ = pz;
            }
            add(new Spawn(role, bed[0], 0, bed[1], bed[0], 0, bed[1], 2, workX, workZ, 0, List.of()));
        }
    }

    /**
     * Village animals within the resident budget: cats on porches (one per ~8 houses), chickens in
     * kitchen-garden yards, koi in the clan pond and the koi-pond yards.
     */
    private void animals(int maxCats, int maxChickens, int maxKoi, int budget) {
        List<Lot> houses = lots(LotType.HOUSE);
        int cats = Math.min(maxCats, Math.max(1, houses.size() / 8));
        for (int i = 0; i < cats && plan.spawns.size() < budget && !houses.isEmpty(); i++) {
            Lot home = houses.get((int) ((long) i * houses.size() / cats + rnd.nextInt(Math.max(1, houses.size() / cats))) % houses.size());
            Direction f = home.facing();
            int x = f.getStepX() > 0 ? home.x2() : f.getStepX() < 0 ? home.x1() : home.cx() + 1;
            int z = f.getStepZ() > 0 ? home.z2() : f.getStepZ() < 0 ? home.z1() : home.cz() + 1;
            add(Spawn.at(Role.CAT, x, 0, z, 4));
        }
        int chickens = 0;
        for (Lot yard : lots(LotType.YARD)) {
            if (chickens >= maxChickens || plan.spawns.size() >= budget) {
                break;
            }
            if (yard.style() != 0 || ring(yard.cx(), yard.cz()) == 2 && plan.kind == VillageKind.CAPITAL) {
                continue;
            }
            int hens = Math.min(2 + rnd.nextInt(3), maxChickens - chickens);
            for (int k = 0; k < hens && plan.spawns.size() < budget; k++) {
                add(Spawn.at(Role.CHICKEN, yard.cx() + (k % 2), 0, yard.cz() + k / 2, 3));
                chickens++;
            }
        }
        int koi = 0;
        Lot compound = plan.first(LotType.CLAN_COMPOUND);
        if (compound != null && maxKoi > 0) {
            // The compound's pond: frame (w/2, d/2 + 1).
            int[] pond = framePoint(compound, (frameW(compound) - 1) / 2, frameD(compound) / 2 + 1);
            for (int k = 0; k < 2 && koi < maxKoi && plan.spawns.size() < budget; k++, koi++) {
                add(Spawn.at(Role.KOI, pond[0] + k, -1, pond[1], 3));
            }
        }
        for (Lot yard : lots(LotType.YARD)) {
            if (koi >= maxKoi || plan.spawns.size() >= budget) {
                break;
            }
            if (yard.style() == 4) {
                for (int k = 0; k < 2 && koi < maxKoi && plan.spawns.size() < budget; k++, koi++) {
                    add(Spawn.at(Role.KOI, yard.cx(), -1, yard.cz(), 2));
                }
            }
        }
    }

    private static int frameW(Lot lot) {
        return lot.facing().getAxis() == Direction.Axis.X ? lot.depth() : lot.width();
    }

    private static int frameD(Lot lot) {
        return lot.facing().getAxis() == Direction.Axis.X ? lot.width() : lot.depth();
    }

    /** The village position of a lot's frame point (u across the front, v from the back), as {@link LotFrame} maps it. */
    private static int[] framePoint(Lot lot, int u, int v) {
        return switch (lot.facing()) {
            case NORTH -> new int[] {lot.x2() - u, lot.z2() - v};
            case WEST -> new int[] {lot.x2() - v, lot.z1() + u};
            case EAST -> new int[] {lot.x1() + v, lot.z2() - u};
            default -> new int[] {lot.x1() + u, lot.z1() + v};
        };
    }

    private List<Lot> lots(LotType type) {
        return plan.lots.stream().filter(l -> l.type() == type).toList();
    }

    /** A spot inside a lot's ground floor, {@code back} blocks behind its middle. */
    static int[] inside(Lot lot, int back) {
        Direction f = lot.facing();
        return new int[] {lot.cx() - f.getStepX() * back, lot.cz() - f.getStepZ() * back};
    }

    /** The street just outside a lot's front door. */
    static int[] front(Lot lot) {
        Direction f = lot.facing();
        int x = f.getStepX() > 0 ? lot.x2() + 2 : f.getStepX() < 0 ? lot.x1() - 2 : lot.cx();
        int z = f.getStepZ() > 0 ? lot.z2() + 2 : f.getStepZ() < 0 ? lot.z1() - 2 : lot.cz();
        return new int[] {x, z};
    }

    private void add(Spawn spawn) {
        plan.spawns.add(spawn);
    }

    // ================================================================ the occupancy grid

    /** The horizontal side closest to the direction (dx, dz). */
    static Direction cardinal(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private void shuffle(int[] values) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = values[i];
            values[i] = values[j];
            values[j] = t;
        }
    }

    /** Whether the box is all free ground inside the lot limit. */
    private boolean free(int x1, int z1, int x2, int z2) {
        return scan(x1, z1, x2, z2) == 0;
    }

    /** 0 = all free, 1 = street in the way (nothing else), 2 = something else in the way (or outside the limit). */
    private int scan(int x1, int z1, int x2, int z2) {
        if (!within(x1, z1) || !within(x1, z2) || !within(x2, z1) || !within(x2, z2)) {
            return 2;
        }
        boolean road = false;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                byte o = occ[plan.cell(x, z)];
                if (o == ROAD) {
                    road = true;
                } else if (o != FREE) {
                    return 2;
                }
            }
        }
        return road ? 1 : 0;
    }

    private boolean hasLot(int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int i = plan.cell(x, z);
                if (i >= 0 && occ[i] == LOT) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean within(int x, int z) {
        return x * x + z * z <= limit * limit;
    }

    private void mark(int x1, int z1, int x2, int z2, byte what) {
        for (int x = Math.max(-half, x1); x <= Math.min(half, x2); x++) {
            for (int z = Math.max(-half, z1); z <= Math.min(half, z2); z++) {
                occ[plan.cell(x, z)] = what;
            }
        }
    }

    /** Height of a building's walls (for things that need to know, like spawns on roofs). */
    static int wallHeight(int floors) {
        return floors * STOREY;
    }
}

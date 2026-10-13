package com.noirm.world.village;

import com.noirm.world.village.VillagePlan.Lot;
import com.noirm.world.village.VillagePlan.LotType;
import com.noirm.world.village.VillagePlan.Paving;
import com.noirm.world.village.VillagePlan.Prop;
import com.noirm.world.village.VillagePlan.PropType;
import com.noirm.world.village.VillagePlan.Road;
import com.noirm.world.village.VillagePlan.Role;
import com.noirm.world.village.VillagePlan.Spawn;
import com.noirm.world.village.VillagePlan.Wire;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

import static com.noirm.world.village.VillagePlan.STOREY;

/**
 * Lays out a village: streets first, then the landmarks, then house plots packed along every
 * street (the way real towns grow), then whatever space is left becomes back-street houses,
 * gardens and parks. Finally the street furniture and the people.
 *
 * <p>Everything comes from one {@link RandomSource} seeded with the village seed, so the same
 * seed always gives the same plan. An occupancy grid keeps lots from overlapping each other or the
 * streets.
 */
final class VillagePlanner {
    // ---- capital layout (local coordinates; gate to the south, +z)
    static final int TOWER_Z = -66;
    static final int PLAZA = 26;
    static final int CROSS_Z = 14;
    /** North of this line (and within {@link #ROCK_HALF} of the middle) stands the Kage Rock. */
    static final int ROCK_Z = -97;
    static final int ROCK_HALF = 92;

    private final VillagePlan plan;
    private final RandomSource rnd;
    private final int size;
    private final int half;
    private final boolean[] used;
    /** Lots must fit inside this radius. */
    private final int limit;

    private VillagePlanner(VillagePlan plan, int limit) {
        this.plan = plan;
        this.rnd = RandomSource.create(plan.seed);
        this.half = plan.kind.radius;
        this.size = half * 2 + 1;
        this.used = new boolean[size * size];
        this.limit = limit;
    }

    static VillagePlan plan(VillageKind kind, VillagePalette palette, long seed) {
        VillagePlan plan = new VillagePlan(kind, palette, seed);
        switch (kind) {
            case CAPITAL -> new VillagePlanner(plan, kind.wall - 8).capital();
            case TOWN -> new VillagePlanner(plan, kind.wall - 4).town();
            default -> new VillagePlanner(plan, kind.wall - 1).outpost();
        }
        return plan;
    }

    // ================================================================ the hidden village

    private void capital() {
        int w = plan.kind.wall;
        boolean rock = plan.palette.hasKageRock();
        // ---- streets
        street(Road.ring(Paving.STREET, 0, 0, w - 8, w - 4));        // the ring road inside the wall
        street(Road.ring(Paving.PLAZA, 0, TOWER_Z, -1, PLAZA));       // the plaza round the Kage tower
        street(Road.rect(Paving.AVENUE, -4, TOWER_Z + PLAZA - 2, 4, w + 4));    // gate → tower
        street(Road.rect(Paving.AVENUE, -w, CROSS_Z - 3, w, CROSS_Z + 3));       // east gate ↔ west gate
        for (int x : new int[] {-82, -40, 40, 82}) {
            street(Road.rect(Paving.STREET, x - 2, -w, x + 2, w));
        }
        for (int z : new int[] {-36, 56, 92}) {
            street(Road.rect(Paving.STREET, -w, z - 2, w, z + 2));
        }
        street(Road.rect(Paving.PATH, -2, rock ? ROCK_Z - 2 : -w, 2, TOWER_Z - PLAZA + 2)); // up to the Kage Rock
        if (rock) {
            reserve(-ROCK_HALF, -half, ROCK_HALF, ROCK_Z);
        }

        // ---- landmarks
        landmark(LotType.KAGE_TOWER, -15, TOWER_Z - 15, 15, TOWER_Z + 15, Direction.SOUTH, 4, 0);
        landmark(LotType.ACADEMY, -78, -32, -44, 8, Direction.EAST, 2, 0);
        landmark(LotType.HOSPITAL, 44, -32, 78, 8, Direction.WEST, 3, 0);
        landmark(LotType.CLAN_COMPOUND, 44, -80, 78, -40, Direction.SOUTH, 1, rnd.nextInt(2));
        landmark(LotType.SHRINE, -78, -80, -44, -40, Direction.SOUTH, 1, 0);
        landmark(LotType.TRAINING_GROUND, -108, -30, -86, 8, Direction.EAST, 0, 0);
        landmark(LotType.TRAINING_GROUND, -78, 20, -44, 52, Direction.EAST, 0, 1); // with a lake
        landmark(LotType.ONSEN, 44, 60, 72, 86, Direction.WEST, 1, 0);
        landmark(LotType.RAMEN_SHOP, 6, CROSS_Z + 5, 15, CROSS_Z + 13, Direction.WEST, 1, 0);
        landmark(LotType.GUARD_POST, 7, w - 20, 15, w - 13, Direction.WEST, 1, 0);
        plan.compoundClan = plan.first(LotType.CLAN_COMPOUND).style() == 0 ? "uchiha" : "hyuga";

        // ---- the houses
        packStreets(3);
        fillGaps(9, 0.55f);
        fillGaps(7, 0.6f); // smaller back-street plots in what's left
        // ---- street furniture
        lanternsAlong(-4, TOWER_Z + PLAZA, 4, w - 6, 10);
        lanternsAlong(-w + 10, CROSS_Z - 3, w - 10, CROSS_Z + 3, 12);
        powerLines(4);
        plazaBenches(0, TOWER_Z, PLAZA - 3);
        streetTrees(CROSS_Z);

        // ---- the people
        capitalPeople(w);
    }

    private void capitalPeople(int w) {
        int h = VillageBuilder.TOWER_HEIGHT;
        add(new Spawn(Role.KAGE, 0, h + 1, TOWER_Z, 0, h + 1, TOWER_Z, 5, 0, TOWER_Z, 0, List.of()));
        // Gate guards, the guard post and the tower door.
        add(Spawn.at(Role.GUARD, -7, 0, w - 6, 5));
        add(Spawn.at(Role.GUARD, 7, 0, w - 6, 5));
        add(Spawn.at(Role.GUARD, 11, 0, w - 16, 4));
        add(Spawn.at(Role.GUARD, -3, 0, TOWER_Z + VillageBuilder.TOWER_RADIUS + 3, 4));
        add(Spawn.at(Role.GUARD, 3, 0, TOWER_Z + VillageBuilder.TOWER_RADIUS + 3, 4));
        // Patrols on the wall walk (or along the ring road for a cliff-walled Sand village).
        int wallTop = plan.palette != VillagePalette.SAND ? VillageBuilder.WALL_HEIGHT : 0;
        int walkR = wallTop > 0 ? w - 1 : w - 6;
        for (int i = 0; i < 4; i++) {
            List<int[]> route = new ArrayList<>();
            double start = i * Math.PI / 2 + Math.PI / 4;
            for (int k = 0; k < 8; k++) {
                double a = start + k * Math.PI / 16;
                int x = (int) Math.round(Math.sin(a) * walkR);
                int z = (int) Math.round(Math.cos(a) * walkR);
                if (!plan.palette.hasKageRock() || z > ROCK_Z + 4) {
                    route.add(new int[] {x, wallTop, z});
                }
            }
            if (!route.isEmpty()) {
                int[] first = route.get(0);
                add(new Spawn(Role.PATROL, first[0], first[1], first[2], first[0], first[1], first[2], 40, first[0], first[2], 0, route));
            }
        }
        // Rooftop runners: ninjas who travel across the roofs.
        runners(6);
        // The training grounds.
        for (Lot ground : lots(LotType.TRAINING_GROUND)) {
            for (int i = 0; i < 3; i++) {
                add(Spawn.at(Role.TRAINEE, ground.cx() - 4 + i * 4, 0, ground.cz() + 2, 8));
            }
            add(Spawn.at(Role.DUMMY, ground.cx() + 1, 0, ground.z1() + 6, 0));
            add(Spawn.at(Role.DUMMY, ground.cx() - 3, 0, ground.z1() + 6, 0));
        }
        // The clan district.
        Lot compound = plan.first(LotType.CLAN_COMPOUND);
        for (int i = 0; i < 5; i++) {
            add(Spawn.at(Role.CLAN_NINJA, compound.x1() + 6 + rnd.nextInt(compound.width() - 12), 0,
                    compound.z1() + 6 + rnd.nextInt(compound.depth() - 12), 14));
        }
        // The academy: teachers and pupils (the children go home at night).
        Lot academy = plan.first(LotType.ACADEMY);
        add(Spawn.at(Role.TEACHER, academy.x2() - 8, 0, academy.cz(), 10));
        add(Spawn.at(Role.TEACHER, academy.x2() - 5, 0, academy.z2() - 6, 8));
        shopkeepers(16);
        townsfolk(18, 6, academy.x2() - 6, academy.z2() - 8, 4);
    }

    // ================================================================ a small village

    private void town() {
        int w = plan.kind.wall;
        street(Road.ring(Paving.PLAZA, 0, 0, -1, 8));
        street(Road.rect(Paving.STREET, -3, -w - 3, 3, w + 3));
        street(Road.rect(Paving.STREET, -w - 3, -2, w + 3, 2));
        landmark(LotType.CHIEF_HOUSE, -28, -24, -8, -6, Direction.SOUTH, 2, 0);
        landmark(LotType.INN, 6, 8, 24, 26, Direction.WEST, 2, 0);
        landmark(LotType.SHRINE, 10, -36, 32, -14, Direction.SOUTH, 1, 1);
        landmark(LotType.TRAINING_GROUND, -32, 12, -12, 32, Direction.EAST, 0, 2);
        landmark(LotType.GUARD_POST, 5, 38, 11, 44, Direction.WEST, 1, 0);
        packStreets(1);
        fillGaps(8, 0.4f);
        fillGaps(7, 0.45f);
        lanternsAlong(-3, -w + 4, 3, w - 4, 11);
        powerLines(2);
        plan.props.add(new Prop(PropType.WELL, 0, 0, Direction.SOUTH, 0));
        plazaBenches(0, 0, 6);

        Lot chief = plan.first(LotType.CHIEF_HOUSE);
        add(new Spawn(Role.CHIEF, chief.cx(), 0, chief.cz(), chief.cx(), 0, chief.cz(), 3, 0, 0, 0, List.of()));
        add(Spawn.at(Role.GUARD, 6, 0, w - 2, 4));
        add(Spawn.at(Role.GUARD, -6, 0, -w + 2, 4));
        Lot ground = plan.first(LotType.TRAINING_GROUND);
        add(Spawn.at(Role.TRAINEE, ground.cx(), 0, ground.cz(), 6));
        add(Spawn.at(Role.DUMMY, ground.cx() + 2, 0, ground.z1() + 4, 0));
        add(Spawn.at(Role.NINJA, 0, 0, 10, 30));
        shopkeepers(8);
        townsfolk(10, 3, 0, 0, 1);
    }

    // ================================================================ an outpost

    private void outpost() {
        // The outpost is drawn by hand (palisade, campfire, posts); only its paths, houses and people are planned.
        street(Road.ring(Paving.STREET, 0, 0, -1, 3));
        street(Road.rect(Paving.STREET, -1, 0, 1, plan.kind.wall + 1));
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

    // ================================================================ lots

    private void landmark(LotType type, int x1, int z1, int x2, int z2, Direction facing, int floors, int style) {
        plan.lots.add(new Lot(type, x1, z1, x2, z2, facing, floors, style, rnd.nextLong()));
        mark(x1 - 1, z1 - 1, x2 + 1, z2 + 1);
    }

    private List<Lot> lots(LotType type) {
        return plan.lots.stream().filter(l -> l.type() == type).toList();
    }

    /**
     * Packs house plots along both sides of every straight street, door to the street, a block of
     * garden between neighbours. Plots are 7–12 wide and 8–13 deep (narrow, deep plots, like a real town).
     */
    private void packStreets(int maxExtraFloors) {
        for (Road road : List.copyOf(plan.roads)) {
            if (road.ring() || road.paving() == Paving.PATH) {
                continue;
            }
            boolean alongZ = road.z2() - road.z1() > road.x2() - road.x1();
            int start = alongZ ? road.z1() : road.x1();
            int end = alongZ ? road.z2() : road.x2();
            for (int side = -1; side <= 1; side += 2) {
                int pos = start;
                while (pos < end) {
                    int width = 7 + rnd.nextInt(6);
                    int depth = 8 + rnd.nextInt(6);
                    int gap = rnd.nextInt(2);
                    int x1, z1, x2, z2;
                    Direction facing;
                    if (alongZ) {
                        z1 = pos;
                        z2 = pos + width - 1;
                        if (side < 0) {
                            x2 = road.x1() - 1 - gap;
                            x1 = x2 - depth + 1;
                            facing = Direction.EAST;
                        } else {
                            x1 = road.x2() + 1 + gap;
                            x2 = x1 + depth - 1;
                            facing = Direction.WEST;
                        }
                    } else {
                        x1 = pos;
                        x2 = pos + width - 1;
                        if (side < 0) {
                            z2 = road.z1() - 1 - gap;
                            z1 = z2 - depth + 1;
                            facing = Direction.SOUTH;
                        } else {
                            z1 = road.z2() + 1 + gap;
                            z2 = z1 + depth - 1;
                            facing = Direction.NORTH;
                        }
                    }
                    LotType type = pickType(x1, z1, x2, z2, true);
                    int floors = floorsFor(type, maxExtraFloors);
                    int style = rnd.nextInt(12);
                    long seed = rnd.nextLong();
                    if (free(x1, z1, x2, z2)) {
                        if (type == LotType.STALL) {
                            // A stall only needs the front of the plot.
                            if (alongZ) {
                                if (side < 0) x1 = x2 - 5; else x2 = x1 + 5;
                            } else {
                                if (side < 0) z1 = z2 - 5; else z2 = z1 + 5;
                            }
                        }
                        plan.lots.add(new Lot(type, x1, z1, x2, z2, facing, floors, style, seed));
                        mark(x1, z1, x2, z2);
                        pos += width + 1;
                    } else {
                        pos += 2;
                    }
                }
            }
        }
    }


    /** Fills the space behind the street fronts with back-street houses, gardens and small parks. */
    private void fillGaps(int minSize, float houseChance) {
        List<int[]> spots = new ArrayList<>();
        for (int x = -limit; x <= limit; x += 3) {
            for (int z = -limit; z <= limit; z += 3) {
                spots.add(new int[] {x, z});
            }
        }
        for (int i = spots.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int[] t = spots.get(i);
            spots.set(i, spots.get(j));
            spots.set(j, t);
        }
        for (int[] spot : spots) {
            int width = minSize + rnd.nextInt(5);
            int depth = minSize + rnd.nextInt(5);
            float roll = rnd.nextFloat();
            int style = rnd.nextInt(12);
            long seed = rnd.nextLong();
            int x1 = spot[0];
            int z1 = spot[1];
            int x2 = x1 + width - 1;
            int z2 = z1 + depth - 1;
            if (!free(x1, z1, x2, z2)) {
                continue;
            }
            LotType type = roll < houseChance ? (roll < houseChance * 0.65f ? LotType.HOUSE : LotType.ROUND_HOUSE)
                    : roll < houseChance + 0.25f ? LotType.GARDEN : LotType.PARK;
            Direction facing = towardsStreet(x1, z1, x2, z2);
            plan.lots.add(new Lot(type, x1, z1, x2, z2, facing, type == LotType.HOUSE || type == LotType.ROUND_HOUSE ? 1 + rnd.nextInt(2) : 0, style, seed));
            mark(x1, z1, x2, z2);
        }
    }

    /** What a street-front plot becomes: shops near the market streets, homes elsewhere. */
    private LotType pickType(int x1, int z1, int x2, int z2, boolean front) {
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        int w = x2 - x1 + 1;
        int d = z2 - z1 + 1;
        float r = rnd.nextFloat();
        boolean capital = plan.kind == VillageKind.CAPITAL;
        boolean market = capital ? Math.abs(cx) < 22 && cz > CROSS_Z - 6 || Math.abs(cz - CROSS_Z) < 14 && Math.abs(cx) < 45
                : Math.abs(cx) < 12 || Math.abs(cz) < 10;
        if (market) {
            return r < 0.45f ? LotType.SHOP : r < 0.62f ? LotType.RESTAURANT : r < 0.78f ? LotType.STALL : LotType.HOUSE;
        }
        boolean big = w >= 10 && d >= 10;
        if (capital && big && r < 0.22f) {
            return LotType.APARTMENT;
        }
        if (capital && big && r < 0.30f) {
            return LotType.ROUND_TOWER;
        }
        if (r < 0.62f) {
            return LotType.HOUSE;
        }
        if (r < 0.82f) {
            return LotType.ROUND_HOUSE;
        }
        if (r < 0.88f) {
            return LotType.WAREHOUSE;
        }
        return r < 0.94f ? LotType.GARDEN : LotType.SHOP;
    }

    private int floorsFor(LotType type, int extra) {
        return switch (type) {
            case APARTMENT -> 4 + rnd.nextInt(3);
            case ROUND_TOWER -> 5 + rnd.nextInt(3);
            case SHOP, RESTAURANT -> 2;
            case HOUSE -> 1 + rnd.nextInt(1 + extra);
            case ROUND_HOUSE -> 1 + rnd.nextInt(2);
            case WAREHOUSE -> 1;
            default -> 1;
        };
    }

    /** The side of a lot that looks onto the nearest street. */
    private Direction towardsStreet(int x1, int z1, int x2, int z2) {
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        Direction best = Direction.SOUTH;
        int bestDistance = Integer.MAX_VALUE;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int x = side.getStepX() > 0 ? x2 : side.getStepX() < 0 ? x1 : cx;
            int z = side.getStepZ() > 0 ? z2 : side.getStepZ() < 0 ? z1 : cz;
            for (int k = 1; k < 30; k++) {
                if (isRoad(x + side.getStepX() * k, z + side.getStepZ() * k)) {
                    if (k < bestDistance) {
                        bestDistance = k;
                        best = side;
                    }
                    break;
                }
            }
        }
        return best;
    }

    private boolean isRoad(int x, int z) {
        for (Road road : plan.roads) {
            if (road.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ street furniture

    private void lanternsAlong(int x1, int z1, int x2, int z2, int every) {
        boolean alongZ = z2 - z1 > x2 - x1;
        if (alongZ) {
            for (int z = z1; z <= z2; z += every) {
                prop(PropType.LANTERN, x1, z);
                prop(PropType.LANTERN, x2, z);
            }
        } else {
            for (int x = x1; x <= x2; x += every) {
                if (Math.abs(x) > 6) {
                    prop(PropType.LANTERN, x, z1);
                    prop(PropType.LANTERN, x, z2);
                }
            }
        }
    }

    private void prop(PropType type, int x, int z) {
        if (x * x + z * z < limit * limit) {
            plan.props.add(new Prop(type, x, z, Direction.SOUTH, rnd.nextInt(4)));
        }
    }

    /** Wooden utility poles along the streets, wired together (a Hidden Leaf trademark). */
    private void powerLines(int maxStreets) {
        int done = 0;
        for (Road road : List.copyOf(plan.roads)) {
            if (road.ring() || road.paving() != Paving.STREET || done >= maxStreets) {
                continue;
            }
            done++;
            boolean alongZ = road.z2() - road.z1() > road.x2() - road.x1();
            int edge = alongZ ? road.x1() : road.z1();
            int start = alongZ ? road.z1() : road.x1();
            int end = alongZ ? road.z2() : road.x2();
            int[] last = null;
            for (int p = start + 6; p <= end - 6; p += 13) {
                int x = alongZ ? edge : p;
                int z = alongZ ? p : edge;
                if (x * x + z * z > limit * limit || crossesStreet(x, z, alongZ)) {
                    last = null;
                    continue;
                }
                plan.props.add(new Prop(PropType.POLE, x, z, alongZ ? Direction.SOUTH : Direction.EAST, 0));
                if (last != null) {
                    plan.wires.add(new Wire(last[0], last[1], x, z, VillageBuilder.POLE_HEIGHT));
                }
                last = new int[] {x, z};
            }
        }
    }

    /** Whether a pole at (x, z), on the near edge of a street running along z (or x), stands at a crossing. */
    private boolean crossesStreet(int x, int z, boolean alongZ) {
        return alongZ ? isRoad(x - 1, z) : isRoad(x, z - 1);
    }

    private void plazaBenches(int cx, int cz, int r) {
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int x = cx + (int) Math.round(Math.sin(a) * r);
            int z = cz + (int) Math.round(Math.cos(a) * r);
            if (Math.abs(x - cx) > 5) {
                plan.props.add(new Prop(PropType.BENCH, x, z, Math.abs(x - cx) > Math.abs(z - cz) ? (x > cx ? Direction.WEST : Direction.EAST)
                        : (z > cz ? Direction.NORTH : Direction.SOUTH), 0));
            }
        }
    }

    /** Trees down the middle of the cross avenue's pavements. */
    private void streetTrees(int z) {
        for (int x = -100; x <= 100; x += 18) {
            if (Math.abs(x) > 12 && (Math.abs(x) - 40) % 42 != 0) {
                prop(PropType.TREE, x, z);
            }
        }
    }

    // ================================================================ people

    /** A ninja for each of the first flat roofs, with a route hopping from roof to roof. */
    private void runners(int count) {
        List<Lot> roofs = new ArrayList<>(plan.lots.stream().filter(l -> l.roofTop() > 0).toList());
        for (int i = 0; i < count && !roofs.isEmpty(); i++) {
            Lot here = roofs.remove(rnd.nextInt(roofs.size()));
            List<int[]> route = new ArrayList<>();
            route.add(new int[] {here.cx(), here.roofTop(), here.cz()});
            for (int step = 0; step < 12; step++) {
                Lot next = null;
                double best = Double.MAX_VALUE;
                for (Lot other : roofs) {
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
                here = next;
            }
            if (route.size() >= 3) {
                int[] first = route.get(0);
                add(new Spawn(Role.RUNNER, first[0], first[1], first[2], first[0], first[1], first[2], 60, first[0], first[2], 0, route));
            }
        }
    }

    /** A merchant inside each of the first {@code max} shops, restaurants and stalls. */
    private void shopkeepers(int max) {
        int n = 0;
        for (Lot lot : plan.lots) {
            if (n >= max) {
                break;
            }
            int shop;
            switch (lot.type()) {
                case RAMEN_SHOP -> shop = 1;
                case RESTAURANT -> shop = 1 + (lot.style() % 2) * 4;   // ramen or a teahouse
                case STALL -> shop = lot.style() % 2 == 0 ? 1 : 5;
                case SHOP -> shop = 2 + lot.style() % 3;              // weapons, scrolls, flowers
                case INN -> shop = 5;
                default -> {
                    continue;
                }
            }
            int[] inside = inside(lot, 2);
            add(new Spawn(Role.MERCHANT, inside[0], 0, inside[1], inside[0], 0, inside[1], 3, inside[0], inside[1], shop, List.of()));
            n++;
        }
    }

    /**
     * Ordinary people: each lives in a house (they sleep there at night) and spends the day out in
     * the village; some are children who spend the day at {@code (schoolX, schoolZ)}.
     */
    private void townsfolk(int adults, int children, int schoolX, int schoolZ, int ninjas) {
        List<Lot> homes = new ArrayList<>(plan.lots.stream().filter(l -> l.type() == LotType.HOUSE || l.type() == LotType.ROUND_HOUSE
                || l.type() == LotType.APARTMENT).toList());
        List<Lot> outings = plan.lots.stream().filter(l -> l.type() == LotType.SHOP || l.type() == LotType.RESTAURANT
                || l.type() == LotType.PARK || l.type() == LotType.STALL || l.type() == LotType.RAMEN_SHOP).toList();
        for (int i = 0; i < adults + children + ninjas && !homes.isEmpty(); i++) {
            Lot home = homes.remove(rnd.nextInt(homes.size()));
            int[] bed = inside(home, 0);
            Role role = i < children ? Role.CHILD : i < children + ninjas ? Role.NINJA : Role.CIVILIAN;
            int workX, workZ;
            if (role == Role.CHILD) {
                workX = schoolX;
                workZ = schoolZ;
            } else if (!outings.isEmpty()) {
                Lot out = outings.get(rnd.nextInt(outings.size()));
                int[] front = front(out);
                workX = front[0];
                workZ = front[1];
            } else {
                workX = 0;
                workZ = 0;
            }
            add(new Spawn(role, bed[0], 0, bed[1], bed[0], 0, bed[1], 2, workX, workZ, 0, List.of()));
        }
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

    private void street(Road road) {
        plan.roads.add(road);
        for (int x = Math.max(-half, road.x1()); x <= Math.min(half, road.x2()); x++) {
            for (int z = Math.max(-half, road.z1()); z <= Math.min(half, road.z2()); z++) {
                if (road.contains(x, z)) {
                    used[index(x, z)] = true;
                }
            }
        }
    }

    private void reserve(int x1, int z1, int x2, int z2) {
        mark(x1, z1, x2, z2);
    }

    private boolean free(int x1, int z1, int x2, int z2) {
        if (!within(x1, z1) || !within(x1, z2) || !within(x2, z1) || !within(x2, z2)) {
            return false;
        }
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (used[index(x, z)]) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean within(int x, int z) {
        return x * x + z * z <= limit * limit;
    }

    private void mark(int x1, int z1, int x2, int z2) {
        for (int x = Math.max(-half, x1); x <= Math.min(half, x2); x++) {
            for (int z = Math.max(-half, z1); z <= Math.min(half, z2); z++) {
                used[index(x, z)] = true;
            }
        }
    }

    private int index(int x, int z) {
        return (x + half) * size + (z + half);
    }

    /** Height of a building's walls (for things that need to know, like spawns on roofs). */
    static int wallHeight(int floors) {
        return floors * STOREY;
    }
}

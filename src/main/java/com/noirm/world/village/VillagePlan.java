package com.noirm.world.village;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a village is made of, decided once from its seed: the streets, the lots (what stands
 * where), the street furniture and the people. All positions are <b>local</b> (see
 * {@link VillageCanvas}): (0, 0) is the centre at ground level, the main gate is to the south (+z).
 *
 * <p>World generation builds a village one chunk at a time, so the same plan is needed by every
 * chunk the village covers. Planning a capital takes a few milliseconds, so plans are kept in a
 * small cache ({@link #of}) and the {@link VillageBuilder} only draws the lots that reach into the
 * chunk being built. Each lot carries its own seed, so a building comes out the same whichever
 * chunk draws which part of it.
 *
 * <p>The streets and the river are also kept as rasters (one entry per block), so curved streets
 * cost nothing to look up: {@link #paving}, {@link #isRoad} and {@link #isWater}.
 */
public final class VillagePlan {
    /** Height of one storey (floor block + three blocks of room). */
    public static final int STOREY = 4;

    /** What a lot holds. */
    public enum LotType {
        HOUSE, ROUND_HOUSE, APARTMENT, ROUND_TOWER, SHOP, RESTAURANT, STALL, RAMEN_SHOP, WAREHOUSE, GARDEN, PARK,
        KAGE_TOWER, ACADEMY, HOSPITAL, CLAN_COMPOUND, CLAN_HOUSE, ONSEN, SHRINE, TRAINING_GROUND, GUARD_POST,
        INN, CHIEF_HOUSE, WATCHTOWER,
        /** A small leftover plot between houses: kitchen garden, laundry yard, woodpile, shed, koi pond, bamboo, roadside shrine ({@code style} picks). */
        YARD,
        /** A park around one giant tree, with a pond and a playground. */
        GIANT_TREE,
        /** A slender tower that breaks the skyline: a cone spire, a "bottle" spire or a bell turret ({@code style} picks). */
        SPIRE,
        /** The jōnin standby station: a round plank-clad lounge beside the Academy. */
        JONIN_STATION,
        /** The cemetery on the village edge: rows of graves and a flame memorial. */
        CEMETERY
    }

    /**
     * One building plot, from (x1, z1) to (x2, z2) inclusive. {@code facing} is the side the front
     * door is on (towards its street); {@code floors} the number of storeys; {@code style} a free
     * number the building uses for its variant (roof shape, shop kind…); {@code colour} the index of
     * the palette colourway it's painted in ({@link VillagePalette#colourway}), chosen by the planner
     * so neighbours differ.
     */
    public record Lot(LotType type, int x1, int z1, int x2, int z2, Direction facing, int floors, int style, long seed, int colour) {
        public Lot(LotType type, int x1, int z1, int x2, int z2, Direction facing, int floors, int style, long seed) {
            this(type, x1, z1, x2, z2, facing, floors, style, seed, (int) ((seed >>> 33) & 0xFF));
        }

        public int cx() {
            return (x1 + x2) >> 1;
        }

        public int cz() {
            return (z1 + z2) >> 1;
        }

        public int width() {
            return x2 - x1 + 1;
        }

        public int depth() {
            return z2 - z1 + 1;
        }

        /** Whether the lot reaches the square (a1, b1)–(a2, b2), with a margin for roofs and steps. */
        public boolean overlaps(int a1, int b1, int a2, int b2, int margin) {
            return x1 - margin <= a2 && x2 + margin >= a1 && z1 - margin <= b2 && z2 + margin >= b1;
        }

        /** The walkable top of a flat roof (where a rooftop runner lands), or -1 if it has no flat roof. */
        public int roofTop() {
            return switch (type) {
                case HOUSE, SHOP, WAREHOUSE -> style % 3 == 0 ? -1 : floors * STOREY;
                case APARTMENT -> floors * STOREY;
                case ROUND_HOUSE -> -1;
                default -> -1;
            };
        }
    }

    /** How a stretch of street is paved. */
    public enum Paving { AVENUE, STREET, PATH, PLAZA }

    /**
     * A simple street shape (inclusive rectangle, or a disk/ring when {@code ring} is set). The
     * plaza, the ring road and a town's straight streets are kept as these; the whole street
     * network, curved streets included, is in the street raster ({@link #paving}).
     */
    public record Road(Paving paving, int x1, int z1, int x2, int z2, boolean ring, int inner) {
        public static Road rect(Paving paving, int x1, int z1, int x2, int z2) {
            return new Road(paving, Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2), false, 0);
        }

        /** A ring of road around (cx, cz) between radius {@code inner} (exclusive, -1 = a full disk) and {@code outer}. */
        public static Road ring(Paving paving, int cx, int cz, int inner, int outer) {
            return new Road(paving, cx - outer, cz - outer, cx + outer, cz + outer, true, inner);
        }

        public boolean contains(int x, int z) {
            if (x < x1 || x > x2 || z < z1 || z > z2) {
                return false;
            }
            if (!ring) {
                return true;
            }
            int r = (x2 - x1) / 2;
            int dx = x - (x1 + r);
            int dz = z - (z1 + r);
            return VillageBuilder.inDisk(dx, dz, r) && (inner < 0 || !VillageBuilder.inDisk(dx, dz, inner));
        }
    }

    /**
     * One street of the network: its centreline (one point per block, local x and z), how wide it is,
     * how it's paved, and whether it's a festival street (lantern lines across it, arched street
     * gates at its ends).
     */
    public record Street(Paving paving, int width, boolean festival, List<int[]> line) {}

    /**
     * The districts of a capital, each with its own mix of buildings and colours: the market along
     * the gate avenue, the shinobi quarter round the Kage residence, the clan district, the temple
     * hill under the rock, the lantern street of restaurants and the hot spring, the homes, and the
     * outer ring of gardens, fields and training glades.
     */
    public enum Ward { MARKET, SHINOBI, CLAN, TEMPLE, LANTERN, RESIDENTIAL, OUTER }

    /** Small things along the streets. */
    public enum PropType {
        LANTERN, POLE, BENCH, TREE, BIG_TREE, WELL, BARRELS, SIGN_BOARD, FLOWERS,
        /** An avenue street lamp: a redstone lamp under an inverted daylight detector (it lights up at dusk by itself). */
        LAMP_POST,
        /** A notice board with papers pinned on it, facing {@code facing}. */
        NOTICE_BOARD,
        /** Paper lanterns strung across a festival street at (x, z): along {@code facing}'s axis, {@code style} blocks each way. */
        LANTERN_LINE,
        /** An arched gate over a festival street at (x, z): the street runs along {@code facing}; posts {@code style} blocks each side. */
        STREET_GATE
    }

    /**
     * A prop at (x, z). For a {@link PropType#POLE}, {@code facing} is the way the street runs and
     * {@code style} holds flags: 1 = a transformer, 2 = a lamp arm, 4 = the street is on the
     * counter-clockwise side of {@code facing} (else clockwise).
     */
    public record Prop(PropType type, int x, int z, Direction facing, int style) {}

    /**
     * A wire from (x1, y, z1) to (x2, y2, z2): telephone and power lines between poles (they sag a
     * block in the middle third when level) and service drops from a pole down to a house wall.
     */
    public record Wire(int x1, int z1, int x2, int z2, int y, int y2) {
        public Wire(int x1, int z1, int x2, int z2, int y) {
            this(x1, z1, x2, z2, y, y);
        }
    }

    /**
     * Where a street crosses the river: a bridge centred on (x, z), running along {@code axis},
     * {@code length} blocks long and {@code width} wide. Arched bridges (red, rising 2 blocks) carry
     * streets; flat stone ones carry the avenues.
     */
    public record Bridge(int x, int z, Direction.Axis axis, int length, int width, boolean arched) {}

    /** Steps down to the river from the bank at (x, z), going down towards {@code facing}. */
    public record Landing(int x, int z, Direction facing) {}

    /** Who lives here. */
    public enum Role { KAGE, GUARD, PATROL, RUNNER, TRAINEE, CLAN_NINJA, NINJA, MERCHANT, CIVILIAN, CHILD, TEACHER, DUMMY, CHIEF,
        /** Village animals: a cat on a porch, chickens in a yard, koi in a pond. */
        CAT, CHICKEN, KOI }

    /**
     * A resident: where they spawn ({@code x, y, z}), where they belong ({@code homeX/Z},
     * {@code radius}), and for some a second place they spend the day ({@code workX/Z}) or a route
     * of waypoints (rooftops for runners, the wall walk for patrols; each point is x, y, z).
     */
    public record Spawn(Role role, int x, int y, int z, int homeX, int homeY, int homeZ, int radius,
                        int workX, int workZ, int shop, List<int[]> route) {
        public static Spawn at(Role role, int x, int y, int z, int radius) {
            return new Spawn(role, x, y, z, x, y, z, radius, x, z, 0, List.of());
        }
    }

    public final VillageKind kind;
    public final VillagePalette palette;
    public final long seed;
    /** The simple street shapes (plaza, ring road, a town's straight streets); {@link #paving} has the whole network. */
    public final List<Road> roads = new ArrayList<>();
    /** The street network as centrelines: avenues, arcs, alleys, the festival street, the river walks. */
    public final List<Street> streets = new ArrayList<>();
    public final List<Lot> lots = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();
    public final List<Wire> wires = new ArrayList<>();
    public final List<Spawn> spawns = new ArrayList<>();
    /** The river's centreline (one point per block, local {x, z}); empty if the village has none. */
    public final List<int[]> river = new ArrayList<>();
    /** Bridges where streets cross the river. */
    public final List<Bridge> bridges = new ArrayList<>();
    /** Steps down to the river from its banks. */
    public final List<Landing> landings = new ArrayList<>();
    /** The canon clan living in the clan compound (capital only), or "" for none. */
    public String compoundClan = "";
    /** Where the capital's landmarks are (local coordinates), set by the planner. */
    public int towerX = 0, towerZ = VillagePlanner.TOWER_Z;
    /** The Kage Rock: the cliff face is at z = {@code rockZ}, and it spans |x| ≤ {@code rockHalf}. */
    public int rockZ = VillagePlanner.ROCK_Z, rockHalf = VillagePlanner.ROCK_HALF;
    /** The z of the street that leaves through the side gates (east and west). */
    public int crossZ = VillagePlanner.CROSS_Z;
    /**
     * The gates through the outer wall: {x, z, width} at the wall line (local coordinates). The
     * fortifications open the wall there; the planner lays a street through each.
     */
    public final List<int[]> gates = new ArrayList<>();

    // ------------------------------------------------------------ street, water and ward rasters

    /** The rasters cover -half..half on both axes (half = the village's radius). */
    private final int half;
    private final int size;
    /** The paving of every block of street: 0 = none, else {@link Paving#ordinal()} + 1. */
    final byte[] streetGrid;
    /** One bit per block: river or canal water. */
    final long[] waterBits;
    /** Ward centres {x, z, ward ordinal}: a place belongs to the ward of the nearest centre. */
    final List<int[]> wardCentres = new ArrayList<>();

    /** Whether (x, z) is river or canal water (the wall leaves a barred water gate there). */
    public boolean isWater(int x, int z) {
        int i = cell(x, z);
        return i >= 0 && (waterBits[i >> 6] & (1L << (i & 63))) != 0;
    }

    /** Whether the village has a river. */
    public boolean hasRiver() {
        return !river.isEmpty();
    }

    /** How the street at (x, z) is paved, or null if no street is there. */
    public Paving paving(int x, int z) {
        int i = cell(x, z);
        if (i < 0 || streetGrid[i] == 0) {
            return null;
        }
        return Paving.values()[streetGrid[i] - 1];
    }

    /** Whether (x, z) is street (any paving; streets over the river count). */
    public boolean isRoad(int x, int z) {
        int i = cell(x, z);
        return i >= 0 && streetGrid[i] != 0;
    }

    /** The ward (district) that (x, z) belongs to; RESIDENTIAL in a plan without wards (towns, outposts). */
    public Ward ward(int x, int z) {
        Ward best = Ward.RESIDENTIAL;
        long bestDistance = Long.MAX_VALUE;
        for (int[] centre : wardCentres) {
            long dx = x - centre[0], dz = z - centre[1];
            long d = dx * dx + dz * dz;
            if (d < bestDistance) {
                bestDistance = d;
                best = Ward.values()[centre[2]];
            }
        }
        return best;
    }

    /** The raster index of (x, z), or -1 outside the rasters. */
    int cell(int x, int z) {
        if (x < -half || x > half || z < -half || z > half) {
            return -1;
        }
        return (x + half) * size + (z + half);
    }

    VillagePlan(VillageKind kind, VillagePalette palette, long seed) {
        this.kind = kind;
        this.palette = palette;
        this.seed = seed;
        this.half = kind.radius;
        this.size = half * 2 + 1;
        this.streetGrid = new byte[size * size];
        this.waterBits = new long[(size * size + 63) >> 6];
    }

    /** The first lot of a type, or null. */
    public Lot first(LotType type) {
        for (Lot lot : lots) {
            if (lot.type == type) {
                return lot;
            }
        }
        return null;
    }

    public long count(LotType type) {
        return lots.stream().filter(l -> l.type == type).count();
    }

    // ------------------------------------------------------------ cache

    private static final int CACHE_SIZE = 12;
    private static final Map<String, VillagePlan> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, VillagePlan> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    /** A freshly made plan, bypassing the cache (for tests). */
    public static VillagePlan uncached(VillageKind kind, VillagePalette palette, long seed) {
        return VillagePlanner.plan(kind, palette, seed);
    }

    /** The plan for a village (the same object for every chunk of it, while it's being built). */
    public static VillagePlan of(VillageKind kind, VillagePalette palette, long seed) {
        String key = kind.ordinal() + ":" + palette.ordinal() + ":" + seed;
        VillagePlan plan = CACHE.get(key);
        if (plan == null) {
            plan = VillagePlanner.plan(kind, palette, seed);
            CACHE.put(key, plan);
        }
        return plan;
    }
}

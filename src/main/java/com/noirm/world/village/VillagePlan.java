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
 */
public final class VillagePlan {
    /** Height of one storey (floor block + three blocks of room). */
    public static final int STOREY = 4;

    /** What a lot holds. */
    public enum LotType {
        HOUSE, ROUND_HOUSE, APARTMENT, ROUND_TOWER, SHOP, RESTAURANT, STALL, RAMEN_SHOP, WAREHOUSE, GARDEN, PARK,
        KAGE_TOWER, ACADEMY, HOSPITAL, CLAN_COMPOUND, CLAN_HOUSE, ONSEN, SHRINE, TRAINING_GROUND, GUARD_POST,
        INN, CHIEF_HOUSE, WATCHTOWER
    }

    /**
     * One building plot, from (x1, z1) to (x2, z2) inclusive. {@code facing} is the side the front
     * door is on (towards its street); {@code floors} the number of storeys; {@code style} a free
     * number the building uses for its variant (roof shape, shop kind…).
     */
    public record Lot(LotType type, int x1, int z1, int x2, int z2, Direction facing, int floors, int style, long seed) {
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

    /** A rectangle of street (inclusive), or a disk/ring when {@code ring} is set. */
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

    /** Small things along the streets. */
    public enum PropType { LANTERN, POLE, BENCH, TREE, BIG_TREE, WELL, BARRELS, SIGN_BOARD, FLOWERS }

    public record Prop(PropType type, int x, int z, Direction facing, int style) {}

    /** A telephone/power line between two pole tops (always straight along x or z). */
    public record Wire(int x1, int z1, int x2, int z2, int y) {}

    /** Who lives here. */
    public enum Role { KAGE, GUARD, PATROL, RUNNER, TRAINEE, CLAN_NINJA, NINJA, MERCHANT, CIVILIAN, CHILD, TEACHER, DUMMY, CHIEF }

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
    public final List<Road> roads = new ArrayList<>();
    public final List<Lot> lots = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();
    public final List<Wire> wires = new ArrayList<>();
    public final List<Spawn> spawns = new ArrayList<>();
    /** The canon clan living in the clan compound (capital only), or "" for none. */
    public String compoundClan = "";

    VillagePlan(VillageKind kind, VillagePalette palette, long seed) {
        this.kind = kind;
        this.palette = palette;
        this.seed = seed;
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

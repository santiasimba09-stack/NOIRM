package com.noirm.world.village;

import net.minecraft.util.StringRepresentable;

/**
 * The three sizes of ninja settlement. The numbers here decide how much land a village needs and
 * how uneven that land may be; how often they appear is in
 * {@code data/noirm/worldgen/structure_set/*.json}.
 *
 * <p>Saved by position in the list ({@code Kind} in the piece's NBT), so new kinds go at the end.
 *
 * <p>A structure can reach at most 8 chunks from the chunk it starts in (vanilla's
 * {@code ChunkGenerator.createReferences}), about 128 blocks. Towns and outposts fit; the capital
 * is assembled from per-chunk "ward" starts instead.
 */
public enum VillageKind implements StringRepresentable {
    /**
     * A hidden village, like the Hidden Leaf: a great wall and gate, the Kage tower and the Kage
     * Rock, an academy, a hospital, a clan district, markets and streets full of houses. ~545
     * blocks across: bigger than one structure can reach, so it's built by many cooperating
     * structure starts (one per chunk) from the same plan.
     */
    CAPITAL("capital", 272, 264, 72, 40),
    /** A small outpost: a palisade, a watchtower, a few houses and a training post. ~30 blocks across. */
    OUTPOST("outpost", 15, 14, 20, 9),
    /** A small village of the Land of Fire: a main street with shops and an inn, a chief, a shrine. ~145 blocks across. */
    TOWN("town", 72, 68, 36, 20);

    public static final com.mojang.serialization.Codec<VillageKind> CODEC = StringRepresentable.fromEnum(VillageKind::values);

    private final String name;
    /** Radius of the cleared, flattened ground (blocks). */
    public final int radius;
    /** Radius of the outer wall, fence or palisade. */
    public final int wall;
    /** How high above the ground the builder clears (trees) and builds. */
    public final int height;
    /** The most the ground may rise and fall across the site before we look elsewhere. */
    public final int maxSlope;

    VillageKind(String name, int radius, int wall, int height, int maxSlope) {
        this.name = name;
        this.radius = radius;
        this.wall = wall;
        this.height = height;
        this.maxSlope = maxSlope;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static VillageKind byOrdinal(int ordinal) {
        VillageKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : OUTPOST;
    }
}

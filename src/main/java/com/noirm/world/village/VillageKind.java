package com.noirm.world.village;

import net.minecraft.util.StringRepresentable;

/**
 * The two sizes of ninja settlement. The numbers here decide how much land a village needs and
 * how uneven that land may be; how often they appear is in
 * {@code data/noirm/worldgen/structure_set/*.json}.
 */
public enum VillageKind implements StringRepresentable {
    /** A hidden village: walls, a big gate, the Kage tower, houses, stalls, a training ground. ~85 blocks across. */
    CAPITAL("capital", 42, 34, 16),
    /** A small outpost: a palisade, a watchtower, a few houses and a training post. ~30 blocks across. */
    OUTPOST("outpost", 15, 16, 9);

    public static final com.mojang.serialization.Codec<VillageKind> CODEC = StringRepresentable.fromEnum(VillageKind::values);

    private final String name;
    /** Radius of the cleared, flattened ground (blocks). */
    public final int radius;
    /** How high above the ground the builder clears (trees) and builds. */
    public final int height;
    /** The most the ground may rise and fall across the site before we look elsewhere. */
    public final int maxSlope;

    VillageKind(String name, int radius, int height, int maxSlope) {
        this.name = name;
        this.radius = radius;
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

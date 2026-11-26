package com.noirm.aburame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Locale;

/**
 * The twelve wild insects of bug collecting (docs/ABURAME.md, 2.6: Shino's hobby), plus the rare
 * bikōchū of the Entomologist's quest. Each lives somewhere, can be caught by anyone (Bug Net or
 * Insect Gathering) into the Insect Case, and bred into an Aburame colony once for a small
 * <b>strain</b> (the effects are read where they apply; see the lang key {@code .strain}).
 */
public enum WildKind {
    FIREFLY(0xFF3A3220, 0xFFE8E070, true),
    SCARAB(0xFF2A5A4A, 0xFFD0B040, false),
    CAVE_CRICKET(0xFF6A5A48, 0xFFB09A80, false),
    WATER_STRIDER(0xFF3A3A40, 0xFF8A8A9A, false),
    MOTH(0xFF7A7068, 0xFFC8BEB0, false),
    STAG_BEETLE(0xFF3A2416, 0xFF6A4026, false),
    JEWEL_BEETLE(0xFF1A6A3A, 0xFF40C0A0, false),
    SNOW_FLEA(0xFF404858, 0xFFD8E0F0, false),
    MANTIS(0xFF4A8A2A, 0xFF90D050, false),
    DUNG_BEETLE(0xFF2A2018, 0xFF5A4A30, false),
    IRONCLAD_WEEVIL(0xFF484850, 0xFF8A8A96, false),
    LOCUST(0xFF8A7A3A, 0xFFC8B060, false),
    /** The rare scent beetle (not part of the case's twelve). */
    BIKOCHU(0xFF6A3A2A, 0xFFE0A070, true);

    public final int body, accent;
    /** Glows (fireflies, the bikōchū's shimmer). */
    public final boolean glows;

    WildKind(int body, int accent, boolean glows) {
        this.body = body;
        this.accent = accent;
        this.glows = glows;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "aburame.noirm.wild." + key();
    }

    /** The twelve of the case (everything but the bikōchū). */
    public static WildKind[] collection() {
        WildKind[] all = values();
        WildKind[] twelve = new WildKind[all.length - 1];
        System.arraycopy(all, 0, twelve, 0, twelve.length);
        return twelve;
    }

    public static WildKind byKey(String key) {
        for (WildKind k : values()) {
            if (k.key().equals(key)) {
                return k;
            }
        }
        return null;
    }

    public static WildKind of(int ordinal) {
        WildKind[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    /** Whether this insect lives at {@code pos} right now (biome, depth, time of day). */
    public boolean livesAt(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        boolean night = level.isNight();
        return switch (this) {
            case FIREFLY -> night && (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(BiomeTags.IS_RIVER));
            case SCARAB -> biome.is(Biomes.DESERT);
            case CAVE_CRICKET -> pos.getY() < 40 && !level.canSeeSky(pos);
            case WATER_STRIDER -> biome.is(BiomeTags.IS_RIVER) && !night;
            case MOTH -> biome.is(Biomes.DARK_FOREST);
            case STAG_BEETLE -> biome.is(Biomes.FOREST) || biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.FLOWER_FOREST);
            case JEWEL_BEETLE -> biome.is(BiomeTags.IS_JUNGLE);
            case SNOW_FLEA -> biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.GROVE)
                    || biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.ICE_SPIKES);
            case MANTIS -> biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.MEADOW);
            case DUNG_BEETLE -> biome.is(BiomeTags.IS_SAVANNA);
            case IRONCLAD_WEEVIL -> biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(Biomes.WINDSWEPT_HILLS);
            case LOCUST -> biome.is(BiomeTags.IS_BADLANDS);
            case BIKOCHU -> biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        };
    }
}

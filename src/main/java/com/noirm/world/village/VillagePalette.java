package com.noirm.world.village;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.Tags;

import java.util.List;

/**
 * The blocks a village is built from, one set per region of the world, so a village in the desert
 * reads as a "Sand village" and one in the snow as a "Snow village":
 * <ul>
 *   <li>{@link #LEAF}: plains and forests. White and birch walls, orange and red roofs, a red Kage
 *       tower (the Hidden Leaf look).</li>
 *   <li>{@link #SAND}: deserts, badlands and savannas. Sandstone domes, terracotta, acacia.</li>
 *   <li>{@link #SNOW}: cold biomes. Spruce, white walls and blue roofs.</li>
 * </ul>
 */
public enum VillagePalette {
    LEAF(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT_PATH, Blocks.STONE_BRICKS, Blocks.RED_TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
            List.of(Blocks.WHITE_TERRACOTTA, Blocks.BIRCH_PLANKS, Blocks.SMOOTH_SANDSTONE, Blocks.WHITE_CONCRETE),
            List.of(Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE),
            Blocks.LIGHT_GRAY_CONCRETE, Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_SLAB, Blocks.OAK_FENCE, Blocks.OAK_DOOR,
            Blocks.OAK_STAIRS, Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.WHITE_CONCRETE, Blocks.RED_CONCRETE,
            Blocks.WHITE_WOOL, Blocks.RED_WOOL, Glyph.FIRE, "entity.noirm.kage.hokage"),
    SAND(Blocks.SAND, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.ORANGE_TERRACOTTA,
            List.of(Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.TERRACOTTA, Blocks.SANDSTONE),
            List.of(Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA),
            Blocks.TERRACOTTA, Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE, Blocks.ACACIA_DOOR,
            Blocks.ACACIA_STAIRS, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.ORANGE_TERRACOTTA, Blocks.BROWN_TERRACOTTA,
            Blocks.ORANGE_WOOL, Blocks.WHITE_WOOL, Glyph.WIND, "entity.noirm.kage.kazekage"),
    SNOW(Blocks.SNOW_BLOCK, Blocks.STONE, Blocks.POLISHED_ANDESITE, Blocks.SPRUCE_LOG, Blocks.WHITE_TERRACOTTA, Blocks.BLUE_TERRACOTTA,
            List.of(Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.WHITE_TERRACOTTA),
            List.of(Blocks.BLUE_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.SPRUCE_PLANKS),
            Blocks.LIGHT_GRAY_CONCRETE, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_DOOR,
            Blocks.SPRUCE_STAIRS, Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.WHITE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE,
            Blocks.BLUE_WOOL, Blocks.WHITE_WOOL, Glyph.SNOWFLAKE, "entity.noirm.kage.village_head");

    /** A 5×5 picture painted on the Kage tower (top row first; '#' = painted). */
    public enum Glyph {
        /** Roughly the kanji for "fire". */
        FIRE(". # . # .", "# . # . #", ". . # . .", ". # . # .", "# . . . #"),
        /** A swirl of wind. */
        WIND("# # # # .", ". . . # .", ". # # # .", ". # . . .", ". # # # #"),
        SNOWFLAKE("# . # . #", ". # # # .", "# # . # #", ". # # # .", "# . # . #");

        private final String[] rows;

        Glyph(String... rows) {
            this.rows = rows;
        }

        /** Whether the cell at column {@code x}, row {@code y} (0..4, top row 0) is painted. */
        public boolean painted(int x, int y) {
            return rows[y].replace(" ", "").charAt(x) == '#';
        }
    }

    public final Block ground, foundation, road, wall, towerWall, towerRoof;
    public final List<Block> houseWalls, roofs;
    public final Block tank, log, planks, slab, fence, door, stairs, rock, carved, plaque, paint, awningA, awningB;
    public final Glyph glyph;
    /** Translation key of the Kage's title. */
    public final String kageTitle;

    VillagePalette(Block ground, Block foundation, Block road, Block wall, Block towerWall, Block towerRoof,
                   List<Block> houseWalls, List<Block> roofs, Block tank, Block log, Block planks, Block slab, Block fence,
                   Block door, Block stairs, Block rock, Block carved, Block plaque, Block paint, Block awningA, Block awningB,
                   Glyph glyph, String kageTitle) {
        this.ground = ground;
        this.foundation = foundation;
        this.road = road;
        this.wall = wall;
        this.towerWall = towerWall;
        this.towerRoof = towerRoof;
        this.houseWalls = houseWalls;
        this.roofs = roofs;
        this.tank = tank;
        this.log = log;
        this.planks = planks;
        this.slab = slab;
        this.fence = fence;
        this.door = door;
        this.stairs = stairs;
        this.rock = rock;
        this.carved = carved;
        this.plaque = plaque;
        this.paint = paint;
        this.awningA = awningA;
        this.awningB = awningB;
        this.glyph = glyph;
        this.kageTitle = kageTitle;
    }

    /** Picks the palette for the biome at the village's centre. */
    public static VillagePalette forBiome(Holder<Biome> biome) {
        if (biome.is(Tags.Biomes.IS_DESERT) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)) {
            return SAND;
        }
        if (biome.is(Tags.Biomes.IS_SNOWY) || biome.is(Tags.Biomes.IS_COLD_OVERWORLD) || biome.is(BiomeTags.IS_TAIGA)) {
            return SNOW;
        }
        return LEAF;
    }

    public static VillagePalette byOrdinal(int ordinal) {
        VillagePalette[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : LEAF;
    }
}

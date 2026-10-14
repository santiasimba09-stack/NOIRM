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
        SNOWFLAKE("# . # . #", ". # # # .", "# # . # #", ". # # # .", "# . # . #"),
        /** A cross: the hospital. */
        HEAL(". . # . .", ". . # . .", "# # # # #", ". . # . .", ". . # . ."),
        /** "Up": the jōnin station. */
        UP(". . # . .", ". . # # .", ". . # . .", ". . # . .", "# # # # #"),
        /** "Earth". */
        EARTH(". . # . .", ". # # # .", ". . # . .", ". . # . .", "# # # # #"),
        /** Three wavy lines over a bowl: the hot spring. */
        BATH("# . # . #", ". # . # .", "# . # . #", "# . . . #", ". # # # ."),
        /** Waves. */
        WATER("# # . . .", ". . # # .", "# # . . #", ". . # # .", "# # . . #"),
        /** A zigzag bolt. */
        LIGHTNING(". . . # #", ". . # # .", ". # # # .", ". # # . .", "# # . . .");

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

    // ------------------------------------------------------------ colourways

    /**
     * The colourway with index {@code index} (any int; it wraps). Index = wall + 4 × (roof + 20 ×
     * trim), so the planner can pick neighbours that differ in wall and roof. Roof schemes are
     * weighted (they repeat in the roof table), measured from how the villages look in the series:
     * Leaf roofs are mostly orange and ochre, some terracotta, a few teal, rare green and red.
     */
    public Materials.Colourway colourway(int index) {
        Block[] walls = walls();
        Block[][] roofs = roofTable();
        Block[] trims = trims();
        int i = Math.floorMod(index, 1 << 30);
        Block wall = walls[i % walls.length];
        Block[] roof = roofs[(i / 4) % roofs.length];
        Block trim = trims[(i / 80) % trims.length];
        Block accent = this == LEAF ? (wall == Blocks.SMOOTH_SANDSTONE ? Blocks.SPRUCE_PLANKS : Blocks.STRIPPED_SPRUCE_LOG)
                : this == SAND ? Blocks.TERRACOTTA : Blocks.SPRUCE_PLANKS;
        return new Materials.Colourway(wall, accent, roof[0], roof[1], roof[2], trim, roof[3]);
    }

    /** Wall plasters (4 of them). */
    private Block[] walls() {
        return switch (this) {
            case LEAF -> new Block[] {Blocks.SMOOTH_SANDSTONE, Blocks.WHITE_TERRACOTTA, Blocks.BIRCH_PLANKS, Blocks.CALCITE};
            case SAND -> new Block[] {Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.PACKED_MUD, Blocks.CUT_SANDSTONE};
            case SNOW -> new Block[] {Blocks.SPRUCE_PLANKS, Blocks.WHITE_TERRACOTTA, Blocks.STONE_BRICKS, Blocks.STRIPPED_SPRUCE_LOG};
        };
    }

    /** Roof schemes {flat deck, tile stairs, tile slab, cloth banner}, repeated by weight (20 rows). */
    private Block[][] roofTable() {
        Block[] orange = {Blocks.ORANGE_TERRACOTTA, Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER_SLAB, Blocks.ORANGE_WALL_BANNER};
        Block[] ochre = {Blocks.YELLOW_TERRACOTTA, Blocks.BAMBOO_MOSAIC_STAIRS, Blocks.BAMBOO_MOSAIC_SLAB, Blocks.YELLOW_WALL_BANNER};
        Block[] terracotta = {Blocks.TERRACOTTA, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.RED_WALL_BANNER};
        Block[] teal = {Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, Blocks.CYAN_WALL_BANNER};
        Block[] green = {Blocks.GREEN_TERRACOTTA, Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB, Blocks.GREEN_WALL_BANNER};
        Block[] red = {Blocks.RED_TERRACOTTA, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_SLAB, Blocks.WHITE_WALL_BANNER};
        Block[] sand = {Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.WHITE_WALL_BANNER};
        Block[] clay = {Blocks.TERRACOTTA, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.BROWN_WALL_BANNER};
        Block[] rust = {Blocks.ORANGE_TERRACOTTA, Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_SLAB, Blocks.ORANGE_WALL_BANNER};
        Block[] slate = {Blocks.GRAY_TERRACOTTA, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.BLUE_WALL_BANNER};
        Block[] wood = {Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.RED_WALL_BANNER};
        Block[] blue = {Blocks.BLUE_TERRACOTTA, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.LIGHT_BLUE_WALL_BANNER};
        return switch (this) {
            case LEAF -> weighted(orange, 6, ochre, 6, terracotta, 4, teal, 2, green, 1, red, 1);
            case SAND -> weighted(sand, 9, clay, 7, rust, 4);
            case SNOW -> weighted(slate, 8, wood, 7, blue, 5);
        };
    }

    private static Block[][] weighted(Object... schemesAndWeights) {
        java.util.List<Block[]> rows = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < schemesAndWeights.length; i += 2) {
            for (int k = 0; k < (Integer) schemesAndWeights[i + 1]; k++) {
                rows.add((Block[]) schemesAndWeights[i]);
            }
        }
        return rows.toArray(new Block[0][]);
    }

    /** Timber for corner posts and beams (3 of them). */
    private Block[] trims() {
        return switch (this) {
            case LEAF -> new Block[] {Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_BIRCH_LOG};
            case SAND -> new Block[] {Blocks.TERRACOTTA, Blocks.CUT_SANDSTONE, Blocks.STRIPPED_ACACIA_LOG};
            case SNOW -> new Block[] {Blocks.SPRUCE_LOG, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG};
        };
    }

    /** How many distinct colourways there are (index range for the planner). */
    public int colourways() {
        return walls().length * roofTable().length * trims().length;
    }

    // ------------------------------------------------------------ block mixes

    /** Stone masonry with cracked and mossy blocks mixed in. */
    public Materials.Mix stoneMix() {
        return Materials.Mix.of(Blocks.STONE_BRICKS, 55, Blocks.CRACKED_STONE_BRICKS, 15, Blocks.MOSSY_STONE_BRICKS, 20, Blocks.ANDESITE, 10);
    }

    /** How a street of each kind is paved, as a mix (dirt paths with gravel and coarse dirt, plazas with flagstones…). */
    public Materials.Mix pavingMix(VillagePlan.Paving paving) {
        return switch (this) {
            case LEAF -> switch (paving) {
                case AVENUE -> Materials.Mix.of(Blocks.PACKED_MUD, 60, Blocks.DIRT_PATH, 25, Blocks.COARSE_DIRT, 15);
                case STREET -> Materials.Mix.of(Blocks.DIRT_PATH, 60, Blocks.COARSE_DIRT, 15, Blocks.GRAVEL, 10, Blocks.PACKED_MUD, 15);
                case PATH -> Materials.Mix.of(Blocks.COARSE_DIRT, 50, Blocks.GRAVEL, 30, Blocks.DIRT_PATH, 20);
                case PLAZA -> Materials.Mix.of(Blocks.STONE_BRICKS, 70, Blocks.POLISHED_ANDESITE, 20, Blocks.CRACKED_STONE_BRICKS, 10);
            };
            case SAND -> switch (paving) {
                case AVENUE, PLAZA -> Materials.Mix.of(Blocks.SMOOTH_SANDSTONE, 70, Blocks.CUT_SANDSTONE, 20, Blocks.SANDSTONE, 10);
                case STREET -> Materials.Mix.of(Blocks.CUT_SANDSTONE, 50, Blocks.SANDSTONE, 30, Blocks.PACKED_MUD, 20);
                case PATH -> Materials.Mix.of(Blocks.SANDSTONE, 60, Blocks.PACKED_MUD, 40);
            };
            case SNOW -> switch (paving) {
                case AVENUE, PLAZA -> Materials.Mix.of(Blocks.POLISHED_ANDESITE, 60, Blocks.STONE_BRICKS, 30, Blocks.CRACKED_STONE_BRICKS, 10);
                case STREET -> Materials.Mix.of(Blocks.STONE_BRICKS, 50, Blocks.COBBLESTONE, 25, Blocks.GRAVEL, 25);
                case PATH -> Materials.Mix.of(Blocks.COARSE_DIRT, 50, Blocks.GRAVEL, 50);
            };
        };
    }

    /** Whether the village sits under a Kage Rock (the Sand village is ringed by cliffs instead). */
    public boolean hasKageRock() {
        return this != SAND;
    }

    /** Stairs for pitched roofs (tiles). */
    public List<Block> roofStairs() {
        return switch (this) {
            case LEAF -> List.of(Blocks.BRICK_STAIRS, Blocks.MUD_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.DARK_OAK_STAIRS);
            case SAND -> List.of(Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_STAIRS);
            case SNOW -> List.of(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_STAIRS);
        };
    }

    /** The slab matching {@link #roofStairs()} (same index), for ridges and eaves. */
    public List<Block> roofSlabs() {
        return switch (this) {
            case LEAF -> List.of(Blocks.BRICK_SLAB, Blocks.MUD_BRICK_SLAB, Blocks.RED_NETHER_BRICK_SLAB, Blocks.DARK_OAK_SLAB);
            case SAND -> List.of(Blocks.SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB);
            case SNOW -> List.of(Blocks.DEEPSLATE_TILE_SLAB, Blocks.DARK_OAK_SLAB, Blocks.SPRUCE_SLAB);
        };
    }

    /** Corner posts and frames of houses. */
    public Block trim() {
        return switch (this) {
            case LEAF -> Blocks.STRIPPED_OAK_LOG;
            case SAND -> Blocks.TERRACOTTA;
            case SNOW -> Blocks.SPRUCE_LOG;
        };
    }

    /** The post of a street lantern. */
    public Block lanternPost() {
        return switch (this) {
            case LEAF, SNOW -> Blocks.STONE_BRICK_WALL;
            case SAND -> Blocks.SANDSTONE_WALL;
        };
    }

    /** The great gate's door leaves. */
    public Block gateDoor() {
        return switch (this) {
            case LEAF -> Blocks.GREEN_TERRACOTTA;
            case SAND -> Blocks.ACACIA_PLANKS;
            case SNOW -> Blocks.SPRUCE_PLANKS;
        };
    }

    /** How each kind of street is paved. */
    public Block paving(VillagePlan.Paving paving) {
        return switch (this) {
            case LEAF -> switch (paving) {
                case AVENUE -> Blocks.PACKED_MUD;
                case STREET -> Blocks.DIRT_PATH;
                case PATH -> Blocks.COARSE_DIRT;
                case PLAZA -> Blocks.STONE_BRICKS;
            };
            case SAND -> switch (paving) {
                case AVENUE, PLAZA -> Blocks.SMOOTH_SANDSTONE;
                case STREET -> Blocks.CUT_SANDSTONE;
                case PATH -> Blocks.SANDSTONE;
            };
            case SNOW -> switch (paving) {
                case AVENUE, PLAZA -> Blocks.POLISHED_ANDESITE;
                case STREET -> Blocks.STONE_BRICKS;
                case PATH -> Blocks.COARSE_DIRT;
            };
        };
    }

    /** Trees in parks and gardens: log and leaves. */
    public Block[] tree(int variant) {
        return switch (this) {
            case LEAF -> switch (variant % 4) {
                case 0 -> new Block[] {Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES};
                case 1 -> new Block[] {Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES};
                default -> new Block[] {Blocks.OAK_LOG, Blocks.OAK_LEAVES};
            };
            case SAND -> variant % 2 == 0 ? new Block[] {Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES} : new Block[] {Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES};
            case SNOW -> new Block[] {Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES};
        };
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

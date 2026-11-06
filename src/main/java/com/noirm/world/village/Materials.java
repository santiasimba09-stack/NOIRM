package com.noirm.world.village;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * Block mixes and colour schemes, so surfaces aren't one flat texture and streets aren't one
 * colour (the trick every good-looking village mod uses).
 *
 * <p>A {@link Mix} picks a block by weight from a hash of the (village-local) position and a seed:
 * the same spot always gets the same block, whichever chunk draws it. A {@link Colourway} is one
 * paint job for a building (walls, roof, timber, cloth); the planner gives each lot one, different
 * from its neighbours, so one house design reads as many.
 */
public final class Materials {
    private Materials() {}

    /** A weighted mix of blocks. */
    public static final class Mix {
        private final Block[] table;

        private Mix(Block[] table) {
            this.table = table;
        }

        /** {@code of(STONE_BRICKS, 55, CRACKED_STONE_BRICKS, 15, ...)}: blocks and their weights. */
        public static Mix of(Object... blocksAndWeights) {
            List<Block> table = new ArrayList<>();
            for (int i = 0; i + 1 < blocksAndWeights.length; i += 2) {
                Block block = (Block) blocksAndWeights[i];
                int weight = (Integer) blocksAndWeights[i + 1];
                for (int k = 0; k < weight; k++) {
                    table.add(block);
                }
            }
            if (table.isEmpty()) {
                throw new IllegalArgumentException("empty mix");
            }
            return new Mix(table.toArray(new Block[0]));
        }

        /** A mix of just one block. */
        public static Mix one(Block block) {
            return new Mix(new Block[] {block});
        }

        /** The block at a position (stable: depends only on the position and the seed). */
        public Block at(int x, int y, int z, long seed) {
            return table[(int) Math.floorMod(hash(x, y, z, seed), (long) table.length)];
        }

        /**
         * The block at a position, shaped like {@code shape} (stairs keep their facing and half,
         * slabs their type, logs their axis) when the picked block has the same properties.
         */
        public BlockState at(int x, int y, int z, long seed, BlockState shape) {
            return copyShape(shape, at(x, y, z, seed).defaultBlockState());
        }

        /** The block that makes up most of the mix. */
        public Block main() {
            return table[0];
        }
    }

    /**
     * One paint job: wall plaster, an accent for bands and wainscots, the flat-roof deck, roof tiles
     * (stairs and the matching slab), timber for posts and beams, and a cloth colour for curtains
     * and awnings (a wall banner).
     */
    public record Colourway(Block wall, Block accent, Block roofFlat, Block roofStairs, Block roofSlab, Block trim, Block cloth) {}

    /** Copies the shape properties (facing, half, axis, type…) that both states share. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static BlockState copyShape(BlockState from, BlockState to) {
        for (Property property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = to.setValue(property, from.getValue(property));
            }
        }
        return to;
    }

    static long hash(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xD6E8FEB86659FD93L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h & Long.MAX_VALUE;
    }
}

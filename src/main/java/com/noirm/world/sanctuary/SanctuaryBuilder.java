package com.noirm.world.sanctuary;

import com.noirm.entity.MasterNinja;
import com.noirm.master.MasterData;
import com.noirm.registry.NoirmEntities;
import com.noirm.senjutsu.Sage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Builds the Stone Hermit's sanctuary, procedurally like our villages (no structure files),
 * around {@code origin} (the floor: you walk at {@code origin.y}). Everything inside {@code clip}
 * is drawn, so world generation can build it one chunk at a time; randomness comes from a hash of
 * the seed and position, so every chunk agrees.
 *
 * <p>The layout (north is up):
 * <pre>
 *            ▲▲▲ rock cliff ▲▲▲        a waterfall pours down a notch into
 *            ▲▲▲▲▲▲▌▐▲▲▲▲▲▲▲▲        the sacred pool (lily pads, mossy rim)
 *        ■      ~~~~~~~~~      ■     stone pillars ring the clearing, lanterns on some
 *      ■   ■   ~~~ pool ~~~   ■  ┌──┐ the shrine to the east (posts, a dark roof,
 *               ▫  ▫  ▫  H       │▣ │ a red beam, an altar with a lantern)
 *      ■  ☗        ║           ■ └──┘ ▫ meditation stones, H the hermit
 *        ■         ║         ■        ☗ statues: students who turned to stone
 *               ╔══╩══╗               the torii gate (red posts, black lintel) and the path
 * </pre>
 */
public final class SanctuaryBuilder {
    /** The clearing's radius. */
    public static final int RADIUS = 13;
    /** The sacred pool's centre (from the origin) and size. */
    public static final int POOL_Z = -6;
    public static final double POOL_RX = 4.6, POOL_RZ = 3.6;
    /** Where the hermit stands (beside the meditation stones, facing the pool). */
    public static final int MASTER_X = 2, MASTER_Z = 0;
    /** The shrine's centre. */
    public static final int SHRINE_X = 9;
    /** The torii gate's row. */
    public static final int GATE_Z = 12;

    private SanctuaryBuilder() {}

    /** The piece's box: the clearing and the cliff, from just under the floor up. */
    public static BoundingBox box(BlockPos origin) {
        int r = RADIUS + 3;
        return new BoundingBox(origin.getX() - r, origin.getY() - 1, origin.getZ() - r, origin.getX() + r, origin.getY() + 16, origin.getZ() + r);
    }

    /** Everything the builder touches (a command build clips to this). */
    public static BoundingBox fullBox(BlockPos origin) {
        int r = RADIUS + 3;
        return new BoundingBox(origin.getX() - r, origin.getY() - 12, origin.getZ() - r, origin.getX() + r, origin.getY() + 22, origin.getZ() + r);
    }

    /** Where the hermit stands. */
    public static BlockPos masterPos(BlockPos origin) {
        return origin.offset(MASTER_X, 0, MASTER_Z);
    }

    /** Is (dx, dz) inside the sacred pool? (0 = centre, 1 = its edge.) */
    public static double pool(int dx, int dz) {
        double x = dx / POOL_RX, z = (dz - POOL_Z) / POOL_RZ;
        return x * x + z * z;
    }

    /** Builds everything inside {@code clip}; spawns the hermit if {@code master} and his spot is inside. */
    public static void build(ServerLevelAccessor level, BoundingBox clip, BlockPos origin, long seed, boolean master) {
        Canvas c = new Canvas(level, clip, origin, seed);
        ground(c);
        pool(c);
        cliff(c);
        pillars(c);
        shrine(c);
        gate(c);
        stones(c);
        plants(c);
        if (master && clip.isInside(masterPos(origin))) {
            spawnHermit(level, origin);
        }
    }

    // ---------------------------------------------------------------- the clearing

    /** Clear the trees and hills, fill the hollows, lay the floor (grass and moss, a stone path, a stone plaza by the pool). */
    private static void ground(Canvas c) {
        int r = RADIUS + 3;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > RADIUS + 2.5) {
                    continue;
                }
                boolean inside = d <= RADIUS;
                for (int dy = 0; dy <= 20; dy++) {
                    c.set(dx, dy, dz, Blocks.AIR.defaultBlockState());
                }
                for (int dy = -2; dy >= -10 && c.inside(dx, dy, dz); dy--) {
                    BlockState below = c.get(dx, dy, dz);
                    if (below.isAir() || !below.getFluidState().isEmpty() || below.canBeReplaced()) {
                        c.set(dx, dy, dz, dy >= -4 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
                    }
                }
                c.set(dx, -1, dz, inside ? floor(c, dx, dz) : Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
    }

    private static BlockState floor(Canvas c, int dx, int dz) {
        double h = c.hash(dx, 0, dz, 1);
        if (Math.abs(dx) <= 1 && dz >= 0 && dz <= GATE_Z + 1) {
            return (h < 0.55 ? Blocks.STONE_BRICKS : h < 0.85 ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
        }
        double p = pool(dx, dz);
        if (p <= 2.6) {
            return (h < 0.4 ? Blocks.MOSSY_COBBLESTONE : h < 0.7 ? Blocks.STONE : h < 0.9 ? Blocks.MOSSY_STONE_BRICKS : Blocks.ANDESITE).defaultBlockState();
        }
        return (h < 0.62 ? Blocks.GRASS_BLOCK : h < 0.86 ? Blocks.MOSS_BLOCK : h < 0.95 ? Blocks.PODZOL : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
    }

    /** The sacred pool: two deep, a clay and gravel bed, a mossy rim, lily pads. */
    private static void pool(Canvas c) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = POOL_Z - 5; dz <= POOL_Z + 5; dz++) {
                double p = pool(dx, dz);
                if (p <= 1.0) {
                    c.set(dx, -1, dz, Blocks.WATER.defaultBlockState());
                    c.set(dx, -2, dz, Blocks.WATER.defaultBlockState());
                    double h = c.hash(dx, -3, dz, 2);
                    c.set(dx, -3, dz, (h < 0.45 ? Blocks.CLAY : h < 0.8 ? Blocks.GRAVEL : Blocks.MOSSY_COBBLESTONE).defaultBlockState());
                    if (p <= 0.8 && c.hash(dx, 0, dz, 3) < 0.13) {
                        c.set(dx, 0, dz, Blocks.LILY_PAD.defaultBlockState());
                    }
                } else if (p <= 1.6) {
                    c.set(dx, -1, dz, (c.hash(dx, -1, dz, 4) < 0.5 ? Blocks.MOSSY_STONE_BRICKS : Blocks.MOSSY_COBBLESTONE).defaultBlockState());
                    c.set(dx, -2, dz, Blocks.STONE.defaultBlockState());
                }
            }
        }
    }

    /** The rock cliff behind the pool, the waterfall down its notch, vines on its face. */
    private static void cliff(Canvas c) {
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -RADIUS - 3; dz <= -10; dz++) {
                double edge = dx / 9.0;
                int height = (int) Math.round(11 - 5 * edge * edge - Math.max(0, dz + 12) + 2 * c.hash(dx, 0, dz, 5));
                if (Math.abs(dx) == 9) {
                    height -= 3;
                }
                for (int dy = -1; dy < height; dy++) {
                    c.set(dx, dy, dz, rock(c, dx, dy, dz));
                }
                c.set(dx, height - 1, dz, (c.hash(dx, height, dz, 6) < 0.6 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK).defaultBlockState());
                if (dz == -10 && (dx == -1 || dx == 0)) {
                    // The notch: a column of water from the top down into the pool.
                    for (int dy = -2; dy < height - 1; dy++) {
                        c.set(dx, dy, dz, Blocks.WATER.defaultBlockState());
                    }
                } else if (dz == -10 && Math.abs(dx) >= 3) {
                    for (int dy = 2; dy < height - 1; dy++) {
                        if (c.hash(dx, dy, dz, 7) < 0.3 && c.inside(dx, dy, dz + 1) && c.get(dx, dy, dz + 1).isAir()) {
                            c.set(dx, dy, dz + 1, Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true));
                        }
                    }
                }
            }
        }
    }

    private static BlockState rock(Canvas c, int dx, int dy, int dz) {
        double h = c.hash(dx, dy, dz, 8);
        Block block = h < 0.42 ? Blocks.STONE : h < 0.6 ? Blocks.ANDESITE : h < 0.74 ? Blocks.MOSSY_COBBLESTONE
                : h < 0.84 ? Blocks.COBBLESTONE : h < 0.94 ? Blocks.TUFF : Blocks.DEEPSLATE;
        return block.defaultBlockState();
    }

    // ---------------------------------------------------------------- what stands in it

    /** Mossy stone pillars in a ring, chiseled tops, lanterns on some; two tall ones flank the pool. */
    private static void pillars(Canvas c) {
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30 + 15);
            int px = (int) Math.round(10.5 * Math.cos(a)), pz = (int) Math.round(10.5 * Math.sin(a));
            if (pz < -7 || (px > 5 && Math.abs(pz) < 5) || (Math.abs(px) <= 2 && pz > 0)) {
                continue; // the cliff, the shrine, the path
            }
            pillar(c, px, pz, 3 + (int) (c.hash(px, 1, pz, 9) * 3.99));
        }
        pillar(c, -6, -2, 6);
        pillar(c, 6, -2, 6);
    }

    private static void pillar(Canvas c, int x, int z, int height) {
        c.set(x, -1, z, Blocks.STONE_BRICKS.defaultBlockState());
        for (int dy = 0; dy < height - 1; dy++) {
            double h = c.hash(x, dy, z, 10);
            c.set(x, dy, z, (h < 0.5 ? Blocks.STONE_BRICKS : h < 0.85 ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).defaultBlockState());
        }
        c.set(x, height - 1, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        if (height >= 5 || c.hash(x, height, z, 11) < 0.5) {
            c.set(x, height, z, Blocks.LANTERN.defaultBlockState());
        }
    }

    /** The shrine: posts, half walls, a red beam, a dark two-step roof, an altar with a lantern, cushions. */
    private static void shrine(Canvas c) {
        int x0 = SHRINE_X - 2, x1 = SHRINE_X + 2;
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int dy = 0; dy <= 6; dy++) {
                    c.set(x, dy, z, Blocks.AIR.defaultBlockState());
                }
                if (x >= x0 && x <= x1 && Math.abs(z) <= 2) {
                    c.set(x, -1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                }
            }
        }
        for (int x : new int[] {x0, x1}) {
            for (int z : new int[] {-2, 2}) {
                for (int dy = 0; dy <= 3; dy++) {
                    c.set(x, dy, z, Blocks.DARK_OAK_LOG.defaultBlockState());
                }
            }
        }
        for (int z = -1; z <= 1; z++) {
            for (int dy = 0; dy <= 2; dy++) {
                c.set(x1, dy, z, Blocks.SPRUCE_PLANKS.defaultBlockState()); // the back wall
            }
        }
        for (int x = x0 + 1; x < x1; x++) {
            for (int z : new int[] {-2, 2}) {
                c.set(x, 0, z, Blocks.SPRUCE_PLANKS.defaultBlockState()); // low side walls
                c.set(x, 3, z, Blocks.MANGROVE_PLANKS.defaultBlockState()); // the red beam
            }
        }
        for (int z = -1; z <= 1; z++) {
            c.set(x0, 3, z, Blocks.MANGROVE_PLANKS.defaultBlockState());
        }
        BlockState slab = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean rim = x == x0 - 1 || x == x1 + 1 || Math.abs(z) == 3;
                c.set(x, 4, z, rim ? slab : Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = -1; z <= 1; z++) {
                c.set(x, 5, z, slab);
            }
        }
        c.set(SHRINE_X, 5, 0, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        c.set(SHRINE_X, 6, 0, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
        // Inside: the altar, a lantern, two cushions.
        c.set(x1 - 1, 0, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        c.set(x1 - 1, 1, 0, Blocks.SOUL_LANTERN.defaultBlockState());
        c.set(x0 + 1, 0, -1, Blocks.MOSS_CARPET.defaultBlockState());
        c.set(x0 + 1, 0, 1, Blocks.MOSS_CARPET.defaultBlockState());
    }

    /** The torii gate on the path: red posts and beam, a black lintel with lifted ends. */
    private static void gate(Canvas c) {
        BlockState red = Blocks.RED_TERRACOTTA.defaultBlockState();
        for (int x : new int[] {-3, 3}) {
            for (int dy = 0; dy <= 3; dy++) {
                c.set(x, dy, GATE_Z, red);
            }
        }
        for (int x = -2; x <= 2; x++) {
            c.set(x, 3, GATE_Z, red);
        }
        for (int x = -4; x <= 4; x++) {
            c.set(x, 4, GATE_Z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        c.set(-4, 5, GATE_Z, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
        c.set(4, 5, GATE_Z, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
    }

    /** Meditation stones before the pool, and statues of students who held too much. */
    private static void stones(Canvas c) {
        BlockState seat = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        for (int x : new int[] {-3, 0, 3}) {
            c.set(x, 0, -1, seat);
        }
        statue(c, -9, -2);
        statue(c, -8, 4);
    }

    private static void statue(Canvas c, int x, int z) {
        c.set(x, -1, z, Blocks.STONE_BRICKS.defaultBlockState());
        c.set(x, 0, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
        c.set(x, 1, z, Blocks.ANDESITE_WALL.defaultBlockState());
        c.set(x, 2, z, Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
    }

    /** Ferns and grass on the grass, moss carpet on the moss, a few azalea bushes near the edge. */
    private static void plants(Canvas c) {
        int r = RADIUS + 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > (RADIUS + 2) * (RADIUS + 2) || !c.inside(dx, 0, dz) || !c.get(dx, 0, dz).isAir()) {
                    continue;
                }
                BlockState ground = c.get(dx, -1, dz);
                double h = c.hash(dx, 0, dz, 12);
                if (ground.is(Blocks.GRASS_BLOCK)) {
                    if (h < 0.025 && dx * dx + dz * dz > 81) {
                        c.set(dx, 0, dz, (c.hash(dx, 1, dz, 13) < 0.5 ? Blocks.AZALEA : Blocks.FLOWERING_AZALEA).defaultBlockState());
                    } else if (h < 0.2) {
                        c.set(dx, 0, dz, Blocks.FERN.defaultBlockState());
                    } else if (h < 0.32) {
                        c.set(dx, 0, dz, Blocks.GRASS.defaultBlockState());
                    }
                } else if (ground.is(Blocks.MOSS_BLOCK) && h < 0.3) {
                    c.set(dx, 0, dz, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
    }

    // ---------------------------------------------------------------- the hermit

    private static void spawnHermit(ServerLevelAccessor level, BlockPos origin) {
        if (MasterData.get(Sage.HERMIT) == null) {
            return;
        }
        MasterNinja hermit = NoirmEntities.MASTER.get().create(level.getLevel());
        if (hermit == null) {
            return;
        }
        BlockPos at = masterPos(origin);
        hermit.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
        hermit.setYHeadRot(180f);
        hermit.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
        hermit.setMaster(Sage.HERMIT);
        hermit.restrictTo(at, MasterNinja.HOME_RADIUS);
        hermit.setPersistenceRequired();
        level.addFreshEntityWithPassengers(hermit);
    }

    // ---------------------------------------------------------------- drawing

    /** Draws relative to the origin, only inside the clip; randomness hashed from seed and position. */
    private record Canvas(ServerLevelAccessor level, BoundingBox clip, BlockPos origin, long seed) {
        void set(int dx, int dy, int dz, BlockState state) {
            BlockPos pos = origin.offset(dx, dy, dz);
            if (clip.isInside(pos)) {
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
        }

        boolean inside(int dx, int dy, int dz) {
            return clip.isInside(origin.offset(dx, dy, dz));
        }

        BlockState get(int dx, int dy, int dz) {
            return level.getBlockState(origin.offset(dx, dy, dz));
        }

        /** A steady random number in [0, 1) for this spot. */
        double hash(int x, int y, int z, int salt) {
            long h = seed ^ 0x9E3779B97F4A7C15L * (salt + 1);
            h ^= x * 0x632BE59BD9B4E019L;
            h = Long.rotateLeft(h, 23) ^ y * 0xBF58476D1CE4E5B9L;
            h = Long.rotateLeft(h, 31) ^ z * 0x94D049BB133111EBL;
            h ^= h >>> 33;
            h *= 0xFF51AFD7ED558CCDL;
            h ^= h >>> 33;
            return (h >>> 11) * 0x1.0p-53;
        }
    }
}

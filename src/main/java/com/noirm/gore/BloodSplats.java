package com.noirm.gore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Blood on the ground: splats and pools lying flat on block tops, as plain data (no world block
 * changes, nothing saved). The client keeps one store ({@link #CLIENT}), fed by blood drops that
 * land and pools the server asks for, and {@code client/GoreDecals} draws it; game tests make
 * their own stores. Fixed arrays and a ring buffer: at most {@link #MAX} stains, the oldest
 * overwritten first, and adding or reading one allocates nothing.
 *
 * <p>Each stain sits on one surface and never hangs over an edge: its size is limited to the block
 * top it landed on plus any neighbours at the same height. A drop landing on a fresh stain nearby
 * makes that stain bigger (and wet again) instead of adding another. Stains dry darker over
 * {@link #DRY_TICKS} and fade away at the end of their life.
 */
public final class BloodSplats {
    /** The most stains at once (the oldest is overwritten). */
    public static final int MAX = 256;
    /** How long a splat and a pool last, and how long they take to fade at the end. */
    public static final int SPLAT_LIFE = 20 * 120;
    public static final int POOL_LIFE = 20 * 180;
    public static final int FADE_TICKS = 20 * 20;
    /** Fresh red → dried dark brown over this long. */
    public static final int DRY_TICKS = 20 * 45;
    /** A pool spreads out to its full size over this long. */
    public static final int GROW_TICKS = 50;
    /** Drops landing this close to a stain (plus a share of its size) join it. */
    public static final float MERGE_DISTANCE = 0.18f;
    public static final float MAX_RADIUS = 1.15f;
    /** Texture cells in {@code blood_decals.png}: six splat shapes, then two pools. */
    public static final int SPLAT_VARIANTS = 6;
    public static final int POOL_VARIANTS = 2;
    /** Each stain lifts this far above the block (and a hair more per slot, so overlaps don't flicker). */
    static final float LIFT = 0.008f;

    /** The client's stains (only touched on the client thread). */
    public static final BloodSplats CLIENT = new BloodSplats(MAX);

    private final int capacity;
    private final float[] x, y, z, radius, angle, cos, sin;
    private final long[] born;
    private final byte[] variant;
    private final boolean[] pool;
    private final boolean[] used;
    private int next;
    private Level world;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public BloodSplats(int capacity) {
        this.capacity = capacity;
        x = new float[capacity];
        y = new float[capacity];
        z = new float[capacity];
        radius = new float[capacity];
        angle = new float[capacity];
        cos = new float[capacity];
        sin = new float[capacity];
        born = new long[capacity];
        variant = new byte[capacity];
        pool = new boolean[capacity];
        used = new boolean[capacity];
    }

    public int capacity() {
        return capacity;
    }

    /** The level these stains lie in (null when empty). */
    public Level world() {
        return world;
    }

    public void clear() {
        java.util.Arrays.fill(used, false);
        next = 0;
        world = null;
    }

    /**
     * A stain of {@code size} (half-width, in blocks) where something landed near ({@code px},
     * {@code py}, {@code pz}): on the first solid surface at or just below it, if there is one
     * and it isn't under water. Returns the slot used, or -1 if nothing could be placed.
     */
    public int add(Level level, double px, double py, double pz, float size, boolean isPool, RandomSource random) {
        if (level != world) {
            clear();
            world = level;
        }
        double surface = surface(level, px, py, pz);
        if (Double.isNaN(surface)) {
            return -1;
        }
        long now = level.getGameTime();
        float sy = (float) surface;
        // Joins a stain close by on the same surface.
        for (int i = 0; i < capacity; i++) {
            if (!used[i] || alpha(i, now, 0f) <= 0f || Math.abs(y[i] - LIFT - sy) > 0.05f) {
                continue;
            }
            float reach = MERGE_DISTANCE + radius[i] * 0.5f;
            double dx = x[i] - px, dz = z[i] - pz;
            if (dx * dx + dz * dz < reach * reach) {
                float limit = limit(level, x[i], z[i], sy, cos[i], sin[i]);
                float grown = (float) Math.sqrt(radius[i] * radius[i] + size * size * (isPool ? 1f : 0.5f));
                radius[i] = Math.max(radius[i], Math.min(Math.min(MAX_RADIUS, limit), grown));
                pool[i] |= isPool;
                born[i] = now; // wet again
                return i;
            }
        }
        float a = random.nextFloat() * Mth.TWO_PI;
        float c = Mth.cos(a), s = Mth.sin(a);
        float limit = limit(level, px, pz, sy, c, s);
        float r = Math.min(Math.min(size, MAX_RADIUS), limit);
        if (r < 0.04f) {
            return -1; // right on an edge: it ran off
        }
        int i = next;
        next = (next + 1) % capacity;
        used[i] = true;
        x[i] = (float) px;
        z[i] = (float) pz;
        y[i] = sy + LIFT + (i & 7) * 0.0015f;
        radius[i] = r;
        angle[i] = a;
        cos[i] = c;
        sin[i] = s;
        born[i] = now;
        pool[i] = isPool;
        variant[i] = (byte) (isPool ? SPLAT_VARIANTS + random.nextInt(POOL_VARIANTS) : random.nextInt(SPLAT_VARIANTS));
        return i;
    }

    /** The top of the first solid surface at or just below the point (NaN: none, or under water). */
    private double surface(Level level, double px, double py, double pz) {
        int bx = Mth.floor(px), bz = Mth.floor(pz);
        int start = Mth.floor(py + 0.3);
        for (int by = start; by >= start - 3; by--) {
            pos.set(bx, by, bz);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double top = by + shape.max(Direction.Axis.Y);
            if (top > py + 0.4) {
                return Double.NaN; // inside a block (it hit a wall)
            }
            pos.set(bx, Mth.floor(top + 0.01), bz);
            if (!level.getFluidState(pos).isEmpty()) {
                return Double.NaN; // blood washes away in water
            }
            return top;
        }
        return Double.NaN;
    }

    /**
     * The biggest half-width a stain turned by ({@code c}, {@code s}) can have at (px, pz) without
     * hanging over the edge of its surface (the block top, widened by neighbours at the same height).
     */
    private float limit(Level level, double px, double pz, float surface, float c, float s) {
        int bx = Mth.floor(px), bz = Mth.floor(pz);
        double x0 = bx - (level(level, bx - 1, bz, surface) ? 1 : 0);
        double x1 = bx + 1 + (level(level, bx + 1, bz, surface) ? 1 : 0);
        double z0 = bz - (level(level, bx, bz - 1, surface) ? 1 : 0);
        double z1 = bz + 1 + (level(level, bx, bz + 1, surface) ? 1 : 0);
        double half = Math.min(Math.min(px - x0, x1 - px), Math.min(pz - z0, z1 - pz));
        return (float) (half / (Math.abs(c) + Math.abs(s))); // the turned square's footprint
    }

    /** Is the block column at (bx, bz) topped at the same height, with room above? */
    private boolean level(Level level, int bx, int bz, float surface) {
        int by = Mth.floor(surface - 0.01);
        pos.set(bx, by, bz);
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (shape.isEmpty() || Math.abs(by + shape.max(Direction.Axis.Y) - surface) > 0.01) {
            return false;
        }
        pos.set(bx, by + 1, bz);
        return !level.getBlockState(pos).isSolidRender(level, pos) && level.getFluidState(pos).isEmpty();
    }

    // ---------------------------------------------------------------- reading (drawing, tests)

    public boolean used(int i) {
        return used[i];
    }

    public float x(int i) {
        return x[i];
    }

    public float y(int i) {
        return y[i];
    }

    public float z(int i) {
        return z[i];
    }

    public float cos(int i) {
        return cos[i];
    }

    public float sin(int i) {
        return sin[i];
    }

    public int variant(int i) {
        return variant[i];
    }

    public boolean pool(int i) {
        return pool[i];
    }

    /** Its full half-width, in blocks. */
    public float size(int i) {
        return radius[i];
    }

    /** Its size now: a pool spreads out over {@link #GROW_TICKS}. */
    public float radius(int i, long now, float partialTick) {
        if (!pool[i]) {
            return radius[i];
        }
        float t = Math.min(1f, (now - born[i] + partialTick) / GROW_TICKS);
        float ease = 1f - (1f - t) * (1f - t);
        return radius[i] * Math.max(0.4f + 0.6f * ease, 0.4f);
    }

    /** 1 while it lies there, falling to 0 over the last {@link #FADE_TICKS} of its life (0: gone). */
    public float alpha(int i, long now, float partialTick) {
        if (!used[i]) {
            return 0f;
        }
        float age = now - born[i] + partialTick;
        int life = pool[i] ? POOL_LIFE : SPLAT_LIFE;
        if (age >= life || age < -40) {
            return 0f;
        }
        return Math.min(1f, (life - age) / FADE_TICKS);
    }

    /** 0 fresh and wet, 1 dried. */
    public float dryness(int i, long now) {
        return Mth.clamp((now - born[i]) / (float) DRY_TICKS, 0f, 1f);
    }

    /** Stains still showing at {@code now}. */
    public int count(long now) {
        int n = 0;
        for (int i = 0; i < capacity; i++) {
            if (alpha(i, now, 0f) > 0f) {
                n++;
            }
        }
        return n;
    }
}

package com.noirm.dojutsu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Byakugan's x-ray scan (ores and containers through stone), done <b>a slice at a time</b>.
 *
 * <p>Before wave "Hyūga" the client scanned the whole sphere in one frame once a second (up to 274,000 blocks at the
 * widest x-ray), which stuttered the game. Now {@link #step} looks at at most {@link #BLOCKS_PER_STEP} blocks per call
 * (one call per client tick), builds the next result in the background and swaps it in when a pass is done; only the
 * {@link #MAX_RESULTS} nearest finds are kept. No client classes: game tests time it on the server level.
 */
public final class XRayScanner {
    /** Blocks looked at per tick. A 16-block sphere (~17k blocks) takes about 4 ticks; the widest (32) about a second and a half. */
    public static final int BLOCKS_PER_STEP = 12_000;
    /** At most this many finds are drawn: the nearest. */
    public static final int MAX_RESULTS = 96;
    /** A new pass starts at most this often (ticks), or as soon as the eye moves this many blocks. */
    public static final int REFRESH_TICKS = 40;
    public static final int MOVE_RESCAN = 3;

    /** A find: block position and what it is. */
    public record Find(int x, int y, int z, boolean container) {}

    private List<Find> shown = List.of();
    private final List<Find> building = new ArrayList<>();
    private BlockPos centre;
    private int radius;
    /** The pass in progress walks a cube index from 0 to (2r+1)³; -1 = idle. */
    private long cursor = -1;
    private long lastPass = Long.MIN_VALUE / 2;

    /** What to draw now (the last finished pass). */
    public List<Find> finds() {
        return shown;
    }

    /** Forgets everything (the eye closed). */
    public void reset() {
        shown = List.of();
        building.clear();
        cursor = -1;
        centre = null;
    }

    /**
     * One tick of scanning around {@code eye} out to {@code range} blocks. Returns how many blocks it looked at (0 when
     * idle), for timing.
     */
    public int step(Level level, BlockPos eye, double range, long gameTime) {
        int r = (int) Math.ceil(range);
        if (cursor < 0) {
            boolean moved = centre == null || centre.distManhattan(eye) >= MOVE_RESCAN || r != radius;
            if (!moved && gameTime - lastPass < REFRESH_TICKS && gameTime >= lastPass) {
                return 0;
            }
            centre = eye.immutable();
            radius = r;
            cursor = 0;
            building.clear();
        }
        int side = 2 * radius + 1;
        long total = (long) side * side * side;
        int r2 = radius * radius;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int looked = 0;
        while (cursor < total && looked < BLOCKS_PER_STEP) {
            long i = cursor++;
            int dx = (int) (i % side) - radius;
            int dz = (int) (i / side % side) - radius;
            int dy = (int) (i / ((long) side * side)) - radius;
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            looked++;
            at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
            if (!level.isLoaded(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (state.isAir()) {
                continue;
            }
            if (state.is(net.minecraftforge.common.Tags.Blocks.ORES)) {
                building.add(new Find(at.getX(), at.getY(), at.getZ(), false));
            } else if (state.hasBlockEntity() && level.getBlockEntity(at) instanceof net.minecraft.world.Container) {
                building.add(new Find(at.getX(), at.getY(), at.getZ(), true));
            }
        }
        if (cursor >= total) {
            finish();
            lastPass = gameTime;
        }
        return looked;
    }

    private void finish() {
        BlockPos c = centre;
        building.sort(Comparator.comparingDouble(f -> {
            double dx = f.x() - c.getX();
            double dy = f.y() - c.getY();
            double dz = f.z() - c.getZ();
            return dx * dx + dy * dy + dz * dz;
        }));
        shown = List.copyOf(building.subList(0, Math.min(MAX_RESULTS, building.size())));
        building.clear();
        cursor = -1;
    }

    /** True while a pass is in progress. */
    public boolean scanning() {
        return cursor >= 0;
    }
}

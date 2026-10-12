package com.noirm.world;

import com.noirm.Noirm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Jutsu and big hits damage the world: craters, scorched ground, frozen water, spikes, smashed
 * walls. Two game rules control it:
 * <ul>
 *   <li>{@code noirmTerrainDamage} (default true): off = no block changes at all.</li>
 *   <li>{@code noirmTerrainRegen} (default 60): seconds until the terrain heals itself; 0 =
 *       damage is permanent.</li>
 * </ul>
 * Never touches blocks with contents (chests, furnaces…), unbreakable blocks, or anything harder
 * than the hit can break; nothing drops items. Everything still waiting to heal is restored
 * when the server stops.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Destruction {
    public static GameRules.Key<GameRules.BooleanValue> TERRAIN_DAMAGE;
    public static GameRules.Key<GameRules.IntegerValue> TERRAIN_REGEN;

    /** A changed block and what it was. */
    private record Change(ResourceKey<Level> dimension, BlockPos pos, BlockState original, BlockState placed, long restoreAt) {}

    private static final List<Change> PENDING = new ArrayList<>();
    /** The position of each pending change, so a block changed twice keeps its true original. */
    private static final Map<BlockPos, Change> BY_POS = new HashMap<>();

    private Destruction() {}

    /** Called once while the mod loads. */
    public static void registerGameRules() {
        TERRAIN_DAMAGE = GameRules.register("noirmTerrainDamage", GameRules.Category.MISC, GameRules.BooleanValue.create(true));
        TERRAIN_REGEN = GameRules.register("noirmTerrainRegen", GameRules.Category.MISC, GameRules.IntegerValue.create(60));
    }

    public static boolean enabled(ServerLevel level) {
        return TERRAIN_DAMAGE == null || level.getGameRules().getBoolean(TERRAIN_DAMAGE);
    }

    /** The hardest block a hit of this power can break: dirt and wood at 1, stone at ~1.5, obsidian never. */
    public static float maxHardness(float power) {
        return Mth.clamp(0.8f + power * 1.2f, 0.5f, 6f);
    }

    // ---------------------------------------------------------------- what jutsu can do

    /** A bowl-shaped crater of {@code radius}, ragged at the edges. */
    public static int crater(ServerLevel level, Vec3 center, float radius, float power) {
        if (!enabled(level) || radius < 0.8f) {
            return 0;
        }
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        float hardness = maxHardness(power);
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r / 2, r))) {
            double dist = Math.sqrt(pos.distToCenterSqr(center.x, center.y, center.z));
            double edge = radius * (0.75 + 0.25 * level.random.nextDouble());
            if (dist <= edge && breakBlock(level, pos.immutable(), hardness)) {
                broken++;
            }
        }
        if (broken > 0) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.6f, 0.8f);
        }
        return broken;
    }

    /** Fire takes the ground: grass and leaves burn away, fire springs up on top. */
    public static void scorch(ServerLevel level, Vec3 center, float radius) {
        if (!enabled(level)) {
            return;
        }
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -2, -r), origin.offset(r, 2, r))) {
            if (pos.distToCenterSqr(center.x, center.y, center.z) > radius * radius) {
                continue;
            }
            BlockPos at = pos.immutable();
            BlockState state = level.getBlockState(at);
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)) {
                change(level, at, Blocks.AIR.defaultBlockState(), 1f);
            } else if (state.is(Blocks.GRASS_BLOCK) && level.random.nextFloat() < 0.6f) {
                change(level, at, Blocks.DIRT.defaultBlockState(), 1f);
            } else if (state.isAir() && level.random.nextFloat() < 0.35f && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
                change(level, at, BaseFireBlock.getState(level, at), 0.15f);
            }
        }
    }

    /** Water freezes, snow settles. */
    public static void freeze(ServerLevel level, Vec3 center, float radius) {
        if (!enabled(level)) {
            return;
        }
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -2, -r), origin.offset(r, 2, r))) {
            if (pos.distToCenterSqr(center.x, center.y, center.z) > radius * radius) {
                continue;
            }
            BlockPos at = pos.immutable();
            BlockState state = level.getBlockState(at);
            if (state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource()) {
                change(level, at, Blocks.ICE.defaultBlockState(), 1f);
            } else if (state.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, at) && level.random.nextFloat() < 0.6f) {
                change(level, at, Blocks.SNOW.defaultBlockState(), 0.5f);
            } else if (state.getBlock() instanceof BaseFireBlock) {
                change(level, at, Blocks.AIR.defaultBlockState(), 1f);
            }
        }
    }

    /** Water puts out fire. */
    public static void douse(ServerLevel level, Vec3 center, float radius) {
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -2, -r), origin.offset(r, 2, r))) {
            if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                level.removeBlock(pos.immutable(), false);
            }
        }
    }

    /** Spikes of {@code block} burst out of the ground (they crumble back after a short while). */
    public static void spikes(ServerLevel level, Vec3 center, float radius, BlockState block, int maxHeight) {
        if (!enabled(level)) {
            return;
        }
        int count = 3 + (int) (radius * 2.5f);
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(level.random.nextDouble()) * radius;
            BlockPos base = BlockPos.containing(center.x + Math.cos(angle) * dist, center.y, center.z + Math.sin(angle) * dist);
            int height = 1 + level.random.nextInt(Math.max(1, maxHeight));
            for (int h = 0; h < height; h++) {
                BlockPos at = base.above(h);
                if (!level.getBlockState(at).isAir() || !level.getEntities(null, new net.minecraft.world.phys.AABB(at)).isEmpty()) {
                    break;
                }
                change(level, at, block, 0.15f);
            }
        }
    }

    /** Fragile things around a heavy impact shatter: glass, leaves, plants, snow. */
    public static void shatterFragile(ServerLevel level, BlockPos center, int radius) {
        if (!enabled(level)) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (isFragile(state)) {
                BlockPos at = pos.immutable();
                level.levelEvent(2001, at, Block.getId(state)); // break particles + sound
                change(level, at, Blocks.AIR.defaultBlockState(), 1f);
            }
        }
    }

    /** A body slammed into a wall: the blocks around the impact give way if the hit was hard enough. */
    public static int smashWall(ServerLevel level, BlockPos center, float power) {
        if (!enabled(level)) {
            return 0;
        }
        float hardness = maxHardness(power);
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 1, 1))) {
            if (level.random.nextFloat() < 0.8f && breakBlock(level, pos.immutable(), hardness)) {
                broken++;
            }
        }
        return broken;
    }

    /** Turns the ground to mud for a while (Swamp of the Underworld). */
    public static void swamp(ServerLevel level, Vec3 center, float radius) {
        if (!enabled(level)) {
            return;
        }
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -2, -r), origin.offset(r, 1, r))) {
            BlockState state = level.getBlockState(pos);
            if (pos.distToCenterSqr(center.x, center.y, center.z) <= radius * radius && level.getBlockState(pos.above()).isAir()
                    && (state.is(BlockTags.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.STONE) || state.is(Blocks.GRAVEL))) {
                change(level, pos.immutable(), Blocks.MUD.defaultBlockState(), 0.5f);
            }
        }
    }

    /**
     * What a jutsu of {@code nature} does to the ground where it lands: fire burns, explosions
     * blast craters, water douses, ice freezes, wind strips fragile things away.
     */
    public static void natureImpact(ServerLevel level, Vec3 at, com.noirm.clan.Nature nature, float radius, float power) {
        if (nature == null) {
            crater(level, at, radius * 0.5f, power);
            return;
        }
        switch (nature) {
            case FIRE, SCORCH -> {
                scorch(level, at, radius);
                crater(level, at, radius * 0.3f, power);
            }
            case LAVA -> {
                scorch(level, at, radius);
                crater(level, at, radius * 0.45f, power);
            }
            case EXPLOSION, DUST -> crater(level, at, radius * 0.65f, power * 1.3f);
            case WATER, STORM, BOIL -> douse(level, at, radius + 1);
            case ICE -> freeze(level, at, radius);
            case WIND -> shatterFragile(level, BlockPos.containing(at), (int) Math.ceil(radius));
            case EARTH, WOOD, MAGNET, LIGHTNING -> crater(level, at, radius * 0.35f, power);
            case YIN, YANG, YIN_YANG -> { }
        }
    }

    // ---------------------------------------------------------------- internals

    public static boolean isFragile(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(BlockTags.IMPERMEABLE) || state.is(Blocks.SNOW)
                || state.is(Blocks.FERN) || state.is(Blocks.DEAD_BUSH);
    }

    private static boolean breakBlock(ServerLevel level, BlockPos pos, float maxHardness) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > maxHardness) {
            return false;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                4, 0.3, 0.3, 0.3, 0.1);
        change(level, pos, Blocks.AIR.defaultBlockState(), 1f);
        return true;
    }

    /**
     * Changes a block and remembers what it was. {@code regenScale} shortens the time to heal
     * (spikes and fire go away sooner than craters fill in).
     */
    private static void change(ServerLevel level, BlockPos pos, BlockState to, float regenScale) {
        BlockState before = level.getBlockState(pos);
        if (before.hasBlockEntity()) {
            return;
        }
        int regen = TERRAIN_REGEN == null ? 60 : level.getGameRules().getInt(TERRAIN_REGEN);
        if (regen > 0) {
            Change existing = BY_POS.get(pos);
            BlockState original = existing != null ? existing.original() : before;
            long at = level.getGameTime() + Math.max(40, Math.round(regen * 20 * regenScale)) + level.random.nextInt(40);
            Change change = new Change(level.dimension(), pos, original, to, at);
            if (existing != null) {
                PENDING.remove(existing);
            }
            PENDING.add(change);
            BY_POS.put(pos, change);
        }
        level.setBlock(pos, to, Block.UPDATE_ALL);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Change> it = PENDING.iterator();
        int restored = 0;
        while (it.hasNext() && restored < 64) {
            Change change = it.next();
            ServerLevel level = server.getLevel(change.dimension());
            if (level == null || level.getGameTime() >= change.restoreAt()) {
                if (level != null) {
                    restore(level, change);
                }
                it.remove();
                BY_POS.remove(change.pos());
                restored++;
            }
        }
    }

    /** Puts the original block back, unless someone has built something else there since. */
    private static void restore(ServerLevel level, Change change) {
        BlockState now = level.getBlockState(change.pos());
        boolean untouched = now.equals(change.placed()) || now.isAir() || now.getBlock() instanceof BaseFireBlock || now.is(Blocks.SNOW);
        if (untouched) {
            level.setBlock(change.pos(), change.original(), Block.UPDATE_ALL);
        }
    }

    /** Heal everything before the world shuts down, so holes are never left behind by accident. */
    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        for (Change change : PENDING) {
            ServerLevel level = event.getServer().getLevel(change.dimension());
            if (level != null) {
                restore(level, change);
            }
        }
        PENDING.clear();
        BY_POS.clear();
    }

    /** How many changes are waiting to heal (for tests). */
    public static int pending() {
        return PENDING.size();
    }
}

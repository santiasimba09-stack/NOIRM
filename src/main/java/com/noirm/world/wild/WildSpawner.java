package com.noirm.world.wild;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.entity.SparringNinja;
import com.noirm.gear.ClanOutfits;
import com.noirm.gear.NoirmGear;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.world.village.BuiltVillages;
import com.noirm.world.village.VillageAtmosphere;
import com.noirm.world.village.VillagePalette;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Brings ninjas into the wild: now and then, around each player, a group from
 * {@code data/noirm/encounters/} turns up on open land a little way off (never in water, caves
 * or villages, which have their own people). Not the vanilla mob caps: this spawner keeps its own.
 *
 * <p><b>How often:</b> every {@link #ATTEMPT_TICKS} per player there's a {@link #BASE_CHANCE}
 * chance of a group, scaled by the gamerule {@code noirmWildNinjas} (a percentage: 100 = normal,
 * 0 = off, up to 1000 for testing). At most {@link #MAX_GROUPS_NEAR} groups and
 * {@link #MAX_NINJAS_NEAR} wild ninjas within {@link #CAP_RADIUS} blocks of a player.
 *
 * <p><b>How strong:</b> the danger level ({@link #danger}) starts at {@link #BASE_DANGER} and grows
 * with the distance from the world spawn and with the time the player has played. Near the world
 * spawn nothing turns up for a new player's first {@link #SAFE_PLAY_MINUTES} minutes.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class WildSpawner {
    /** {@code noirmWildNinjas}: how often groups turn up, as a percentage of normal (0 = never). */
    public static GameRules.Key<GameRules.IntegerValue> RULE;
    public static final int MAX_RATE = 1000;

    // ---------------------------------------------------------------- how often
    /** Ticks between tries per player at the normal rate (30 s). */
    public static final int ATTEMPT_TICKS = 20 * 30;
    /** The chance a try brings a group (normal rate): about one group every minute and a half. */
    public static final float BASE_CHANCE = 0.35f;
    /** Never try more often than this (high rates). */
    public static final int MIN_ATTEMPT_TICKS = 40;
    /** How often wild ninjas far from everyone are swept away. */
    public static final int SWEEP_TICKS = 100;

    // ---------------------------------------------------------------- where
    /** A group turns up this far from the player (blocks). */
    public static final int MIN_DISTANCE = 40;
    public static final int MAX_DISTANCE = 60;
    /** Tries to find a spot per attempt. */
    public static final int SPOT_TRIES = 8;
    /** Caps around each player. */
    public static final double CAP_RADIUS = 128;
    public static final int MAX_GROUPS_NEAR = 2;
    public static final int MAX_NINJAS_NEAR = 8;
    /** Around the world spawn, nothing turns up until the player has played this long. */
    public static final int SPAWN_SAFE_RADIUS = 160;
    public static final int SAFE_PLAY_MINUTES = 20;
    /** Spots this close to a village (blocks) are skipped too. */
    public static final int VILLAGE_MARGIN = 24;

    // ---------------------------------------------------------------- how strong
    /** Danger at the world spawn for a new player: about a trainee (stats are 10 for a new player). */
    public static final float BASE_DANGER = 12f;
    /** +1 danger per this many blocks from the world spawn, up to {@link #DISTANCE_DANGER_MAX}. */
    public static final float BLOCKS_PER_DANGER = 60f;
    public static final float DISTANCE_DANGER_MAX = 50f;
    /** +this much danger per hour played, up to {@link #TIME_DANGER_MAX}. */
    public static final float DANGER_PER_HOUR = 4f;
    public static final float TIME_DANGER_MAX = 25f;
    /** Each ninja's stats vary by ±15 %. */
    public static final float STAT_SPREAD = 0.15f;

    /** What a try came to (for tests and the log). */
    public enum Result { SPAWNED, DISABLED, TOO_EARLY, CAPPED, NO_SPOT, NO_ENCOUNTER }

    private static final Map<UUID, Long> NEXT_TRY = new HashMap<>();

    private WildSpawner() {}

    /** Called once while the mod loads. */
    public static void registerGameRules() {
        RULE = GameRules.register("noirmWildNinjas", GameRules.Category.SPAWNING, GameRules.IntegerValue.create(100));
    }

    public static int rate(ServerLevel level) {
        return RULE == null ? 100 : Mth.clamp(level.getGameRules().getInt(RULE), 0, MAX_RATE);
    }

    /** Ticks between tries at {@code rate} percent. */
    public static int interval(int rate) {
        return rate <= 100 ? ATTEMPT_TICKS : Math.max(MIN_ATTEMPT_TICKS, ATTEMPT_TICKS * 100 / rate);
    }

    /** The chance a try brings a group at {@code rate} percent. */
    public static float chance(int rate) {
        return BASE_CHANCE * Math.min(100, rate) / 100f;
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.players().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (now % SWEEP_TICKS == 0) {
            WildNinjas.sweep(level);
        }
        if (now % 20 != 3 || level.dimension() != Level.OVERWORLD) {
            return;
        }
        int rate = rate(level);
        if (rate <= 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            long next = NEXT_TRY.computeIfAbsent(player.getUUID(), u -> now + interval(rate));
            if (now < next) {
                continue;
            }
            NEXT_TRY.put(player.getUUID(), now + interval(rate));
            if (level.getRandom().nextFloat() < chance(rate)) {
                Result result = attempt(level, player.blockPosition(), playedTicks(player), level.getRandom(), MIN_DISTANCE, MAX_DISTANCE);
                Noirm.LOGGER.debug("Wild ninja try near {}: {}", player.getName().getString(), result);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NEXT_TRY.clear();
    }

    /** How long {@code player} has played (ticks). */
    public static long playedTicks(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
    }

    /**
     * One try around {@code origin} (a player's position): checks the rule, the safe start and the
     * caps, then looks for a spot {@code minDistance}–{@code maxDistance} blocks away and brings a
     * group there.
     */
    public static Result attempt(ServerLevel level, BlockPos origin, long playedTicks, RandomSource random, int minDistance, int maxDistance) {
        if (rate(level) <= 0) {
            return Result.DISABLED;
        }
        if (fromSpawn(level, origin) < SPAWN_SAFE_RADIUS && playedTicks < SAFE_PLAY_MINUTES * 60L * 20L) {
            return Result.TOO_EARLY;
        }
        int[] near = WildNinjas.countNear(level, origin, CAP_RADIUS);
        if (near[0] >= MAX_GROUPS_NEAR || near[1] >= MAX_NINJAS_NEAR) {
            return Result.CAPPED;
        }
        BlockPos spot = findSpot(level, origin, random, minDistance, maxDistance);
        if (spot == null) {
            return Result.NO_SPOT;
        }
        ResourceLocation id = pick(level, spot, playedTicks, random);
        if (id == null) {
            return Result.NO_ENCOUNTER;
        }
        List<SparringNinja> group = spawn(level, id, spot, danger(level, spot, playedTicks), random);
        if (group.isEmpty()) {
            return Result.NO_SPOT;
        }
        Noirm.LOGGER.info("Wild ninjas turned up: {} ({} ninjas) at {}", id, group.size(), spot.toShortString());
        return Result.SPAWNED;
    }

    // ---------------------------------------------------------------- danger and picking

    /** Blocks (flat) from the world spawn. */
    public static double fromSpawn(ServerLevel level, BlockPos pos) {
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = pos.getX() - spawn.getX(), dz = pos.getZ() - spawn.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The danger level at {@code pos} for a player who has played {@code playedTicks}: the base stat of the ninjas met there. */
    public static float danger(ServerLevel level, BlockPos pos, long playedTicks) {
        float distance = (float) Math.min(DISTANCE_DANGER_MAX, fromSpawn(level, pos) / BLOCKS_PER_DANGER);
        float time = Math.min(TIME_DANGER_MAX, playedTicks / 72000f * DANGER_PER_HOUR);
        return BASE_DANGER + distance + time;
    }

    /** Picks an encounter that may turn up here (distance, play time, night, difficulty), by weight. */
    public static ResourceLocation pick(ServerLevel level, BlockPos pos, long playedTicks, RandomSource random) {
        double distance = fromSpawn(level, pos);
        long minutes = playedTicks / 1200L;
        boolean night = level.isNight();
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        List<ResourceLocation> ids = new ArrayList<>(Encounters.all().keySet());
        ids.sort(null); // the same order every time
        int total = 0;
        int[] weights = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            EncounterType type = Encounters.get(ids.get(i));
            boolean allowed = distance >= type.minDistance() && minutes >= type.minPlayedMinutes() && !(peaceful && type.hostile());
            weights[i] = allowed ? type.weightAt(night) : 0;
            total += weights[i];
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < ids.size(); i++) {
            roll -= weights[i];
            if (roll < 0) {
                return ids.get(i);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- where

    /** A spot on open land {@code min}–{@code max} blocks from {@code origin}, or null. Only looks at loaded, ticking ground. */
    public static BlockPos findSpot(ServerLevel level, BlockPos origin, RandomSource random, int min, int max) {
        for (int i = 0; i < SPOT_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * Math.max(0, max - min);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, origin.getY(), z);
            if (!level.hasChunkAt(column) || !level.isPositionEntityTicking(column)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (isGoodSpot(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Whether a ninja can turn up standing at {@code pos} (feet): solid dry ground, room to stand,
     * nothing but sky (or leaves) above (so not in a cave), and not in or next to a village.
     */
    public static boolean isGoodSpot(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || pos.getY() <= level.getMinBuildHeight() + 1 || pos.getY() >= level.getMaxBuildHeight() - 2) {
            return false;
        }
        if (!standable(level, pos)) {
            return false;
        }
        if (pos.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ())) {
            return false; // a roof over their head: a cave or a house
        }
        return !nearVillage(level, pos);
    }

    public static boolean standable(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.getFluidState().isEmpty() || !ground.isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        for (BlockPos p : new BlockPos[] {pos, pos.above()}) {
            BlockState state = level.getBlockState(p);
            if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, p).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** In a ninja village (world-generated or built), or within {@link #VILLAGE_MARGIN} blocks of one. */
    public static boolean nearVillage(ServerLevel level, BlockPos pos) {
        BuiltVillages built = BuiltVillages.of(level);
        BlockPos[] probes = {pos, pos.offset(VILLAGE_MARGIN, 0, 0), pos.offset(-VILLAGE_MARGIN, 0, 0), pos.offset(0, 0, VILLAGE_MARGIN),
                pos.offset(0, 0, -VILLAGE_MARGIN)};
        for (BlockPos p : probes) {
            if (built.at(p) != null) {
                return true;
            }
            if (level.hasChunkAt(p) && level.structureManager().getStructureWithPieceAt(p, VillageAtmosphere.VILLAGES).isValid()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- bringing a group

    /**
     * Brings encounter {@code id} at {@code spot} with stats around {@code danger}: the leader at
     * the spot, the others around them. Returns the group (leader first; empty if the id is unknown).
     */
    public static List<SparringNinja> spawn(ServerLevel level, ResourceLocation id, BlockPos spot, float danger, RandomSource random) {
        EncounterType type = Encounters.get(id);
        List<SparringNinja> group = new ArrayList<>();
        if (type == null || level.getServer() == null) {
            return group;
        }
        int size = Math.max(1, type.minSize()) + random.nextInt(type.clampedMax() - Math.max(1, type.minSize()) + 1);
        UUID groupId = UUID.randomUUID();
        WorldClans clans = WorldClans.get(level.getServer());
        Clan shared = type.sameClan() ? clans.rollForNewcomer(random) : null;
        VillagePalette palette = paletteNear(level, spot);
        float yaw = random.nextFloat() * 360f;
        for (int i = 0; i < size; i++) {
            SparringNinja ninja = NoirmEntities.SPARRING_NINJA.get().create(level);
            if (ninja == null) {
                continue;
            }
            boolean leader = i == 0;
            BlockPos at = leader ? spot : around(level, spot, random);
            ninja.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
            ninja.setYHeadRot(yaw);
            ninja.setYBodyRot(yaw);
            float strength = danger * (leader ? type.leaderStrength() : type.memberStrength());
            ninja.setCombatStats(stat(strength, random), stat(strength, random)); // (their jutsu set follows their strength)
            if (leader && "village".equals(type.outfit())) {
                ninja.setVillageRole("PATROL"); // a squad leader knows at least a chūnin's jutsu
            }
            ninja.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
            Clan clan = shared != null ? shared : clans.rollForNewcomer(random);
            ninja.joinClan(clan); // their clan's natures, jutsu, look and weapons (the usual way)
            dress(ninja, type.outfit(), leader, clan, palette, random);
            WildNinjas.mark(ninja, new WildNinjas.Member(groupId, id, leader, type.hostile(),
                    random.nextFloat() < type.sparChance(), type.huntsRogues(), i));
            if (level.addFreshEntity(ninja)) {
                group.add(ninja);
            } else {
                WildNinjas.forget(ninja);
            }
        }
        return group;
    }

    private static float stat(float strength, RandomSource random) {
        float spread = 1f - STAT_SPREAD + random.nextFloat() * 2f * STAT_SPREAD;
        return Mth.clamp(strength * spread, 5f, ShinobiData.MAX_STAT);
    }

    /** A spot within two blocks of {@code spot} on the same ground, or the spot itself. */
    private static BlockPos around(ServerLevel level, BlockPos spot, RandomSource random) {
        for (int i = 0; i < 4; i++) {
            int x = spot.getX() + random.nextInt(5) - 2, z = spot.getZ() + random.nextInt(5) - 2;
            if (x == spot.getX() && z == spot.getZ()) {
                continue;
            }
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos pos = new BlockPos(x, spot.getY() + dy, z);
                if (level.hasChunkAt(pos) && standable(level, pos)) {
                    return pos;
                }
            }
        }
        return spot;
    }

    /** The village look near {@code pos}: a village built nearby, otherwise the look a village in this biome would have. */
    public static VillagePalette paletteNear(ServerLevel level, BlockPos pos) {
        BuiltVillages.Entry built = BuiltVillages.of(level).nearest(pos, 800);
        return built != null ? built.palette() : VillagePalette.forBiome(level.getBiome(pos));
    }

    /** Clothes for a wild ninja, over whatever their clan gave them. */
    static void dress(SparringNinja ninja, String outfit, boolean leader, Clan clan, VillagePalette palette, RandomSource random) {
        Map<EquipmentSlot, Item> clothes = switch (outfit) {
            case "rogue" -> ClanOutfits.rogue(ClanOutfits.roll(clan.id(), random), random);
            case "village" -> {
                Map<EquipmentSlot, Item> uniform = new EnumMap<>(ClanOutfits.residentOutfit("GUARD", palette, random));
                uniform.put(EquipmentSlot.CHEST, leader ? NoirmGear.JONIN_VEST.get() : ClanOutfits.villageVest(palette));
                yield uniform;
            }
            default -> ClanOutfits.roll(clan.id(), random);
        };
        ninja.setGearRolled(true);
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            Item item = clothes.get(slot);
            ninja.setItemSlot(slot, item == null ? ItemStack.EMPTY : new ItemStack(item));
            ninja.setDropChance(slot, ClanOutfits.DROP_CHANCE);
        }
    }
}

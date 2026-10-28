package com.noirm.mission;

import com.noirm.economy.Ryo;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.VillageMerchant;
import com.noirm.player.ShinobiCapability;
import com.noirm.registry.NoirmEntities;
import com.noirm.world.wild.WildSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The rules of missions: what a board offers, taking one, how each kind plays out (checked every
 * half second, {@link #update}), reporting back for the reward, failing and abandoning.
 *
 * <p>One mission at a time. Every mission ends with "report back to a mission board" (any board):
 * that's when the ryo and rank points are paid. Failing (time out, the client dies, you leave
 * the village you're defending) or abandoning costs half the mission's rank points.
 *
 * <ul>
 *   <li><b>Hunt:</b> the rogues are put in the world when you get within {@link #SPAWN_RANGE}
 *       blocks of the marked area. Killing them or beating them into yielding both count, and
 *       your crew can help (except in a {@code solo} mission, where the final blows must be yours).</li>
 *   <li><b>Escort:</b> the client follows you (and catches up if left behind); rogues may ambush
 *       you halfway. Get them within {@link #ARRIVE} blocks of the place. If they die, it fails.</li>
 *   <li><b>Deliver:</b> you get a sealed parcel; the recipient waits at the place (glowing).
 *       Walk up to them with the parcel.</li>
 *   <li><b>Scout:</b> reach each marked point in turn; a lookout may wait at the last one.</li>
 *   <li><b>Retrieve:</b> a camp is pitched at the place when you come near, a scroll in its chest
 *       and guards around; take the scroll.</li>
 *   <li><b>Defend:</b> raiders come at the village in waves (the first after
 *       {@link #DEFEND_COUNTDOWN} ticks); stay within {@link #DEFEND_LEAVE} blocks of the board.</li>
 * </ul>
 */
public final class Missions {
    /** How near the marked area you must come for the enemies / recipient / camp to appear. */
    public static final double SPAWN_RANGE = 48;
    /** How near the place counts as arrived (escort, scout). */
    public static final double ARRIVE = 8;
    /** How near the recipient you hand the parcel over. */
    public static final double HAND_OVER = 4.5;
    /** Defending: going further than this from the board fails the mission. */
    public static final double DEFEND_LEAVE = 96;
    /** Within this of where an enemy should be, an enemy who's simply gone counts as beaten (fled). */
    public static final double VANISH_NEAR = 64;
    /** An escorted client farther than this from you is brought to your side. */
    public static final double CLIENT_CATCH_UP = 24;
    /** Ticks before the first wave of a defence, and between waves. */
    public static final int DEFEND_COUNTDOWN = 300, WAVE_GAP = 200;
    /** How many missions of each rank letter a board offers a day (D, C, B, A, S). */
    public static final int[] OFFERS_PER_LETTER = {2, 2, 2, 1, 1};
    /** Tags on everything spawned for a mission. */
    public static final String MISSION_TAG = "noirm_mission", OWNER_PREFIX = "noirm_owner:";

    /** Who owns what was spawned for a mission (players by UUID; works for test players too). */
    private static final Map<UUID, WeakReference<Player>> OWNERS = new HashMap<>();

    private Missions() {}

    public static Optional<CareerData> career(Player player) {
        return ShinobiCapability.get(player).map(d -> d.career());
    }

    /** The mission {@code player} is on, or null. */
    public static Mission active(Player player) {
        return career(player).map(CareerData::mission).orElse(null);
    }

    static void remember(Player player) {
        OWNERS.put(player.getUUID(), new WeakReference<>(player));
    }

    /** The player a mission enemy, client or examiner was spawned for (null if unknown or gone). */
    public static Player owner(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(OWNER_PREFIX)) {
                try {
                    UUID id = UUID.fromString(tag.substring(OWNER_PREFIX.length()));
                    WeakReference<Player> ref = OWNERS.get(id);
                    Player player = ref == null ? null : ref.get();
                    if (player == null && entity.level().getServer() != null) {
                        player = entity.level().getServer().getPlayerList().getPlayer(id);
                    }
                    return player;
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    static void tagFor(Entity entity, Player owner) {
        entity.addTag(MISSION_TAG);
        entity.addTag(OWNER_PREFIX + owner.getUUID());
    }

    // ================================================================ offers

    /** The in-game day (offers change every day). */
    public static long day(Level level) {
        return level.getDayTime() / 24000L;
    }

    public static String offerKey(BlockPos board, int index) {
        return board.getX() + "," + board.getY() + "," + board.getZ() + "#" + index;
    }

    /**
     * Today's missions on the board at {@code board}: {@link #OFFERS_PER_LETTER} of each rank
     * letter, picked by weight from the templates (the same for everyone, all day).
     */
    public static List<ResourceLocation> offers(Level level, BlockPos board) {
        RandomSource random = RandomSource.create(board.asLong() * 31L + day(level) * 1_000_003L);
        List<ResourceLocation> offers = new ArrayList<>();
        for (MissionTemplate.Letter letter : MissionTemplate.Letter.values()) {
            List<ResourceLocation> pool = new ArrayList<>(MissionTemplates.offerable(letter));
            for (int i = 0; i < OFFERS_PER_LETTER[letter.ordinal()] && !pool.isEmpty(); i++) {
                int total = pool.stream().mapToInt(id -> MissionTemplates.get(id).weight()).sum();
                int roll = random.nextInt(Math.max(1, total));
                for (ResourceLocation id : pool) {
                    roll -= MissionTemplates.get(id).weight();
                    if (roll < 0) {
                        offers.add(id);
                        pool.remove(id);
                        break;
                    }
                }
            }
        }
        return offers;
    }

    /** Why {@code player} can't take {@code template} now (null if they can). */
    public static Component blocker(Player player, MissionTemplate template) {
        CareerData career = career(player).orElse(null);
        if (career == null || template == null) {
            return Component.translatable("mission.noirm.why.unknown");
        }
        if (!template.exam() && !career.rank().atLeast(template.rank().required())) {
            return Component.translatable("mission.noirm.why.rank", template.rank().required().title());
        }
        if (career.mission() != null) {
            return Component.translatable("mission.noirm.why.busy");
        }
        if (career.exam() != null && !template.exam()) {
            return Component.translatable("mission.noirm.why.exam");
        }
        return null;
    }

    /** Takes offer {@code index} of the board at {@code board}. Returns why not, or null if taken. */
    public static Component acceptOffer(Player player, BlockPos board, int index) {
        List<ResourceLocation> offers = offers(player.level(), board);
        if (index < 0 || index >= offers.size()) {
            return Component.translatable("mission.noirm.why.unknown");
        }
        CareerData career = career(player).orElse(null);
        long day = day(player.level());
        if (career != null && career.offerTaken(offerKey(board, index), day)) {
            return Component.translatable("mission.noirm.why.taken");
        }
        Component why = accept(player, offers.get(index), board);
        if (why == null && career != null) {
            career.takeOffer(offerKey(board, index), day);
        }
        return why;
    }

    // ================================================================ taking a mission

    /**
     * Starts mission {@code id} for {@code player}, taken at the board at {@code board}: plans its
     * marked places and sets up what it needs (the escorted client, the parcel, the raid countdown).
     * Returns why not, or null if it started.
     */
    public static Component accept(Player player, ResourceLocation id, BlockPos board) {
        MissionTemplate def = MissionTemplates.get(id);
        Component why = blocker(player, def);
        if (why != null) {
            return why;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return Component.translatable("mission.noirm.why.unknown");
        }
        CareerData career = career(player).orElseThrow();
        remember(player);
        Mission m = new Mission(id, UUID.randomUUID(), board.immutable(), level.dimension().location().toString());
        RandomSource random = level.getRandom();
        int min = Math.max(4, def.minDistance()), max = Math.max(min, def.maxDistance());
        switch (def.kind()) {
            case SCOUT -> {
                BlockPos from = board;
                int count = Math.max(1, def.count());
                for (int i = 0; i < count; i++) {
                    from = place(level, from, random, i == 0 ? min : Math.max(4, min / 2), i == 0 ? max : Math.max(8, max / 2));
                    m.points.add(from);
                }
            }
            case DEFEND -> {
                m.points.add(board);
                m.nextAt = level.getGameTime() + DEFEND_COUNTDOWN;
            }
            default -> m.points.add(place(level, board, random, min, max));
        }
        if (def.timeLimit() > 0) {
            m.deadline = level.getGameTime() + def.timeLimit() * 20L;
        }
        career.setMission(m);
        switch (def.kind()) {
            case ESCORT -> spawnClient(level, player, m);
            case DELIVER -> give(player, missionItem(CareerItems.MISSION_PARCEL.get().getDefaultInstance(), m));
            default -> {
            }
        }
        player.sendSystemMessage(Component.translatable("mission.noirm.accepted",
                Component.literal(def.rank().name()).withStyle(s -> s.withColor(def.rank().color())),
                Component.translatable(MissionTemplate.nameKey(id))).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable(MissionTemplate.nameKey(id) + ".desc").withStyle(ChatFormatting.GRAY));
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        return null;
    }

    /** A marked place {@code min}–{@code max} blocks from {@code from} in a random direction (on the ground if it's loaded). */
    static BlockPos place(ServerLevel level, BlockPos from, RandomSource random, int min, int max) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = min + random.nextDouble() * Math.max(0, max - min);
        int x = from.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = from.getZ() + (int) Math.round(Math.sin(angle) * distance);
        return new BlockPos(x, surfaceY(level, x, z, from.getY()), z);
    }

    static int surfaceY(ServerLevel level, int x, int z, int fallback) {
        return level.hasChunkAt(new BlockPos(x, 0, z)) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : fallback;
    }

    static BlockPos ground(ServerLevel level, BlockPos pos) {
        return new BlockPos(pos.getX(), surfaceY(level, pos.getX(), pos.getZ(), pos.getY()), pos.getZ());
    }

    /** Marks an item as belonging to mission {@code m}. */
    static ItemStack missionItem(ItemStack stack, Mission m) {
        stack.getOrCreateTag().putUUID(Mission.ITEM_TAG, m.id);
        return stack;
    }

    static boolean isFor(ItemStack stack, Mission m) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().hasUUID(Mission.ITEM_TAG) && m.id.equals(stack.getTag().getUUID(Mission.ITEM_TAG));
    }

    /** Whether {@code player} carries mission {@code m}'s item of this kind. */
    static boolean carries(Player player, Mission m, net.minecraft.world.item.Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item) && isFor(stack, m)) {
                return true;
            }
        }
        return stack(player.getInventory().offhand, item, m) || stack(player.getInventory().armor, item, m);
    }

    private static boolean stack(List<ItemStack> list, net.minecraft.world.item.Item item, Mission m) {
        return list.stream().anyMatch(s -> s.is(item) && isFor(s, m));
    }

    /** Takes every item of mission {@code m} out of the player's inventory. */
    static void removeItems(Player player, Mission m) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isFor(inventory.getItem(i), m)) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ================================================================ every half second

    /** Moves {@code player}'s mission along: spawns what's due, checks the objective, the clock. */
    public static void update(Player player) {
        CareerData career = career(player).orElse(null);
        Mission m = career == null ? null : career.mission();
        if (m == null || !(player.level() instanceof ServerLevel level) || !level.dimension().location().toString().equals(m.dimension)) {
            return;
        }
        remember(player);
        MissionTemplate def = m.definition();
        if (def == null) {
            career.setMission(null); // its template is gone (a data pack removed it)
            return;
        }
        long now = level.getGameTime();
        if (m.stage == Mission.Stage.GO && m.deadline > 0 && now > m.deadline) {
            fail(player, "mission.noirm.failed.time");
            return;
        }
        if (m.stage == Mission.Stage.RETURN) {
            return;
        }
        switch (def.kind()) {
            case HUNT -> hunt(level, player, m, def);
            case ESCORT -> escort(level, player, m, def);
            case DELIVER -> deliver(level, player, m);
            case SCOUT -> scout(level, player, m, def);
            case RETRIEVE -> retrieve(level, player, m, def);
            case DEFEND -> defend(level, player, m, def, now);
        }
    }

    private static void hunt(ServerLevel level, Player player, Mission m, MissionTemplate def) {
        BlockPos area = m.points.get(0);
        if (!m.spawned) {
            if (flat(player, area) < SPAWN_RANGE) {
                List<SparringNinja> group = spawnGroup(level, player, def.encounter().orElse(com.noirm.world.wild.Encounters.ROGUE),
                        ground(level, area), def.strength(), 0, 4);
                group.forEach(n -> m.foes.add(n.getUUID()));
                m.required = group.size();
                m.spawned = true;
                career(player).ifPresent(CareerData::markDirty);
                player.displayClientMessage(Component.translatable("mission.noirm.hunt.found").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if (checkFoes(level, player, m, area) && m.foes.isEmpty()) {
            objectiveDone(player, m);
        }
    }

    private static void escort(ServerLevel level, Player player, Mission m, MissionTemplate def) {
        BlockPos destination = m.points.get(0);
        Entity found = m.npc == null ? null : level.getEntity(m.npc);
        if (!(found instanceof VillageMerchant client) || !client.isAlive()) {
            spawnClient(level, player, m); // lost track of them (unloaded): they catch up
            return;
        }
        double distance = client.distanceTo(player);
        if (distance > CLIENT_CATCH_UP) {
            client.teleportTo(player.getX(), player.getY(), player.getZ());
            client.getNavigation().stop();
        } else if (distance > 3) {
            client.getNavigation().moveTo(player, 0.75);
        }
        double left = flat(client, destination);
        double total = Math.max(1, Math.sqrt(m.board.distSqr(destination)));
        if (!m.ambushed && def.ambush().isPresent() && left < total * 0.6) {
            m.ambushed = true;
            List<SparringNinja> ambush = spawnGroup(level, player, def.ambush().get(), client.blockPosition(), def.strength(), 12, 18, false);
            ambush.forEach(n -> m.foes.add(n.getUUID()));
            player.displayClientMessage(Component.translatable("mission.noirm.ambush").withStyle(ChatFormatting.RED), true);
            level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.5f, 1.4f);
        }
        if (left < ARRIVE) {
            client.say(Component.translatable("mission.noirm.client.thanks"), 60);
            poof(level, client);
            client.discard();
            m.npc = null;
            objectiveDone(player, m);
        }
    }

    private static void deliver(ServerLevel level, Player player, Mission m) {
        BlockPos destination = m.points.get(0);
        if (!m.spawned) {
            if (flat(player, destination) < SPAWN_RANGE) {
                VillageMerchant recipient = NoirmEntities.VILLAGE_MERCHANT.get().create(level);
                if (recipient != null) {
                    BlockPos at = ground(level, destination);
                    recipient.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
                    recipient.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
                    recipient.setup(VillageMerchant.Role.CIVILIAN, 0, at, null, 2);
                    recipient.setCustomName(Component.translatable("mission.noirm.npc.recipient"));
                    recipient.setGlowingTag(true);
                    tagFor(recipient, player);
                    level.addFreshEntity(recipient);
                    m.npc = recipient.getUUID();
                }
                m.spawned = true;
                career(player).ifPresent(CareerData::markDirty);
            }
            return;
        }
        Entity found = m.npc == null ? null : level.getEntity(m.npc);
        if (found != null && found.isAlive() && player.distanceTo(found) < HAND_OVER) {
            if (!carries(player, m, CareerItems.MISSION_PARCEL.get())) {
                player.displayClientMessage(Component.translatable("mission.noirm.deliver.no_parcel").withStyle(ChatFormatting.RED), true);
                return;
            }
            removeItems(player, m);
            player.sendSystemMessage(Component.translatable("villager.noirm.says", found.getDisplayName(),
                    Component.translatable("mission.noirm.recipient.thanks")));
            poof(level, found);
            found.discard();
            m.npc = null;
            objectiveDone(player, m);
        } else if (found == null && flat(player, destination) < 16) {
            m.spawned = false; // the recipient is gone: another waits
        }
    }

    private static void scout(ServerLevel level, Player player, Mission m, MissionTemplate def) {
        if (m.index >= m.points.size()) {
            objectiveDone(player, m);
            return;
        }
        BlockPos next = m.points.get(m.index);
        boolean last = m.index == m.points.size() - 1;
        if (last && !m.ambushed && def.ambush().isPresent() && flat(player, next) < SPAWN_RANGE) {
            m.ambushed = true;
            spawnGroup(level, player, def.ambush().get(), ground(level, next), def.strength(), 2, 6, false).forEach(n -> m.foes.add(n.getUUID()));
        }
        if (flat(player, next) < ARRIVE) {
            m.index++;
            career(player).ifPresent(CareerData::markDirty);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
            player.displayClientMessage(Component.translatable("mission.noirm.scout.point", m.index, m.points.size())
                    .withStyle(ChatFormatting.GREEN), true);
            if (m.index >= m.points.size()) {
                objectiveDone(player, m);
            }
        }
    }

    private static void retrieve(ServerLevel level, Player player, Mission m, MissionTemplate def) {
        BlockPos area = m.points.get(0);
        if (!m.spawned && flat(player, area) < SPAWN_RANGE) {
            BlockPos camp = ground(level, area);
            m.points.set(0, camp);
            pitchCamp(level, camp, m);
            def.encounter().ifPresent(e -> spawnGroup(level, player, e, camp, def.strength(), 3, 7, false).forEach(n -> m.foes.add(n.getUUID())));
            m.spawned = true;
            career(player).ifPresent(CareerData::markDirty);
        }
        if (carries(player, m, CareerItems.MISSION_SCROLL.get())) {
            objectiveDone(player, m);
        }
    }

    private static void defend(ServerLevel level, Player player, Mission m, MissionTemplate def, long now) {
        if (flat(player, m.board) > DEFEND_LEAVE) {
            fail(player, "mission.noirm.failed.left");
            return;
        }
        checkFoes(level, player, m, m.board);
        if (!m.foes.isEmpty()) {
            for (UUID id : m.foes) {
                if (level.getEntity(id) instanceof SparringNinja raider && raider.getTarget() == null) {
                    raider.setTarget(player); // raiders come for the defender
                }
            }
            return;
        }
        int waves = Math.max(1, def.count());
        if (m.wave >= waves) {
            objectiveDone(player, m);
            return;
        }
        if (m.nextAt == 0) {
            m.nextAt = now + WAVE_GAP;
            player.displayClientMessage(Component.translatable("mission.noirm.defend.cleared", m.wave, waves).withStyle(ChatFormatting.GREEN), true);
            career(player).ifPresent(CareerData::markDirty);
        } else if (now >= m.nextAt) {
            List<SparringNinja> raiders = spawnGroup(level, player, def.encounter().orElse(com.noirm.world.wild.Encounters.ROGUE_BAND),
                    m.board, def.strength(), 22, 34);
            raiders.forEach(n -> m.foes.add(n.getUUID()));
            m.required += raiders.size();
            m.wave++;
            m.nextAt = 0;
            career(player).ifPresent(CareerData::markDirty);
            player.displayClientMessage(Component.translatable("mission.noirm.defend.wave", m.wave, waves).withStyle(ChatFormatting.RED), true);
            level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.8f, 1f);
        }
    }

    /**
     * Drops the beaten enemies from {@code m.foes} (dead, yielded, or simply gone while you're near
     * {@code where}). In a solo mission an enemy someone else finished off fails the mission.
     * Returns false if the mission failed.
     */
    static boolean checkFoes(ServerLevel level, Player player, Mission m, BlockPos where) {
        boolean near = flat(player, where) < VANISH_NEAR;
        int before = m.foes.size();
        m.foes.removeIf(id -> {
            Entity e = level.getEntity(id);
            if (e == null) {
                return near;
            }
            return !e.isAlive() || e instanceof SparringNinja n && n.isYielded();
        });
        if (m.foes.size() != before) {
            m.defeated += before - m.foes.size();
            career(player).ifPresent(CareerData::markDirty);
        }
        return true;
    }

    /** Called when something spawned for a mission dies. */
    public static void onDeath(Entity dead, Entity killer) {
        Player owner = owner(dead);
        Mission m = owner == null ? null : active(owner);
        if (m == null) {
            return;
        }
        MissionTemplate def = m.definition();
        if (dead.getUUID().equals(m.npc) && def != null && def.kind() == MissionKind.ESCORT) {
            fail(owner, "mission.noirm.failed.client");
        } else if (m.foes.contains(dead.getUUID()) && def != null && def.solo() && killer != owner && m.stage == Mission.Stage.GO) {
            fail(owner, "mission.noirm.failed.solo");
        }
    }

    private static void objectiveDone(Player player, Mission m) {
        m.stage = Mission.Stage.RETURN;
        career(player).ifPresent(CareerData::markDirty);
        player.sendSystemMessage(Component.translatable("mission.noirm.objective_done").withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    // ================================================================ spawning

    /**
     * Brings a wild-ninja group ({@code data/noirm/encounters/}) {@code min}–{@code max} blocks
     * from {@code near}, kept in the world (no despawning), tagged as the player's mission enemies
     * and set on them.
     */
    static List<SparringNinja> spawnGroup(ServerLevel level, Player owner, ResourceLocation encounter, BlockPos near, float strength,
                                          int min, int max) {
        return spawnGroup(level, owner, encounter, near, strength, min, max, true);
    }

    /** {@code persistent}: kept in the world until beaten (what must be beaten); otherwise they come and go like other wild ninjas. */
    static List<SparringNinja> spawnGroup(ServerLevel level, Player owner, ResourceLocation encounter, BlockPos near, float strength,
                                          int min, int max, boolean persistent) {
        RandomSource random = level.getRandom();
        BlockPos spot = null;
        for (int i = 0; i < 12 && spot == null; i++) {
            BlockPos p = max <= 0 ? near : place(level, near, random, min, max);
            if (level.hasChunkAt(p) && WildSpawner.standable(level, p)) {
                spot = p;
            }
        }
        if (spot == null) {
            spot = ground(level, near);
        }
        List<SparringNinja> group = WildSpawner.spawn(level, encounter, spot, strength, random);
        for (SparringNinja ninja : group) {
            if (persistent) {
                ninja.setPersistenceRequired();
            }
            tagFor(ninja, owner);
            ninja.setTarget(owner);
        }
        return group;
    }

    /** The escorted client, by the player. */
    static void spawnClient(ServerLevel level, Player player, Mission m) {
        VillageMerchant client = NoirmEntities.VILLAGE_MERCHANT.get().create(level);
        if (client == null) {
            return;
        }
        client.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
        client.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), MobSpawnType.EVENT, null, null);
        client.setup(VillageMerchant.Role.CIVILIAN, 0, player.blockPosition(), null, 2);
        client.clearRestriction(); // they go where you go
        client.setCustomName(Component.translatable("mission.noirm.npc.client"));
        tagFor(client, player);
        level.addFreshEntity(client);
        m.npc = client.getUUID();
    }

    /** A rogue camp: a campfire, log seats, a little tent and a chest with the scroll in it. */
    static void pitchCamp(ServerLevel level, BlockPos at, Mission m) {
        placeIfFree(level, at, Blocks.CAMPFIRE.defaultBlockState());
        placeIfFree(level, at.offset(0, 0, -2), Blocks.SPRUCE_LOG.defaultBlockState());
        placeIfFree(level, at.offset(-2, 0, 0), Blocks.SPRUCE_LOG.defaultBlockState());
        for (int x = -1; x <= 1; x++) {
            placeIfFree(level, at.offset(x, 0, 3), Blocks.BROWN_WOOL.defaultBlockState());
            placeIfFree(level, at.offset(x, 1, 3), Blocks.BROWN_WOOL.defaultBlockState());
            placeIfFree(level, at.offset(x, 2, 3), Blocks.SPRUCE_SLAB.defaultBlockState());
        }
        BlockPos chestPos = at.offset(2, 0, 1);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.WEST), 3);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(13, missionItem(CareerItems.MISSION_SCROLL.get().getDefaultInstance(), m));
            chest.setItem(4, new ItemStack(net.minecraft.world.item.Items.BREAD, 3));
        }
    }

    private static void placeIfFree(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (level.getBlockState(pos).canBeReplaced()) {
            level.setBlock(pos, state, 3);
        }
    }

    static void poof(ServerLevel level, Entity entity) {
        level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 1, entity.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
    }

    static double flat(Entity entity, BlockPos pos) {
        double dx = entity.getX() - (pos.getX() + 0.5), dz = entity.getZ() - (pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ================================================================ reporting back, failing

    /** Right-click on a board: report back if the objective is done, then show the board. */
    public static void useBoard(ServerPlayer player, BlockPos board) {
        remember(player);
        report(player, board);
        CareerPackets.openBoard(player, board);
    }

    /**
     * Reports back at a board: if the objective is done, pays the ryo and rank points (or moves a
     * rank-up exam on). Returns whether a mission was completed.
     */
    public static boolean report(Player player, BlockPos board) {
        CareerData career = career(player).orElse(null);
        Mission m = career == null ? null : career.mission();
        if (m == null || m.stage != Mission.Stage.RETURN) {
            return false;
        }
        MissionTemplate def = m.definition();
        removeItems(player, m);
        career.setMission(null);
        if (def == null) {
            return false;
        }
        if (def.exam()) {
            Exams.trialDone(player, board);
            return true;
        }
        Ryo.give(player, def.reward());
        career.setPoints(career.points() + def.points());
        career.addCompleted(def.rank());
        player.sendSystemMessage(Component.translatable("mission.noirm.complete", Component.translatable(MissionTemplate.nameKey(m.template)),
                Ryo.format(def.reward()), def.points()).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.1f);
        Exams.afterPoints(player);
        return true;
    }

    /** The mission fails: what was spawned for it goes, its items vanish, and half its rank points are lost. */
    public static void fail(Player player, String reasonKey) {
        CareerData career = career(player).orElse(null);
        Mission m = career == null ? null : career.mission();
        if (m == null) {
            return;
        }
        cleanUp(player, m);
        career.setMission(null);
        MissionTemplate def = m.definition();
        int lost = def == null || def.exam() ? 0 : def.points() / 2;
        career.setPoints(career.points() - lost);
        player.sendSystemMessage(Component.translatable("mission.noirm.failed", Component.translatable(MissionTemplate.nameKey(m.template)),
                Component.translatable(reasonKey), lost).withStyle(ChatFormatting.RED));
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, 0.8f);
        if (def != null && def.exam()) {
            Exams.fail(player, reasonKey);
        }
    }

    /** Gives up on the mission (counts as failing it). Returns whether there was one. */
    public static boolean abandon(Player player) {
        if (active(player) == null) {
            return false;
        }
        fail(player, "mission.noirm.failed.abandoned");
        return true;
    }

    /** Removes the client / recipient and any loaded enemies of {@code m}, and its items. */
    static void cleanUp(Player player, Mission m) {
        removeItems(player, m);
        if (player.level() instanceof ServerLevel level) {
            List<UUID> spawned = new ArrayList<>(m.foes);
            if (m.npc != null) {
                spawned.add(m.npc);
            }
            for (UUID id : spawned) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    poof(level, e);
                    e.discard();
                }
            }
        }
    }

    // ================================================================ what the HUD and board say

    /** The objective in one line, as it stands. */
    public static Component objective(Player player, Mission m) {
        MissionTemplate def = m.definition();
        if (def == null) {
            return Component.empty();
        }
        if (m.stage == Mission.Stage.RETURN) {
            return Component.translatable("mission.noirm.obj.return");
        }
        long now = player.level().getGameTime();
        return switch (def.kind()) {
            case HUNT -> m.spawned ? Component.translatable("mission.noirm.obj.hunt", m.defeated, Math.max(m.required, m.defeated))
                    : Component.translatable("mission.noirm.obj.hunt.find");
            case ESCORT -> Component.translatable("mission.noirm.obj.escort");
            case DELIVER -> Component.translatable(m.spawned ? "mission.noirm.obj.deliver.hand" : "mission.noirm.obj.deliver");
            case SCOUT -> Component.translatable("mission.noirm.obj.scout", m.index, m.points.size());
            case RETRIEVE -> Component.translatable("mission.noirm.obj.retrieve");
            case DEFEND -> m.foes.isEmpty() && m.nextAt > 0
                    ? Component.translatable("mission.noirm.obj.defend.soon", Math.max(0, (m.nextAt - now) / 20), m.wave + 1, Math.max(1, def.count()))
                    : Component.translatable("mission.noirm.obj.defend", m.wave, Math.max(1, def.count()), m.foes.size());
        };
    }
}

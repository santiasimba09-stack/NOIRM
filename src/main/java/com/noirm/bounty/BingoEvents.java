package com.noirm.bounty;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.entity.SparringNinja;
import com.noirm.gear.ClanOutfits;
import com.noirm.mission.PlayerRank;
import com.noirm.registry.NoirmEntities;
import com.noirm.world.wild.Encounters;
import com.noirm.world.wild.WildNinjas;
import com.noirm.world.wild.WildSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The bingo book in the world:
 * <ul>
 *   <li><b>Seeding</b> the book when the server starts, and a <b>refresh</b> once a day.</li>
 *   <li><b>Bringing rogues in:</b> a listed rogue only exists while a player is near: within
 *       {@link #SPAWN_NEAR} blocks of their last-seen area, they're put in the world exactly as the page
 *       remembers them (name, clan, strength, look, clothes, jutsu set, eyes, beast). When every
 *       player is more than {@link #DESPAWN_FAR} blocks away they move on: the page notes where they
 *       were and the body goes. Only one body per page is ever in the world.</li>
 *   <li><b>Bounties:</b> killing a listed rogue, or beating them into yielding, pays the bounty to
 *       whoever did it; a crew mate's (or a shadow clone's) kill pays their leader. Paid once.</li>
 *   <li>The <b>chūnin gift:</b> reaching chūnin, the village hands you a bingo book.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class BingoEvents {
    /** A listed rogue's page id, in their Forge persistent data. */
    public static final String TAG = "NoirmBingo";
    /** A player this close (flat blocks) to a rogue's last-seen area brings them into the world. */
    public static final double SPAWN_NEAR = 96;
    /** With no player this close, a rogue moves on (the body goes, the page keeps where they were). */
    public static final double DESPAWN_FAR = 160;
    /** How often (ticks) the book checks players and bodies. */
    public static final int CHECK_TICKS = 20;
    /** Player-persisted flag: the chūnin gift was given. */
    public static final String GIFT_TAG = "noirm_bingo_gift";

    /** The bodies in the world right now, by page id. */
    private static final Map<Integer, SparringNinja> BODIES = new HashMap<>();

    private BingoEvents() {}

    /** The loaded body of page {@code e} (null if none). */
    public static SparringNinja body(BingoEntry e) {
        SparringNinja n = BODIES.get(e.id);
        if (n != null && (n.isRemoved() || !n.isAlive())) {
            BODIES.remove(e.id);
            return null;
        }
        return n;
    }

    /** The page {@code entity} belongs to (-1 = not a listed rogue). */
    public static int entryId(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(TAG) ? data.getInt(TAG) : -1;
    }

    // ------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        BingoBook.get(event.getServer()).seed(level, level.getRandom());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        BODIES.clear();
    }

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % CHECK_TICKS != 7) {
            return;
        }
        BingoBook book = BingoBook.get(level.getServer());
        if (!book.seeded()) {
            book.seed(level, level.getRandom());
        }
        if (book.refreshDue(level.getGameTime())) {
            BingoEntry added = book.refresh(level, level.getRandom());
            if (added != null) {
                for (ServerPlayer player : level.players()) {
                    if (holdsBook(player)) {
                        player.sendSystemMessage(Component.translatable("bingo.noirm.new_page", added.name()).withStyle(ChatFormatting.DARK_RED));
                    }
                }
            }
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                checkNear(level, book, player.blockPosition());
            }
            giftCheck(player);
        }
        sweep(level, book);
    }

    /**
     * Brings in every page at large whose last-seen area is within {@link #SPAWN_NEAR} of {@code pos}
     * and has no body yet. Returns how many came.
     */
    public static int checkNear(ServerLevel level, BingoBook book, BlockPos pos) {
        int count = 0;
        for (BingoEntry e : book.entries()) {
            if (!e.active() || body(e) != null) {
                continue;
            }
            double dx = e.area.getX() - pos.getX(), dz = e.area.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= SPAWN_NEAR * SPAWN_NEAR && spawn(level, book, e, e.area) != null) {
                count++;
            }
        }
        return count;
    }

    /** Rogues with nobody near move on: the page keeps where they were, the body goes. Returns how many went. */
    public static int sweep(ServerLevel level, BingoBook book) {
        int gone = 0;
        for (BingoEntry e : book.entries()) {
            SparringNinja n = body(e);
            if (n == null || n.level() != level || n.inCrew()) {
                continue;
            }
            Player nearest = level.getNearestPlayer(n, -1);
            if (nearest == null || nearest.distanceTo(n) > DESPAWN_FAR) {
                e.area = n.blockPosition();
                e.entity = null;
                book.setDirty();
                BODIES.remove(e.id);
                n.discard();
                gone++;
            }
        }
        return gone;
    }

    /**
     * Puts page {@code e}'s rogue into the world near {@code near}, as the page remembers them.
     * Returns the rogue, or null if there was no ground to stand on (try again later) or the page
     * isn't at large.
     */
    public static SparringNinja spawn(ServerLevel level, BingoBook book, BingoEntry e, BlockPos near) {
        if (!e.active() || level.getServer() == null) {
            return null;
        }
        SparringNinja existing = body(e);
        if (existing != null) {
            return existing;
        }
        BlockPos spot = findSpot(level, near, level.getRandom());
        if (spot == null) {
            return null;
        }
        SparringNinja n = NoirmEntities.SPARRING_NINJA.get().create(level);
        if (n == null) {
            return null;
        }
        RandomSource seeded = RandomSource.create(e.seed);
        float yaw = seeded.nextFloat() * 360f;
        n.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0f);
        n.setYHeadRot(yaw);
        n.setYBodyRot(yaw);
        n.setCombatStats(e.strength, e.strength);
        n.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null, null);
        n.sealBeast(e.tails); // also settles it: no random beast on top
        WorldClans clans = WorldClans.get(level.getServer());
        Clan clan = clans.find(e.clanId).orElseGet(() -> clans.rollForNewcomer(seeded));
        n.joinClan(clan);
        if (e.extraNature != null) {
            n.learnNature(e.extraNature, Clan.PRIMARY_AFFINITY);
        }
        if (e.look.isEmpty()) {
            e.look = n.outfit();
        } else {
            n.changeOutfit(e.look);
        }
        dress(n, clan, seeded);
        if (e.mangekyo) {
            Legendary.giveMangekyo(n, e.seed);
        }
        n.setJutsuSeed(e.seed);
        if (e.legendary) {
            Legendary.apply(n, e);
        }
        n.setCustomName(e.name());
        n.setPersistenceRequired();
        n.getPersistentData().putInt(TAG, e.id);
        WildNinjas.mark(n, new WildNinjas.Member(new UUID(0xB1460L, e.id), Encounters.ROGUE, true, true, false, false, 0));
        e.entity = n.getUUID();
        book.setDirty();
        if (!level.addFreshEntity(n)) {
            WildNinjas.forget(n);
            e.entity = null;
            return null;
        }
        BODIES.put(e.id, n);
        Noirm.LOGGER.info("Bingo book: page {} ({}) turned up at {}", e.id, e.given, spot.toShortString());
        return n;
    }

    /** Ground to stand on within a few blocks of {@code near} (loaded chunks only), or null. */
    static BlockPos findSpot(ServerLevel level, BlockPos near, RandomSource random) {
        for (int i = 0; i < 12; i++) {
            int x = near.getX() + (i == 0 ? 0 : random.nextInt(17) - 8);
            int z = near.getZ() + (i == 0 ? 0 : random.nextInt(17) - 8);
            BlockPos column = new BlockPos(x, near.getY(), z);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (WildSpawner.standable(level, top)) {
                return top;
            }
            if (WildSpawner.standable(level, column)) {
                return column; // (an area under a roof: tests, caves)
            }
        }
        return null;
    }

    /** A rogue's clothes, the same every time (from the page's seed). */
    private static void dress(SparringNinja n, Clan clan, RandomSource seeded) {
        Map<EquipmentSlot, Item> clothes = ClanOutfits.rogue(ClanOutfits.roll(clan.id(), seeded), seeded);
        n.setGearRolled(true);
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            Item item = clothes.get(slot);
            n.setItemSlot(slot, item == null ? ItemStack.EMPTY : new ItemStack(item));
            n.setDropChance(slot, ClanOutfits.DROP_CHANCE);
        }
    }

    // ------------------------------------------------------------ one body per page

    /**
     * A listed rogue coming into the world (spawned, or loaded with its chunk): adopted as the
     * page's body, or turned away if the page was claimed or another body is already about.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof SparringNinja n) || event.getLevel().getServer() == null) {
            return;
        }
        int id = entryId(n);
        if (id < 0) {
            return;
        }
        BingoBook book = BingoBook.get(event.getLevel().getServer());
        BingoEntry e = book.find(id);
        if (!mayJoin(e, n)) {
            event.setCanceled(true); // a claimed (or forgotten) rogue never comes back
            return;
        }
        if (!n.getUUID().equals(e.entity)) {
            e.entity = n.getUUID();
            book.setDirty();
        }
        BODIES.put(e.id, n);
    }

    /** Whether a body of page {@code e} may come into the world: the page is at large and no other body is about. */
    public static boolean mayJoin(BingoEntry e, SparringNinja n) {
        if (e == null || !e.active()) {
            return false;
        }
        SparringNinja other = body(e);
        return other == null || other == n;
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof SparringNinja n) || event.getLevel().getServer() == null) {
            return;
        }
        int id = entryId(n);
        if (id >= 0 && BODIES.get(id) == n) {
            BODIES.remove(id);
            BingoEntry e = BingoBook.get(event.getLevel().getServer()).find(id);
            if (e != null && e.active() && n.isAlive()) {
                e.area = n.blockPosition(); // unloaded with its chunk: last seen here
                BingoBook.get(event.getLevel().getServer()).setDirty();
            }
        }
    }

    // ------------------------------------------------------------ bounties

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead.getServer() == null || entryId(dead) < 0) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer == null) {
            killer = dead.getKillCredit();
        }
        Player payee = payee(killer, uuid -> dead.getServer().getPlayerList().getPlayer(uuid));
        BingoBook book = BingoBook.get(dead.getServer());
        BingoEntry e = book.find(entryId(dead));
        BODIES.remove(entryId(dead));
        if (e != null && payee != null) {
            book.claim(e, payee);
        } else if (e != null) {
            e.entity = null; // fell to something else: rumours say they survived
            book.setDirty();
        }
    }

    /** From {@code ally.Crew.yieldTo}: a listed rogue gave up to {@code victor}. Pays the bounty and makes them an ordinary ninja. */
    public static boolean onYield(SparringNinja ninja, Player victor) {
        int id = entryId(ninja);
        if (id < 0 || ninja.getServer() == null) {
            return false;
        }
        BingoBook book = BingoBook.get(ninja.getServer());
        boolean paid = book.claim(book.find(id), victor);
        ninja.getPersistentData().remove(TAG);
        BODIES.remove(id);
        return paid;
    }

    /**
     * Who is paid for a kill by {@code killer}: a player; a crew mate's or shadow clone's leader
     * ({@code players} looks the leader up). Null if nobody.
     */
    public static Player payee(Entity killer, Function<UUID, Player> players) {
        if (killer instanceof Player player) {
            return player;
        }
        if (killer instanceof LivingEntity living) {
            UUID side = Crew.sideOf(living);
            if (!side.equals(living.getUUID())) {
                return players.apply(side);
            }
        }
        return null;
    }

    // ------------------------------------------------------------ the chūnin gift

    /** Once a player is chūnin: a bingo book from the village (once). Returns whether it was given now. */
    public static boolean giftCheck(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIFT_TAG) || !PlayerRank.atLeast(player, PlayerRank.CHUNIN)) {
            return false;
        }
        persisted.putBoolean(GIFT_TAG, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(BountyItems.BINGO_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.sendSystemMessage(Component.translatable("bingo.noirm.gift").withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Sneak-using a mission board: buys a bingo book for {@link BingoBook#BOOK_PRICE} ryo. Returns whether it was bought. */
    public static boolean buyAtBoard(Player player) {
        if (!com.noirm.economy.Ryo.take(player, BingoBook.BOOK_PRICE)) {
            player.displayClientMessage(Component.translatable("bingo.noirm.board.poor", com.noirm.economy.Ryo.format(BingoBook.BOOK_PRICE))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack book = new ItemStack(BountyItems.BINGO_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.displayClientMessage(Component.translatable("bingo.noirm.board.bought", com.noirm.economy.Ryo.format(BingoBook.BOOK_PRICE))
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    static boolean holdsBook(Player player) {
        return player.getInventory().contains(new ItemStack(BountyItems.BINGO_BOOK.get()));
    }
}

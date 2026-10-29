package com.noirm.bounty;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.clan.WorldClans;
import com.noirm.economy.Ryo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The bingo book: the world's register of notable rogues (saved with the world, in the overworld's
 * data as {@value #NAME}).
 *
 * <ul>
 *   <li><b>Seeded</b> when the world first starts: {@link #REGULAR_TARGET} A- and S-class rogues
 *       {@link #MIN_DISTANCE}–{@link #MAX_DISTANCE} blocks from the world spawn, and
 *       {@link #LEGEND_COUNT} legends further out ({@link #LEGEND_MIN_DISTANCE}–{@link #LEGEND_MAX_DISTANCE}).</li>
 *   <li><b>Refreshed</b> once a day ({@link #REFRESH_TICKS}): rogues nobody is near drift a little
 *       ({@link #WANDER} blocks at most), and a new name appears when fewer than
 *       {@link #REGULAR_TARGET} are still at large. Legends never come back once claimed.</li>
 *   <li><b>Claimed</b> pages stay in the book, crossed out with who claimed them (the last
 *       {@link #MAX_CLAIMED_SHOWN} regular ones; legends forever).</li>
 * </ul>
 * Bringing rogues into the world near their last-seen area, and paying bounties, is
 * {@link BingoEvents}.
 */
public final class BingoBook extends SavedData {
    public static final String NAME = "noirm_bingo_book";
    /** How many ordinary rogues are at large at once, and how many legends a world has. */
    public static final int REGULAR_TARGET = 6, LEGEND_COUNT = 4;
    /** Where ordinary rogues are seen (blocks from the world spawn). */
    public static final int MIN_DISTANCE = 500, MAX_DISTANCE = 2400;
    public static final int LEGEND_MIN_DISTANCE = 1500, LEGEND_MAX_DISTANCE = 3500;
    /**
     * Legends are deadly to a beginner (they can drop a new player in under two seconds), so a
     * legend's last-seen area is never closer than this to the world spawn, however they wander,
     * chase or flee ({@link #keepAway}), and they never turn up for a player in their first
     * {@link #LEGEND_NEW_PLAYER_MINUTES} minutes of play.
     */
    public static final int LEGEND_SAFE_RADIUS = 1000;
    public static final int LEGEND_NEW_PLAYER_MINUTES = 120;
    /** One refresh per Minecraft day. */
    public static final int REFRESH_TICKS = 24000;
    public static final int WANDER = 160;
    public static final int MAX_CLAIMED_SHOWN = 8;
    /** One in this many ordinary rogues is S-class (the rest are A). */
    public static final int S_CLASS_ONE_IN = 3;
    /** Strength (speed and taijutsu) by class: A, S, legendary. Chosen inside each class (see {@link NinjaClass}). */
    public static final float A_MIN = 48f, A_MAX = 62f, S_MIN = 90f, S_MAX = 140f, LEGEND_MIN = 180f, LEGEND_MAX = 240f;
    /** Bounties in ryo: A, S, legendary (min–max). */
    public static final int A_BOUNTY_MIN = 1200, A_BOUNTY_MAX = 2000, S_BOUNTY_MIN = 4000, S_BOUNTY_MAX = 7000,
            LEGEND_BOUNTY_MIN = 20000, LEGEND_BOUNTY_MAX = 35000;
    /** A legendary jinchūriki holds at least this many tails. */
    public static final int LEGEND_MIN_TAILS = 6;
    /** What a bingo book costs at a village shop. */
    public static final int BOOK_PRICE = 60;

    /** Syllables for rogues' names (our own). */
    private static final String[] SYLLABLES = {"ka", "ri", "zen", "ro", "ku", "sa", "mon", "ha", "ji", "tsu", "ra", "gen", "shi", "do",
            "ma", "ko", "ten", "yo", "ru", "zan", "kai", "sei", "mu", "ga", "o", "ran", "to", "e", "kyo", "ba"};

    private final List<BingoEntry> entries = new ArrayList<>();
    private int nextId = 1;
    private boolean seeded;
    private long lastRefresh;

    public BingoBook() {}

    public static BingoBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BingoBook::load, BingoBook::new, NAME);
    }

    // ------------------------------------------------------------ reading

    public List<BingoEntry> entries() {
        return entries;
    }

    public BingoEntry find(int id) {
        for (BingoEntry e : entries) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    public boolean seeded() {
        return seeded;
    }

    /** The pages in book order: rogues at large first (legends first, then by bounty), then claimed ones (latest first). */
    public List<BingoEntry> pages() {
        List<BingoEntry> list = new ArrayList<>(entries);
        list.sort(Comparator.<BingoEntry, Boolean>comparing(e -> !e.active())
                .thenComparing(e -> !e.legendary)
                .thenComparing(e -> e.active() ? -e.bounty : -e.claimedAt)
                .thenComparingInt(e -> e.id));
        return list;
    }

    public int activeRegulars() {
        return (int) entries.stream().filter(e -> e.active() && !e.legendary).count();
    }

    // ------------------------------------------------------------ seeding and refreshing

    /** Fills a new world's book (once). Returns whether it did. */
    public boolean seed(ServerLevel level, RandomSource random) {
        if (seeded) {
            return false;
        }
        seeded = true;
        lastRefresh = level.getGameTime();
        for (int i = 0; i < REGULAR_TARGET; i++) {
            add(level, random, false, null);
        }
        List<Legendary.Kind> kinds = new ArrayList<>(List.of(Legendary.Kind.values()));
        for (int i = 0; i < LEGEND_COUNT && !kinds.isEmpty(); i++) {
            add(level, random, true, kinds.remove(random.nextInt(kinds.size())));
        }
        setDirty();
        Noirm.LOGGER.info("The bingo book lists {} rogues ({} legends)", entries.size(), LEGEND_COUNT);
        return true;
    }

    /**
     * A day's refresh: rogues nobody is near wander, and a new one is added if too few are at large.
     * Returns the page added, or null.
     */
    public BingoEntry refresh(ServerLevel level, RandomSource random) {
        lastRefresh = level.getGameTime();
        for (BingoEntry e : entries) {
            if (e.active() && BingoEvents.body(e) == null) {
                BlockPos moved = keepAway(level, e, e.area.offset(random.nextInt(WANDER * 2 + 1) - WANDER, 0, random.nextInt(WANDER * 2 + 1) - WANDER));
                if (isLand(level, moved)) {
                    e.area = moved;
                }
            }
        }
        BingoEntry added = null;
        if (activeRegulars() < REGULAR_TARGET) {
            added = add(level, random, false, null);
        }
        // Old claimed pages fall out (legends stay forever).
        List<BingoEntry> claimed = new ArrayList<>(entries.stream().filter(e -> !e.active() && !e.legendary).toList());
        claimed.sort(Comparator.comparingLong(e -> -e.claimedAt));
        for (int i = MAX_CLAIMED_SHOWN; i < claimed.size(); i++) {
            entries.remove(claimed.get(i));
        }
        setDirty();
        return added;
    }

    /** Whether a refresh is due. */
    public boolean refreshDue(long now) {
        return now - lastRefresh >= REFRESH_TICKS;
    }

    /** Adds a page: an ordinary A/S rogue, or a legend of {@code kind}, at a fitting distance. */
    public BingoEntry add(ServerLevel level, RandomSource random, boolean legendary, Legendary.Kind kind) {
        BlockPos area = pickArea(level, random, legendary ? LEGEND_MIN_DISTANCE : MIN_DISTANCE, legendary ? LEGEND_MAX_DISTANCE : MAX_DISTANCE);
        return add(level, random, legendary, kind, area);
    }

    /** Adds a page last seen at {@code area}. */
    public BingoEntry add(ServerLevel level, RandomSource random, boolean legendary, Legendary.Kind kind, BlockPos area) {
        BingoEntry e = new BingoEntry();
        e.id = nextId++;
        e.given = name(random);
        e.seed = random.nextLong();
        if (e.seed == 0) {
            e.seed = 1;
        }
        e.area = area;
        e.added = level.getGameTime();
        e.legendary = legendary;
        WorldClans clans = WorldClans.get(level.getServer());
        Clan clan;
        if (legendary) {
            e.kind = kind == null ? Legendary.Kind.SHADOW : kind;
            e.epithet = e.kind.epithets[random.nextInt(e.kind.epithets.length)];
            e.strength = LEGEND_MIN + random.nextFloat() * (LEGEND_MAX - LEGEND_MIN);
            e.bounty = round100(LEGEND_BOUNTY_MIN + random.nextInt(LEGEND_BOUNTY_MAX - LEGEND_BOUNTY_MIN + 1));
            clan = clanFor(clans, e.kind, random);
            if (e.kind == Legendary.Kind.MANGEKYO) {
                e.mangekyo = clan.hasSharingan();
            } else if (e.kind == Legendary.Kind.BEAST) {
                e.tails = LEGEND_MIN_TAILS + random.nextInt(com.noirm.biju.BijuData.MAX_TAILS - LEGEND_MIN_TAILS + 1);
            }
            if (e.kind.nature != null && !clan.natures().containsKey(e.kind.nature)) {
                e.extraNature = e.kind.nature;
            }
        } else {
            boolean s = random.nextInt(S_CLASS_ONE_IN) == 0;
            e.strength = s ? S_MIN + random.nextFloat() * (S_MAX - S_MIN) : A_MIN + random.nextFloat() * (A_MAX - A_MIN);
            e.bounty = s ? round100(S_BOUNTY_MIN + random.nextInt(S_BOUNTY_MAX - S_BOUNTY_MIN + 1))
                    : round100(A_BOUNTY_MIN + random.nextInt(A_BOUNTY_MAX - A_BOUNTY_MIN + 1));
            clan = clans.rollForNewcomer(random);
        }
        e.clanId = clan.id();
        e.clanName = clan.name();
        entries.add(e);
        setDirty();
        return e;
    }

    /** Removes a page (tests, commands). */
    public void remove(BingoEntry e) {
        entries.remove(e);
        setDirty();
    }

    private static Clan clanFor(WorldClans clans, Legendary.Kind kind, RandomSource random) {
        List<Clan> all = clans.allClans();
        List<Clan> fitting = switch (kind) {
            case MANGEKYO -> all.stream().filter(Clan::hasSharingan).toList();
            case LIGHTNING, FIRE -> all.stream().filter(c -> c.natures().containsKey(kind.nature) && !c.hasByakugan()).toList();
            default -> List.of();
        };
        if (!fitting.isEmpty()) {
            return fitting.get(random.nextInt(fitting.size()));
        }
        return clans.rollForNewcomer(random);
    }

    private static int round100(int amount) {
        return Math.round(amount / 100f) * 100;
    }

    /** A name like "Kazen" or "Rokumon" (two or three syllables). */
    static String name(RandomSource random) {
        int n = 2 + (random.nextInt(3) == 0 ? 1 : 0);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append(SYLLABLES[random.nextInt(SYLLABLES.length)]);
        }
        return Character.toUpperCase(b.charAt(0)) + b.substring(1);
    }

    /** A spot on land {@code min}–{@code max} blocks from the world spawn (y = sea level until they're met). */
    static BlockPos pickArea(ServerLevel level, RandomSource random, int min, int max) {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos pos = spawn;
        for (int i = 0; i < 24; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = min + random.nextDouble() * (max - min);
            pos = new BlockPos(spawn.getX() + Mth.floor(Math.cos(a) * d), level.getSeaLevel(), spawn.getZ() + Mth.floor(Math.sin(a) * d));
            if (isLand(level, pos)) {
                return pos;
            }
        }
        return pos;
    }

    /**
     * Where page {@code e} may be last seen, given {@code area}: anywhere for an ordinary rogue; for
     * a legend, pushed straight out from the world spawn to at least {@link #LEGEND_SAFE_RADIUS}.
     */
    public static BlockPos keepAway(ServerLevel level, BingoEntry e, BlockPos area) {
        if (e == null || !e.legendary) {
            return area;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = area.getX() - spawn.getX(), dz = area.getZ() - spawn.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d >= LEGEND_SAFE_RADIUS) {
            return area;
        }
        if (d < 1) {
            dx = 1;
            dz = 0;
            d = 1;
        }
        double k = LEGEND_SAFE_RADIUS / d;
        return new BlockPos(spawn.getX() + Mth.floor(dx * k), area.getY(), spawn.getZ() + Mth.floor(dz * k));
    }

    /** Not ocean or river (works without loading the chunk: the biome comes from the world generator). */
    static boolean isLand(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER);
    }

    // ------------------------------------------------------------ bounties

    /**
     * Pays {@code entry}'s bounty to {@code payee} and crosses the page out. Only once: returns false
     * if it was already claimed.
     */
    public boolean claim(BingoEntry entry, Player payee) {
        if (entry == null || !entry.active() || payee == null) {
            return false;
        }
        entry.status = BingoEntry.Status.CLAIMED;
        entry.claimedBy = payee.getName().getString();
        entry.claimedAt = payee.level().getGameTime();
        entry.entity = null;
        setDirty();
        Ryo.give(payee, entry.bounty);
        payee.sendSystemMessage(Component.translatable("bingo.noirm.claimed", entry.name(), Ryo.format(entry.bounty))
                .withStyle(entry.legendary ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        payee.level().playSound(null, payee.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.8f);
        payee.level().playSound(null, payee.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.8f, 1.4f);
        if (payee.getServer() != null) {
            for (ServerPlayer other : payee.getServer().getPlayerList().getPlayers()) {
                if (other != payee) {
                    other.sendSystemMessage(Component.translatable("bingo.noirm.claimed.other", payee.getDisplayName(), entry.name())
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
        Noirm.LOGGER.info("Bingo book: {} claimed page {} ({} ryo)", entry.claimedBy, entry.id, entry.bounty);
        return true;
    }

    // ------------------------------------------------------------ saving

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach(e -> list.add(e.save()));
        tag.put("Entries", list);
        tag.putInt("NextId", nextId);
        tag.putBoolean("Seeded", seeded);
        tag.putLong("LastRefresh", lastRefresh);
        return tag;
    }

    public static BingoBook load(CompoundTag tag) {
        BingoBook book = new BingoBook();
        for (Tag t : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            book.entries.add(BingoEntry.load((CompoundTag) t));
        }
        book.nextId = Math.max(1, tag.getInt("NextId"));
        book.seeded = tag.getBoolean("Seeded");
        book.lastRefresh = tag.getLong("LastRefresh");
        return book;
    }

    /** The natures a page lists: their clan's, plus any extra. */
    public static List<Nature> natures(MinecraftServer server, BingoEntry e) {
        List<Nature> list = new ArrayList<>();
        WorldClans.get(server).find(e.clanId).ifPresent(c -> list.addAll(c.natures().keySet()));
        if (e.extraNature != null && !list.contains(e.extraNature)) {
            list.add(e.extraNature);
        }
        list.sort(Comparator.comparingInt(Enum::ordinal));
        return list;
    }
}

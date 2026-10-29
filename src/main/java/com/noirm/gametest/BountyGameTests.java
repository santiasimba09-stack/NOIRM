package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.bounty.BingoBook;
import com.noirm.bounty.BingoEntry;
import com.noirm.bounty.BingoEvents;
import com.noirm.bounty.BountyItems;
import com.noirm.bounty.Legendary;
import com.noirm.bounty.NinjaClass;
import com.noirm.clan.Nature;
import com.noirm.dojutsu.MangekyoNpcs;
import com.noirm.economy.Ryo;
import com.noirm.entity.SparringNinja;
import com.noirm.mission.PlayerRank;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.training.KillXp;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Ninja classes (D–S) and the bingo book: class thresholds for players and ninjas, the name tag,
 * kill XP by class, the saved registry, rogues turning up near where they were last seen, bounties
 * (paid once, to the leader for crew kills), legends and their marks, and claimed legends never
 * coming back. Every test runs in its own batch and finishes within one tick (the book's sweep
 * sends away bodies with no player near).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BountyGameTests {
    private BountyGameTests() {}

    // ------------------------------------------------------------ classes

    @GameTest(template = "empty", batch = "noirm_bounty_class")
    public static void classesFollowPower(GameTestHelper helper) {
        floor(helper);
        helper.assertTrue(NinjaClass.ofPower(10f) == NinjaClass.D, "10 is D");
        helper.assertTrue(NinjaClass.ofPower(15f) == NinjaClass.C && NinjaClass.ofPower(27.9f) == NinjaClass.C, "15–28 is C");
        helper.assertTrue(NinjaClass.ofPower(28f) == NinjaClass.B, "28 is B");
        helper.assertTrue(NinjaClass.ofPower(45f) == NinjaClass.A && NinjaClass.ofPower(79f) == NinjaClass.A, "45–80 is A");
        helper.assertTrue(NinjaClass.ofPower(80f) == NinjaClass.S && NinjaClass.ofPower(5000f) == NinjaClass.S, "80+ is S");
        // Players: every stat counts.
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        helper.assertTrue(NinjaClass.of(player) == NinjaClass.D, "a new player is D: " + NinjaClass.power(player));
        setAll(data, 30f);
        helper.assertTrue(NinjaClass.of(player) == NinjaClass.B, "30 in everything is B: " + NinjaClass.power(player));
        data.setSpeed(200f);
        data.setTaijutsu(200f);
        helper.assertTrue(NinjaClass.of(player) == NinjaClass.S, "a fast, hard hitter is S: " + NinjaClass.power(player));
        // Ninjas: strength, then the extras.
        float[] strengths = {10f, 20f, 35f, 60f, 120f};
        NinjaClass[] expected = {NinjaClass.D, NinjaClass.C, NinjaClass.B, NinjaClass.A, NinjaClass.S};
        for (int i = 0; i < strengths.length; i++) {
            SparringNinja ninja = ninja(helper, TestClans.PLAIN, strengths[i]);
            helper.assertTrue(NinjaClass.of(ninja) == expected[i], "strength " + strengths[i] + " is " + expected[i] + ", got "
                    + NinjaClass.of(ninja) + " (" + NinjaClass.power(ninja) + ")");
            ninja.discard();
        }
        SparringNinja plain = ninja(helper, TestClans.PLAIN, 40f);
        SparringNinja eyes = ninja(helper, TestClans.SHARINGAN, 40f);
        eyes.setSharinganStage(3);
        MangekyoNpcs.makeMangekyo(eyes, RandomSource.create(5));
        helper.assertTrue(NinjaClass.of(plain) == NinjaClass.B, "a plain 40 is B");
        helper.assertTrue(NinjaClass.of(eyes) == NinjaClass.A, "the same with a Mangekyō is A: " + NinjaClass.power(eyes));
        helper.assertTrue(NinjaClass.code(plain) == NinjaClass.B.ordinal(), "the synced code is the letter");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_bounty_tag")
    public static void theNameTagShowsTheClass(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = ninja(helper, TestClans.PLAIN, 60f);
        ninja.setCustomName(Component.literal("Kenji"));
        helper.assertTrue(NinjaClass.nameplate(ninja).getString().equals("[A] Kenji"), "an A-class tag: " + NinjaClass.nameplate(ninja).getString());
        helper.assertTrue(NinjaClass.nameplate(NinjaClass.LEGENDARY_CODE, Component.literal("Raizen")).getString().equals("[S+] Raizen"),
                "a legend is S+");
        helper.assertTrue(NinjaClass.tag(NinjaClass.C.ordinal()).getStyle().getColor() != null, "the tag is coloured");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_bounty_kill")
    public static void killXpFollowsTheClass(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer(); // a new player: D, worth 10
        ShinobiData data = data(player);
        helper.assertTrue(KillXp.enemyWorth(player) == 10f, "D is worth 10");
        SparringNinja c = ninja(helper, TestClans.PLAIN, 20f);
        helper.assertTrue(KillXp.enemyWorth(c) == 20f, "C is worth 20");
        data.training().fight().hit(player, false);
        Map<String, Float> xp = KillXp.onDefeated(player, c);
        float willpower = xp.getOrDefault("Willpower", 0f); // (a foe 1.5× your worth also builds willpower, on top)
        near(helper, KillXp.KILL_XP * 2f, total(xp) - willpower, "one class above you: twice the pool");
        near(helper, KillXp.KILL_XP * 2f * KillXp.WIL_SHARE, willpower, "and willpower on top");
        SparringNinja a = ninja(helper, TestClans.PLAIN, 60f);
        helper.assertTrue(KillXp.enemyWorth(a) == 80f, "A is worth 80");
        near(helper, KillXp.MAX_WORTH, KillXp.worthFactor(player, a), "three classes above: capped");
        SparringNinja d = ninja(helper, TestClans.PLAIN, 10f);
        near(helper, 1f, KillXp.worthFactor(player, d), "an equal class: the base pool");
        setAll(data, 60f); // now A
        near(helper, KillXp.MIN_WORTH, KillXp.worthFactor(player, d), "a D is next to nothing for an A");
        near(helper, 1f, KillXp.worthFactor(player, a), "A against A");
        clearAround(helper);
    }

    // ------------------------------------------------------------ the registry

    @GameTest(template = "empty", batch = "noirm_bounty_seed")
    public static void theBookIsSeededAndSaved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(BingoBook.get(level.getServer()).seeded(), "the world's book was filled when the server started");
        BingoBook book = new BingoBook();
        helper.assertTrue(book.seed(level, RandomSource.create(42)), "a fresh book fills");
        helper.assertFalse(book.seed(level, RandomSource.create(43)), "only once");
        long legends = book.entries().stream().filter(e -> e.legendary).count();
        helper.assertTrue(book.entries().size() == BingoBook.REGULAR_TARGET + BingoBook.LEGEND_COUNT, "rogues and legends: " + book.entries().size());
        helper.assertTrue(legends == BingoBook.LEGEND_COUNT, "legends: " + legends);
        helper.assertTrue(book.entries().stream().filter(e -> e.legendary).map(e -> e.kind).distinct().count() == legends,
                "every legend is a different kind");
        BlockPos spawn = level.getSharedSpawnPos();
        for (BingoEntry e : book.entries()) {
            int code = e.code();
            double distance = Math.sqrt(e.area.distSqr(new BlockPos(spawn.getX(), e.area.getY(), spawn.getZ())));
            if (e.legendary) {
                helper.assertTrue(code == NinjaClass.LEGENDARY_CODE, "legends are S+");
                helper.assertTrue(e.bounty >= BingoBook.LEGEND_BOUNTY_MIN && !e.epithet.isEmpty(), "a huge bounty and an epithet");
                helper.assertTrue(distance >= BingoBook.LEGEND_MIN_DISTANCE - 1 && distance <= BingoBook.LEGEND_MAX_DISTANCE + 1, "far out: " + distance);
            } else {
                helper.assertTrue(code == NinjaClass.A.ordinal() || code == NinjaClass.S.ordinal(), "only A and S rogues: " + code);
                helper.assertTrue(e.bounty >= BingoBook.A_BOUNTY_MIN, "a bounty: " + e.bounty);
                helper.assertTrue(distance >= BingoBook.MIN_DISTANCE - 1 && distance <= BingoBook.MAX_DISTANCE + 1, "around: " + distance);
            }
            helper.assertFalse(e.given.isEmpty() || e.clanId.isEmpty(), "a name and a clan");
        }
        // Saved and read back.
        BingoBook copy = BingoBook.load(book.save(new CompoundTag()));
        helper.assertTrue(copy.entries().size() == book.entries().size() && copy.seeded(), "the same pages");
        for (BingoEntry e : book.entries()) {
            BingoEntry c = copy.find(e.id);
            helper.assertTrue(c != null && c.given.equals(e.given) && c.bounty == e.bounty && c.area.equals(e.area) && c.kind == e.kind
                    && c.legendary == e.legendary && c.seed == e.seed && c.tails == e.tails && c.clanId.equals(e.clanId), "page " + e.id + " kept");
        }
        // A day later: one more name if one was claimed.
        BingoEntry first = book.entries().stream().filter(e -> !e.legendary).findFirst().orElseThrow();
        first.status = BingoEntry.Status.CLAIMED;
        BingoEntry added = book.refresh(level, RandomSource.create(7));
        helper.assertTrue(added != null && !added.legendary && book.activeRegulars() == BingoBook.REGULAR_TARGET, "a new name appears");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_bounty_spawn")
    public static void rogueTurnsUpNearWhereTheyWereLastSeen(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        BingoBook book = BingoBook.get(level.getServer());
        BlockPos area = helper.absolutePos(new BlockPos(1, 1, 1));
        BingoEntry e = book.add(level, RandomSource.create(11), false, null, area);
        try {
            helper.assertTrue(BingoEvents.checkNear(level, book, area.offset(400, 0, 0)) == 0, "nobody near: nobody comes");
            helper.assertTrue(BingoEvents.body(e) == null, "not in the world");
            helper.assertTrue(BingoEvents.checkNear(level, book, area.offset(30, 0, 20)) == 1, "a player near: they turn up");
            SparringNinja n = BingoEvents.body(e);
            helper.assertTrue(n != null && n.isAlive(), "a body");
            helper.assertTrue(n.distanceToSqr(area.getX() + 0.5, n.getY(), area.getZ() + 0.5) <= 13 * 13, "near the area");
            helper.assertTrue(n.getDisplayName().getString().equals(e.name().getString()), "with their name: " + n.getDisplayName().getString());
            helper.assertTrue(BingoEvents.entryId(n) == e.id && e.entity.equals(n.getUUID()), "tied to the page");
            helper.assertTrue(n.clanId().equals(e.clanId) && Math.abs(n.combatSpeed() - e.strength) < 0.01f, "their clan and strength");
            helper.assertTrue(NinjaClass.of(n).ordinal() == e.code(), "the class the book shows: " + NinjaClass.of(n) + " vs " + e.code());
            helper.assertTrue(com.noirm.world.wild.WildNinjas.isRogue(n), "a rogue (hostile)");
            helper.assertTrue(BingoEvents.checkNear(level, book, area) == 0 && BingoEvents.body(e) == n, "only one body");
            // A second body (an old one loading with its chunk) is turned away.
            SparringNinja copy = NoirmEntities.SPARRING_NINJA.get().create(level);
            copy.load(n.saveWithoutId(new CompoundTag()));
            copy.setUUID(java.util.UUID.randomUUID());
            helper.assertFalse(BingoEvents.mayJoin(e, copy), "no second body");
            helper.assertFalse(level.addFreshEntity(copy), "it never comes into the world");
            // Remembered look: brought back later, they look the same.
            String look = e.look;
            helper.assertFalse(look.isEmpty(), "their look is remembered");
            // Nobody near (no players in a test world): they move on, the page keeps where.
            int gone = BingoEvents.sweep(level, book);
            helper.assertTrue(gone >= 1 && BingoEvents.body(e) == null && n.isRemoved(), "they moved on");
            helper.assertTrue(e.entity == null && e.active(), "still at large");
            BingoEvents.checkNear(level, book, area);
            SparringNinja again = BingoEvents.body(e);
            helper.assertTrue(again != null && again.outfit().equals(look), "back with the same look");
            again.discard();
        } finally {
            book.remove(e);
        }
        clearAround(helper);
    }

    /** Legends are never seen near the world spawn and never come for a player in their first hours. */
    @GameTest(template = "empty", batch = "noirm_bounty_legend_safe", timeoutTicks = 40)
    public static void legendsStayAwayFromNewPlayers(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        BingoBook book = BingoBook.get(level.getServer());
        BlockPos area = helper.absolutePos(new BlockPos(1, 1, 1));
        BingoEntry legend = book.add(level, RandomSource.create(31), true, com.noirm.bounty.Legendary.Kind.FIRE, area);
        BingoEntry rogue = book.add(level, RandomSource.create(32), false, null, area.offset(2, 0, 0));
        try {
            BlockPos spawn = level.getSharedSpawnPos();
            BlockPos nearSpawn = spawn.offset(200, 0, 50);
            BlockPos pushed = BingoBook.keepAway(level, legend, nearSpawn);
            double d = Math.hypot(pushed.getX() - spawn.getX(), pushed.getZ() - spawn.getZ());
            helper.assertTrue(Math.abs(d - BingoBook.LEGEND_SAFE_RADIUS) <= 2, "a legend wandering toward spawn is kept " + BingoBook.LEGEND_SAFE_RADIUS
                    + " blocks out, got " + d);
            helper.assertTrue(BingoBook.keepAway(level, rogue, nearSpawn).equals(nearSpawn), "ordinary rogues may come closer");
            BlockPos far = spawn.offset(3000, 0, 0);
            helper.assertTrue(BingoBook.keepAway(level, legend, far).equals(far), "far out, nothing changes");
            // A new player near their area: the ordinary rogue comes, the legend doesn't.
            helper.assertTrue(BingoEvents.checkNear(level, book, area.offset(10, 0, 0), false) == 1, "only the ordinary rogue comes");
            helper.assertTrue(BingoEvents.body(legend) == null && BingoEvents.body(rogue) != null, "not the legend");
            // A seasoned player: the legend comes too.
            helper.assertTrue(BingoEvents.checkNear(level, book, area.offset(10, 0, 0), true) == 1 && BingoEvents.body(legend) != null,
                    "for a seasoned player the legend turns up");
            helper.assertTrue(com.noirm.bounty.BountyPackets.traits(level.getServer(), legend).get(0).getString().contains("S+"),
                    "and their page opens with the danger line");
        } finally {
            for (BingoEntry e : java.util.List.of(legend, rogue)) {
                SparringNinja body = BingoEvents.body(e);
                if (body != null) {
                    body.discard();
                }
                book.remove(e);
            }
        }
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_bounty_pay")
    public static void bountyIsPaidOnceAndToTheLeader(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        BingoBook book = BingoBook.get(level.getServer());
        BlockPos area = helper.absolutePos(new BlockPos(1, 1, 1));
        BingoEntry e = book.add(level, RandomSource.create(21), false, null, area);
        BingoEntry y = book.add(level, RandomSource.create(22), false, null, area);
        BingoEntry y2 = book.add(level, RandomSource.create(23), false, null, area);
        try {
            Player player = helper.makeMockPlayer();
            Ryo.set(player, 0);
            SparringNinja n = BingoEvents.spawn(level, book, e, area);
            helper.assertTrue(n != null, "the rogue is here");
            n.hurt(n.damageSources().playerAttack(player), 100000f);
            helper.assertFalse(n.isAlive(), "the rogue falls");
            helper.assertTrue(!e.active() && e.claimedBy.equals(player.getName().getString()), "the page is crossed out with who did it");
            helper.assertTrue(Ryo.balance(player) == e.bounty, "the bounty is paid: " + Ryo.balance(player) + " / " + e.bounty);
            helper.assertFalse(book.claim(e, player), "only once");
            helper.assertTrue(Ryo.balance(player) == e.bounty, "nothing more");
            // Making a listed rogue yield pays too, and they become an ordinary ninja.
            Player other = helper.makeMockPlayer();
            Ryo.set(other, 0);
            SparringNinja yielder = BingoEvents.spawn(level, book, y, area);
            helper.assertTrue(BingoEvents.onYield(yielder, other) && Ryo.balance(other) == y.bounty, "a yield pays the bounty");
            helper.assertTrue(BingoEvents.entryId(yielder) < 0 && !BingoEvents.onYield(yielder, other), "and only once");
            yielder.discard();
            // Dying to no one's hand pays nobody: the rogue "survives" and is next seen far away.
            SparringNinja unlucky = BingoEvents.spawn(level, book, y2, area);
            unlucky.hurt(unlucky.damageSources().genericKill(), 1000000f);
            helper.assertTrue(!unlucky.isAlive() && y2.active() && BingoEvents.body(y2) == null, "still at large");
            helper.assertTrue(Math.sqrt(y2.area.distSqr(area)) > BingoEvents.FLEE_DISTANCE - 2, "and fled far away");
            helper.assertTrue(BingoEvents.checkNear(level, book, area) == 0, "so they don't walk straight back in");
            // Crew mates' and clones' kills pay their leader.
            SparringNinja mate = ninja(helper, TestClans.PLAIN, 20f);
            mate.joinCrew(player.getUUID());
            helper.assertTrue(BingoEvents.payee(mate, id -> id.equals(player.getUUID()) ? player : null) == player, "a crew kill pays the leader");
            helper.assertTrue(BingoEvents.payee(player, id -> null) == player, "a player is paid themself");
            SparringNinja stranger = ninja(helper, TestClans.PLAIN, 20f);
            helper.assertTrue(BingoEvents.payee(stranger, id -> player) == null, "a stranger's kill pays nobody");
            helper.assertTrue(BingoEvents.payee(null, id -> player) == null, "nor does a fall");
            // The chūnin gift: once.
            ShinobiCapability.get(player).ifPresent(d -> d.career().setRank(PlayerRank.CHUNIN));
            helper.assertTrue(BingoEvents.giftCheck(player), "a chūnin gets a bingo book");
            helper.assertFalse(BingoEvents.giftCheck(player), "once");
            helper.assertTrue(player.getInventory().contains(new ItemStack(BountyItems.BINGO_BOOK.get())), "in the inventory");
        } finally {
            book.remove(e);
            book.remove(y);
            book.remove(y2);
        }
        clearAround(helper);
    }

    // ------------------------------------------------------------ legends

    @GameTest(template = "empty", batch = "noirm_bounty_legend")
    public static void legendsHaveTheirMarks(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        BingoBook book = BingoBook.get(level.getServer());
        BlockPos area = helper.absolutePos(new BlockPos(1, 1, 1));
        BingoEntry storm = book.add(level, RandomSource.create(31), true, Legendary.Kind.LIGHTNING, area);
        BingoEntry beast = book.add(level, RandomSource.create(32), true, Legendary.Kind.BEAST, area);
        BingoEntry eyes = book.add(level, RandomSource.create(33), true, Legendary.Kind.MANGEKYO, area);
        try {
            SparringNinja n = BingoEvents.spawn(level, book, storm, area);
            helper.assertTrue(n != null && Legendary.isLegendary(n) && Legendary.kindOf(n) == Legendary.Kind.LIGHTNING, "a lightning legend");
            helper.assertTrue(NinjaClass.code(n) == NinjaClass.LEGENDARY_CODE && NinjaClass.worth(n) == NinjaClass.LEGENDARY_WORTH, "S+, worth 320");
            helper.assertTrue(NinjaClass.of(n) == NinjaClass.S, "top-tier stats: " + NinjaClass.power(n));
            helper.assertTrue(n.getMaxHealth() > 150f, "and the health to match: " + n.getMaxHealth());
            helper.assertTrue(n.natures().containsKey(Nature.LIGHTNING), "lightning in their hands");
            helper.assertTrue(n.getDisplayName().getString().contains(", "), "a name and an epithet: " + n.getDisplayName().getString());
            helper.assertTrue(storm.bounty >= BingoBook.LEGEND_BOUNTY_MIN, "a huge bounty");
            for (int i = 0; i < 6; i++) {
                Legendary.tick(n); // aura, steps, entrance, bar: no trouble
            }
            helper.assertTrue(Legendary.crack(level, n, 3f) > 0, "the ground cracks where they land");
            Player player = helper.makeMockPlayer();
            helper.assertTrue(Legendary.entrance(player, n), "an entrance the first time");
            helper.assertFalse(Legendary.entrance(player, n), "not the second");
            helper.assertTrue(storm.met.contains(player.getUUID()), "remembered in the book");
            SparringNinja b = BingoEvents.spawn(level, book, beast, area);
            helper.assertTrue(b != null && b.tails() >= BingoBook.LEGEND_MIN_TAILS, "a jinchūriki of many tails: " + (b == null ? -1 : b.tails()));
            SparringNinja m = BingoEvents.spawn(level, book, eyes, area);
            helper.assertTrue(m != null && (!eyes.mangekyo || MangekyoNpcs.hasMangekyo(m)), "the Mangekyō legend has the eyes");
            n.discard();
            b.discard();
            m.discard();
        } finally {
            book.remove(storm);
            book.remove(beast);
            book.remove(eyes);
        }
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_bounty_noreturn")
    public static void aClaimedLegendNeverComesBack(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        BingoBook book = BingoBook.get(level.getServer());
        BlockPos area = helper.absolutePos(new BlockPos(1, 1, 1));
        BingoEntry e = book.add(level, RandomSource.create(41), true, Legendary.Kind.FIRE, area);
        try {
            Player player = helper.makeMockPlayer();
            SparringNinja n = BingoEvents.spawn(level, book, e, area);
            CompoundTag saved = n.saveWithoutId(new CompoundTag());
            n.hurt(n.damageSources().playerAttack(player), 1000000f);
            helper.assertTrue(!n.isAlive() && !e.active(), "the legend falls, the page is claimed");
            helper.assertTrue(BingoEvents.checkNear(level, book, area) == 0 && BingoEvents.spawn(level, book, e, area) == null, "never brought back");
            book.refresh(level, RandomSource.create(8));
            helper.assertTrue(!e.active() && book.find(e.id) == e, "a refresh keeps the legend's page, crossed out");
            SparringNinja ghost = NoirmEntities.SPARRING_NINJA.get().create(level);
            ghost.load(saved);
            ghost.setUUID(java.util.UUID.randomUUID());
            helper.assertFalse(level.addFreshEntity(ghost), "an old body loading from disk is turned away");
        } finally {
            book.remove(e);
        }
        clearAround(helper);
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja ninja(GameTestHelper helper, com.noirm.clan.Clan clan, float strength) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.sealBeast(0);
        ninja.joinClan(clan);
        ninja.setCombatStats(strength, strength);
        ninja.setInvulnerable(false);
        return ninja;
    }

    private static void setAll(ShinobiData data, float value) {
        data.setSpeed(value);
        data.setTaijutsu(value);
        data.setVitality(value);
        data.setReserves(value);
        data.setControl(value);
        data.setWillpower(value);
        data.setDurability(value);
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static float total(Map<String, Float> xp) {
        float sum = 0;
        for (float v : xp.values()) {
            sum += v;
        }
        return sum;
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }
}

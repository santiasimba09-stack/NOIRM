package com.noirm.gametest;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.clan.ClanOdds;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.WorldClans;
import com.noirm.creator.CharacterCreator;
import com.noirm.creator.CreatorData;
import com.noirm.creator.GeneticRoll;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.player.BigNumbers;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.traits.Perk;
import com.noirm.traits.PerkEvents;
import com.noirm.traits.Perks;
import com.noirm.training.TrainingSystem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clans, round 3 (v0.3, docs/WAVE_V03.md items 3–5): canon-strength clans (the Uzumaki's ×100 pool), canon
 * clans as the normal roll (80%), and the creator's five clan rolls plus separate talent rerolls.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClanBoostGameTests {
    private static final String BATCH = "noirm_clan_boost";

    private ClanBoostGameTests() {}

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow();
    }

    private static Clan canon(String id) {
        return ClanData.canon(id).orElseThrow(() -> new IllegalStateException("no canon clan " + id));
    }

    private static Player member(GameTestHelper helper, Clan clan) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, clan);
        data.talents().set(List.of());
        TrainingSystem.applyReserves(data);
        PerkEvents.tick(player, data);
        return player;
    }

    private static void near(GameTestHelper helper, float expected, float actual, float tolerance, String what) {
        helper.assertTrue(Math.abs(expected - actual) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    // ---------------------------------------------------------------- the Uzumaki: simply overpowered

    /** About 100x a normal ninja's own pool (150x with a beast), refilling no slower than a normal pool. */
    @GameTest(template = "empty", batch = BATCH)
    public static void uzumakiPoolIsAHundredTimesAndRefills(GameTestHelper helper) {
        Player plainPlayer = member(helper, TestClans.PLAIN);
        Player uzumakiPlayer = member(helper, canon("uzumaki"));
        ShinobiData p = data(plainPlayer);
        ShinobiData u = data(uzumakiPlayer);
        float ratio = u.getMaxChakra() / p.getMaxChakra();
        helper.assertTrue(ratio >= 95f && ratio <= 101f, "an Uzumaki's pool is ~100x: " + u.getMaxChakra() + " vs " + p.getMaxChakra());
        near(helper, 100f, Perks.poolMultiplier(u), 0.01f, "the pool multiplier");
        // Seconds to refill from empty: a normal pool and an Uzumaki's.
        float plainRefill = p.getMaxChakra() / (ShinobiData.CHAKRA_REGEN_PER_TICK * 20f * p.chakraRegenMultiplier());
        float uzumakiRefill = u.getMaxChakra() / (ShinobiData.CHAKRA_REGEN_PER_TICK * 20f * u.chakraRegenMultiplier());
        helper.assertTrue(uzumakiRefill <= plainRefill * 0.5f, "enormous regen: a 100x pool refills in " + uzumakiRefill
                + " s, a normal one in " + plainRefill + " s");
        helper.assertTrue(uzumakiRefill >= 10f, "but not instantly: " + uzumakiRefill + " s");
        // Ticking really fills it that fast.
        u.setChakra(0f);
        for (int t = 0; t < 20; t++) {
            u.tick();
        }
        helper.assertTrue(u.getChakra() >= 100f * 0.9f * ShinobiData.CHAKRA_REGEN_PER_TICK * 20f * 2f,
                "one second gives an Uzumaki a whole normal pool's worth and more: " + u.getChakra());
        // Vitality and sealing.
        helper.assertTrue(uzumakiPlayer.getMaxHealth() >= plainPlayer.getMaxHealth() + 10f, "huge vitality: " + uzumakiPlayer.getMaxHealth());
        helper.assertTrue(Perks.mult(u, Perk.SEALING_POWER) >= 2.5f, "sealing mastery");
        // Reserves don't train a hundred times faster just because the pool never runs dry.
        float plainXp = TrainingSystem.spendXp(p, 100f);
        float uzumakiXp = TrainingSystem.spendXp(u, 100f);
        near(helper, plainXp / 10f, uzumakiXp, 0.001f, "Reserves XP per chakra spent is /10 for a 100x pool");
        // A beast on top.
        com.noirm.biju.BijuSystem.seal(uzumakiPlayer, u, 9);
        TrainingSystem.applyReserves(u);
        helper.assertTrue(u.getMaxChakra() / p.getMaxChakra() >= 145f, "an Uzumaki jinchuriki: ~150x, " + u.getMaxChakra());
        helper.succeed();
    }

    /** Big pools make more shadow clones: +4 per cast for an Uzumaki, +3 for a Senju, none for a normal pool. */
    @GameTest(template = "empty", batch = BATCH)
    public static void bigPoolsMakeMoreShadowClones(GameTestHelper helper) {
        helper.assertTrue(ShadowClone.bonusClones(member(helper, TestClans.PLAIN)) == 0, "a normal pool: the base count");
        helper.assertTrue(ShadowClone.bonusClones(member(helper, canon("uzumaki"))) == 4, "an Uzumaki: +4");
        helper.assertTrue(ShadowClone.bonusClones(member(helper, canon("senju"))) == 3, "a Senju: +3");
        helper.assertTrue(ShadowClone.bonusClones(member(helper, canon("hatake"))) == 0, "a Hatake: average reserves, the base count");
        helper.succeed();
    }

    /** The other great clans and the canon fixes. */
    @GameTest(template = "empty", batch = BATCH)
    public static void greatClansAndCanonFixes(GameTestHelper helper) {
        Player plainPlayer = member(helper, TestClans.PLAIN);
        ShinobiData plain = data(plainPlayer);
        ShinobiData uchiha = data(member(helper, canon("uchiha")));
        near(helper, 1.5f, Perks.mult(uchiha, Perk.SHARINGAN_XP), 0.01f, "Uchiha eyes grow faster");
        helper.assertTrue(Perks.mult(uchiha, Perk.GENJUTSU_POWER) >= 2f, "Uchiha genjutsu x2");
        helper.assertTrue(Perks.mult(uchiha, Perk.STRAIN) > 1.5f, "and the canon cost stays: strain");
        Player kaguyaPlayer = member(helper, canon("kaguya"));
        ShinobiData kaguya = data(kaguyaPlayer);
        helper.assertTrue(kaguya.getDurability() >= 25f && Perks.mult(kaguya, Perk.MELEE_DAMAGE) >= 1.4f, "Kaguya: bones and blows");
        helper.assertTrue(Perks.get(kaguya, Perk.HEALTH_REGEN) < 0f, "and the illness stays");
        Player senjuPlayer = member(helper, canon("senju"));
        helper.assertTrue(senjuPlayer.getMaxHealth() >= plainPlayer.getMaxHealth() + 20f, "Senju vitality: " + senjuPlayer.getMaxHealth());
        helper.assertTrue(Perks.get(data(senjuPlayer), Perk.ROUGH_CONTROL) == 0f, "no rough control for the Senju (not canon)");
        ShinobiData hatake = data(member(helper, canon("hatake")));
        near(helper, plain.getMaxChakra(), hatake.getMaxChakra(), 0.01f, "Hatake: average reserves");
        near(helper, plain.chakraRegenMultiplier(), hatake.chakraRegenMultiplier(), 0.01f, "and average regen");
        helper.assertTrue("Kazekage line".equals(canon("kazekage").name()), "no invented surname for the Kazekage line");
        helper.succeed();
    }

    /** An NPC of a big-pool clan has a bigger pool, but capped (x3), so it still runs dry. */
    @GameTest(template = "empty", batch = "noirm_clan_boost_npc")
    public static void npcPoolsAreCapped(GameTestHelper helper) {
        SparringNinja uzumaki = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        SparringNinja plain = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);
        uzumaki.joinClan(canon("uzumaki"));
        plain.joinClan(TestClans.PLAIN);
        Perks.forget(uzumaki);
        Perks.forget(plain);
        float base = ShinobiData.DEFAULT_MAX_CHAKRA + (uzumaki.combatReserves() - ShinobiData.DEFAULT_STAT) * TrainingSystem.CHAKRA_PER_RESERVES;
        near(helper, base * Perks.NPC_POOL_CAP, uzumaki.maxChakra(), 0.5f, "an Uzumaki ninja: x" + Perks.NPC_POOL_CAP + " chakra, not x100");
        float plainBase = ShinobiData.DEFAULT_MAX_CHAKRA + (plain.combatReserves() - ShinobiData.DEFAULT_STAT) * TrainingSystem.CHAKRA_PER_RESERVES;
        near(helper, plainBase, plain.maxChakra(), 0.5f, "a plain ninja: x1");
        uzumaki.discard();
        plain.discard();
        helper.succeed();
    }

    // ---------------------------------------------------------------- big numbers on the HUD

    @GameTest(template = "empty", batch = BATCH)
    public static void bigNumbersAreFormatted(GameTestHelper helper) {
        Map<Float, String> compact = new HashMap<>(Map.of(950f, "950", 2500f, "2.5k", 10000f, "10k", 12400f, "12.4k",
                12450f, "12.4k", 124000f, "124k", 1_250_000f, "1.2M", 0f, "0", 999f, "999", 20200f, "20.2k"));
        compact.forEach((v, s) -> helper.assertTrue(BigNumbers.compact(v).equals(s), v + " should read " + s + ", got " + BigNumbers.compact(v)));
        helper.assertTrue(BigNumbers.grouped(10000f).equals("10,000"), "chat: " + BigNumbers.grouped(10000f));
        helper.assertTrue(BigNumbers.grouped(950f).equals("950"), "chat: " + BigNumbers.grouped(950f));
        // the widest a 100x pool gets at Reserves 100 (550 x 100): still short
        helper.assertTrue((BigNumbers.compact(54321f) + "/" + BigNumbers.compact(55000f)).length() <= 11, "fits next to the bar");
        helper.succeed();
    }

    // ---------------------------------------------------------------- canon clans are the normal roll

    /** ~80% canon over many seeded rolls; the great clans rare (2–4% each) but there; generated clans still appear. */
    @GameTest(template = "empty", batch = BATCH)
    public static void canonClansAreTheNormalRoll(GameTestHelper helper) {
        near(helper, 0.8f, ClanOdds.get().canonShare(), 0.001f, "the table loaded: 80% canon");
        WorldClans clans = new WorldClans();
        RandomSource random = RandomSource.create(20261125L);
        int rolls = 6000;
        Map<String, Integer> counts = new HashMap<>();
        int canon = 0;
        for (int i = 0; i < rolls; i++) {
            Clan clan = clans.rollForNewcomer(random);
            if (clan.canon()) {
                canon++;
                counts.merge(clan.id(), 1, Integer::sum);
            }
        }
        float share = canon / (float) rolls;
        helper.assertTrue(share > 0.77f && share < 0.83f, "about 80% canon, got " + share);
        helper.assertTrue(!clans.customClans().isEmpty(), "generated clans still exist");
        for (String great : List.of("uchiha", "senju", "uzumaki", "hyuga", "kaguya")) {
            float s = counts.getOrDefault(great, 0) / (float) rolls;
            helper.assertTrue(s >= 0.012f && s <= 0.05f, great + " is rare but reachable (2-4%), got " + s);
            float expected = ClanOdds.get().chance(canon(great), ClanData.canonClans());
            helper.assertTrue(expected >= 0.02f && expected <= 0.04f, great + "'s table chance is 2-4%: " + expected);
        }
        helper.assertTrue(counts.getOrDefault("nara", 0) > counts.getOrDefault("uchiha", 0), "the common clans outnumber the great ones");
        float total = 0f;
        for (Clan c : ClanData.canonClans()) {
            total += ClanOdds.get().chance(c, ClanData.canonClans());
        }
        near(helper, 0.8f, total, 0.001f, "the table's chances add up to the canon share");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the creator's rolls

    private static FakePlayer newcomer(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_roller"));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        return player;
    }

    /** Five clan rolls, and five talent rerolls apart from them that keep the clan and stats; every set stays pickable. */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void talentRerollsKeepTheClan(GameTestHelper helper) {
        FakePlayer player = newcomer(helper);
        ShinobiData data = ShinobiCapability.get(player).orElseThrow();
        CharacterCreator.beginFirstJoin(player, data);
        CreatorData creator = data.creator();
        helper.assertTrue(creator.rerollsLeft() == 4 && creator.talentRerollsLeft() == 5, "4 clan rerolls (5 rolls) and 5 talent rerolls");
        CharacterCreator.reroll(player, data);
        CharacterCreator.reroll(player, data);
        CharacterCreator.choose(player, data, 1);
        GeneticRoll before = creator.chosenRoll();
        List<String> firstTalents = before.talents();
        for (int i = 0; i < CharacterCreator.MAX_TALENT_REROLLS; i++) {
            helper.assertTrue(CharacterCreator.rerollTalents(player, data), "talent reroll " + (i + 1) + " is allowed");
            GeneticRoll now = creator.chosenRoll();
            helper.assertTrue(now.clanId().equals(before.clanId()), "the clan stays: " + now.clanId());
            helper.assertTrue(java.util.Arrays.equals(now.stats(), before.stats()), "the stats stay");
        }
        helper.assertFalse(CharacterCreator.rerollTalents(player, data), "a sixth talent reroll is refused");
        helper.assertTrue(creator.rolls().size() == 3, "talent rerolls add no clan rolls: " + creator.rolls().size());
        helper.assertTrue(creator.rerollsLeft() == 2, "and use none of the clan rerolls");
        helper.assertTrue(creator.talentSetCount(1) == 6 && creator.talentChoice(1) == 5, "six talent sets seen, the newest held");
        helper.assertTrue(CharacterCreator.chooseTalents(player, data, 0), "an earlier set can be picked again");
        helper.assertTrue(creator.chosenRoll().talents().equals(firstTalents), "the first talents are back");
        helper.assertFalse(CharacterCreator.chooseTalents(player, data, 6), "a set that doesn't exist can't");
        // Everything survives a save (a relog mid-creation).
        ShinobiData copy = new ShinobiData();
        copy.load(data.save());
        helper.assertTrue(copy.creator().talentSetCount(1) == 6 && copy.creator().talentRerollsLeft() == 0, "talent sets and rerolls are saved");
        helper.assertTrue(copy.creator().talentSets(1).get(3).equals(creator.talentSets(1).get(3)), "with every set");
        // Finishing applies the kept roll with the talents it holds.
        CharacterCreator.chooseTalents(player, data, 2);
        List<String> kept = creator.chosenRoll().talents();
        CharacterCreator.finish(player, data, creator.look());
        helper.assertTrue(data.talents().talents().equals(kept), "the kept talents are applied: " + data.talents().talents());
        helper.assertTrue(data.identity().clanId().equals(before.clanId()), "with the kept clan");
        helper.succeed();
    }
}

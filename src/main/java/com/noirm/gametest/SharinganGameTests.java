package com.noirm.gametest;

import static com.noirm.gametest.TestKit.floor;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.clan.ClanSystem;
import com.noirm.dojutsu.EyeData;
import com.noirm.dojutsu.Sharingan;
import com.noirm.dojutsu.SharinganAwakening;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The Sharingan's awakening routes (a comrade falls, a long hard fight, pressure) and its growth
 * from 1 to 3 tomoe through real fights.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SharinganGameTests {
    private SharinganGameTests() {}

    @GameTest(template = "empty", batch = "noirm_sharingan_ally")
    public static void aFallenComradeAwakensTheSharingan(GameTestHelper helper) {
        Player player = uchihaAt(helper, 0.5, 0.5);
        ShinobiData data = data(player);
        SparringNinja comrade = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 2.5f);
        SparringNinja stranger = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);
        comrade.joinCrew(player.getUUID());

        helper.assertFalse(SharinganAwakening.witnessDeath(player, stranger, null), "a stranger's death isn't a comrade's");
        helper.assertFalse(SharinganAwakening.witnessDeath(player, comrade, player), "not when you're the one who did it");
        helper.assertTrue(data.eyes().sharinganStage() == 0, "still dormant");

        helper.assertTrue(SharinganAwakening.witnessDeath(player, comrade, stranger), "seeing a crew mate fall awakens it");
        helper.assertTrue(data.eyes().sharinganStage() == 1 && data.eyes().isActive(), "1 tomoe, and the eyes open");
        helper.assertTrue(data.eyes().freeTicks() > 0, "for a while they stay open without draining chakra");

        int before = data.eyes().sharinganXp();
        SharinganAwakening.witnessDeath(player, comrade, stranger);
        helper.assertTrue(data.eyes().sharinganXp() - before == Sharingan.ALLY_LOSS_XP, "an awakened eye grows from the loss instead");
        comrade.discard();
        stranger.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_sharingan_hard_fight")
    public static void aLongHardFightAwakensTheSharingan(GameTestHelper helper) {
        Player player = uchihaAt(helper, 0.5, 0.5);
        Player other = uchihaAt(helper, 0.5, 2.5);
        SparringNinja strong = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);
        SparringNinja equal = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 2.5f);
        strong.setCombatStats(40, 40);
        equal.setCombatStats(10, 10);
        ShinobiData data = data(player);
        ShinobiData otherData = data(other);
        for (int tick = 1; tick <= SharinganAwakening.HARD_FIGHT_TICKS; tick++) {
            // A blow traded every tick (tiny, so the pressure route doesn't fire first).
            SharinganAwakening.onExchange(player, strong, 0.01f, false);
            SharinganAwakening.tick(player, data);
            SharinganAwakening.onExchange(other, equal, 0.01f, false);
            SharinganAwakening.tick(other, otherData);
            if (tick == SharinganAwakening.HARD_FIGHT_TICKS / 2) {
                helper.assertTrue(data.eyes().sharinganStage() == 0, "not yet halfway through the fight");
            }
        }
        helper.assertTrue(data.eyes().sharinganStage() == 1, "a minute against a far stronger foe awakens the eyes");
        helper.assertTrue(otherData.eyes().sharinganStage() == 0, "an even fight doesn't (it only builds pressure)");
        helper.assertTrue(otherData.eyes().pressure() > 0 && otherData.eyes().pressure() < SharinganAwakening.MAX_PRESSURE,
                "pressure builds in any real fight");
        strong.discard();
        equal.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pressureAwakensAtTheNextIntenseMoment(GameTestHelper helper) {
        Player player = uchihaAt(helper, 1.5, 1.5);
        ShinobiData data = data(player);
        helper.assertTrue(SharinganAwakening.hintTier(60) == 2 && SharinganAwakening.hintTier(100) == 4, "hint tiers by quarter");
        SharinganAwakening.addPressure(player, data, 50);
        SharinganAwakening.onClutch(player, data);
        helper.assertTrue(data.eyes().sharinganStage() == 0, "half-full pressure: a close call isn't enough yet");
        helper.assertTrue(data.eyes().pressure() > 50, "but it builds more pressure");
        SharinganAwakening.addPressure(player, data, 100);
        helper.assertTrue(data.eyes().sharinganStage() == 0, "full pressure alone doesn't open the eyes...");
        SharinganAwakening.onClutch(player, data);
        helper.assertTrue(data.eyes().sharinganStage() == 1 && data.eyes().isActive(), "...the next intense moment does");
        helper.assertTrue(data.eyes().pressure() == 0, "the pressure is spent");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_sharingan_growth")
    public static void theSharinganGrowsInRealFights(GameTestHelper helper) {
        Player player = uchihaAt(helper, 0.5, 0.5);
        ShinobiData data = data(player);
        EyeData eyes = data.eyes();
        Sharingan.advance(player, data, "x");
        SparringNinja strong = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);
        strong.setCombatStats(20, 20);
        TrainingDummy dummy = helper.spawnWithNoFreeWill(NoirmEntities.TRAINING_DUMMY.get(), 2.5f, 1f, 2.5f);

        eyes.setActive(false);
        Sharingan.onHitLanded(player, strong);
        helper.assertTrue(eyes.sharinganXp() == 0, "closed eyes don't grow");

        eyes.setActive(true);
        Sharingan.onHitLanded(player, strong);
        helper.assertTrue(eyes.sharinganXp() == Math.round(Sharingan.HIT_XP * 2f), "a hit on a foe twice as strong: twice the XP, got " + eyes.sharinganXp());

        int before = eyes.sharinganXp();
        for (int i = 0; i < 300; i++) {
            Sharingan.onHitLanded(player, dummy);
        }
        helper.assertTrue(eyes.sharinganXp() - before == Sharingan.DRILL_XP_CAP, "the dummy teaches only up to its cap");
        helper.assertTrue(eyes.sharinganStage() == 1, "the dummy alone can't reach 2 tomoe");

        while (eyes.sharinganStage() == 1) {
            Sharingan.onHitLanded(player, strong);
        }
        helper.assertTrue(eyes.sharinganStage() == 2, "real fights bring the second tomoe");
        helper.assertTrue(eyes.isActive() && eyes.freeTicks() > 0, "a new tomoe flares the eyes open");
        helper.assertTrue(data.jutsu().knows(Sharingan.GENJUTSU), "and 2 tomoe teaches the genjutsu");

        Sharingan.addXp(player, data, Sharingan.STAGE_XP[3]);
        helper.assertTrue(eyes.sharinganStage() == 3, "1200 XP: three tomoe");
        strong.discard();
        dummy.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_sharingan_foresight")
    public static void foreseenDodgesTeachMore(GameTestHelper helper) {
        Player player = uchihaAt(helper, 0.5, 0.5);
        ShinobiData data = data(player);
        EyeData eyes = data.eyes();
        Sharingan.advance(player, data, "x");
        Sharingan.advance(player, data, "x");
        eyes.setActive(true);
        SparringNinja attacker = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);

        int before = eyes.sharinganXp();
        SharinganAwakening.onClutch(player, data);
        helper.assertTrue(eyes.sharinganXp() - before == Sharingan.CLUTCH_XP, "a perfect dodge with the eyes open");

        Sharingan.onAttackWindup(attacker, player, 10);
        before = eyes.sharinganXp();
        SharinganAwakening.onClutch(player, data);
        helper.assertTrue(eyes.sharinganXp() - before == Sharingan.CLUTCH_XP + Sharingan.FORESEEN_XP, "a dodge the eyes foresaw teaches more");
        attacker.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_sharingan_npc")
    public static void aDormantUchihaNinjaAwakensWhenHurt(GameTestHelper helper) {
        floor(helper);
        Clan uchiha = ClanData.canon("uchiha").orElseThrow();
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        SparringNinja foe = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 2.5f);
        ninja.joinClan(uchiha);
        ninja.setSharinganStage(0);
        helper.assertTrue(SharinganAwakening.isDormantNpc(ninja), "an Uchiha ninja with closed eyes is dormant");
        ninja.setTarget(foe);
        helper.assertFalse(SharinganAwakening.tickNpc(ninja), "healthy: nothing happens");
        ninja.setHealth(ninja.getMaxHealth() * 0.2f);
        helper.assertTrue(SharinganAwakening.tickNpc(ninja), "badly hurt in a fight, it awakens");
        helper.assertTrue(Sharingan.activeStage(ninja) == 1, "with 1 tomoe");
        helper.assertFalse(SharinganAwakening.isDormantNpc(ninja), "no longer dormant");
        ninja.discard();
        foe.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    private static Player uchihaAt(GameTestHelper helper, double x, double z) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        ClanSystem.assign(player, data(player), TestClans.SHARINGAN);
        return player;
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow();
    }
}

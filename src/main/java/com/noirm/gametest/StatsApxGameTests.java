package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.Perception;
import com.noirm.entity.SparringNinja;
import com.noirm.player.Movement;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.training.Durability;
import com.noirm.training.FightLog;
import com.noirm.training.KillXp;
import com.noirm.training.TrainingSystem;
import com.noirm.training.TrainingSystem.Stat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * APX 0.1, "Stats": the gentler speed curve (35 / 50 breakpoints, the cap, perception), speed
 * trained in combat, the new Durability stat (negation, its exceptions, training, saving) and
 * kill XP.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StatsApxGameTests {
    private StatsApxGameTests() {}

    // ------------------------------------------------------------ speed curve

    @GameTest(template = "empty")
    public static void speedCurveHasItsBreakpoints(GameTestHelper helper) {
        near(helper, 1f, Movement.runMultiplier(10f), "speed 10 runs at the normal pace");
        near(helper, 1.35f, Movement.runMultiplier(35f), "gentle up to 35: ×1.35");
        near(helper, 1.41f, Movement.runMultiplier(50f), "only a little more up to 50: ×1.41");
        near(helper, Movement.runMultiplier(50f), Movement.runMultiplier(100f), "no more movement past 50");
        near(helper, Movement.runMultiplier(50f), Movement.runMultiplier(5000f), "even for legends");
        helper.assertTrue(Movement.runMultiplier(20f) < 1.2f, "gentler than v0.13 (×1.41 at 20): " + Movement.runMultiplier(20f));
        float early = Movement.runMultiplier(35f) - Movement.runMultiplier(20f);
        float late = Movement.runMultiplier(50f) - Movement.runMultiplier(35f);
        helper.assertTrue(late * 2 < early, "35→50 grows much less than 20→35: " + late + " vs " + early);
        helper.assertTrue(Movement.jumpBonus(100f) == Movement.jumpBonus(50f) && Movement.leap(100f) == Movement.leap(50f),
                "jumps and leaps stop at 50 too");
        helper.assertTrue(Movement.stepBonus(50f) == 1.0 && Movement.stepBonus(49f) == 0.5, "a full block step-up at 50");
        // Perception takes what movement stops taking.
        near(helper, 35f, Perception.perceptionSpeed(35f), "perception: plain speed up to 35");
        near(helper, 60.5f, Perception.perceptionSpeed(50f), "perception: the rest of 35→50 goes here");
        helper.assertTrue(Perception.perceptionSpeed(100f) > Perception.perceptionSpeed(50f), "perception keeps growing past 50");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerSpeedIsCapped(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        TrainingSystem.applyMovementBonus(player, 50f);
        double at50 = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        TrainingSystem.applyMovementBonus(player, 200f);
        double at200 = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(Math.abs(at50 - base * Movement.MAX_RUN_MULTIPLIER) < 1e-4, "speed 50 = ×1.41, got " + at50 / base);
        helper.assertTrue(Math.abs(at200 - at50) < 1e-6, "speed 200 moves like 50");
        TrainingSystem.applyMovementBonus(player, 10f);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_apx_npc_speed")
    public static void ninjaSpeedIsCapped(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(TestClans.PLAIN);
        ninja.setCombatStats(10f, 10f);
        double base = ninja.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        ninja.setCombatStats(50f, 10f);
        double at50 = ninja.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        ninja.setCombatStats(300f, 10f);
        double at300 = ninja.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        helper.assertTrue(Math.abs(at50 / base - Movement.MAX_RUN_MULTIPLIER) < 1e-4, "a speed-50 ninja runs ×1.41, got " + at50 / base);
        helper.assertTrue(Math.abs(at300 - at50) < 1e-6, "a speed-300 ninja runs no faster");
        clearAround(helper);
    }

    // ------------------------------------------------------------ speed trained in combat

    @GameTest(template = "empty", batch = "noirm_apx_speed_xp")
    public static void speedGrowsFromDodgesStringsChasesAndFlicker(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(TestClans.PLAIN);
        float equal = TrainingSystem.REAL_FIGHT_BONUS; // an equal teaches ×1.5
        // A real dodge (not perfect) and a perfect one.
        TrainingSystem.onAttackDodged(player, ninja, false);
        near(helper, TrainingSystem.DODGE_REAL_XP * equal, data.training().xp(Stat.SPEED), "slipping a ninja's attack trains speed");
        data.training().setXp(Stat.SPEED, 0f);
        TrainingSystem.onAttackDodged(player, ninja, true);
        near(helper, TrainingSystem.PERFECT_DODGE_XP * equal, data.training().xp(Stat.SPEED), "a perfect dodge trains more");
        helper.assertTrue(data.training().fight().dodges() == 1 && data.training().fight().perfectDodges() == 1, "and both are logged for kill XP");
        // Past the drill cap, real dodges still teach.
        data.setSpeed(40f);
        data.training().setXp(Stat.SPEED, 0f);
        TrainingSystem.onAttackDodged(player, ninja, false);
        helper.assertTrue(data.training().xp(Stat.SPEED) > 0f, "real dodges teach past 30");
        TrainingSystem.onAttackDodged(player, null, false);
        TrainingSystem.onJutsuCast(player, "flicker"); // no fight: a drill, and 40 is past the cap
        float before = data.training().xp(Stat.SPEED);
        data.setSpeed(10f);
        TrainingSystem.onJutsuCast(player, "flicker");
        near(helper, before + TrainingSystem.FLICKER_XP, data.training().xp(Stat.SPEED), "Body Flicker trains speed (a drill alone)");
        helper.assertTrue(data.training().xp(Stat.SPEED) == before + TrainingSystem.FLICKER_XP, "nothing else counted");
        // A fast string: two hits in a row.
        data.training().setXp(Stat.SPEED, 0f);
        TrainingSystem.onHitLanded(player, ninja, false, false);
        TrainingSystem.onHitLanded(player, ninja, false, false);
        near(helper, TrainingSystem.FAST_STRING_XP * equal, data.training().xp(Stat.SPEED), "a fast string trains speed");
        // A chase: both cover 3 blocks and the gap doesn't grow.
        data.training().setXp(Stat.SPEED, 0f);
        player.setPos(ninja.getX() - 3, ninja.getY(), ninja.getZ());
        helper.assertFalse(KillXp.chaseCheck(player, ninja), "the first look only sets the baseline");
        ninja.setPos(ninja.getX() + 3, ninja.getY(), ninja.getZ());
        player.setPos(player.getX() + 3.2, player.getY(), player.getZ());
        helper.assertTrue(KillXp.chaseCheck(player, ninja), "chasing a moving ninja counts");
        near(helper, TrainingSystem.CHASE_XP * equal, data.training().xp(Stat.SPEED), "and trains speed");
        helper.assertFalse(KillXp.chaseCheck(player, ninja), "standing still is no chase");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_apx_real_dodge")
    public static void dodgingARealAttackTrainsSpeed(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 1f, 1.5f);
        helper.assertTrue(CombatSystem.requestDodge(player, 0f, 0f), "the dodge starts");
        float health = player.getHealth();
        player.hurt(player.damageSources().mobAttack(zombie), 4f);
        helper.assertTrue(player.getHealth() == health, "the dodge slips the hit");
        near(helper, TrainingSystem.DODGE_XP + TrainingSystem.PERFECT_DODGE_XP * TrainingSystem.REAL_FIGHT_BONUS,
                data.training().xp(Stat.SPEED), "the dodge (drill) and the perfect dodge of a real attack both train speed");
        clearAround(helper);
    }

    // ------------------------------------------------------------ durability

    @GameTest(template = "empty")
    public static void durabilityCurve(GameTestHelper helper) {
        near(helper, 0f, Durability.negation(10f), "DUR 10 negates nothing");
        near(helper, 0.198f, Durability.negation(30f), "~20% at 30");
        near(helper, 0.379f, Durability.negation(60f), "~38% at 60");
        near(helper, 0.501f, Durability.negation(100f), "~50% at 100");
        float cap = Durability.negation(ShinobiData.MAX_STAT);
        helper.assertTrue(cap <= Durability.MAX_NEGATION && cap > 0.599f, "60% at the cap, got " + cap);
        float last = 0f;
        for (int dur = 10; dur <= 1000; dur += 10) {
            float n = Durability.negation(dur);
            helper.assertTrue(n >= last, "smooth and never going down at " + dur);
            last = n;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_apx_dur_damage")
    public static void durabilityNegatesDamageButNotKillOrVoid(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        float[] levels = {10f, 30f, 60f, 200f};
        for (float dur : levels) {
            data.setDurability(dur);
            player.setHealth(20f);
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 10f); // (generic: no difficulty scaling)
            float lost = 20f - player.getHealth();
            near(helper, 10f * (1f - Durability.negation(dur)), lost, "a 10-point hit at DUR " + dur);
        }
        data.setDurability(ShinobiData.MAX_STAT);
        player.setHealth(20f);
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().generic(), 10f);
        helper.assertTrue(20f - player.getHealth() >= 4f - 0.01f, "even at the cap a hit keeps 40%: never immune");
        // Exceptions: /kill, the void and starvation go straight through.
        player.setHealth(20f);
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().starve(), 5f);
        near(helper, 15f, player.getHealth(), "starvation ignores DUR");
        player.setHealth(20f);
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().fellOutOfWorld(), 5f);
        near(helper, 15f, player.getHealth(), "the void ignores DUR");
        helper.assertTrue(Durability.exempt(player.damageSources().genericKill()), "/kill ignores DUR");
        near(helper, 5f, Durability.reduce(player, player.damageSources().genericKill(), 5f), "/kill isn't reduced");
        // NPC ninjas: DUR from their strength.
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        ninja.joinClan(TestClans.PLAIN);
        ninja.setCombatStats(50f, 70f);
        near(helper, 60f, Durability.of(ninja), "a ninja's DUR is their strength");
        near(helper, 10f * (1f - Durability.negation(60f)), Durability.reduce(ninja, ninja.damageSources().playerAttack(player), 10f),
                "so strong ninjas are tankier");
        player.setHealth(20f);
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_apx_dur_xp")
    public static void durabilityTrainsFromRealHits(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 1f, 1.5f);
        float percent = 100f * 5f / player.getMaxHealth();
        Durability.onHitTaken(player, zombie, 5f);
        near(helper, percent * Durability.XP_PER_PERCENT * TrainingSystem.REAL_FIGHT_BONUS, data.training().xp(Stat.DURABILITY),
                "a zombie's hit (an equal) trains DUR by the share of health it took");
        helper.assertTrue(data.training().fight().damagePercent() > 0f, "and is logged for kill XP");
        data.training().setXp(Stat.DURABILITY, 0f);
        Durability.onHitTaken(player, null, 5f);
        near(helper, percent * Durability.XP_PER_PERCENT, data.training().xp(Stat.DURABILITY), "a hit with no attacker is a drill");
        data.setDurability(TrainingSystem.DRILL_CAP);
        data.training().setXp(Stat.DURABILITY, 0f);
        Durability.onHitTaken(player, null, 5f);
        near(helper, 0f, data.training().xp(Stat.DURABILITY), "drills stop at 30");
        Durability.onHitTaken(player, zombie, 5f);
        helper.assertTrue(data.training().xp(Stat.DURABILITY) > 0f, "real fights keep teaching past 30");
        // Through the real damage path too.
        data.setDurability(10f);
        data.training().setXp(Stat.DURABILITY, 0f);
        player.invulnerableTime = 0;
        Player rival = helper.makeMockPlayer(); // a player's hit: no difficulty scaling
        player.hurt(player.damageSources().playerAttack(rival), 4f);
        helper.assertTrue(data.training().xp(Stat.DURABILITY) > 0f, "a real hit trains DUR");
        player.setHealth(player.getMaxHealth());
        clearAround(helper);
    }

    @GameTest(template = "empty")
    public static void durabilitySavesAndOldSavesLoadAt10(GameTestHelper helper) {
        ShinobiData data = new ShinobiData();
        data.setDurability(42f);
        data.training().setXp(Stat.DURABILITY, 17f);
        ShinobiData loaded = new ShinobiData();
        loaded.load(data.save());
        near(helper, 42f, loaded.getDurability(), "DUR is saved");
        near(helper, 17f, loaded.training().xp(Stat.DURABILITY), "and its XP");
        CompoundTag old = data.save();
        old.remove("Durability");
        loaded.load(old);
        near(helper, ShinobiData.DEFAULT_STAT, loaded.getDurability(), "old saves load at 10");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_apx_crew_dur")
    public static void crewMatesKeepTheirDurability(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(TestClans.PLAIN);
        ninja.setCombatStats(20f, 30f);
        ninja.joinCrew(java.util.UUID.randomUUID());
        near(helper, 25f, ninja.stat(Stat.DURABILITY), "a recruit keeps the DUR of their strength");
        ninja.setStat(Stat.DURABILITY, 33f);
        CompoundTag tag = ninja.saveWithoutId(new CompoundTag());
        SparringNinja copy = NoirmEntities.SPARRING_NINJA.get().create(helper.getLevel());
        copy.load(tag);
        near(helper, 33f, copy.stat(Stat.DURABILITY), "and their trained DUR is saved");
        copy.discard();
        clearAround(helper);
    }

    // ------------------------------------------------------------ kill XP

    @GameTest(template = "empty", batch = "noirm_apx_kill_xp")
    public static void killXpGoesToTheStatsYouUsed(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        FightLog log = data.training().fight();
        // A brawl: punches only.
        SparringNinja brawled = ninja(helper, 0.5f);
        for (int i = 0; i < 6; i++) {
            log.parried(player);
        }
        Map<String, Float> brawl = KillXp.onDefeated(player, brawled);
        helper.assertTrue(!brawl.isEmpty() && brawl.keySet().iterator().next().equals(Stat.TAIJUTSU.key()), "punching wins teach taijutsu: " + brawl);
        helper.assertFalse(brawl.containsKey(Stat.SPEED.key()), "and not speed: " + brawl);
        near(helper, KillXp.KILL_XP, total(brawl), "an equal is worth the base pool");
        helper.assertTrue(KillXp.onDefeated(player, brawled).isEmpty(), "a ninja is only worth it once");
        // A dance: dodges only.
        SparringNinja danced = ninja(helper, 1.5f);
        for (int i = 0; i < 4; i++) {
            log.dodged(player, true);
        }
        log.damaged(player, 30f);
        Map<String, Float> dance = KillXp.onDefeated(player, danced);
        helper.assertTrue(dance.keySet().iterator().next().equals(Stat.SPEED.key()), "dodging wins teach speed first: " + dance);
        helper.assertTrue(dance.containsKey(Stat.DURABILITY.key()) && dance.containsKey(Stat.VITALITY.key()), "taking hits teaches DUR and vitality: " + dance);
        // Jutsu.
        SparringNinja cast = ninja(helper, 2.5f);
        log.jutsu(player);
        log.jutsu(player);
        Map<String, Float> jutsu = KillXp.onDefeated(player, cast);
        helper.assertTrue(jutsu.keySet().iterator().next().equals(Stat.RESERVES.key()) && jutsu.containsKey("Control"),
                "jutsu wins teach reserves and chakra control: " + jutsu);
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_apx_kill_worth")
    public static void killXpScalesWithTheEnemysStrength(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        SparringNinja strong = ninja(helper, 0.5f);
        strong.setCombatStats(60f, 60f);
        data.training().fight().hit(player, false);
        Map<String, Float> big = KillXp.onDefeated(player, strong);
        SparringNinja weak = ninja(helper, 2.5f);
        // Kill XP follows the class (bounty/NinjaClass): an A-class player against a D-class ninja.
        data.setSpeed(60f);
        data.setTaijutsu(60f);
        data.setVitality(60f);
        data.setDurability(60f);
        data.setReserves(60f);
        data.training().fight().hit(player, false);
        Map<String, Float> small = KillXp.onDefeated(player, weak);
        near(helper, KillXp.KILL_XP * KillXp.MAX_WORTH, big.get(Stat.TAIJUTSU.key()), "a much stronger foe gives the most");
        near(helper, KillXp.KILL_XP * KillXp.MIN_WORTH, total(small), "a much weaker one the least");
        helper.assertTrue(big.containsKey("Willpower") && !small.containsKey("Willpower"), "beating a stronger foe builds willpower");
        helper.assertTrue(KillXp.enemyWorth(strong) > KillXp.enemyWorth(weak), "worth follows strength");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_apx_kill_event")
    public static void killingOrBeatingANinjaGivesKillXp(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        // Yield: brought low, the ninja gives up, and that is a win.
        SparringNinja yielder = ninja(helper, 0.5f);
        yielder.setHealth(yielder.getMaxHealth() * 0.3f);
        yielder.hurt(yielder.damageSources().playerAttack(player), 3f);
        helper.assertTrue(yielder.isYielded(), "the ninja yields");
        helper.assertTrue(yielder.getPersistentData().getBoolean(KillXp.REWARDED_TAG), "a yield gives kill XP");
        // Kill.
        SparringNinja victim = ninja(helper, 2.5f);
        float before = data.training().xp(Stat.TAIJUTSU) + data.getTaijutsu() * 1000f;
        victim.hurt(victim.damageSources().playerAttack(player), 1000f);
        helper.assertFalse(victim.isAlive(), "the ninja falls");
        helper.assertTrue(victim.getPersistentData().getBoolean(KillXp.REWARDED_TAG), "a kill gives kill XP");
        helper.assertTrue(data.training().xp(Stat.TAIJUTSU) + data.getTaijutsu() * 1000f > before, "taijutsu grew from it");
        // Your own crew mate gives nothing.
        SparringNinja mate = ninja(helper, 1.5f);
        mate.joinCrew(player.getUUID());
        helper.assertTrue(KillXp.onDefeated(player, mate).isEmpty(), "crew mates are not trophies");
        clearAround(helper);
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja ninja(GameTestHelper helper, float x) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, 1.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        ninja.setInvulnerable(false);
        return ninja;
    }

    private static float total(Map<String, Float> xp) {
        float sum = 0f;
        for (float value : xp.values()) {
            sum += value;
        }
        return sum;
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }
}

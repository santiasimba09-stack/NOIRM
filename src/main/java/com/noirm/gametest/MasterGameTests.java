package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.combat.CombatData;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.StyleSystem;
import com.noirm.entity.MasterNinja;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.ModeSystem;
import com.noirm.master.MasterData;
import com.noirm.master.MasterSystem;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Masters (Might Guy): data, the spar, lessons; the Eight Gates and the Front Lotus. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MasterGameTests {
    private static final ResourceLocation GATE_1 = Noirm.id("taijutsu/gate_of_opening");
    private static final ResourceLocation GATE_2 = Noirm.id("taijutsu/gate_of_limit");
    private static final ResourceLocation LOTUS = Noirm.id("taijutsu/front_lotus");
    private static final ResourceLocation PEACOCK = Noirm.id("taijutsu/morning_peacock");
    private static final String GUY = MasterData.MIGHT_GUY.toString();

    private MasterGameTests() {}

    @GameTest(template = "empty", batch = "noirm_master_spawn")
    public static void masterComesFromDataAndKeepsThePeace(GameTestHelper helper) {
        floor(helper);
        helper.assertTrue(MasterData.get(MasterData.MIGHT_GUY) != null, "might_guy.json is loaded");
        MasterNinja guy = master(helper);
        helper.assertTrue(guy.definition() != null, "he knows who he is");
        helper.assertTrue(guy.getName().getContents() instanceof TranslatableContents t && t.getKey().equals("master.noirm.might_guy"),
                "named from his data: " + guy.getName());
        helper.assertTrue(Noirm.id("strong_fist").equals(guy.combatStyle()), "fights with the Strong Fist, got " + guy.combatStyle());
        helper.assertTrue(guy.outfit().startsWith("taijutsu_master:"), "wears his jumpsuit look, got " + guy.outfit());
        helper.assertTrue(guy.knownJutsu().isEmpty(), "spars with his body, not jutsu");
        Player player = player(helper);
        CombatSystem.setCombatMode(player, true);
        helper.assertFalse(guy.wouldChallenge(player), "never picks a fight, even with someone in combat mode");
        helper.assertFalse(Crew.recruit(player, guy), "can't be recruited");
        float health = guy.getHealth();
        helper.assertFalse(guy.hurt(guy.damageSources().playerAttack(player), 5f), "outside a spar he isn't hit");
        helper.assertTrue(guy.getHealth() == health && guy.getTarget() == null, "and doesn't fight back");
        // Saved and loaded, he's still Guy.
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        guy.saveWithoutId(tag);
        helper.assertTrue(GUY.equals(tag.getString("Master")), "the master id is saved");
        guy.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_master_spar", timeoutTicks = 60)
    public static void sparEndsWithoutDeathAndRecordsAPass(GameTestHelper helper) {
        floor(helper);
        MasterNinja guy = master(helper);
        Player player = player(helper);
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        helper.assertFalse(StyleSystem.available(data).contains(Noirm.id("strong_fist")), "no Strong Fist at taijutsu 10");

        // 1. Guy wins: the student stops at low health, alive, and hasn't passed.
        helper.assertTrue(MasterSystem.startSpar(player, guy), "the spar starts");
        helper.assertTrue(guy.isSparring() && guy.sparPartner() == player, "Guy is sparring the player");
        helper.assertTrue(guy.getMaxHealth() < 80f, "he holds back: " + guy.getMaxHealth() + " health in a spar");
        player.invulnerableTime = 0;
        player.hurt(guy.damageSources().mobAttack(guy), 1000f);
        helper.assertTrue(player.isAlive() && player.getHealth() >= 1f, "the student survives a huge blow, health " + player.getHealth());
        helper.assertFalse(guy.isSparring(), "and the spar ends");
        helper.assertFalse(data.masters().passed(GUY), "without a pass");
        player.invulnerableTime = 0;
        player.setHealth(player.getMaxHealth());
        player.hurt(guy.damageSources().mobAttack(guy), 5f);
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "outside a spar, Guy's hits do nothing");

        // 2. The student wears Guy down: he survives, and the test is passed.
        helper.assertTrue(MasterSystem.startSpar(player, guy), "a second spar");
        guy.invulnerableTime = 0;
        guy.hurt(guy.damageSources().playerAttack(player), 1000f);
        helper.assertTrue(guy.isAlive(), "Guy survives too");
        helper.assertFalse(guy.isSparring(), "the spar ends");
        helper.assertTrue(data.masters().passed(GUY), "and the test is passed");
        helper.assertTrue(StyleSystem.available(data).contains(Noirm.id("strong_fist")), "which grants the Strong Fist below taijutsu 18");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(guy.getHealth() == guy.getMaxHealth() && guy.getMaxHealth() >= 80f,
                    "back to full strength after: " + guy.getHealth() + "/" + guy.getMaxHealth());

            // 3. Clean hits: six land, the sixth passes.
            helper.assertTrue(MasterSystem.startSpar(player, guy), "a third spar");
            for (int i = 0; i < 6; i++) {
                helper.assertTrue(guy.isSparring(), "still sparring before hit " + (i + 1));
                guy.invulnerableTime = 0;
                guy.hurt(guy.damageSources().playerAttack(player), 1f);
            }
            helper.assertTrue(guy.cleanHits() == 6 && !guy.isSparring(), "six clean hits end it, got " + guy.cleanHits());
            guy.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "noirm_master_lessons")
    public static void lessonsNeedTheirRequirements(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        var guy = MasterData.MIGHT_GUY;
        helper.assertFalse(MasterSystem.learn(player, guy, "gate_of_opening"), "not before the test");
        MasterSystem.passTest(player, guy);
        helper.assertFalse(MasterSystem.learn(player, guy, "gate_of_opening"), "not at taijutsu 10 (needs 15)");
        data.setTaijutsu(15);
        helper.assertTrue(MasterSystem.learn(player, guy, "gate_of_opening"), "the Gate of Opening at taijutsu 15");
        helper.assertTrue(data.jutsu().knows(GATE_1), "the jutsu is learned");
        helper.assertFalse(MasterSystem.learn(player, guy, "gate_of_opening"), "a lesson is learned once");
        data.setTaijutsu(20);
        helper.assertFalse(MasterSystem.learn(player, guy, "front_lotus"), "the Lotus needs practice with the gate first");
        var def = MasterData.get(guy);
        var why = MasterSystem.lessonBlocker(data, guy, def, def.lesson("front_lotus").orElseThrow());
        helper.assertTrue(why != null && why.getContents() instanceof TranslatableContents t && t.getKey().equals("screen.noirm.master.why.practice"),
                "and says so: " + why);
        data.jutsu().addXp(GATE_1, 10); // ★1
        helper.assertTrue(MasterSystem.learn(player, guy, "front_lotus"), "practised: the Front Lotus");
        helper.assertTrue(data.jutsu().knows(LOTUS), "known");
        helper.assertFalse(MasterSystem.learn(player, guy, "morning_peacock"), "the Peacock needs the Gate of Limit lesson first");
        helper.assertFalse(MasterSystem.learn(player, guy, "gate_of_limit"), "the Gate of Limit needs taijutsu 30");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_master_gates", timeoutTicks = 120)
    public static void gatesDrainHealthAndCloseAtLowHealth(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(TestClans.PLAIN);
        ninja.sealBeast(0);
        ninja.setInvulnerable(false);
        var lotus = JutsuData.get(LOTUS);
        helper.assertTrue(JutsuCaster.checkRequirements(ninja, LOTUS, lotus) == JutsuCaster.Result.NEEDS_GATE, "the Lotus needs a gate open");
        helper.assertTrue(JutsuCaster.npcCast(ninja, GATE_1, 1f), "open the Gate of Opening");
        helper.assertTrue(ModeSystem.gateTier(ninja) == 1, "gate tier 1 is open");
        helper.assertTrue(ModeSystem.damageMultiplier(ninja) > 1.4f && ModeSystem.actionSpeed(ninja) > 1.2f, "harder, faster taijutsu");
        helper.assertTrue(JutsuCaster.checkRequirements(ninja, LOTUS, lotus) == JutsuCaster.Result.OK, "now the Lotus can be used");
        helper.assertTrue(JutsuCaster.checkRequirements(ninja, PEACOCK, JutsuData.get(PEACOCK)) == JutsuCaster.Result.NEEDS_GATE,
                "the Morning Peacock needs the second tier");
        float full = ninja.getHealth();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(ninja.getHealth() < full, "the gate burns health: " + ninja.getHealth() + " of " + full);
            ninja.setHealth(ninja.getMaxHealth() * 0.26f);
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(ModeSystem.gateTier(ninja) == 0, "the gate closed at low health");
                helper.assertTrue(ninja.isAlive() && ninja.getHealth() >= ninja.getMaxHealth() * 0.25f - 0.01f,
                        "before it could kill: " + ninja.getHealth());
                helper.assertTrue(JutsuCaster.checkRequirements(ninja, GATE_1, JutsuData.get(GATE_1)) == JutsuCaster.Result.TOO_HURT,
                        "and won't open again on a spent body");
                helper.assertTrue(JutsuData.get(GATE_2).mode().orElseThrow().gate() == 2, "the Gate of Limit is tier 2");
                clearAround(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "noirm_master_lotus", timeoutTicks = 100)
    public static void frontLotusLaunchesAndKnocksDown(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        caster.joinClan(TestClans.PLAIN);
        caster.sealBeast(0);
        caster.setCombatStyle(CombatData.DEFAULT_STYLE);
        SparringNinja target = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 2.0f);
        target.joinClan(TestClans.PLAIN);
        target.sealBeast(0);
        target.setCombatStyle(CombatData.DEFAULT_STYLE);
        face(caster, target);
        double startY = target.getY();
        float startHealth = target.getHealth();
        helper.assertTrue(JutsuCaster.npcCast(caster, GATE_1, 1f), "open the gate");
        helper.assertTrue(JutsuCaster.npcCast(caster, LOTUS, 1f), "the Front Lotus");
        boolean[] launched = {false};
        helper.onEachTick(() -> {
            if (target.getY() > startY + 0.8) {
                launched[0] = true;
            }
            if (launched[0] && target.onGround() && target.getHealth() < startHealth * 0.85f && CombatSystem.isStaggered(target)) {
                helper.assertTrue(target.isAlive(), "a sparring-weight Lotus doesn't kill a fresh ninja");
                ModeSystem.end(caster);
                clearAround(helper); // launched high, slammed down, knocked flat
            }
        });
    }

    // ------------------------------------------------------------ helpers

    private static MasterNinja master(GameTestHelper helper) {
        MasterNinja guy = helper.spawnWithNoFreeWill(NoirmEntities.MASTER.get(), 1.5f, 1f, 1.5f);
        guy.setMaster(MasterData.MIGHT_GUY);
        return guy;
    }

    /** A mock player standing in the test area (a spar ends if the student is far away). */
    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.moveTo(at.x, at.y, at.z);
        return player;
    }
}

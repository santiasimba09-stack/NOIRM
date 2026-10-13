package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatData;
import com.noirm.combat.CombatMove;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatStyle;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.Perception;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the combat system: data, routing, perception, hits, guard, parry, dodge, focus, AI.
 *
 * <p>Tests in one batch run side by side in the same world. A focus moment slows everything within
 * {@link CombatSystem#FOCUS_RADIUS} blocks, so tests that trigger one run in their own batch.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatGameTests {
    private static final float NINJA_HEALTH = 40f;
    private static final String FOCUS_BATCH = "noirm_focus";

    private CombatGameTests() {}

    // ------------------------------------------------------------ data and rules

    @GameTest(template = "empty")
    public static void movesAndStylesLoad(GameTestHelper helper) {
        CombatStyle basic = CombatData.style(CombatData.DEFAULT_STYLE);
        helper.assertTrue(basic != null, "The basic style should load from data/noirm/combat/styles/basic.json");
        helper.assertTrue(CombatData.moveCount() >= 6, "Expected the 6 basic moves, got " + CombatData.moveCount());
        for (AttackInput input : AttackInput.values()) {
            helper.assertTrue(CombatData.move(basic.opener(input)) != null, "Missing opener for " + input);
        }
        // Every style's openers and every move's routes must point at real moves.
        CombatData.styles().forEach((id, style) -> style.openers().forEach((input, move) ->
                helper.assertTrue(CombatData.move(move) != null, id + " opens " + input + " with missing move " + move)));
        for (String style : new String[]{"brawler", "strong_fist", "gentle_fist"}) {
            helper.assertTrue(CombatData.style(Noirm.id(style)) != null, "Missing style " + style);
        }
        for (String path : new String[]{"jab", "hook", "spin_kick", "flip_kick", "heavy_kick", "uppercut"}) {
            CombatMove move = CombatData.move(Noirm.id("basic/" + path));
            helper.assertTrue(move != null, "Missing move basic/" + path);
            move.next().values().forEach(next -> helper.assertTrue(CombatData.move(next) != null, path + " routes to missing move " + next));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void comboRoutesFollowTheData(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        assertRoute(helper, player, null, AttackInput.LIGHT, "basic/jab");
        assertRoute(helper, player, "basic/jab", AttackInput.LIGHT, "basic/hook");
        assertRoute(helper, player, "basic/hook", AttackInput.LIGHT, "basic/spin_kick");
        assertRoute(helper, player, "basic/hook", AttackInput.HEAVY, "basic/flip_kick");
        assertRoute(helper, player, null, AttackInput.HEAVY, "basic/heavy_kick");
        assertRoute(helper, player, null, AttackInput.LAUNCHER, "basic/uppercut");
        // A finisher has no follow-up: the next press starts a fresh combo.
        assertRoute(helper, player, "basic/spin_kick", AttackInput.LIGHT, "basic/jab");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void perceptionIsProportional(GameTestHelper helper) {
        Player strong = helper.makeMockPlayer();
        Player normal = helper.makeMockPlayer();
        ShinobiCapability.get(strong).ifPresent(d -> {
            d.setSpeed(12);
            d.setTaijutsu(12);
        });
        assertNear(helper, 1.2f, Perception.slowFactor(strong, normal), "20% stronger should see attacks 20% slower");
        assertNear(helper, 1f, Perception.slowFactor(normal, strong), "weaker fighters get no slowdown (and no speed-up)");
        ShinobiCapability.get(strong).ifPresent(d -> {
            d.setSpeed(100);
            d.setTaijutsu(100);
        });
        assertNear(helper, Perception.MAX_SLOW_FACTOR, Perception.slowFactor(strong, normal), "the slowdown is capped");
        helper.succeed();
    }

    // ------------------------------------------------------------ fights between two ninjas (AI off)

    @GameTest(template = "empty")
    public static void lightAttackLands(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.0f);
        face(attacker, target);
        CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < NINJA_HEALTH, "jab hasn't landed yet");
            assertNear(helper, NINJA_HEALTH - 3f, target.getHealth(), "a jab at taijutsu 10 deals 3 damage");
        });
    }

    @GameTest(template = "empty")
    public static void jabKeepsTargetInComboRange(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.0f);
        face(attacker, target);
        net.minecraft.world.phys.Vec3 start = target.position();
        CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(target.getHealth() < NINJA_HEALTH, "the jab should have landed");
            double pushed = target.position().distanceTo(start);
            helper.assertTrue(pushed < 0.7, "a jab should barely push (vanilla knockback must not stack), pushed " + pushed);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void earlyPressIsBufferedIntoTheCombo(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.0f);
        face(attacker, target);
        helper.assertTrue(CombatSystem.requestAttack(attacker, AttackInput.LIGHT), "first jab should start immediately");
        helper.assertFalse(CombatSystem.requestAttack(attacker, AttackInput.LIGHT), "second press should be buffered, not started");
        CombatState state = CombatStates.get(attacker);
        helper.succeedWhen(() -> helper.assertTrue(Noirm.id("basic/hook").equals(state.moveId()),
                "buffered press should become the hook, current move: " + state.moveId()));
    }

    @GameTest(template = "empty")
    public static void guardCutsDamageAndBuildsPosture(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.0f);
        face(attacker, target);
        face(target, attacker);
        CombatSystem.setGuard(target, true);
        // Wait past the parry window so this is a normal block.
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 4, () -> CombatSystem.requestAttack(attacker, AttackInput.LIGHT));
        CombatState state = CombatStates.get(target);
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < NINJA_HEALTH, "blocked jab hasn't landed yet");
            assertNear(helper, NINJA_HEALTH - 3f * CombatSystem.GUARD_DAMAGE_MULTIPLIER, target.getHealth(), "guard keeps 25% of the damage");
            helper.assertTrue(state.posture() > 0, "a blocked hit should build posture");
            helper.assertTrue(state.isGuarding(), "one jab shouldn't break guard");
        });
    }

    @GameTest(template = "empty", batch = FOCUS_BATCH)
    public static void lastSecondGuardParries(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.0f);
        face(attacker, target);
        face(target, attacker);
        CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        CombatSystem.setGuard(target, true); // raised just as the 3-tick wind-up starts
        CombatState attackerState = CombatStates.get(attacker);
        helper.succeedWhen(() -> {
            helper.assertTrue(attackerState.phase() == CombatPhase.STAGGER, "a parry should stagger the attacker, phase: " + attackerState.phase());
            assertNear(helper, NINJA_HEALTH, target.getHealth(), "a parried hit deals no damage");
        });
    }

    @GameTest(template = "empty")
    public static void dodgeIsInvulnerable(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja dodger = ninja(helper, 1.5f, 2.0f);
        helper.assertTrue(CombatSystem.requestDodge(dodger, -1f, 0f), "an idle fighter can dodge");
        CombatState state = CombatStates.get(dodger);
        helper.assertTrue(CombatSystem.classify(dodger, state, attacker) == CombatSystem.Outcome.DODGED,
                "attacks during a dodge's first ticks should miss");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_posture")
    public static void blockedHitsBreakPosture(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.0f);
        face(target, attacker);
        CombatSystem.setGuard(target, true);
        CombatState state = CombatStates.get(target);
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 4, () -> {
            // Each blocked 10-damage hit costs 30 posture: the 4th breaks the guard.
            for (int i = 0; i < 4; i++) {
                target.invulnerableTime = 0;
                target.hurt(target.damageSources().mobAttack(attacker), 10f);
            }
            helper.assertTrue(state.phase() == CombatPhase.STAGGER, "a full posture bar should stagger, phase: " + state.phase());
            helper.assertFalse(state.isGuarding(), "a posture break drops the guard");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = FOCUS_BATCH + "_2")
    public static void focusSlowsEveryoneNearby(GameTestHelper helper) {
        SparringNinja center = ninja(helper, 1.5f, 0.5f);
        SparringNinja other = ninja(helper, 1.5f, 2.0f);
        CombatSystem.focus(center);
        assertNear(helper, CombatSystem.FOCUS_TIME_SCALE, CombatStates.get(other).timeScale(), "nearby fighters act slower");
        helper.assertTrue(other.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "nearby fighters move slower");
        helper.assertTrue(CombatStates.getIfPresent(center) == null || CombatStates.get(center).timeScale() == 1f, "the focuser is not slowed");
        helper.succeed();
    }

    // ------------------------------------------------------------ AI

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ninjaFightsItsTarget(GameTestHelper helper) {
        floor(helper);
        SparringNinja attacker = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        attacker.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        SparringNinja target = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 2.0f);
        target.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        attacker.setTarget(target);
        helper.succeedWhen(() -> helper.assertTrue(target.getHealth() < NINJA_HEALTH, "the ninja should land a hit on its target"));
    }

    // ------------------------------------------------------------ helpers

    /** A ninja with AI off and a plain clan, so no random Sharingan changes the timings. */
    private static SparringNinja ninja(GameTestHelper helper, float x, float z) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        ninja.joinClan(TestClans.PLAIN);
        return ninja;
    }

    /** Turns {@code entity} to face {@code other}. Mobs with AI off keep this rotation. */

    private static void assertRoute(GameTestHelper helper, Player player, String from, AttackInput input, String expected) {
        ResourceLocation result = CombatSystem.resolveMove(player, from == null ? null : Noirm.id(from), input);
        helper.assertTrue(Noirm.id(expected).equals(result), from + " + " + input + " should be " + expected + ", got " + result);
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }
}

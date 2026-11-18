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
import com.noirm.combat.StyleBook;
import com.noirm.combat.StyleItems;
import com.noirm.combat.StyleLearning;
import com.noirm.combat.StyleSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.master.MasterData;
import com.noirm.master.MasterSystem;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The styles wave (docs/COMBAT_DESIGN.md "Fighting styles"): ten new unarmed styles and four weapon
 * styles, each with a full kit; their mechanical hooks (feint, parry window and counter stance, posture
 * armour and armoured strikes, stamina drain, backstab, aerial, evasive sway, style dodges); learning
 * them (scroll, master, watching); NPCs rolling and using them. (Every move's strike pose landing on its
 * hit tick is checked for all styles by CombatRebuildGameTests.strikePosesLandOnTheHitTick.)
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StyleWaveGameTests {
    static final List<String> UNARMED = List.of("drunken_fist", "iron_body", "viper", "crane", "mountain", "wind_step", "shadow",
            "leaf_hurricane", "sumo", "monkey");
    static final List<String> WEAPON = List.of("blade_iai", "blade_dance", "staff_wall", "spear_dance");
    private static final float HP = 40f;

    private StyleWaveGameTests() {}

    // ------------------------------------------------------------ data

    /** Every new style loads with a full kit: all inputs, a counter, stances, a 4+ hit light string to an ender, a guard break. */
    @GameTest(template = "empty")
    public static void newStylesHaveFullKits(GameTestHelper helper) {
        java.util.Map<String, com.noirm.client.anim.KeyframeAnimation> anims = AnimationGameTests.loadAll(helper);
        for (String key : concat(UNARMED, WEAPON)) {
            ResourceLocation id = Noirm.id(key);
            CombatStyle style = CombatData.style(id);
            helper.assertTrue(style != null, key + " loads");
            helper.assertTrue(style.playable() && style.learn().required(), key + " is learned in play");
            helper.assertTrue(WEAPON.contains(key) == style.isWeaponStyle(), key + " weapon style or not");
            for (AttackInput input : List.of(AttackInput.LIGHT, AttackInput.HEAVY, AttackInput.LAUNCHER, AttackInput.FORWARD_LIGHT,
                    AttackInput.FORWARD_HEAVY, AttackInput.BACK_HEAVY)) {
                helper.assertTrue(style.opener(input) != null && CombatData.move(style.opener(input)) != null, key + " answers " + input);
            }
            helper.assertTrue(CombatData.move(style.counterMove()) != null && style.counterMove().getPath().startsWith(key + "/"),
                    key + " has its own counter");
            helper.assertTrue(style.stances().size() >= 3, key + " has sub-stances: " + style.stances().keySet());
            style.stances().values().forEach(s -> helper.assertTrue(anims.containsKey(s.animation().getPath()), key + " stance " + s.animation()));
            // The light route: at least 4 hits, ending in an ender.
            ResourceLocation at = style.opener(AttackInput.LIGHT);
            int hits = 1;
            Set<ResourceLocation> seen = new HashSet<>();
            while (seen.add(at) && CombatData.move(at).next().containsKey(AttackInput.LIGHT)
                    && CombatData.move(at).hit().ender() == CombatMove.Hit.Ender.NONE) {
                at = CombatData.move(at).next().get(AttackInput.LIGHT);
                hits++;
            }
            helper.assertTrue(hits >= 4, key + "'s light string is " + hits + " hits");
            helper.assertTrue(CombatData.move(at).hit().ender() != CombatMove.Hit.Ender.NONE || CombatData.move(at).hit().clinch() != CombatMove.Hit.Clinch.NONE,
                    key + "'s string ends in an ender (or a grab into throws): " + at);
            helper.assertTrue(reachable(style).stream().anyMatch(m -> CombatData.move(m).hit().guardBreak()), key + " has a guard break");
        }
        helper.assertTrue(CombatData.style(Noirm.id("wind_step")).identity().guard().evasive(), "Wind Step never blocks");
        helper.assertTrue(CombatData.style(Noirm.id("mountain")).identity().guard().counter(), "Mountain is a counter stance");
        helper.assertTrue(CombatData.style(Noirm.id("shadow")).identity().feint().isPresent(), "Shadow feints");
        helper.assertTrue(anims.containsKey("feint_pull") && anims.containsKey("dodge_hop_back") && anims.containsKey("dodge_sway_left"),
                "the feint and style dodges are animated");
        helper.succeed();
    }

    private static Set<ResourceLocation> reachable(CombatStyle style) {
        Set<ResourceLocation> out = new HashSet<>();
        ArrayDeque<ResourceLocation> todo = new ArrayDeque<>(style.openers().values());
        todo.add(style.counterMove());
        while (!todo.isEmpty()) {
            ResourceLocation id = todo.poll();
            CombatMove move = CombatData.move(id);
            if (move != null && out.add(id)) {
                todo.addAll(move.next().values());
                todo.addAll(move.variants().values());
            }
        }
        return out;
    }

    // ------------------------------------------------------------ hooks

    /** Shadow: guard during the wind-up cancels it (a feint); the real blow comes in a tick and pierces the guard. */
    @GameTest(template = "empty", batch = "noirm_style_feint", timeoutTicks = 80)
    public static void feintCancelsAndPierces(GameTestHelper helper) {
        SparringNinja shadow = ninja(helper, "shadow", 1.5f, 0.5f);
        SparringNinja target = ninja(helper, "basic", 1.5f, 1.9f);
        target.setInvulnerable(false);
        face(shadow, target);
        face(target, shadow);
        CombatSystem.setGuard(target, true);
        CombatState state = CombatStates.get(shadow);
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 3, () -> {
            face(shadow, target);
            helper.assertTrue(CombatSystem.requestAttack(shadow, AttackInput.HEAVY), "the hidden palm winds up");
            helper.assertTrue(state.phase() == CombatPhase.WINDUP, "winding up");
            CombatSystem.setGuard(shadow, true); // guard pressed mid wind-up: a feint
            helper.assertTrue(state.phase() == CombatPhase.IDLE && state.feints() == 1, "the feint cancels it: " + state.phase());
            helper.assertTrue(state.feintTicks() > 0, "and opens the follow-up window");
            CombatSystem.setGuard(shadow, false);
            helper.assertTrue(CombatSystem.requestAttack(shadow, AttackInput.LIGHT), "the real blow");
            helper.assertTrue(state.isFeintFollowUp() && state.phaseLength() <= 1f, "comes out in a tick: " + state.phaseLength());
        });
        float jab = CombatData.move(Noirm.id("shadow/shade_jab")).hit().damage();
        helper.succeedWhen(() -> {
            helper.assertTrue(CombatStates.get(target).isGuarding(), "the target kept guarding");
            helper.assertTrue(target.getHealth() <= HP - jab * 0.55f, "it slipped through the guard: health " + target.getHealth());
            clearAround(helper);
        });
    }

    /** Parry windows follow the style; Mountain's counter stance answers a parry at once with its riposte. */
    @GameTest(template = "empty", batch = "noirm_style_parry", timeoutTicks = 80)
    public static void parryWindowAndCounterStance(GameTestHelper helper) {
        SparringNinja mountain = ninja(helper, "mountain", 1.5f, 0.5f);
        SparringNinja attacker = ninja(helper, "basic", 1.5f, 1.9f);
        SparringNinja plain = ninja(helper, "basic", 0.5f, 0.5f);
        helper.assertTrue(CombatSystem.parryWindow(mountain) == Math.round(CombatSystem.PARRY_WINDOW * 2.2f), "Mountain: a wide parry window");
        helper.assertTrue(CombatSystem.parryWindow(plain) == CombatSystem.PARRY_WINDOW, "the Academy: the normal one");
        helper.assertTrue(CombatSystem.parryWindow(ninja(helper, "drunken_fist", 0.5f, 1.5f)) == 7, "the Drunken Fist: a little wider");
        face(mountain, attacker);
        face(attacker, mountain);
        CombatSystem.setGuard(mountain, true);
        CombatSystem.setGuard(plain, true);
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 3, () -> {
            helper.assertTrue(CombatSystem.classify(plain, CombatStates.get(plain), attacker) == CombatSystem.Outcome.GUARDED, "late: a plain block");
            helper.assertTrue(CombatSystem.classify(mountain, CombatStates.get(mountain), attacker) == CombatSystem.Outcome.PARRIED,
                    "late for others, still a parry for Mountain");
            face(attacker, mountain);
            CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        });
        helper.succeedWhen(() -> {
            CombatState state = CombatStates.get(mountain);
            helper.assertTrue(Noirm.id("mountain/riposte").equals(state.moveId()), "the parry answers with the riposte, got " + state.moveId());
            helper.assertTrue(CombatStates.get(attacker).phase() == CombatPhase.STAGGER, "the attacker reels");
            clearAround(helper);
        });
    }

    /** Iron Body: posture armour shrugs off a third of posture damage; an armoured strike doesn't flinch and halves the hit. */
    @GameTest(template = "empty", batch = "noirm_style_armour", timeoutTicks = 80)
    public static void postureArmourAndArmouredStrike(GameTestHelper helper) {
        SparringNinja iron = ninja(helper, "iron_body", 1.5f, 0.5f);
        SparringNinja plain = ninja(helper, "basic", 0.5f, 0.5f);
        CombatSystem.addPostureDamage(iron, 30f);
        CombatSystem.addPostureDamage(plain, 30f);
        near(helper, 30f * 0.65f, CombatStates.get(iron).posture(), "iron shrugs off 35% of posture");
        near(helper, 30f, CombatStates.get(plain).posture(), "the Academy takes it all");
        CombatStates.get(iron).recoverPosture(100f);
        SparringNinja attacker = ninja(helper, "basic", 1.5f, 1.9f);
        iron.setInvulnerable(false);
        face(iron, attacker);
        face(attacker, iron);
        helper.assertTrue(CombatSystem.requestAttack(iron, AttackInput.HEAVY), "the iron palm winds up (armour 0.5)");
        helper.assertTrue(CombatSystem.requestAttack(attacker, AttackInput.LIGHT), "a jab comes in during it");
        float jab = CombatData.move(Noirm.id("basic/jab")).hit().damage();
        boolean[] checked = {false};
        helper.onEachTick(() -> {
            if (!checked[0] && iron.getHealth() < HP) {
                checked[0] = true;
                near(helper, HP - jab * 0.5f, iron.getHealth(), "the armoured strike takes half");
                helper.assertTrue(CombatStates.get(iron).phase() != CombatPhase.STAGGER, "and doesn't flinch");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(checked[0], "the jab landed");
            clearAround(helper);
        });
    }

    /** Viper: every hit knocks stamina out of a player (an NPC loses posture instead). */
    @GameTest(template = "empty", batch = "noirm_style_drain", timeoutTicks = 60)
    public static void viperDrainsStamina(GameTestHelper helper) {
        // On a player: the drain comes straight out of their stamina.
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        float before = data.getStamina();
        CombatSystem.drainStamina(player, CombatStates.get(player), 8f);
        near(helper, before - 8f, data.getStamina(), "a player loses the stamina");
        // A real fang jab on a ninja (who has no stamina): the drain becomes posture.
        SparringNinja viper = ninja(helper, "viper", 1.5f, 0.5f);
        SparringNinja target = ninja(helper, "basic", 1.5f, 1.9f);
        target.setInvulnerable(false);
        face(viper, target);
        helper.assertTrue(CombatSystem.requestAttack(viper, AttackInput.LIGHT), "a fang jab");
        CombatMove jab = CombatData.move(Noirm.id("viper/fang_jab"));
        float drain = CombatData.style(Noirm.id("viper")).identity().strike().staminaDrain() + jab.effects().staminaDrain();
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < HP, "it lands");
            near(helper, jab.hit().posture() * CombatSystem.UNGUARDED_POSTURE_RATIO + drain * CombatSystem.DRAIN_POSTURE_PER_STAMINA,
                    CombatStates.get(target).posture(), "the drain (" + drain + ") rocks the ninja's posture");
            clearAround(helper);
        });
    }

    /** Shadow: a hit from behind does +60%. */
    @GameTest(template = "empty", batch = "noirm_style_backstab", timeoutTicks = 60)
    public static void backstabBonus(GameTestHelper helper) {
        SparringNinja shadow = ninja(helper, "shadow", 1.5f, 0.5f);
        SparringNinja target = ninja(helper, "basic", 1.5f, 1.9f);
        target.setInvulnerable(false);
        face(shadow, target);
        face(target, shadow);
        float away = target.getYRot() + 180f;
        target.setYRot(away);
        target.setYHeadRot(away);
        target.setYBodyRot(away);
        helper.assertTrue(CombatSystem.isBehind(shadow, target), "the shadow stands behind");
        CombatSystem.requestAttack(shadow, AttackInput.LIGHT);
        float jab = CombatData.move(Noirm.id("shadow/shade_jab")).hit().damage();
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < HP, "it lands");
            near(helper, HP - jab * 1.6f, target.getHealth(), "x1.6 from behind");
            clearAround(helper);
        });
    }

    /** Crane: a kick thrown from the air does +35%. */
    @GameTest(template = "empty", batch = "noirm_style_aerial", timeoutTicks = 60)
    public static void aerialBonus(GameTestHelper helper) {
        SparringNinja crane = ninja(helper, "crane", 1.5f, 0.5f);
        SparringNinja target = ninja(helper, "basic", 1.5f, 2.1f);
        target.setInvulnerable(false);
        crane.setNoGravity(true);
        crane.teleportTo(crane.getX(), crane.getY() + 0.8, crane.getZ());
        crane.setOnGround(false);
        face(crane, target);
        CombatSystem.requestAttack(crane, AttackInput.LIGHT);
        float kick = CombatData.move(Noirm.id("crane/snap_kick")).hit().damage();
        helper.onEachTick(() -> crane.setOnGround(false));
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < HP, "it lands");
            near(helper, HP - kick * 1.35f, target.getHealth(), "x1.35 from the air");
            clearAround(helper);
        });
    }

    /** Wind Step's guard is a sway (a short dodge, no block); each style dodges its own way. */
    @GameTest(template = "empty", batch = "noirm_style_dodge", timeoutTicks = 40)
    public static void evasiveGuardAndStyleDodges(GameTestHelper helper) {
        SparringNinja wind = ninja(helper, "wind_step", 1.5f, 1.5f);
        CombatSystem.setGuard(wind, true);
        CombatState state = CombatStates.get(wind);
        helper.assertTrue(!state.isGuarding() && state.phase() == CombatPhase.DODGE && state.sways() == 1, "a sway, not a block");
        helper.assertTrue(wind.animationId().startsWith("noirm:dodge_sway_"), "it sways: " + wind.animationId());
        SparringNinja crane = ninja(helper, "crane", 0.5f, 0.5f);
        helper.assertTrue(CombatSystem.requestDodge(crane, -1f, 0f), "the crane dodges");
        CombatState cs = CombatStates.get(crane);
        helper.assertTrue(cs.phaseLength() == 11 && cs.dodgeIframes() == 7, "its own timing: " + cs.phaseLength() + "/" + cs.dodgeIframes());
        helper.assertTrue("noirm:dodge_hop_back".equals(crane.animationId()), "a hop back: " + crane.animationId());
        helper.runAfterDelay(20, () -> clearAround(helper));
        helper.runAfterDelay(21, helper::succeed);
    }

    // ------------------------------------------------------------ learning

    /** A style scroll teaches its style (or one you don't know); the style is then usable and survives a save. */
    @GameTest(template = "empty")
    public static void scrollTeachesAStyle(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ResourceLocation viper = Noirm.id("viper");
        helper.assertFalse(StyleSystem.available(data).contains(viper), "Viper must be learned first");
        helper.assertTrue(StyleItems.StyleScrollItem.read(player, StyleItems.named(viper)), "the scroll is read");
        helper.assertTrue(StyleSystem.knows(data, viper) && StyleSystem.available(data).contains(viper), "Viper is learned and usable");
        helper.assertTrue(StyleSystem.SCROLL.equals(data.styles().howLearned(viper.toString())), "from a scroll");
        helper.assertFalse(StyleItems.StyleScrollItem.read(player, StyleItems.named(viper)), "nothing new the second time");
        helper.assertTrue(StyleItems.StyleScrollItem.read(player, new ItemStack(StyleItems.STYLE_SCROLL.get())), "a blank scroll teaches some style");
        helper.assertTrue(data.styles().known().size() == 2, "two styles now: " + data.styles().known());
        StyleBook copy = new StyleBook();
        copy.load(data.styles().save());
        helper.assertTrue(copy.knows(viper.toString()) && copy.known().size() == 2, "saved and loaded");
        helper.assertTrue(StyleSystem.choose(player, viper), "chosen");
        helper.assertTrue(viper.equals(StyleSystem.styleOf(data)), "it's the player's style");
        helper.succeed();
    }

    /** A master's style lesson teaches a style (Might Guy: Iron Body, then the Leaf Hurricane). */
    @GameTest(template = "empty")
    public static void masterTeachesAStyle(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        data.setTaijutsu(23f);
        helper.assertFalse(MasterSystem.learn(player, MasterData.MIGHT_GUY, "iron_body_style"), "not before his test");
        MasterSystem.passTest(player, MasterData.MIGHT_GUY);
        helper.assertTrue(MasterSystem.learn(player, MasterData.MIGHT_GUY, "iron_body_style"), "the Iron Body lesson");
        helper.assertTrue(StyleSystem.knows(data, Noirm.id("iron_body")), "Iron Body learned");
        helper.assertTrue(MasterSystem.learn(player, MasterData.MIGHT_GUY, "leaf_hurricane_style"), "then the Leaf Hurricane");
        helper.assertTrue(StyleSystem.MASTER.equals(data.styles().howLearned("noirm:leaf_hurricane")), "taught by a master");
        helper.assertTrue(StyleSystem.available(data).contains(Noirm.id("leaf_hurricane")), "and usable");
        helper.succeed();
    }

    /** Watching (or sparring) a ninja who fights in a style can teach it, with a small chance. */
    @GameTest(template = "empty")
    public static void watchingCanTeach(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ResourceLocation sumo = Noirm.id("sumo");
        float chance = CombatData.style(sumo).learn().watch();
        helper.assertFalse(StyleLearning.witness(player, sumo, false, chance + 0.001f), "an unlucky roll teaches nothing");
        helper.assertTrue(StyleLearning.witness(player, sumo, true, chance * 1.5f), "sparring doubles the chance");
        helper.assertTrue(StyleSystem.WATCH.equals(data.styles().howLearned(sumo.toString())), "picked up by watching");
        helper.assertFalse(StyleLearning.witness(player, Noirm.id("leaf_hurricane"), true, 0f), "the Leaf Hurricane can't be copied by eye");
        helper.assertTrue(StyleSystem.grant(player, Noirm.id("monkey")), "the clan hook grants a style");
        helper.assertTrue(StyleSystem.CLAN.equals(data.styles().howLearned("noirm:monkey")), "as the clan's");
        helper.succeed();
    }

    /** A weapon style replaces the weapon's own moves once picked (Iai with a katana). */
    @GameTest(template = "empty")
    public static void weaponStyleReplacesTheMoveset(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(com.noirm.weapon.NinjaBlades.KATANA.get()));
        helper.assertTrue(Noirm.id("weapon_katana").equals(CombatSystem.styleFor(player)), "the katana's own moves first");
        helper.assertFalse(StyleSystem.choose(player, Noirm.id("blade_iai")), "Iai must be learned");
        StyleSystem.grant(player, Noirm.id("blade_iai"), StyleSystem.SCROLL);
        helper.assertTrue(StyleSystem.choose(player, Noirm.id("blade_iai")), "chosen");
        helper.assertTrue(Noirm.id("blade_iai").equals(CombatSystem.styleFor(player)), "the katana now fights in Iai");
        helper.assertTrue(Noirm.id("blade_iai/draw_cut").equals(CombatSystem.resolveMove(player, null, AttackInput.LIGHT)), "a draw cut");
        StyleSystem.clearWeaponPick(player, "katana");
        helper.assertTrue(Noirm.id("weapon_katana").equals(CombatSystem.styleFor(player)), "back to its own moves");
        helper.succeed();
    }

    // ------------------------------------------------------------ ninjas

    /** Ninjas roll styles by clan and class: an Akimichi leans to sumo; Mountain needs a class B ninja. */
    @GameTest(template = "empty")
    public static void npcStylesByClanAndClass(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7);
        int plain = 0, akimichi = 0, mountainD = 0;
        for (int i = 0; i < 600; i++) {
            if (Noirm.id("sumo").equals(StyleSystem.pickForNpc(random, 30f, false, "", com.noirm.bounty.NinjaClass.C))) plain++;
            if (Noirm.id("sumo").equals(StyleSystem.pickForNpc(random, 30f, false, "akimichi", com.noirm.bounty.NinjaClass.C))) akimichi++;
            if (Noirm.id("mountain").equals(StyleSystem.pickForNpc(random, 30f, false, "", com.noirm.bounty.NinjaClass.D))) mountainD++;
        }
        helper.assertTrue(akimichi > plain * 2, "Akimichi pick sumo far more often: " + akimichi + " vs " + plain);
        helper.assertTrue(mountainD == 0, "a class D ninja never fights Mountain: " + mountainD);
        helper.assertTrue(StyleSystem.npcWeight(CombatData.style(Noirm.id("mountain")), "", com.noirm.bounty.NinjaClass.B) > 0, "a class B one may");
        helper.succeed();
    }

    /** A ninja fighting in a new style (sumo, on its own AI) throws that style's moves. */
    @GameTest(template = "empty", batch = "noirm_style_npc", timeoutTicks = 240)
    public static void npcFightsInANewStyle(GameTestHelper helper) {
        floor(helper);
        SparringNinja target = ninja(helper, "basic", 1.5f, 2.2f);
        SparringNinja sumo = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), new Vec3(1.5, 1, 0.6));
        sumo.setCombatStyle(Noirm.id("sumo"));
        sumo.joinClan(TestClans.PLAIN);
        sumo.setTarget(target);
        Set<String> seen = new HashSet<>();
        helper.onEachTick(() -> {
            ResourceLocation move = CombatStates.get(sumo).moveId();
            if (move != null) {
                seen.add(move.getPath());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen.stream().anyMatch(m -> m.startsWith("sumo/")), "it fights with sumo moves: " + seen);
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja ninja(GameTestHelper helper, String style, float x, float z) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(Noirm.id(style));
        ninja.joinClan(TestClans.PLAIN);
        return ninja;
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.05f, what + ": expected " + expected + ", got " + actual);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}

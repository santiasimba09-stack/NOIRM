package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.combat.CombatStates;
import com.noirm.entity.SparringNinja;
import com.noirm.genjutsu.GenjutsuMinds;
import com.noirm.genjutsu.GenjutsuScene;
import com.noirm.genjutsu.GenjutsuScenes;
import com.noirm.hiraishin.HiraishinItems;
import com.noirm.hiraishin.HiraishinJump;
import com.noirm.hiraishin.HiraishinLearning;
import com.noirm.hiraishin.HiraishinMark;
import com.noirm.hiraishin.HiraishinMarking;
import com.noirm.hiraishin.HiraishinMarks;
import com.noirm.hiraishin.HiraishinNpc;
import com.noirm.jutsu.BodyFlicker;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuSeals;
import com.noirm.jutsu.SpiritTechniques;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.weapon.Throwing;
import com.noirm.weapon.ThrownWeapon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Genjutsu scenes, the Body Flicker and the Flying Raijin (docs/GENJUTSU.md, docs/FLYING_RAIJIN.md).
 * Every test that moves or throws something has its own batch and cleans up.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceTimeGameTests {
    private static final List<String> SCENES = List.of("demonic_illusion", "false_surroundings", "tree_binding_death",
            "sharingan_crows", "tsukuyomi", "kotoamatsukami");

    private SpaceTimeGameTests() {}

    // ================================================================ genjutsu scenes

    @GameTest(template = "empty")
    public static void genjutsuSceneDefinitionsLoad(GameTestHelper helper) {
        for (String name : SCENES) {
            ResourceLocation id = Noirm.id(name);
            GenjutsuScene scene = GenjutsuScenes.get(id);
            helper.assertTrue(scene != null, "scene " + name + " loads");
            helper.assertFalse(scene.jutsu().isEmpty(), name + " names its jutsu");
            for (ResourceLocation jutsu : scene.jutsu()) {
                helper.assertTrue(JutsuData.get(jutsu) != null, name + " opens for a real jutsu: " + jutsu);
                helper.assertTrue(id.equals(GenjutsuScenes.sceneFor(jutsu)), jutsu + " opens " + name);
            }
            // It travels to the victim's client as JSON and comes back the same.
            GenjutsuScene back = GenjutsuScenes.fromJson(GenjutsuScenes.toJson(scene)).orElse(null);
            helper.assertTrue(scene.equals(back), name + " survives the trip to the client");
        }
        helper.assertTrue(GenjutsuScenes.get(Noirm.id("tsukuyomi")).timeScale() < 1f, "the Tsukuyomi slows time");
        helper.assertTrue(GenjutsuScenes.get(Noirm.id("kotoamatsukami")).swapAllies(), "Kotoamatsukami turns friends to foes");
        helper.assertTrue(GenjutsuScenes.get(Noirm.id("false_surroundings")).hideCaster(), "False Surroundings hides the real caster");
        // The space-time jutsu are learnable data with seals and names.
        List<JsonObject> langs = List.of(lang("en_us"), lang("es_es"), lang("es_mx"));
        for (ResourceLocation id : List.of(HiraishinLearning.FORMULA, HiraishinLearning.RAIJIN)) {
            JutsuDefinition def = JutsuData.get(id);
            helper.assertTrue(def != null && JutsuBehaviors.exists(def.behavior()), id + " loads");
            helper.assertTrue(JutsuSeals.get(id) != null, id + " has seals");
            for (JsonObject lang : langs) {
                helper.assertTrue(lang.has("jutsu.noirm." + id.getPath().replace('/', '.')), "no name for " + id);
                helper.assertTrue(lang.has("screen.noirm.jutsu.behavior." + def.behavior()), "no behavior text for " + def.behavior());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_st_mob_scene", timeoutTicks = 140)
    public static void catchingAMobChangesWhatItDoes(GameTestHelper helper) {
        openFloor(helper, -4, -4, 6, 6, 4);
        clearStrays(helper);
        SparringNinja caster = ninja(helper, 1.5f, -3f, TestClans.PLAIN);
        Zombie lost = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 1f, 2.5f);
        lost.setNoAi(false);
        Zombie fighter = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4.5f, 1f, 2.5f);
        fighter.setNoAi(false);
        lost.setTarget(caster);
        fighter.setTarget(caster);
        Vec3 start = lost.position();
        helper.assertTrue(GenjutsuMinds.enterScene(caster, lost, Noirm.id("sharingan_crows"), 120, null), "the crow scene takes the zombie");
        helper.assertTrue(GenjutsuMinds.enterScene(caster, fighter, Noirm.id("false_surroundings"), 120,
                fighter.position().add(2, 0, 0)), "the false image takes the other");
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(GenjutsuMinds.reactionOf(lost) == GenjutsuScene.Reaction.WANDER, "lost among the crows");
            helper.assertTrue(lost.getTarget() == null, "it forgot the caster");
            helper.assertTrue(lost.position().distanceTo(start) > 0.6, "it wandered off (" + lost.position().distanceTo(start) + ")");
            helper.assertTrue(GenjutsuMinds.reactionOf(fighter) == GenjutsuScene.Reaction.ATTACK_PHANTOMS, "fighting what isn't there");
            helper.assertTrue(GenjutsuMinds.phantomOf(fighter) != null, "it has a phantom to swing at");
            helper.assertTrue(fighter.getTarget() != caster, "not the real caster");
            GenjutsuMinds.release(lost, false);
            GenjutsuMinds.release(fighter, false);
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_st_kai_scene")
    public static void kaiShattersAGenjutsuScene(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 0.5f, 0.5f, TestClans.PLAIN);
        SparringNinja victim = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        helper.assertTrue(GenjutsuMinds.enterScene(caster, victim, Noirm.id("tsukuyomi"), 200, null), "caught");
        helper.assertTrue(SpiritTechniques.underGenjutsu(victim), "a scene counts as a genjutsu (Kai can break it)");
        run(victim, "universal/genjutsu_release");
        helper.assertFalse(GenjutsuMinds.inScene(victim), "Kai broke the scene");
        // A friend's blow wakes you too.
        helper.assertTrue(GenjutsuMinds.enterScene(caster, victim, Noirm.id("demonic_illusion"), 200, null), "caught again");
        SpiritTechniques.releaseFrom(victim);
        helper.assertFalse(GenjutsuMinds.inScene(victim), "and released again");
        clearAround(helper);
    }

    // ================================================================ Body Flicker

    @GameTest(template = "empty", batch = "noirm_st_flicker_range")
    public static void bodyFlickerReachesItsRange(GameTestHelper helper) {
        openFloor(helper, 0, 0, 2, 14, 4);
        clearStrays(helper, 18);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        caster.setTarget(null);
        lookAlongZ(caster);
        Vec3 from = caster.position();
        BodyFlicker.Result result = BodyFlicker.flicker(caster, null, BodyFlicker.Mode.POINT, 8);
        double moved = caster.position().distanceTo(from);
        helper.assertTrue(Math.abs(moved - 8) < 0.6, "a full flicker in the open: 8 blocks, got " + moved);
        helper.assertFalse(result.blocked(), "nothing in the way");
        helper.assertTrue(caster.position().z > from.z + 7, "straight where it looked");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_st_flicker_wall")
    public static void bodyFlickerStopsAtAWall(GameTestHelper helper) {
        openFloor(helper, 0, 0, 2, 14, 4);
        clearStrays(helper, 18);
        for (int x = -1; x <= 3; x++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
            }
        }
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        caster.setTarget(null);
        lookAlongZ(caster);
        Vec3 wall = helper.absoluteVec(new Vec3(1.5, 1, 5));
        BodyFlicker.Result result = BodyFlicker.flicker(caster, null, BodyFlicker.Mode.POINT, 8);
        helper.assertTrue(caster.getZ() < wall.z - 0.25, "stopped before the wall, not inside it (z " + caster.getZ() + ", wall " + wall.z + ")");
        helper.assertTrue(caster.getZ() > caster.position().z - 10 && result.to().distanceTo(result.from()) > 2, "but went as far as it could");
        helper.assertTrue(helper.getLevel().noCollision(caster, caster.getBoundingBox()), "never inside a block");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_st_flicker_behind")
    public static void bodyFlickerLandsBehindTheTarget(GameTestHelper helper) {
        openFloor(helper, -2, 0, 4, 12, 4);
        clearStrays(helper, 18);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 7.5f, TestClans.FIRE);
        face(caster, target);
        face(target, caster);
        caster.setTarget(target); // an NPC's lock-on is its target: the real jutsu, as ninjas cast it
        run(caster, "universal/body_flicker");
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 fromTarget = caster.position().subtract(target.position());
        helper.assertTrue(fromTarget.dot(facing) < -0.8, "behind the target (" + fromTarget + ")");
        helper.assertTrue(fromTarget.horizontalDistance() < 2.2, "right at their back");
        helper.assertTrue(CombatStates.get(caster).dodgeCounterTicks() > 0, "the flicker-strike: a counter window is open");
        // Chaining is limited: the fourth in a row winds you.
        BodyFlicker.reset(caster);
        for (int i = 0; i < BodyFlicker.MAX_CHAIN; i++) {
            run(caster, "universal/body_flicker");
        }
        Vec3 before = caster.position();
        run(caster, "universal/body_flicker");
        helper.assertTrue(caster.position().distanceTo(before) < 0.01, "winded after " + BodyFlicker.MAX_CHAIN + " in a row");
        clearAround(helper);
    }

    // ================================================================ Flying Raijin

    @GameTest(template = "empty", batch = "noirm_st_marks_place", timeoutTicks = 100)
    public static void marksArePlacedByThrowHitAndKey(GameTestHelper helper) {
        openFloor(helper, -2, -2, 4, 10, 4);
        clearStrays(helper, 18);
        SparringNinja owner = legend(helper, 1.5f, 1.5f);
        SparringNinja enemy = ninja(helper, 1.5f, 8.5f, TestClans.FIRE);
        HiraishinMarks marks = HiraishinMarks.get(helper.getLevel().getServer());
        marks.clear(owner.getUUID());
        face(owner, enemy);
        // 1. Throw: a marked kunai carries the formula.
        ThrownWeapon kunai = Throwing.npcThrow(owner, enemy, new ItemStack(HiraishinItems.KUNAI.get()), false);
        helper.assertTrue(kunai != null && has(marks, owner, HiraishinMark.Kind.KUNAI, kunai.getUUID()), "the thrown kunai is marked");
        // 2. A strike: a landed blow marks the body.
        enemy.hurt(helper.getLevel().damageSources().mobAttack(owner), 2f);
        helper.assertTrue(has(marks, owner, HiraishinMark.Kind.BODY, enemy.getUUID()), "the blow marked the enemy");
        // 3. The key: on the block looked at.
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 2, 3), Blocks.STONE);
        HiraishinMark written = HiraishinMarking.markHere(owner, false);
        helper.assertTrue(written != null && written.kind() == HiraishinMark.Kind.BLOCK, "the mark key writes on the block in front");
        // 4. An ally: touched on purpose.
        SparringNinja ally = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        owner.joinCrew(leader);
        ally.joinCrew(leader);
        HiraishinMark onAlly = HiraishinMarking.markEntity(owner, ally);
        helper.assertTrue(onAlly.kind() == HiraishinMark.Kind.ALLY, "a friend gets an ally mark");
        helper.runAfterDelay(30, () -> {
            marks.clear(owner.getUUID());
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_st_jump")
    public static void theJumpIsInstantToTheAimedMark(GameTestHelper helper) {
        openFloor(helper, -6, 0, 8, 14, 4);
        clearStrays(helper, 20);
        SparringNinja jumper = legend(helper, 1.5f, 1.5f);
        jumper.setTarget(null);
        HiraishinMarks marks = HiraishinMarks.get(helper.getLevel().getServer());
        marks.clear(jumper.getUUID());
        Vec3 ahead = helper.absoluteVec(new Vec3(1.5, 1, 12.5));
        Vec3 aside = helper.absoluteVec(new Vec3(-5.5, 1, 1.5));
        HiraishinMark target = HiraishinMarking.markBlock(jumper, ahead, Direction.UP);
        HiraishinMarking.markBlock(jumper, aside, Direction.UP); // newer, selected: but not where it looks
        lookAlongZ(jumper);
        HiraishinMark chosen = HiraishinJump.choose(jumper);
        helper.assertTrue(chosen != null && chosen.id() == target.id(), "the mark under the crosshair, not the newest");
        HiraishinJump.Result result = HiraishinJump.jumpTo(jumper, chosen);
        helper.assertTrue(result.ok(), "jumped");
        helper.assertTrue(jumper.position().distanceTo(ahead) < 0.2, "there in the same tick: " + jumper.position() + " vs " + ahead);
        helper.assertTrue(jumper.getDeltaMovement().lengthSqr() < 1e-6, "no momentum carried from a standing mark");
        marks.clear(jumper.getUUID());
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_st_cap_decay", timeoutTicks = 80)
    public static void marksAreCappedAndFade(GameTestHelper helper) {
        floor(helper);
        SparringNinja owner = legend(helper, 1.5f, 1.5f);
        HiraishinMarks marks = HiraishinMarks.get(helper.getLevel().getServer());
        marks.clear(owner.getUUID());
        int cap = HiraishinMarking.cap(owner);
        HiraishinMark first = null;
        for (int i = 0; i < cap + 4; i++) {
            HiraishinMark m = HiraishinMarking.markBlock(owner, helper.absoluteVec(new Vec3(0.2 + (i % 4) * 0.6, 1, 0.2 + (i / 4) * 0.6)), Direction.UP);
            if (first == null) {
                first = m;
            }
        }
        helper.assertTrue(marks.marks(owner.getUUID()).size() == cap, "never more than " + cap + " marks");
        int firstId = first.id();
        helper.assertTrue(marks.find(owner.getUUID(), firstId) == null, "the oldest faded first");
        marks.clear(owner.getUUID());
        HiraishinMarking.place(owner, HiraishinMark.Kind.BLOCK, helper.absoluteVec(new Vec3(1.5, 1, 1.5)), Direction.UP, null, 5);
        helper.assertTrue(marks.marks(owner.getUUID()).size() == 1, "a short-lived mark");
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(marks.marks(owner.getUUID()).isEmpty(), "it decayed");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_st_slash")
    public static void thunderGodSlashLandsBehindWithABackstab(GameTestHelper helper) {
        openFloor(helper, -2, 0, 4, 12, 4);
        clearStrays(helper, 18);
        SparringNinja jumper = legend(helper, 1.5f, 1.5f);
        SparringNinja enemy = ninja(helper, 1.5f, 9.5f, TestClans.FIRE);
        face(enemy, jumper);
        face(jumper, enemy);
        HiraishinMarks marks = HiraishinMarks.get(helper.getLevel().getServer());
        marks.clear(jumper.getUUID());
        // A kunai lying at the enemy's feet (thrown past them): the slash.
        HiraishinMark mark = HiraishinMarking.markBlock(jumper, enemy.position().add(0.8, 0, 0), Direction.UP);
        HiraishinJump.Result result = HiraishinJump.jumpTo(jumper, mark);
        helper.assertTrue(result.slashTarget() == enemy, "the enemy next to the mark is the slash's target");
        Vec3 facing = Vec3.directionFromRotation(0, enemy.getYRot());
        helper.assertTrue(jumper.position().subtract(enemy.position()).dot(facing) < -0.5, "behind them");
        helper.assertFalse(result.read(), "an ordinary enemy doesn't see it coming");
        helper.assertTrue(HiraishinJump.slashReady(jumper, enemy), "the backstab window is open");
        helper.assertTrue(CombatStates.get(jumper).dodgeCounterTicks() > 0, "the next blow is a counter (unguardable, x1.5)");
        float before = enemy.getHealth();
        enemy.hurt(helper.getLevel().damageSources().mobAttack(jumper), 4f);
        helper.assertFalse(HiraishinJump.slashReady(jumper, enemy), "the bonus was spent on that blow");
        helper.assertTrue(before - enemy.getHealth() > 4f * 1.15f || enemy.getHealth() < before, "the slash hit hard");
        // A sharp eye reads it: a much faster enemy sees the flash coming.
        HiraishinJump.reset(jumper);
        SparringNinja seer = ninja(helper, -0.5f, 9.5f, TestClans.FIRE);
        seer.setCombatStats(120, 120);
        face(seer, jumper);
        HiraishinJump.Result read = HiraishinJump.jumpTo(jumper, HiraishinMarking.markBlock(jumper, seer.position().add(-0.8, 0, 0), Direction.UP));
        helper.assertTrue(read.slashTarget() == seer && read.read(), "the seer read it");
        helper.assertFalse(HiraishinJump.slashReady(jumper, seer), "no free backstab on someone who saw it");
        marks.clear(jumper.getUUID());
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_st_carry")
    public static void anAllyTouchingYouComesAlong(GameTestHelper helper) {
        openFloor(helper, -2, 0, 4, 14, 4);
        clearStrays(helper, 18);
        SparringNinja jumper = legend(helper, 1.5f, 1.5f);
        SparringNinja ally = ninja(helper, 2.5f, 1.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        jumper.joinCrew(leader);
        ally.joinCrew(leader);
        HiraishinMarks marks = HiraishinMarks.get(helper.getLevel().getServer());
        marks.clear(jumper.getUUID());
        Vec3 far = helper.absoluteVec(new Vec3(1.5, 1, 12.5));
        HiraishinJump.Result result = HiraishinJump.jumpTo(jumper, HiraishinMarking.markBlock(jumper, far, Direction.UP));
        helper.assertTrue(result.carried().contains(ally), "the ally came along");
        helper.assertTrue(ally.position().distanceTo(far) < 2.5, "and stands beside you at the mark");
        marks.clear(jumper.getUUID());
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_st_redirect")
    public static void anIncomingProjectileIsSentToAMark(GameTestHelper helper) {
        openFloor(helper, -2, -2, 4, 14, 4);
        clearStrays(helper, 18);
        SparringNinja jumper = legend(helper, 1.5f, 1.5f);
        SparringNinja archer = ninja(helper, 1.5f, 5.5f, TestClans.FIRE);
        HiraishinMarks marks = HiraishinMarks.get(helper.getLevel().getServer());
        marks.clear(jumper.getUUID());
        Vec3 far = helper.absoluteVec(new Vec3(1.5, 1, 13.5));
        HiraishinMarking.markBlock(jumper, far, Direction.UP);
        Arrow arrow = new Arrow(helper.getLevel(), archer);
        arrow.setPos(jumper.getX(), jumper.getY() + 1, jumper.getZ() + 3);
        arrow.setDeltaMovement(0, 0, -1.5);
        helper.getLevel().addFreshEntity(arrow);
        helper.assertTrue(HiraishinJump.incomingProjectile(jumper) == arrow, "the arrow is seen coming");
        helper.assertTrue(HiraishinJump.redirect(jumper) == arrow, "and sent away");
        helper.assertTrue(arrow.position().distanceTo(far) < 2.0, "it's at the mark now");
        helper.assertTrue(arrow.getOwner() == jumper, "and it's ours");
        arrow.discard();
        marks.clear(jumper.getUUID());
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_st_cost")
    public static void chakraCooldownAndMastery(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        ShinobiData data = ShinobiCapability.get(player).orElseThrow();
        data.jutsu().learn(HiraishinLearning.RAIJIN, false);
        data.jutsu().equip(0, HiraishinLearning.RAIJIN);
        data.setChakra(data.getMaxChakra());
        HiraishinMarks marks = HiraishinMarks.get(helper.getLevel().getServer());
        marks.clear(player.getUUID());
        Vec3 mark = helper.absoluteVec(new Vec3(1.5, 1, 2.5));
        HiraishinMarking.markBlock(player, mark, Direction.UP);
        JutsuDefinition def = JutsuData.get(HiraishinLearning.RAIJIN);
        float cost = JutsuCaster.cost(player, HiraishinLearning.RAIJIN, def);
        float before = data.getChakra();
        helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "cast");
        for (int i = 0; i < 6; i++) {
            JutsuCaster.tick(player);
        }
        helper.assertTrue(player.position().distanceTo(mark) < 0.2, "jumped to the mark");
        helper.assertTrue(Math.abs(before - cost - data.getChakra()) < 0.01, "paid " + cost + " chakra");
        helper.assertTrue(data.jutsu().cooldown(HiraishinLearning.RAIJIN) == def.cooldown(), "a fresh user waits the full cooldown");
        helper.assertTrue(HiraishinJump.costFactor(player) < 1f, "the next jump in the flow is cheaper");
        data.jutsu().addXp(HiraishinLearning.RAIJIN, 1000);
        helper.assertTrue(HiraishinJump.cooldownTicks(player, def) < def.cooldown() / 2, "a master's wait is far shorter");
        marks.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_st_npc", timeoutTicks = 120)
    public static void legendsUseTheFlyingRaijin(GameTestHelper helper) {
        openFloor(helper, -3, -3, 5, 16, 5);
        clearStrays(helper, 20);
        SparringNinja legend = legend(helper, 1.5f, 1.5f);
        SparringNinja enemy = ninja(helper, 1.5f, 11.5f, TestClans.FIRE);
        enemy.setInvulnerable(true);
        HiraishinMarks marks = HiraishinMarks.get(helper.getLevel().getServer());
        marks.clear(legend.getUUID());
        face(legend, enemy);
        face(enemy, legend);
        legend.setTarget(enemy);
        Vec3 start = legend.position();
        HiraishinNpc.thinkNow(legend, enemy);
        helper.assertFalse(HiraishinNpc.ready(legend), "it threw a marked kunai");
        helper.assertTrue(marks.marks(legend.getUUID()).stream().anyMatch(m -> m.kind() == HiraishinMark.Kind.KUNAI), "the kunai carries its mark");
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(legend.position().distanceTo(start) > 5, "and was there before it landed");
            helper.assertTrue(legend.distanceTo(enemy) < 3.5, "right at the enemy");
            marks.clear(legend.getUUID());
            clearAround(helper);
        });
    }

    // ================================================================ helpers

    private static boolean has(HiraishinMarks marks, LivingEntity owner, HiraishinMark.Kind kind, UUID carrier) {
        return marks.marks(owner.getUUID()).stream().anyMatch(m -> m.kind() == kind && carrier.equals(m.entity()));
    }

    private static void lookAlongZ(LivingEntity entity) {
        entity.setYRot(0);
        entity.yRotO = 0;
        entity.setYHeadRot(0);
        entity.setYBodyRot(0);
        entity.setXRot(0);
        entity.xRotO = 0;
    }

    private static void run(LivingEntity caster, String path) {
        ResourceLocation id = Noirm.id(path);
        JutsuDefinition def = JutsuData.get(id);
        com.noirm.jutsu.FriendlyZones.mark(caster);
        JutsuBehaviors.get(def.behavior()).cast(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), 1f));
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        ninja.setHealth(ninja.getMaxHealth());
        return ninja;
    }

    /** A master of the Flying Raijin (★5). */
    private static SparringNinja legend(GameTestHelper helper, float x, float z) {
        SparringNinja ninja = ninja(helper, x, z, TestClans.PLAIN);
        HiraishinLearning.teachNpc(ninja, 5);
        ninja.setChakra(ninja.maxChakra());
        return ninja;
    }

    private static JsonObject lang(String code) {
        try (var in = SpaceTimeGameTests.class.getResourceAsStream("/assets/noirm/lang/" + code + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("can't read " + code, e);
        }
    }
}

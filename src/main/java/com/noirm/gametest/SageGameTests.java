package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.entity.MasterNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.master.MasterData;
import com.noirm.master.MasterDefinition;
import com.noirm.master.MasterSystem;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.senjutsu.Meditation;
import com.noirm.senjutsu.MeditationData;
import com.noirm.senjutsu.NatureSpirit;
import com.noirm.senjutsu.Sage;
import com.noirm.senjutsu.SageMode;
import com.noirm.senjutsu.SageTrial;
import com.noirm.world.sanctuary.SanctuaryBuilder;
import com.noirm.world.sanctuary.SanctuaryStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** The sage path: the sanctuary, the hermit's test, training, petrification, Sage Mode, saving. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SageGameTests {
    private SageGameTests() {}

    private static FakePlayer sitter(GameTestHelper helper, String name) {
        floor(helper);
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        player.setOnGround(true);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static ShinobiData data(FakePlayer player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static void learn(ShinobiData data, String... lessons) {
        data.masters().pass(Sage.HERMIT.toString());
        for (String lesson : lessons) {
            data.masters().learn(Sage.HERMIT.toString(), lesson);
        }
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.02f + Math.abs(expected) * 0.01f, what + ": expected " + expected + ", got " + actual);
    }

    // ---------------------------------------------------------------- the sanctuary

    @GameTest(template = "empty", batch = "noirm_sage_sanctuary", timeoutTicks = 200)
    public static void sanctuaryGeneratesAndBuilds(GameTestHelper helper) {
        var level = helper.getLevel();
        // World generation: the structure, its rare placement, its biomes, far from the centre.
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        helper.assertTrue(structures.get(Noirm.id("sage_sanctuary")) instanceof SanctuaryStructure, "the sanctuary structure is registered");
        var set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(Noirm.id("sage_sanctuaries"));
        helper.assertTrue(set != null && set.placement() instanceof RandomSpreadStructurePlacement spread && spread.spacing() == 64,
                "rare: one try every 64 chunks");
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        helper.assertTrue(biomes.getOrCreateTag(net.minecraft.tags.TagKey.create(Registries.BIOME, Noirm.id("has_structure/sage_sanctuary"))).size() > 5,
                "mountain, jungle and misty biomes");
        helper.assertFalse(SanctuaryStructure.farEnough(300, -400), "never near the world's centre");
        helper.assertTrue(SanctuaryStructure.farEnough(1200, 300), "far away is fine");
        // The builder (what each chunk's piece runs), far off where nothing else is tested.
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1)).offset(0, 40, 3000);
        SanctuaryBuilder.build(level, SanctuaryBuilder.fullBox(origin), origin, 1234L, true);
        helper.assertTrue(level.getBlockState(origin.offset(0, -1, SanctuaryBuilder.POOL_Z)).is(Blocks.WATER), "the sacred pool");
        helper.assertTrue(level.getBlockState(origin.offset(0, 4, -10)).is(Blocks.WATER), "the waterfall");
        helper.assertTrue(level.getBlockState(origin.offset(-3, 0, SanctuaryBuilder.GATE_Z)).is(Blocks.RED_TERRACOTTA), "the torii gate");
        helper.assertTrue(level.getBlockState(origin.offset(SanctuaryBuilder.SHRINE_X + 1, 0, 0)).is(Blocks.CHISELED_STONE_BRICKS), "the shrine's altar");
        helper.assertTrue(level.getBlockState(origin.offset(-6, 5, -2)).is(Blocks.CHISELED_STONE_BRICKS), "a stone pillar");
        var hermits = level.getEntitiesOfClass(MasterNinja.class, new AABB(SanctuaryBuilder.masterPos(origin)).inflate(3));
        helper.assertTrue(hermits.size() == 1 && Sage.HERMIT.equals(hermits.get(0).masterId()), "the hermit lives there");
        helper.assertTrue(hermits.get(0).outfit().startsWith("sage_hermit:"), "in his own look");
        hermits.forEach(MasterNinja::discard);
        helper.succeed();
    }

    // ---------------------------------------------------------------- the hermit and his test

    @GameTest(template = "empty", batch = "noirm_sage_trial", timeoutTicks = 200)
    public static void hermitTestOfStillness(GameTestHelper helper) {
        MasterDefinition def = MasterData.get(Sage.HERMIT);
        helper.assertTrue(def != null && SageTrial.KIND.equals(def.trial()) && def.lessons().size() == 3, "the hermit's data");
        FakePlayer player = sitter(helper, "noirm_sage_trial");
        ShinobiData data = data(player);
        MasterNinja hermit = MasterSystem.spawn(helper.getLevel(), Sage.HERMIT, helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0f);
        helper.assertTrue(hermit != null && SageTrial.isTrialMaster(hermit), "a trial master");
        helper.assertTrue(MasterSystem.startSpar(player, hermit), "the test starts from the master screen's button");
        helper.assertTrue(SageTrial.trial(hermit) != null, "a test of stillness, not a spar");
        helper.assertFalse(hermit.isSparring(), "he doesn't fight");
        helper.assertTrue(Meditation.start(player), "sit down");
        for (int i = 0; i < SageTrial.FIRST_SPIRIT_SECONDS * 20 + 1; i++) {
            SageTrial.tick(hermit);
        }
        var spirits = SageTrial.trial(hermit).spiritsSent();
        helper.assertTrue(spirits >= 1, "a restless spirit comes");
        // A spirit wearing you down is stopped at the floor, and the test is failed.
        NatureSpirit spirit = SageTrial.trial(hermit).spirits().get(0);
        player.setHealth(player.getMaxHealth() * 0.35f);
        var hit = new net.minecraftforge.event.entity.living.LivingDamageEvent(player, helper.getLevel().damageSources().mobAttack(spirit), 10f);
        com.noirm.senjutsu.SageEvents.damage(hit); // (fake players can't be hurt: run the rule directly)
        helper.assertTrue(player.getHealth() - hit.getAmount() >= SageTrial.floor(player) - 0.01f, "nobody dies in the test");
        helper.assertTrue(SageTrial.trial(hermit) == null, "worn down: the test is over");
        helper.assertFalse(spirit.isAlive(), "the spirits go away");
        helper.assertFalse(data.masters().passed(Sage.HERMIT.toString()), "not passed");
        // Again, this time staying still to the end.
        player.setHealth(player.getMaxHealth());
        hermit.restoreAfterSpar();
        helper.assertTrue(MasterSystem.startSpar(player, hermit), "try again");
        helper.assertTrue(Meditation.start(player) || Meditation.isMeditating(player) || forceSit(player), "sit down again");
        int needed = def.test().hits() * 20;
        for (int i = 0; i < needed + 1 && SageTrial.trial(hermit) != null; i++) {
            SageTrial.tick(hermit);
        }
        helper.assertTrue(data.masters().passed(Sage.HERMIT.toString()), "still as the mountain: passed");
        helper.assertTrue(SageTrial.trial(hermit) == null, "the test is over");
        // Lessons: the first needs only the test; the next needs CTL 15 and practice.
        helper.assertTrue(MasterSystem.learn(player, Sage.HERMIT, Sage.LESSON_OPEN), "Open to Nature");
        helper.assertTrue(data.jutsu().knows(Sage.NATURE_GATHERING), "teaches Nature Gathering");
        helper.assertFalse(MasterSystem.learn(player, Sage.HERMIT, Sage.LESSON_BALANCE), "Stone Balance needs CTL 15");
        data.setControl(15);
        data.jutsu().addXp(Sage.NATURE_GATHERING, 10);
        helper.assertTrue(MasterSystem.learn(player, Sage.HERMIT, Sage.LESSON_BALANCE), "then Stone Balance");
        helper.assertFalse(MasterSystem.learn(player, Sage.HERMIT, Sage.LESSON_SAGE), "Sage Mode needs CTL 20 and taijutsu 15");
        data.setControl(20);
        data.setTaijutsu(15);
        helper.assertTrue(MasterSystem.learn(player, Sage.HERMIT, Sage.LESSON_SAGE), "then Sage Mode");
        hermit.discard();
        clearAround(helper);
    }

    /** A fake player that was just hurt counts as "in a fight" for 5 s: let it sit anyway. */
    private static boolean forceSit(FakePlayer player) {
        ShinobiCapability.get(player).ifPresent(d -> d.senjutsu().setLastHurtTimeForTests(Long.MIN_VALUE / 2));
        return Meditation.start(player);
    }

    // ---------------------------------------------------------------- training

    @GameTest(template = "empty", batch = "noirm_sage_gain")
    public static void lessonsRaiseTheGainAndTheLimitHolds(GameTestHelper helper) {
        FakePlayer player = sitter(helper, "noirm_sage_gain");
        ShinobiData data = data(player);
        near(helper, 20f, Sage.safeLimit(data), "CTL 10: a safe limit of 20");
        near(helper, 40f, Sage.safeLimit(20f), "CTL 20: 40");
        near(helper, 100f, Sage.safeLimit(60f), "and never past 100");
        float base = gainAfter(player, data, 100);
        learn(data, Sage.LESSON_OPEN);
        near(helper, base * Sage.GAIN_OPEN, gainAfter(player, data, 100), "Open to Nature draws ×1.5");
        learn(data, Sage.LESSON_BALANCE);
        near(helper, base * Sage.GAIN_BALANCE, gainAfter(player, data, 100), "Stone Balance draws ×2");
        // Where meditation stops.
        data.masters().clear();
        helper.assertTrue(Sage.meditationCap(data) == 20f, "untrained: the body stops at its limit");
        learn(data, Sage.LESSON_OPEN);
        helper.assertTrue(Sage.meditationCap(data) == Meditation.NATURE_MAX, "opened: you can overdraw");
        learn(data, Sage.LESSON_BALANCE);
        helper.assertTrue(Sage.meditationCap(data) == 20f, "balanced: it stops at the limit again");
        // Nature Gathering (opened) can overdraw; Nature Purge vents the excess.
        data.senjutsu().setNatureEnergy(19.99f);
        for (int t = 1; t <= 200; t++) {
            SageMode.gather(player, data, t);
        }
        helper.assertTrue(data.senjutsu().natureEnergy() > 20.5f, "Nature Gathering draws past the limit");
        data.senjutsu().setNatureEnergy(50f);
        data.senjutsu().sage().setPetrification(60f);
        near(helper, 30f, SageMode.purge(player, data), "Nature Purge vents everything above the limit");
        near(helper, 20f, data.senjutsu().natureEnergy(), "down to the limit");
        near(helper, 60f - SageMode.PURGE_PETRIFY, data.senjutsu().sage().petrification(), "and cracks some stone off");
        helper.succeed();
    }

    private static float gainAfter(FakePlayer player, ShinobiData data, int ticks) {
        data.senjutsu().setNatureEnergy(0f);
        if (!Meditation.isMeditating(player)) {
            Meditation.start(player);
        }
        for (int i = 0; i < ticks; i++) {
            Meditation.tick(player, data);
        }
        return data.senjutsu().natureEnergy();
    }

    // ---------------------------------------------------------------- petrification

    @GameTest(template = "empty", batch = "noirm_sage_stone")
    public static void overTheLimitPetrifiesAndRecovers(GameTestHelper helper) {
        FakePlayer player = sitter(helper, "noirm_sage_stone");
        ShinobiData data = data(player);
        MeditationData m = data.senjutsu();
        // Untrained: the excess drains away harmlessly.
        m.setNatureEnergy(40f);
        tick(player, data, 200);
        helper.assertTrue(m.sage().petrification() == 0f, "an untrained body never petrifies");
        helper.assertTrue(m.natureEnergy() < 40f, "its excess drains away");
        // A student opened to nature, 20 over the limit: stone builds.
        learn(data, Sage.LESSON_OPEN);
        m.setNatureEnergy(40f);
        tick(player, data, 20);
        float expected = Sage.PETRIFY_BASE + Sage.PETRIFY_PER_EXCESS * 20f;
        helper.assertTrue(Math.abs(m.sage().petrification() - expected) < 0.2f, "about " + expected + " a second at 20 over, got " + m.sage().petrification());
        helper.assertTrue(m.natureEnergy() < 40f, "the excess bleeds off while not drawing");
        m.sage().setPetrification(55f);
        tick(player, data, 1);
        var slow = player.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().anyMatch(mod -> mod.getName().equals("noirm_stone_body"));
        helper.assertTrue(slow && Sage.stage(m.sage().petrification()) == 2, "stage 2: limbs of stone, much slower");
        // Back under the limit: it recovers.
        m.setNatureEnergy(5f);
        float before = m.sage().petrification();
        tick(player, data, 100);
        near(helper, before - 5 * Sage.PETRIFY_RECOVERY, m.sage().petrification(), "recovers 2 a second");
        // All the way: a statue, and without the hermit near, it's for good.
        m.setNatureEnergy(60f);
        m.sage().setPetrification(99.99f);
        tick(player, data, 1);
        helper.assertTrue(Sage.isStatue(player), "a statue");
        helper.assertTrue(Meditation.canStart(player) == Meditation.Refusal.STONE, "a statue can't meditate");
        tick(player, data, Sage.STATUE_SECONDS * 20);
        helper.assertTrue(player.isDeadOrDying(), "turned to stone for good");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_sage_rescue")
    public static void theHermitSavesHisStudent(GameTestHelper helper) {
        FakePlayer player = sitter(helper, "noirm_sage_rescue");
        ShinobiData data = data(player);
        learn(data, Sage.LESSON_OPEN);
        MasterNinja hermit = MasterSystem.spawn(helper.getLevel(), Sage.HERMIT, helper.absoluteVec(new Vec3(2.5, 1, 0.5)), 0f);
        data.senjutsu().setNatureEnergy(60f);
        data.senjutsu().sage().setPetrification(99.99f);
        tick(player, data, 1);
        helper.assertTrue(Sage.isStatue(player), "a statue");
        tick(player, data, Sage.RESCUE_DELAY + 1);
        helper.assertFalse(Sage.isStatue(player), "the hermit cracks the stone off");
        helper.assertTrue(player.isAlive() && data.senjutsu().sage().petrification() == 0f, "alive, no stone left");
        helper.assertTrue(data.senjutsu().natureEnergy() <= Sage.safeLimit(data), "and spent");
        hermit.discard();
        helper.succeed();
    }

    private static void tick(FakePlayer player, ShinobiData data, int ticks) {
        for (int i = 0; i < ticks; i++) {
            player.tickCount++;
            SageMode.tick(player, data);
            Sage.tick(player, data);
        }
    }

    // ---------------------------------------------------------------- Sage Mode

    @GameTest(template = "empty", batch = "noirm_sage_mode")
    public static void sageModeBoostsAndDrains(GameTestHelper helper) {
        FakePlayer player = sitter(helper, "noirm_sage_mode");
        ShinobiData data = data(player);
        data.senjutsu().setNatureEnergy(50f);
        helper.assertFalse(SageMode.start(player), "not without the lesson");
        learn(data, Sage.LESSON_OPEN, Sage.LESSON_BALANCE, Sage.LESSON_SAGE);
        data.setControl(30); // limit 60
        data.senjutsu().setNatureEnergy(10f);
        helper.assertFalse(SageMode.start(player), "not below 20 nature energy");
        var def = JutsuData.get(Sage.NATURE_PURGE);
        float jutsuBefore = JutsuCaster.power(player, Sage.NATURE_PURGE, def);
        data.senjutsu().setNatureEnergy(30f);
        helper.assertTrue(SageMode.start(player), "Sage Mode on");
        helper.assertTrue(SageMode.damageMultiplier(player) == SageMode.DAMAGE && SageMode.defenseMultiplier(player) == SageMode.DEFENSE,
                "hits harder, takes less");
        near(helper, jutsuBefore * SageMode.JUTSU_POWER, JutsuCaster.power(player, Sage.NATURE_PURGE, def), "stronger jutsu");
        helper.assertTrue(com.noirm.combat.Perception.perception(player) > 0f && SageMode.perceptionMultiplier(player) > 1f, "sharper perception");
        helper.assertTrue(player.getAttribute(ForgeMod.ENTITY_REACH.get()).getModifiers().stream().anyMatch(mod -> mod.getAmount() == SageMode.REACH),
                "the aura reaches further");
        tick(player, data, 200);
        near(helper, 30f - 10 * SageMode.DRAIN_PER_SECOND, data.senjutsu().natureEnergy(), "burns 0.5 a second");
        data.senjutsu().setNatureEnergy(0.01f);
        tick(player, data, 1);
        helper.assertFalse(SageMode.isActive(player), "runs dry");
        helper.assertTrue(data.senjutsu().sage().exhaustionTicks() > 0, "then exhaustion");
        data.senjutsu().setNatureEnergy(50f);
        helper.assertFalse(SageMode.start(player), "no Sage Mode while exhausted");
        helper.assertTrue(player.getAttribute(ForgeMod.ENTITY_REACH.get()).getModifiers().stream().noneMatch(mod -> mod.getAmount() == SageMode.REACH),
                "the reach goes back");
        helper.succeed();
    }

    // ---------------------------------------------------------------- saving

    @GameTest(template = "empty", batch = "noirm_sage_save")
    public static void sageStateSurvivesSaveAndLoad(GameTestHelper helper) {
        FakePlayer player = sitter(helper, "noirm_sage_save");
        ShinobiData data = data(player);
        learn(data, Sage.LESSON_OPEN, Sage.LESSON_BALANCE, Sage.LESSON_SAGE);
        data.setControl(30);
        data.senjutsu().setNatureEnergy(55f);
        SageMode.start(player);
        data.senjutsu().sage().setPetrification(42f);
        CompoundTag saved = data.save();
        ShinobiData copy = new ShinobiData();
        copy.load(saved);
        near(helper, 55f, copy.senjutsu().natureEnergy(), "nature energy");
        near(helper, 42f, copy.senjutsu().sage().petrification(), "petrification");
        helper.assertTrue(copy.senjutsu().sage().active(), "Sage Mode stays on");
        helper.assertTrue(Sage.learned(copy, Sage.LESSON_SAGE) && Sage.student(copy), "lessons");
        SageMode.end(player, false);
        helper.succeed();
    }
}

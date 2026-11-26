package com.noirm.gametest;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.ally.AllyPackets;
import com.noirm.ally.CrewEyes;
import com.noirm.ally.CrewFusion;
import com.noirm.ally.Order;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.clan.Nature;
import com.noirm.dojutsu.MangekyoAbility;
import com.noirm.dojutsu.MangekyoTransplant.EyeInfo;
import com.noirm.dojutsu.Sharingan;
import com.noirm.dojutsu.SharinganAwakening;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.ShinobiCapability;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Crew mates with powers of their own: a Sharingan you give them or that awakens beside you
 * (ally/CrewEyes), and combined natures they fuse themselves (ally/CrewFusion). AI off, no fights,
 * but the eye flares and sounds get a batch of their own.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrewPowersGameTests {
    private static final String EYES = "noirm_crew_eyes";
    private static final String FUSION = "noirm_crew_fusion";

    private CrewPowersGameTests() {}

    /** A Senju crew mate takes a 2-tomoe eye: always-open implanted Sharingan with its genjutsu, kept through a save and load. */
    @GameTest(template = "empty", batch = EYES)
    public static void aTransplantedSharinganIsSavedWithTheCrewMate(GameTestHelper helper) {
        Player leader = leader(helper);
        SparringNinja ally = ally(helper, leader, "senju", 1.5f);
        helper.assertTrue(ally.activeSharinganStage() == 0, "a Senju has no Sharingan");
        ItemStack eye = EyeInfo.sharingan(2, "Shisui").toStack();
        helper.assertTrue(CrewEyes.outcome(ally, EyeInfo.of(eye)) == CrewEyes.Outcome.GIVEN, "the eye would take");
        helper.assertTrue(CrewEyes.giveEye(leader, ally, eye) == CrewEyes.Outcome.GIVEN, "the transplant works");
        helper.assertTrue(eye.isEmpty(), "the eye is used up");
        helper.assertTrue(Sharingan.activeStage(ally) == 2, "2 tomoe, open");
        helper.assertTrue(ally.allyState().eyeImplanted(), "marked as transplanted");
        helper.assertTrue(JutsuData.get(Sharingan.GENJUTSU) == null || ally.knownJutsu().contains(Sharingan.GENJUTSU), "2 tomoe brings the genjutsu");
        helper.assertTrue(ally.allyState().eyeXp() >= Sharingan.STAGE_XP[2], "growth starts at its stage");
        // A weaker or equal eye is refused; a Mangekyō is too much.
        helper.assertTrue(CrewEyes.outcome(ally, EyeInfo.sharingan(2, "x")) == CrewEyes.Outcome.REJECTED_WEAKER, "no weaker eye");
        helper.assertTrue(CrewEyes.outcome(ally, new EyeInfo(3, true, 0, MangekyoAbility.values()[0], null, 0, 0f, "x"))
                == CrewEyes.Outcome.REJECTED_MANGEKYO, "no Mangekyō");
        // Rejoining their clan (as on load) keeps the implanted eye.
        ally.joinClan(ClanData.canon("senju").orElseThrow());
        helper.assertTrue(ally.activeSharinganStage() == 2, "rejoining the clan keeps the eye");
        // Save and load.
        ally.allyState().setEyeXp(450f);
        CompoundTag tag = new CompoundTag();
        ally.saveWithoutId(tag);
        tag.remove("UUID");
        SparringNinja copy = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 1.5f);
        copy.load(tag);
        helper.assertTrue(copy.activeSharinganStage() == 2, "the tomoe are kept, got " + copy.activeSharinganStage());
        helper.assertTrue(copy.allyState().eyeImplanted() && Math.abs(copy.allyState().eyeXp() - 450f) < 1e-3, "growth and implant are kept");
        clear(helper);
    }

    /** Sneak + right-click with an eye gives it; a Hyūga's Byakugan rejects it; a wary mate won't be operated on. */
    @GameTest(template = "empty", batch = EYES)
    public static void sneakRightClickGivesTheEye(GameTestHelper helper) {
        Player leader = leader(helper);
        SparringNinja ally = ally(helper, leader, "hatake", 1.5f);
        leader.setItemInHand(InteractionHand.MAIN_HAND, EyeInfo.sharingan(1, "Obito").toStack());
        leader.setShiftKeyDown(true);
        ally.interact(leader, InteractionHand.MAIN_HAND);
        helper.assertTrue(ally.activeSharinganStage() == 1, "sneak + right-click transplanted the eye");
        helper.assertTrue(leader.getMainHandItem().isEmpty(), "and used it up");
        helper.assertTrue(ally.order() == Order.FOLLOW, "without toggling their order");
        SparringNinja hyuga = ally(helper, leader, "hyuga", 0.5f);
        helper.assertTrue(CrewEyes.outcome(hyuga, EyeInfo.sharingan(3, "x")) == CrewEyes.Outcome.REJECTED_BYAKUGAN, "the Byakugan rejects it");
        ally.setRespect(5f);
        helper.assertTrue(CrewEyes.outcome(ally, EyeInfo.sharingan(3, "x")) == CrewEyes.Outcome.DISTRUST, "low respect: they won't let you");
        clear(helper);
    }

    /** A dormant Uchiha crew mate awakens seeing you near death; open eyes grow with fights; no second jolt during the cooldown. */
    @GameTest(template = "empty", batch = EYES)
    public static void anUchihaCrewMateAwakensWhenTheLeaderNearlyDies(GameTestHelper helper) {
        Player leader = leader(helper);
        SparringNinja ally = ally(helper, leader, "uchiha", 1.5f);
        ally.setSharinganStage(0);
        helper.assertTrue(SharinganAwakening.isDormantNpc(ally), "dormant");
        SparringNinja stranger = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        stranger.joinClan(ClanData.canon("uchiha").orElseThrow());
        stranger.sealBeast(0);
        stranger.setSharinganStage(0);
        helper.assertTrue(CrewEyes.comradeInDanger(leader) == 1, "one crew mate reacts");
        helper.assertTrue(ally.activeSharinganStage() == 1, "the crew mate awakens 1 tomoe");
        helper.assertTrue(stranger.activeSharinganStage() == 0, "a stranger doesn't care");
        // Growth from fighting: enough reaches 2 tomoe.
        helper.assertFalse(CrewEyes.addXp(ally, 100f), "not yet");
        helper.assertTrue(CrewEyes.addXp(ally, Sharingan.STAGE_XP[2]), "a new tomoe");
        helper.assertTrue(ally.activeSharinganStage() == 2, "2 tomoe");
        helper.assertTrue(CrewEyes.progress(ally) > 0f && CrewEyes.progress(ally) < 1f, "on the way to 3");
        helper.assertTrue(CrewEyes.comradeInDanger(leader) == 0, "the cooldown holds");
        ally.allyState().lastEyeMoment -= CrewEyes.DANGER_COOLDOWN_TICKS;
        float before = ally.allyState().eyeXp();
        helper.assertTrue(CrewEyes.comradeInDanger(leader) == 1, "later, open eyes deepen");
        helper.assertTrue(ally.allyState().eyeXp() >= before + CrewEyes.DANGER_XP - 1e-3, "by " + CrewEyes.DANGER_XP);
        stranger.discard();
        clear(helper);
    }

    /** A Senju crew mate (Earth + Water) fuses Wood though you lack it: fast, with the nature and its starters at the end. */
    @GameTest(template = "empty", batch = FUSION)
    public static void aSenjuCrewMateFusesWoodRelease(GameTestHelper helper) {
        FakePlayer leader = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fusion_leader"));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
        leader.moveTo(at.x, at.y, at.z, 0f, 0f);
        ShinobiCapability.get(leader).orElseThrow().identity().addNature(Nature.FIRE, Clan.SECONDARY_AFFINITY);
        SparringNinja ally = ally(helper, leader, "senju", 1.5f);
        helper.assertTrue(ally.jutsuAffinity(Nature.WOOD) <= 0f, "no Wood yet");
        helper.assertTrue(CrewFusion.kind(ally, Nature.WOOD) == CrewFusion.Kind.GIFTED, "a Senju is gifted for Wood");
        AllyPackets.Info info = AllyPackets.Info.of(leader, ally);
        helper.assertTrue(info.extra().natureStatus()[Nature.WOOD.ordinal()] == AllyPackets.NATURE_FUSABLE, "the screen offers the fusion");
        AllyPackets.apply(leader, ally, AllyPackets.Action.FUSE_START, "wood");
        helper.assertTrue(ally.allyState().fusion() == Nature.WOOD, "the screen's button starts it");
        helper.assertTrue(AllyPackets.Info.of(leader, ally).extra().natureStatus()[Nature.WOOD.ordinal()] == AllyPackets.NATURE_FUSING, "shown as fusing");
        ally.setOrder(Order.MEDITATE);
        CrewFusion.study(ally, leader, 10f);
        near(helper, 10f * CrewFusion.APTITUDE_RATE, ally.allyState().fusionSeconds(), "gifted: 3x pace");
        helper.assertTrue(CrewFusion.secondsNeeded(Nature.WOOD) > com.noirm.ally.NatureTeaching.secondsNeeded(Nature.WOOD),
                "fusing takes longer than being taught");
        // Saved mid-way.
        CompoundTag saved = ally.allyState().save();
        com.noirm.ally.AllyState loaded = new com.noirm.ally.AllyState();
        loaded.load(saved);
        helper.assertTrue(loaded.fusion() == Nature.WOOD && Math.abs(loaded.fusionSeconds() - 30f) < 1e-3, "the fusion is saved");
        // Far away: paused.
        leader.setPos(at.add(CrewFusion.GUIDE_RANGE + 8, 0, 0));
        helper.assertFalse(CrewFusion.study(ally, leader, 10f), "paused");
        near(helper, 30f, ally.allyState().fusionSeconds(), "no progress while you're away");
        leader.setPos(at);
        // Finish it (sped up).
        ally.allyState().setFusionSeconds(CrewFusion.secondsNeeded(Nature.WOOD) - 1f);
        helper.assertTrue(CrewFusion.study(ally, leader, 1f), "done");
        near(helper, Clan.SECONDARY_AFFINITY, ally.jutsuAffinity(Nature.WOOD), "Wood at their weakest part's strength");
        helper.assertTrue(ally.allyState().fusion() == null, "the fusion is over");
        helper.assertTrue(ally.knownJutsu().containsAll(JutsuData.startersFor(Nature.WOOD)), "they know its starting jutsu");
        helper.assertTrue(ally.allyState().learned().containsKey(Nature.WOOD), "kept across clan re-joins");
        clear(helper);
    }

    /** No parts, no fusion; Yin–Yang can't be fused; taught parts make it very slow; one fusion at a time. */
    @GameTest(template = "empty", batch = FUSION)
    public static void aCrewMateWithoutThePartsCannotFuse(GameTestHelper helper) {
        Player leader = leader(helper);
        SparringNinja ally = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ally.joinClan(TestClans.PLAIN); // water only
        ally.sealBeast(0);
        ally.joinCrew(leader.getUUID());
        helper.assertTrue("screen.noirm.ally.fusion.why.parts".equals(CrewFusion.blocker(ally, Nature.WOOD)), "Wood needs Earth too");
        helper.assertFalse(CrewFusion.start(leader, ally, Nature.WOOD), "can't start");
        helper.assertTrue(ally.allyState().fusion() == null, "nothing started");
        helper.assertTrue("screen.noirm.ally.fusion.why.not_fusable".equals(CrewFusion.blocker(ally, Nature.YIN_YANG)), "no Yin-Yang");
        helper.assertTrue("screen.noirm.ally.fusion.why.not_fusable".equals(CrewFusion.blocker(ally, Nature.WATER)), "nor a basic nature");
        // Given Earth by a lesson, they can, but slowly.
        ally.learnNature(Nature.EARTH, Clan.TRAINED_AFFINITY);
        helper.assertTrue(CrewFusion.kind(ally, Nature.WOOD) == CrewFusion.Kind.TAUGHT, "a taught part: slow");
        helper.assertTrue(CrewFusion.start(leader, ally, Nature.WOOD), "now they can");
        ally.learnNature(Nature.WIND, Clan.TRAINED_AFFINITY);
        helper.assertTrue("screen.noirm.ally.fusion.why.busy".equals(CrewFusion.blocker(ally, Nature.ICE)), "one fusion at a time");
        ally.setOrder(Order.TRAIN);
        CrewFusion.study(ally, leader, 10f);
        near(helper, 10f * CrewFusion.TAUGHT_RATE, ally.allyState().fusionSeconds(), "taught parts: very slow");
        CrewFusion.stop(ally);
        helper.assertTrue(ally.allyState().fusion() == null && ally.allyState().fusionSeconds() == 0f, "stopped");
        clear(helper);
    }

    // ------------------------------------------------------------ helpers

    private static Player leader(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setPos(helper.absoluteVec(new Vec3(1.5, 1, 0.5)));
        return player;
    }

    /** A crew mate of a canon clan (so a save and load re-joins it), AI off. */
    private static SparringNinja ally(GameTestHelper helper, Player leader, String clan, float x) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, 1.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(ClanData.canon(clan).orElseThrow());
        ninja.sealBeast(0);
        ninja.joinCrew(leader.getUUID());
        ninja.setRespect(50f);
        return ninja;
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 1e-3, what + ": expected " + expected + ", got " + actual);
    }

    /** Removes this test's ninjas (they stand still, so only the 3×3 area). */
    private static void clear(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(SparringNinja.class, new net.minecraft.world.phys.AABB(helper.absolutePos(
                net.minecraft.core.BlockPos.ZERO)).expandTowards(3, 3, 3)).forEach(SparringNinja::discard);
        helper.succeed();
    }
}

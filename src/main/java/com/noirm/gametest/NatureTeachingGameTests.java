package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.ally.AllyPackets;
import com.noirm.ally.NatureTeaching;
import com.noirm.ally.Order;
import com.noirm.ally.Tactics;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Teaching a crew mate a chakra nature over time (ally/NatureTeaching), their tactics and the
 * ally screen's data. Nobody fights here (AI off), but they share one batch of their own.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NatureTeachingGameTests {
    private static final String BATCH = "noirm_nature_teaching";

    private NatureTeachingGameTests() {}

    /** The lesson moves whenever you're near: full pace under Train/Meditate, half otherwise; fighting and mastery speed it up. */
    @GameTest(template = "empty", batch = BATCH)
    public static void lessonsNeedTheRightOrderAndYouNearby(GameTestHelper helper) {
        Player teacher = teacher(helper, Nature.WATER, Nature.WIND);
        SparringNinja ally = ally(helper, teacher);
        helper.assertTrue(NatureTeaching.start(teacher, ally, Nature.WIND), "you can teach a nature you own that they lack");
        helper.assertTrue(ally.allyState().lesson() == Nature.WIND, "the lesson is on");
        // Following: half pace.
        float casual = 10f * NatureTeaching.CASUAL_RATE;
        NatureTeaching.study(ally, teacher, 10f);
        near(helper, casual, ally.allyState().lessonSeconds(), "following you teaches at half pace");
        // Meditating near you: one second per second.
        ally.setOrder(Order.MEDITATE);
        NatureTeaching.study(ally, teacher, 10f);
        near(helper, casual + 10f, ally.allyState().lessonSeconds(), "meditating near you: 10 s of study");
        ally.setOrder(Order.TRAIN);
        NatureTeaching.study(ally, teacher, 10f);
        float sofar = casual + 20f;
        near(helper, sofar, ally.allyState().lessonSeconds(), "training counts too");
        // Too far away: paused.
        Vec3 home = teacher.position();
        teacher.setPos(home.add(NatureTeaching.TEACH_RANGE + 10, 0, 0));
        NatureTeaching.study(ally, teacher, 10f);
        near(helper, sofar, ally.allyState().lessonSeconds(), "out of range: paused");
        teacher.setPos(home);
        // Fighting side by side is faster, under any order.
        ally.setOrder(Order.FOLLOW);
        SparringNinja enemy = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        enemy.joinClan(TestClans.PLAIN);
        enemy.sealBeast(0);
        ally.setTarget(enemy);
        NatureTeaching.study(ally, teacher, 10f);
        near(helper, sofar + casual * NatureTeaching.FIGHT_BONUS, ally.allyState().lessonSeconds(), "fighting together is faster");
        ally.setTarget(null);
        // Having mastered the nature yourself is faster too.
        ally.setOrder(Order.MEDITATE);
        data(teacher).jutsu().learn(Noirm.id("futon/air_palm"), false);
        data(teacher).jutsu().addXp(Noirm.id("futon/air_palm"), 1000);
        float before = ally.allyState().lessonSeconds();
        NatureTeaching.study(ally, teacher, 10f);
        near(helper, before + 10f * NatureTeaching.MASTERED_BONUS, ally.allyState().lessonSeconds(), "a mastered nature teaches faster");
        // The per-tick hook moves it once a second.
        before = ally.allyState().lessonSeconds();
        ally.tickCount = NatureTeaching.INTERVAL * 7;
        NatureTeaching.tick(ally, teacher);
        near(helper, before + NatureTeaching.MASTERED_BONUS, ally.allyState().lessonSeconds(), "one second per interval");
        // Stopping loses the lesson.
        NatureTeaching.stop(ally);
        helper.assertTrue(ally.allyState().lesson() == null && ally.allyState().lessonSeconds() == 0f, "stopped");
        clear(helper);
    }

    /** At the end they gain the nature (trained strength) and its starter jutsu, and can be taught its jutsu. */
    @GameTest(template = "empty", batch = BATCH)
    public static void finishingALessonGrantsTheNature(GameTestHelper helper) {
        Player teacher = teacher(helper, Nature.WATER, Nature.WIND);
        SparringNinja ally = ally(helper, teacher);
        ally.setOrder(Order.MEDITATE);
        var airPalm = com.noirm.jutsu.JutsuData.get(Noirm.id("futon/air_palm"));
        helper.assertTrue("screen.noirm.ally.teach.nature".equals(AllyPackets.blockerFor(ally, airPalm)), "no wind jutsu before wind");
        NatureTeaching.start(teacher, ally, Nature.WIND);
        float needed = NatureTeaching.secondsNeeded(Nature.WIND);
        helper.assertTrue(needed >= 1200f && needed <= 3600f, "a basic nature takes 1-3 Minecraft days, got " + needed);
        ally.allyState().setLessonSeconds(needed - 3f);
        helper.assertFalse(NatureTeaching.study(ally, teacher, 1f), "not done yet");
        helper.assertTrue(NatureTeaching.study(ally, teacher, 5f), "done");
        near(helper, Clan.TRAINED_AFFINITY, ally.jutsuAffinity(Nature.WIND), "they have wind at trained strength");
        helper.assertTrue(ally.allyState().lesson() == null, "the lesson is over");
        helper.assertTrue(ally.knownJutsu().contains(Noirm.id("futon/air_palm")), "and know its starter jutsu");
        helper.assertTrue(ally.knownJutsu().stream().filter(id -> com.noirm.jutsu.JutsuData.get(id) != null
                        && com.noirm.jutsu.JutsuData.get(id).nature().orElse(null) == Nature.WIND).toList()
                        .equals(com.noirm.jutsu.JutsuData.startersFor(Nature.WIND)), "only its starters, not every wind jutsu");
        helper.assertFalse("screen.noirm.ally.teach.nature".equals(AllyPackets.blockerFor(ally, airPalm)), "wind jutsu can be taught now");
        helper.assertTrue("screen.noirm.ally.nature.why.owned".equals(NatureTeaching.blocker(teacher, ally, Nature.WIND)), "and it can't be taught twice");
        clear(helper);
    }

    /** Combined natures need both parts (or have them queued first); you must own what you teach; more lessons wait in a queue. */
    @GameTest(template = "empty", batch = BATCH)
    public static void combinedNaturesNeedBothParts(GameTestHelper helper) {
        Player teacher = teacher(helper, Nature.WATER, Nature.EARTH, Nature.WOOD, Nature.FIRE);
        SparringNinja ally = ally(helper, teacher); // water only
        ally.setOrder(Order.TRAIN);
        helper.assertTrue("screen.noirm.ally.nature.why.parts".equals(NatureTeaching.blocker(teacher, ally, Nature.WOOD)), "wood needs earth first");
        helper.assertFalse(NatureTeaching.start(teacher, ally, Nature.WOOD), "so it can't start");
        helper.assertTrue("screen.noirm.ally.nature.why.you_lack".equals(NatureTeaching.blocker(teacher, ally, Nature.LIGHTNING)),
                "you can't teach what you don't have");
        helper.assertTrue(NatureTeaching.start(teacher, ally, Nature.EARTH), "earth first");
        helper.assertTrue(NatureTeaching.start(teacher, ally, Nature.FIRE), "fire waits in the queue");
        helper.assertTrue(NatureTeaching.start(teacher, ally, Nature.WOOD), "wood can be queued once earth is in hand");
        helper.assertFalse(NatureTeaching.start(teacher, ally, Nature.FIRE), "not queued twice");
        helper.assertTrue(ally.allyState().lesson() == Nature.EARTH && ally.allyState().queue().equals(java.util.List.of(Nature.FIRE, Nature.WOOD)),
                "earth now, then fire and wood: " + ally.allyState().queue());
        NatureTeaching.dequeue(ally, Nature.FIRE);
        helper.assertTrue(ally.allyState().queue().equals(java.util.List.of(Nature.WOOD)), "fire taken out of the queue");
        ally.allyState().setLessonSeconds(NatureTeaching.secondsNeeded(Nature.EARTH));
        helper.assertTrue(NatureTeaching.study(ally, teacher, 1f), "earth learned");
        helper.assertTrue(ally.allyState().lesson() == Nature.WOOD && ally.allyState().queue().isEmpty(), "wood starts by itself");
        helper.assertTrue(NatureTeaching.secondsNeeded(Nature.WOOD) > NatureTeaching.secondsNeeded(Nature.EARTH), "and takes longer");
        ally.allyState().setLessonSeconds(NatureTeaching.secondsNeeded(Nature.WOOD));
        helper.assertTrue(NatureTeaching.study(ally, teacher, 1f), "wood learned");
        near(helper, Clan.TRAINED_AFFINITY, ally.jutsuAffinity(Nature.WOOD), "at their weaker part's strength (trained earth)");
        clear(helper);
    }

    /** Learned natures, the lesson and tactics survive a save and load (and the clan being re-applied). */
    @GameTest(template = "empty", batch = BATCH)
    public static void lessonsAndTacticsAreSaved(GameTestHelper helper) {
        Player teacher = teacher(helper, Nature.WATER, Nature.WIND, Nature.EARTH);
        SparringNinja ally = ally(helper, teacher);
        ally.setOrder(Order.MEDITATE);
        NatureTeaching.start(teacher, ally, Nature.WIND);
        ally.allyState().setLessonSeconds(NatureTeaching.secondsNeeded(Nature.WIND));
        NatureTeaching.study(ally, teacher, 1f);
        NatureTeaching.start(teacher, ally, Nature.EARTH);
        ally.allyState().setLessonSeconds(123f);
        ally.allyState().setRange(Tactics.Range.FAR);
        ally.allyState().setJutsuUse(Tactics.JutsuUse.CONSERVE);
        CompoundTag tag = new CompoundTag();
        ally.saveWithoutId(tag);
        tag.remove("UUID");
        SparringNinja copy = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 1.5f);
        copy.load(tag);
        copy.joinClan(TestClans.PLAIN); // (test clans aren't in the world's list, so re-join by hand)
        copy.sealBeast(0);
        near(helper, Clan.TRAINED_AFFINITY, copy.jutsuAffinity(Nature.WIND), "the learned nature is kept");
        helper.assertTrue(copy.knownJutsu().contains(Noirm.id("futon/air_palm")), "with its jutsu");
        helper.assertTrue(copy.allyState().lesson() == Nature.EARTH, "the lesson is kept");
        near(helper, 123f, copy.allyState().lessonSeconds(), "with its progress");
        helper.assertTrue(copy.allyState().range() == Tactics.Range.FAR && copy.allyState().jutsuUse() == Tactics.JutsuUse.CONSERVE, "and the tactics");
        helper.assertTrue(Tactics.range(copy) == Tactics.Range.FAR, "a crew mate fights at the range they were told");
        copy.leaveCrew();
        helper.assertTrue(Tactics.range(copy) == Tactics.Range.CLOSE && Tactics.jutsuUse(copy) == Tactics.JutsuUse.FREE,
                "outside a crew they fight their own way");
        clear(helper);
    }

    /** The screen's data goes over the wire and back unchanged. */
    @GameTest(template = "empty", batch = BATCH)
    public static void allyScreenDataRoundTrips(GameTestHelper helper) {
        byte[] status = new byte[Nature.values().length];
        float[] affinity = new float[Nature.values().length];
        status[Nature.WIND.ordinal()] = AllyPackets.NATURE_LEARNING;
        affinity[Nature.WATER.ordinal()] = 1f;
        AllyPackets.Extra extra = new AllyPackets.Extra("Uchiha", 40f, 100f, "weapon_sword", 2, 1, status, affinity,
                new AllyPackets.Lesson(Nature.WIND.ordinal(), 50f, 3600f, 1.5f, AllyPackets.PACE_NEAR | AllyPackets.PACE_STUDYING),
                3, 2, new byte[]{(byte) Nature.FIRE.ordinal(), (byte) Nature.EARTH.ordinal()}, 2, 42.5f, 17);
        AllyPackets.Info info = new AllyPackets.Info(7, 30f, 40f, new float[]{1, 2, 3, 4, 5}, new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f}, 3,
                "basic", List.of("basic", "brawler"), "generic:1", List.of(Noirm.id("futon/air_palm")),
                List.of(new AllyPackets.Teachable(Noirm.id("katon/great_fireball"), "B", "screen.noirm.ally.teach.nature")),
                55f, "LOYAL", List.of(Noirm.id("futon/air_palm")), List.of(2), extra);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        info.encode(buf);
        AllyPackets.Info back = AllyPackets.Info.decode(buf);
        helper.assertTrue(buf.readableBytes() == 0, "everything was read");
        helper.assertTrue(back.extra().clan().equals("Uchiha") && back.extra().weaponStyle().equals("weapon_sword"), "clan and moveset");
        helper.assertTrue(back.extra().range() == 2 && back.extra().jutsuUse() == 1, "tactics");
        helper.assertTrue(back.extra().natureStatus()[Nature.WIND.ordinal()] == AllyPackets.NATURE_LEARNING, "nature status");
        helper.assertTrue(back.extra().lesson().nature() == Nature.WIND.ordinal() && back.extra().lesson().has(AllyPackets.PACE_NEAR)
                && !back.extra().lesson().has(AllyPackets.PACE_FIGHTING), "the lesson");
        near(helper, 1.5f, back.extra().lesson().rate(), "its pace");
        helper.assertTrue(back.mastery().equals(List.of(2)) && back.order() == 3 && back.trait().equals("LOYAL"), "the rest");
        helper.assertTrue(back.extra().occupation() == 3 && back.extra().behaviour() == 2 && back.extra().queue().length == 2
                && back.extra().queue()[1] == Nature.EARTH.ordinal() && back.extra().pendingJutsu() == 2 && back.extra().carried() == 17,
                "job, behaviour, queue, pending jutsu and loot");
        near(helper, 42.5f, back.extra().natureEnergy(), "nature energy");
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    /** A player standing in the test area who owns these natures. */
    private static Player teacher(GameTestHelper helper, Nature... natures) {
        Player player = helper.makeMockPlayer();
        player.setPos(helper.absoluteVec(new Vec3(1.5, 1, 0.5)));
        ShinobiData data = data(player);
        for (Nature nature : natures) {
            data.identity().addNature(nature, Clan.SECONDARY_AFFINITY);
        }
        return player;
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    /** A water-nature crew mate with AI off. */
    private static SparringNinja ally(GameTestHelper helper, Player leader) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        ninja.sealBeast(0);
        ninja.joinCrew(leader.getUUID());
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

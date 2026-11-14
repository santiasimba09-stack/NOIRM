package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.arts.ArtsSync;
import com.noirm.arts.ChakraShape;
import com.noirm.arts.FusionTrial;
import com.noirm.arts.ShapeTraining;
import com.noirm.arts.SpiritPaths;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.CastType;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuSeals;
import com.noirm.nature.NatureDrills;
import com.noirm.network.PowerPackets;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.senjutsu.Meditation;
import com.noirm.workshop.JutsuDesign;
import com.noirm.workshop.Workshop;
import com.noirm.workshop.WorkshopItems;
import com.noirm.workshop.WorkshopRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Chakra wave, part 2: fusing natures alone (success and failure), shape training and shape + nature
 * fusion, Yin/Yang alone, the jutsu workshop (numbers, forms refused, gates), casting a creation
 * (misfire, then mastery), backlash, teaching a crew mate, scrolls, and saving. Each test has its own
 * batch (drills read the blocks around the player; casts hit what's near).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChakraWave2GameTests {
    private static final ResourceLocation FIREBALL = Noirm.id("katon/great_fireball");
    private static final ResourceLocation AIR_PALM = Noirm.id("futon/air_palm");

    private ChakraWave2GameTests() {}

    /** A fake (server) player on a stone floor in the middle of the test area, with only these natures (at 1.0). */
    private static FakePlayer ninja(GameTestHelper helper, String name, Nature... natures) {
        floor(helper);
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        player.setOnGround(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.getInventory().clearContent();
        ShinobiData data = data(player);
        data.identity().natures().clear();
        data.jutsu().clear();
        data.creations().clear();
        data.arts().load(new CompoundTag());
        data.natureTraining().load(new CompoundTag());
        for (Nature nature : natures) {
            data.identity().addNature(nature, Clan.SECONDARY_AFFINITY);
        }
        data.setReserves(ShinobiData.DEFAULT_STAT);
        data.setControl(ShinobiData.DEFAULT_STAT);
        data.setWillpower(ShinobiData.DEFAULT_STAT);
        data.setVitality(ShinobiData.DEFAULT_STAT);
        data.setMaxChakra(100f);
        data.setChakra(data.getMaxChakra());
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static void master(ShinobiData data, ResourceLocation jutsu, int stars) {
        data.jutsu().learn(jutsu, false);
        data.jutsu().addXp(jutsu, com.noirm.jutsu.JutsuBook.xpForLevel(stars) - data.jutsu().xp(jutsu));
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 1e-2, what + ": expected " + expected + ", got " + actual);
    }

    // ================================================================ fusing natures alone

    /** Fire + Wind → Scorch: blocked until both are ★4 and CTL 40; attune, blend, prove; the nature is yours. */
    @GameTest(template = "empty", batch = "noirm_cw2_fusion_ok", timeoutTicks = 100)
    public static void fusionTrialSucceeds(GameTestHelper helper) {
        FakePlayer player = ninja(helper, "noirm_fuser", Nature.FIRE, Nature.WIND);
        ShinobiData data = data(player);
        master(data, FIREBALL, 2);
        master(data, AIR_PALM, 2);
        helper.assertFalse(FusionTrial.start(player, data, Nature.SCORCH), "★2 isn't enough");
        master(data, FIREBALL, FusionTrial.PART_MASTERY);
        master(data, AIR_PALM, FusionTrial.PART_MASTERY);
        helper.assertTrue(NatureSystem.status(data, Nature.SCORCH) == NatureSystem.Status.AVAILABLE, "both parts mastered: available");
        helper.assertFalse(FusionTrial.start(player, data, Nature.SCORCH), "CTL 10 is too low");
        helper.assertFalse(FusionTrial.start(player, data, Nature.WOOD), "you lack earth and water");
        data.setControl(FusionTrial.MIN_CTL);
        helper.assertTrue(FusionTrial.start(player, data, Nature.SCORCH), "the trial begins");
        List<FusionTrial.Stage> stages = FusionTrial.stages(Nature.SCORCH);
        helper.assertTrue(stages.size() == 4, "attune ×2, blend, proof: " + stages);
        helper.assertTrue(Meditation.start(player), "meditating");
        // Attune to fire (the first part): nothing moves without a flame.
        helper.assertFalse(FusionTrial.study(player, data, 10f, 0.99f), "no flame, no progress");
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.CAMPFIRE);
        helper.assertTrue(FusionTrial.study(player, data, 10f, 0.99f), "by a flame it moves");
        near(helper, 10f, data.arts().trialProgress(), "10 s attuned");
        FusionTrial.study(player, data, FusionTrial.ATTUNE_SECONDS, 0.99f);
        helper.assertTrue(data.arts().trialStage() == 1, "fire attuned, now wind");
        helper.assertFalse(FusionTrial.study(player, data, 10f, 0.99f), "wind needs a leaf in hand");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_LEAVES));
        FusionTrial.study(player, data, FusionTrial.ATTUNE_SECONDS, 0.99f);
        helper.assertTrue(data.arts().trialStage() == 2, "now the blend");
        helper.assertTrue(FusionTrial.inSetting(player, data.arts()), "flame and leaf at once: the blend's setting");
        for (int i = 0; i < 10 && data.arts().trialStage() == 2; i++) {
            FusionTrial.study(player, data, FusionTrial.CHECKPOINT_SECONDS, 0.99f); // a high roll: no failure
        }
        helper.assertTrue(data.arts().trialStage() == 3 && data.arts().trialFails() == 0, "blended without a failure");
        // The proof: a fire jutsu and a wind jutsu close together, five times.
        helper.assertFalse(FusionTrial.study(player, data, 100f, 0.99f), "the proof isn't meditation");
        for (int i = 0; i < FusionTrial.PROOF_WEAVES; i++) {
            FusionTrial.onCast(player, data, JutsuData.get(FIREBALL));
            helper.assertFalse(data.identity().affinity(Nature.SCORCH) > 0f && i < FusionTrial.PROOF_WEAVES - 1, "one part alone isn't a weave");
            FusionTrial.onCast(player, data, JutsuData.get(AIR_PALM));
        }
        helper.assertTrue(data.arts().trialNature() == null, "the trial is over");
        near(helper, Clan.SECONDARY_AFFINITY, data.identity().affinity(Nature.SCORCH), "scorch at the weaker part's strength");
        for (ResourceLocation starter : JutsuData.startersFor(Nature.SCORCH)) {
            helper.assertTrue(data.jutsu().knows(starter), "scorch's starter is learned: " + starter);
        }
        Meditation.stop(player);
        helper.succeed();
    }

    /** A failed blend: half of it lost, 30% of max health and half the chakra burnt; never lethal. */
    @GameTest(template = "empty", batch = "noirm_cw2_fusion_fail")
    public static void fusionTrialCanFail(GameTestHelper helper) {
        FakePlayer player = ninja(helper, "noirm_failer", Nature.FIRE, Nature.WIND);
        ShinobiData data = data(player);
        data.setControl(FusionTrial.MIN_CTL);
        FusionTrial.begin(player, data, Nature.SCORCH);
        data.arts().setTrialStage(2); // the blend
        data.arts().setTrialProgress(FusionTrial.CHECKPOINT_SECONDS - 10f);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.CAMPFIRE);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_LEAVES));
        helper.assertTrue(Meditation.start(player), "meditating");
        float health = player.getHealth();
        float chakra = data.getChakra();
        float chance = FusionTrial.failChance(data);
        helper.assertTrue(chance >= FusionTrial.FAIL_MIN && chance <= FusionTrial.FAIL_MAX, "a sane failure chance: " + chance);
        FusionTrial.study(player, data, 20f, 0f); // crosses a checkpoint with the worst roll
        helper.assertTrue(data.arts().trialFails() == 1, "it failed");
        near(helper, (FusionTrial.CHECKPOINT_SECONDS - 10f) * (1f - FusionTrial.FAIL_PROGRESS_LOSS), data.arts().trialProgress(), "progress lost");
        near(helper, health - player.getMaxHealth() * FusionTrial.FAIL_HEALTH, player.getHealth(), "health burnt");
        near(helper, chakra * (1f - FusionTrial.FAIL_CHAKRA), data.getChakra(), "chakra burnt");
        // Never the last point of health.
        player.setHealth(2f);
        FusionTrial.fail(player, data);
        helper.assertTrue(player.getHealth() >= 1f, "a failure never kills");
        // A high roll passes the next checkpoint.
        data.arts().setTrialProgress(FusionTrial.CHECKPOINT_SECONDS * 2 - 5f);
        FusionTrial.study(player, data, 10f, 0.99f);
        helper.assertTrue(data.arts().trialFails() == 2 && data.arts().trialProgress() > FusionTrial.CHECKPOINT_SECONDS * 2, "survived the checkpoint");
        helper.assertTrue(FusionTrial.abandon(player, data) && data.arts().trialNature() == null, "give up");
        Meditation.stop(player);
        helper.succeed();
    }

    // ================================================================ shapes

    /** Three stages while holding the focus item; the canon boost; shape + nature fusion in the nature's setting. */
    @GameTest(template = "empty", batch = "noirm_cw2_shape")
    public static void shapeTrainingAndFusion(GameTestHelper helper) {
        FakePlayer player = ninja(helper, "noirm_shaper", Nature.WIND);
        ShinobiData data = data(player);
        data.setMaxChakra(5000f);
        data.setChakra(5000f);
        helper.assertTrue(Meditation.start(player), "meditating");
        helper.assertTrue(ShapeTraining.study(player, data, 10f).shape() < 0, "no focus item: nothing moves");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChakraShape.BALL.focus()));
        helper.assertTrue(ShapeTraining.study(player, data, 10f).shape() == ChakraShape.BALL.ordinal(), "the ball moves");
        near(helper, 10f, data.arts().progress(ChakraShape.BALL), "10 s of Form");
        ShapeTraining.study(player, data, ShapeTraining.STAGE_SECONDS[0]);
        helper.assertTrue(data.arts().stage(ChakraShape.BALL) == 1, "Form done");
        float chakra = data.getChakra();
        ShapeTraining.study(player, data, 10f);
        near(helper, chakra - ShapeTraining.STAGE_DRAIN[1] * 10f, data.getChakra(), "Power pours chakra in");
        // The Power stage also counts canon jutsu of the shape.
        float before = data.arts().progress(ChakraShape.BALL);
        ShapeTraining.onCast(player, data, JutsuData.get(Noirm.id("universal/rasengan")));
        near(helper, before + ShapeTraining.POWER_CAST_SECONDS, data.arts().progress(ChakraShape.BALL), "a Rasengan counts in the Power stage");
        ShapeTraining.study(player, data, ShapeTraining.STAGE_SECONDS[1]);
        helper.assertTrue(data.arts().stage(ChakraShape.BALL) == 2, "Power done");
        data.setChakra(data.getMaxChakra() * 0.4f);
        helper.assertTrue(ShapeTraining.study(player, data, 10f).shape() < 0, "containment needs chakra above half");
        data.setChakra(data.getMaxChakra());
        data.setControl(40f);
        ShapeTraining.study(player, data, 10f);
        near(helper, 10f * ShapeTraining.CONTAIN_MAX_PACE, data.arts().progress(ChakraShape.BALL), "CTL 40 contains twice as fast");
        ShapeTraining.study(player, data, ShapeTraining.STAGE_SECONDS[2]);
        helper.assertTrue(data.arts().stage(ChakraShape.BALL) == ShapeTraining.STAGES, "the ball is mastered");
        // Canon jutsu of the shape: stronger and cheaper.
        JutsuDefinition rasengan = JutsuData.get(Noirm.id("universal/rasengan"));
        near(helper, 1f + 3 * ShapeTraining.CANON_POWER_PER_STAGE, ShapeTraining.canonPower(data, rasengan), "Rasengan power with the ball");
        near(helper, 1f - 3 * ShapeTraining.CANON_COST_CUT_PER_STAGE, ShapeTraining.canonCost(data, rasengan), "Rasengan cost with the ball");
        near(helper, 1f, ShapeTraining.canonPower(data, JutsuData.get(AIR_PALM)), "a burst isn't a ball");
        // Shape + nature fusion: wind needs ★3 and its setting (a leaf in hand).
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_LEAVES));
        helper.assertTrue(ShapeTraining.study(player, data, 10f).pair() < 0, "wind not mastered yet: no fusion");
        master(data, AIR_PALM, ShapeTraining.FUSION_NATURE_MASTERY);
        ShapeTraining.Moved moved = ShapeTraining.study(player, data, 10f);
        helper.assertTrue(moved.pair() == com.noirm.arts.ArtsData.pair(ChakraShape.BALL, Nature.WIND), "ball + wind moves");
        ShapeTraining.study(player, data, ShapeTraining.FUSION_SECONDS);
        helper.assertTrue(data.arts().fused(ChakraShape.BALL, Nature.WIND), "a wind ball (a Wind Rasengan)");
        // The client hears about it.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ArtsSync.of(data).encode(buf);
        ArtsSync sync = ArtsSync.decode(buf);
        helper.assertTrue(sync.stage(ChakraShape.BALL) == 3 && sync.fused(ChakraShape.BALL, Nature.WIND), "synced");
        Meditation.stop(player);
        helper.succeed();
    }

    /** Yin (WIL, darkness or mind practice), Yang (Vitality, sun or body training), Yin-Yang once both are held. */
    @GameTest(template = "empty", batch = "noirm_cw2_spirit")
    public static void yinYangAlone(GameTestHelper helper) {
        FakePlayer player = ninja(helper, "noirm_spirit", Nature.FIRE);
        ShinobiData data = data(player);
        helper.assertTrue(NatureDrills.DRILLABLE.containsAll(List.of(Nature.YIN, Nature.YANG, Nature.YIN_YANG)), "drills for the spirit natures");
        helper.assertTrue(NatureDrills.pace(player, data, Nature.YIN).rate() == 0f, "WIL 10: Yin's gate is shut");
        data.setWillpower(SpiritPaths.YIN_MIN_WIL);
        BlockPos head = BlockPos.containing(player.getEyePosition());
        boolean dark = helper.getLevel().getMaxLocalRawBrightness(head) <= SpiritPaths.DARK_LIGHT;
        helper.assertTrue(NatureDrills.inSetting(player, Nature.YIN) == dark, "darkness is Yin's setting");
        data.arts().noteMind(helper.getLevel().getGameTime()); // mind practice: a genjutsu just cast or broken
        helper.assertTrue(NatureDrills.inSetting(player, Nature.YIN), "right after mind practice");
        helper.assertTrue(NatureDrills.pace(player, data, Nature.YIN).rate() > 0f, "WIL 20: Yin moves");
        boolean sunny = helper.getLevel().isDay() && helper.getLevel().canSeeSky(head);
        helper.assertTrue(NatureDrills.inSetting(player, Nature.YANG) == sunny, "open sun is Yang's setting");
        SpiritPaths.noteBody(player);
        helper.assertTrue(NatureDrills.inSetting(player, Nature.YANG), "right after body training");
        helper.assertTrue(NatureDrills.pace(player, data, Nature.YANG).rate() == 0f, "but Vitality 10 shuts Yang's gate");
        data.setVitality(SpiritPaths.STRONG_STAT);
        near(helper, SpiritPaths.STRONG_PACE, NatureDrills.pace(player, data, Nature.YANG).rate(), "a strong body: faster");
        // Learn both.
        helper.assertTrue(Meditation.start(player), "meditating");
        helper.assertTrue(NatureDrills.choose(player, data, Nature.YIN), "pick Yin");
        NatureDrills.setShare(player, data, Nature.YIN, 0.999f);
        helper.assertTrue(NatureDrills.study(player, data, 10f) == Nature.YIN, "Yin learned");
        NatureDrills.setShare(player, data, Nature.YANG, 1f);
        helper.assertTrue(data.identity().affinity(Nature.YANG) > 0f, "Yang learned");
        helper.assertFalse(NatureDrills.choose(player, data, Nature.YIN_YANG), "Yin-Yang needs a jutsu of each at ★2");
        for (ResourceLocation id : JutsuData.allFor(Nature.YIN)) {
            master(data, id, 2);
            break;
        }
        for (ResourceLocation id : JutsuData.allFor(Nature.YANG)) {
            master(data, id, 2);
            break;
        }
        helper.assertTrue(NatureSystem.status(data, Nature.YIN_YANG) == NatureSystem.Status.AVAILABLE, "Yin-Yang available");
        helper.assertTrue(NatureSystem.unlock(player, data, Nature.YIN_YANG), "begins the Yin-Yang drill");
        helper.assertTrue(NatureDrills.inSetting(player, Nature.YIN_YANG), "anywhere, once both are held");
        NatureDrills.setShare(player, data, Nature.YIN_YANG, 0.999f);
        helper.assertTrue(NatureDrills.study(player, data, 10f) == Nature.YIN_YANG, "Yin-Yang learned");
        near(helper, Clan.TRAINED_AFFINITY, data.identity().affinity(Nature.YIN_YANG), "as strong as the weaker half");
        Meditation.stop(player);
        helper.succeed();
    }

    // ================================================================ the workshop

    /** Cost, rank, cooldown and seals come from the choices, the same every time; the shapes cast with real behaviours. */
    @GameTest(template = "empty", batch = "noirm_cw2_numbers")
    public static void workshopNumbers(GameTestHelper helper) {
        JutsuDesign mid = new JutsuDesign(UUID.randomUUID(), "Spiral Flame", Nature.FIRE, ChakraShape.BALL, 3, 3, 3, 3, null, "");
        Workshop.Stats s = Workshop.stats(mid);
        helper.assertTrue(s.weight() == 15, "weight 3+3+2×3+3 = 15, got " + s.weight());
        helper.assertTrue("A".equals(s.rank()), "weight 15 is A rank, got " + s.rank());
        near(helper, Workshop.cost(15), s.cost(), "cost");
        helper.assertTrue(s.cost() > 25f && s.cost() < 35f, "an A-rank cost like canon (25-35): " + s.cost());
        helper.assertTrue(s.cooldown() == Workshop.cooldown(15, 3), "cooldown");
        helper.assertTrue(s.seals().size() == 4 && "tiger".equals(s.seals().get(0)) && "ram".equals(s.seals().get(3)),
                "4 seals, opening with fire's tiger, closing with the ball's ram: " + s.seals());
        JutsuDesign same = new JutsuDesign(UUID.randomUUID(), "Other", Nature.FIRE, ChakraShape.BALL, 3, 3, 3, 3, null, "");
        helper.assertTrue(Workshop.stats(same).seals().equals(s.seals()), "the same choices give the same seals");
        helper.assertTrue(s.cast() == CastType.CHARGE && "rasengan".equals(s.behavior()), "a ball is charged like the Rasengan");
        helper.assertTrue(s.backlash() == 0f, "no backlash at weight 15");
        JutsuDesign light = new JutsuDesign(UUID.randomUUID(), "Spark", Nature.LIGHTNING, ChakraShape.SPEAR, 1, 1, 1, 1, null, "");
        helper.assertTrue(Workshop.weight(light) == 5 && "D".equals(Workshop.stats(light).rank()), "the lightest is D rank");
        helper.assertTrue(Workshop.stats(light).seals().size() == Workshop.SEALS_MIN, "two seals");
        JutsuDesign heavy = new JutsuDesign(UUID.randomUUID(), "Sky Breaker", Nature.WIND, ChakraShape.WAVE, 5, 5, 5, 5, null, "");
        Workshop.Stats h = Workshop.stats(heavy);
        helper.assertTrue("S".equals(h.rank()) && h.seals().size() == Workshop.SEALS_MAX, "the heaviest is S with 7 seals");
        helper.assertTrue(h.cost() > 120f, "a huge chakra cost past canon S: " + h.cost());
        near(helper, (25 - Workshop.BACKLASH_FROM) * Workshop.BACKLASH_PER_POINT, h.backlash(), "backlash");
        helper.assertTrue(h.damage() > 12f, "can beat canon S damage (10-16): " + h.damage());
        for (ChakraShape shape : ChakraShape.values()) {
            JutsuDesign d = new JutsuDesign(UUID.randomUUID(), "T", Nature.WATER, shape, 3, 3, 3, 3, null, "");
            JutsuDefinition def = Workshop.definition(d);
            helper.assertTrue(JutsuBehaviors.exists(def.behavior()), shape + " casts with a real behaviour: " + def.behavior());
            helper.assertFalse(ChakraShape.FORM_BEHAVIORS.contains(def.behavior()), shape + " is never a form");
            helper.assertTrue(def.mode().isEmpty() && def.requires().isEmpty(), shape + ": no mode, no requirement");
        }
        // Registered at runtime: the game sees it as a jutsu, with its seals; data-pack lists never do.
        WorkshopRegistry.register(mid);
        helper.assertTrue(JutsuData.get(mid.jutsuId()) != null && JutsuSeals.get(mid.jutsuId()) != null, "a runtime jutsu");
        helper.assertFalse(JutsuData.all().containsKey(mid.jutsuId()), "not in the data-pack list (NPC sets, catalog, scrolls)");
        helper.assertTrue("Spiral Flame".equals(com.noirm.dojutsu.Sharingan.jutsuName(mid.jutsuId()).getString()), "its own name");
        helper.succeed();
    }

    /** Forms are refused by name; untrained shapes, the shape's stage and the stat budget hold designs back. */
    @GameTest(template = "empty", batch = "noirm_cw2_gates")
    public static void workshopRefusesFormsAndGates(GameTestHelper helper) {
        FakePlayer player = ninja(helper, "noirm_maker", Nature.FIRE);
        ShinobiData data = data(player);
        for (String form : List.of("armor", "armour", "cloak", "avatar", "susanoo", "mode")) {
            Workshop.Made made = Workshop.create(player, data, "Shell", Nature.FIRE, form, 1, 1, 1, 1);
            helper.assertTrue(made.design() == null && made.error() != null
                    && "message.noirm.workshop.why.form".equals(((net.minecraft.network.chat.contents.TranslatableContents) made.error().getContents()).getKey()),
                    "a form is refused: " + form);
        }
        helper.assertTrue(Workshop.create(player, data, "Ball", Nature.FIRE, "ball", 1, 1, 1, 1).design() == null, "an untrained shape");
        ShapeTraining.setStage(data, ChakraShape.BALL, 1);
        helper.assertTrue(Workshop.create(player, data, "Ball", Nature.WATER, "ball", 1, 1, 1, 1).design() == null, "a nature you lack");
        helper.assertTrue(Workshop.create(player, data, " ", Nature.FIRE, "ball", 1, 1, 1, 1).design() == null, "no name");
        helper.assertTrue(Workshop.create(player, data, "Big", Nature.FIRE, "ball", 2, 2, 2, 2).design() == null, "weight 10 > budget 6");
        data.setReserves(30f);
        data.setControl(30f);
        helper.assertTrue(Workshop.create(player, data, "Big", Nature.FIRE, "ball", 3, 3, 3, 3).design() == null, "stage 1 allows C, not A");
        Workshop.Made ok = Workshop.create(player, data, "Ember Orb", Nature.FIRE, "ball", 2, 2, 2, 2);
        helper.assertTrue(ok.design() != null, "a C-rank ball within the budget: " + ok.error());
        helper.assertTrue(data.jutsu().knows(ok.design().jutsuId()) && data.creations().get(ok.design().jutsuId()) != null, "learned and kept");
        ShapeTraining.setStage(data, ChakraShape.BALL, 3);
        data.setReserves(200f);
        data.setControl(200f);
        helper.assertTrue(Workshop.create(player, data, "Sun", Nature.FIRE, "ball", 5, 5, 5, 5).design() == null, "S rank needs the fusion");
        data.arts().setFused(ChakraShape.BALL, Nature.FIRE, true);
        helper.assertTrue(Workshop.create(player, data, "Sun", Nature.FIRE, "ball", 5, 5, 5, 5).design() != null, "fused: S rank");
        helper.succeed();
    }

    /** A new creation misfires (chakra spent, nothing hit, mastery gained); at ★5 it fires; a heavy one backlashes. */
    @GameTest(template = "empty", batch = "noirm_cw2_cast", timeoutTicks = 120)
    public static void castingACreation(GameTestHelper helper) {
        FakePlayer player = ninja(helper, "noirm_caster", Nature.FIRE);
        ShinobiData data = data(player);
        data.setReserves(200f);
        data.setControl(200f);
        data.setMaxChakra(2000f);
        data.setChakra(2000f);
        ShapeTraining.setStage(data, ChakraShape.FIELD, 3);
        data.arts().setFused(ChakraShape.FIELD, Nature.FIRE, true);
        JutsuDesign nova = Workshop.create(player, data, "Ember Ring", Nature.FIRE, "field", 3, 3, 1, 3).design();
        helper.assertTrue(nova != null, "made");
        ResourceLocation id = nova.jutsuId();
        data.jutsu().equip(0, id);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 1f, 2.5f);
        target.setHealth(target.getMaxHealth());
        float full = target.getHealth();
        try {
            Workshop.testRoll = 0f; // the worst roll: misfires whenever it can
            helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "cast");
            for (int i = 0; i < 20 && JutsuCaster.casting(player) != null; i++) {
                JutsuCaster.tick(player);
            }
            helper.assertTrue(data.jutsu().xp(id) == 1, "a misfire is still practice");
            helper.assertTrue(data.getChakra() < 2000f, "the chakra is spent");
            helper.assertTrue(target.getHealth() >= full, "nothing came out");
            helper.assertTrue(data.jutsu().cooldown(id) > 0 && data.jutsu().cooldown(id) <= Workshop.cooldown(Workshop.weight(nova), 3), "a short cooldown");
            // Mastered: it never misfires.
            master(data, id, 5);
            data.jutsu().setCooldown(id, 0);
            helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "cast again");
            for (int i = 0; i < 20 && JutsuCaster.casting(player) != null; i++) {
                JutsuCaster.tick(player);
            }
            helper.assertTrue(target.getHealth() < full || !target.isAlive(), "at ★5 it lands");
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "a light jutsu has no backlash");
            // A heavy one tears at its user.
            JutsuDesign heavy = Workshop.create(player, data, "Inferno Ring", Nature.FIRE, "field", 5, 5, 5, 5).design();
            helper.assertTrue(heavy != null, "a heavy one");
            master(data, heavy.jutsuId(), 5);
            data.jutsu().equip(1, heavy.jutsuId());
            float health = player.getHealth();
            helper.assertTrue(JutsuCaster.onKey(player, 1, true) == JutsuCaster.Result.OK, "cast the heavy one");
            for (int i = 0; i < 20 && JutsuCaster.casting(player) != null; i++) {
                JutsuCaster.tick(player);
            }
            near(helper, health - player.getMaxHealth() * Workshop.backlash(25), player.getHealth(), "backlash");
            // Too heavy for weaker stats: refused before any chakra is spent.
            data.setReserves(10f);
            data.setControl(10f);
            data.jutsu().setCooldown(heavy.jutsuId(), 0);
            helper.assertTrue(JutsuCaster.onKey(player, 1, true) == JutsuCaster.Result.TOO_DEMANDING, "the stat gate holds at cast time");
        } finally {
            Workshop.testRoll = -1f;
            target.discard();
        }
        helper.succeed();
    }

    /** A crew mate learns a creation once you've practised it, and keeps it when saved. */
    @GameTest(template = "empty", batch = "noirm_cw2_teach")
    public static void teachingACrewMate(GameTestHelper helper) {
        FakePlayer player = ninja(helper, "noirm_teacher", Nature.FIRE);
        ShinobiData data = data(player);
        ShapeTraining.setStage(data, ChakraShape.SPEAR, 1);
        JutsuDesign spear = Workshop.create(player, data, "Cinder Lance", Nature.FIRE, "spear", 1, 1, 1, 1).design();
        helper.assertTrue(spear != null, "made");
        SparringNinja ally = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        ally.joinClan(TestClans.FIRE);
        ally.joinCrew(player.getUUID());
        try {
            helper.assertFalse(com.noirm.ally.AllyPackets.teachOrQueue(player, ally, spear.jutsuId()), "practise it first");
            master(data, spear.jutsuId(), Workshop.TEACH_MASTERY);
            helper.assertTrue(com.noirm.ally.AllyPackets.teachOrQueue(player, ally, spear.jutsuId()), "taught");
            helper.assertTrue(ally.knownJutsu().contains(spear.jutsuId()), "they know it");
            CompoundTag saved = ally.saveWithoutId(new CompoundTag());
            helper.assertTrue(saved.getList("Creations", 10).size() == 1, "the design is saved with them");
            helper.assertTrue(JutsuData.all().get(spear.jutsuId()) == null, "never in a random ninja's pool");
        } finally {
            ally.discard();
        }
        helper.succeed();
    }

    /** Writing needs ★2, a blank scroll and ink, and chakra; reading teaches that exact jutsu to whoever owns its nature. */
    @GameTest(template = "empty", batch = "noirm_cw2_scroll")
    public static void writingAndReadingAScroll(GameTestHelper helper) {
        FakePlayer author = ninja(helper, "noirm_author", Nature.WIND);
        ShinobiData data = data(author);
        ShapeTraining.setStage(data, ChakraShape.BLADE, 1);
        JutsuDesign blade = Workshop.create(author, data, "Paper Cut", Nature.WIND, "blade", 1, 2, 1, 1).design();
        helper.assertTrue(blade != null, "made");
        helper.assertTrue(Workshop.writeScroll(author, data, blade.jutsuId()).error() != null, "★0: too soon");
        master(data, blade.jutsuId(), Workshop.WRITE_MASTERY);
        helper.assertTrue(Workshop.writeScroll(author, data, blade.jutsuId()).error() != null, "no blank scroll or ink");
        author.getInventory().add(new ItemStack(WorkshopItems.BLANK_SCROLL.get()));
        author.getInventory().add(new ItemStack(Items.INK_SAC));
        float chakra = data.getChakra();
        Workshop.Written written = Workshop.writeScroll(author, data, blade.jutsuId());
        helper.assertTrue(written.error() == null, "written: " + written.error());
        helper.assertTrue(data.getChakra() < chakra, "it took chakra");
        helper.assertFalse(author.getInventory().contains(new ItemStack(Items.INK_SAC)), "the ink is used");
        ItemStack scroll = written.scroll();
        helper.assertTrue(blade.equals(WorkshopItems.CreationScrollItem.design(scroll)), "the scroll holds that exact design");
        // Readers.
        FakePlayer stranger = ninja(helper, "noirm_stranger", Nature.FIRE);
        helper.assertFalse(Workshop.readScroll(stranger, data(stranger), blade), "no wind: can't read it");
        FakePlayer reader = ninja(helper, "noirm_reader", Nature.WIND);
        reader.setItemInHand(InteractionHand.MAIN_HAND, scroll.copy());
        reader.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), reader, InteractionHand.MAIN_HAND);
        ShinobiData rd = data(reader);
        helper.assertTrue(rd.jutsu().knows(blade.jutsuId()), "the reader learned it");
        helper.assertTrue(blade.equals(rd.creations().get(blade.jutsuId())), "the same design, author and all");
        helper.assertTrue(reader.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the scroll is used up");
        helper.assertTrue(Workshop.writeScroll(reader, rd, blade.jutsuId()).error() != null, "only its author writes it");
        helper.succeed();
    }

    /** Shapes, fusions, the trial and creations survive saving and reach the client in the powers sync. */
    @GameTest(template = "empty", batch = "noirm_cw2_save")
    public static void saveAndSync(GameTestHelper helper) {
        FakePlayer player = ninja(helper, "noirm_saver", Nature.FIRE, Nature.WIND);
        ShinobiData data = data(player);
        ShapeTraining.setStage(data, ChakraShape.WAVE, 2);
        data.arts().setProgress(ChakraShape.WAVE, 42f);
        data.arts().setFused(ChakraShape.BALL, Nature.FIRE, true);
        data.arts().setPairProgress(com.noirm.arts.ArtsData.pair(ChakraShape.WAVE, Nature.WIND), 77f);
        FusionTrial.begin(player, data, Nature.SCORCH);
        data.arts().setTrialStage(2);
        data.arts().setTrialProgress(123f);
        data.arts().addTrialFail();
        JutsuDesign d = new JutsuDesign(UUID.randomUUID(), "Tide Breaker", Nature.FIRE, ChakraShape.WAVE, 2, 3, 4, 5, player.getUUID(), "noirm_saver");
        Workshop.learnDesign(data, d);
        ShinobiData copy = new ShinobiData();
        copy.load(data.save());
        helper.assertTrue(copy.arts().stage(ChakraShape.WAVE) == 2, "stage");
        near(helper, 42f, copy.arts().progress(ChakraShape.WAVE), "stage progress");
        helper.assertTrue(copy.arts().fused(ChakraShape.BALL, Nature.FIRE), "fusion");
        near(helper, 77f, copy.arts().pairProgress(com.noirm.arts.ArtsData.pair(ChakraShape.WAVE, Nature.WIND)), "fusion progress");
        helper.assertTrue(copy.arts().trialNature() == Nature.SCORCH && copy.arts().trialStage() == 2 && copy.arts().trialFails() == 1, "the trial");
        near(helper, 123f, copy.arts().trialProgress(), "trial progress");
        helper.assertTrue(d.equals(copy.creations().get(d.jutsuId())) && copy.jutsu().knows(d.jutsuId()), "the creation");
        // The powers sync carries it all.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PowerPackets.SyncPowers.of(player, data).encode(buf);
        PowerPackets.SyncPowers sync = PowerPackets.SyncPowers.decode(buf);
        helper.assertTrue(sync.creations().size() == 1 && d.equals(sync.creations().get(0)), "creations synced");
        helper.assertTrue(sync.arts().stage(ChakraShape.WAVE) == 2 && sync.arts().trialNature() == Nature.SCORCH.ordinal(), "arts synced");
        helper.assertTrue(sync.learned().stream().anyMatch(e -> e.id().equals(d.jutsuId())), "the creation is in the J screen's list");
        helper.succeed();
    }
}

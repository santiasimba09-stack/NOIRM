package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.clan.ClanSystem;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.CanonTechniques;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.registry.NoirmItems;
import com.noirm.training.ChakraDrills;
import com.noirm.training.CloneTraining;
import com.noirm.training.Supplementary;
import com.noirm.training.TrainingSystem;
import com.noirm.training.WeightedTraining;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The clones-and-training wave: canon shadow clones, clone training and fatigue, weights, chakra drills. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CloneWaveGameTests {
    /** Clones are full fighters: keep each clone test in its own batch. */
    private static final String BATCH = "noirm_clone_wave";

    private CloneWaveGameTests() {}

    @GameTest(template = "empty", batch = BATCH + "_split")
    public static void chakraIsSplitEvenlyAndComesBack(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper, plain());
        ShinobiData data = data(player);
        data.setChakra(90f);
        float before = data.getChakra();
        cast(player, "universal/shadow_clone");
        List<ShadowClone> clones = clones(helper, player);
        helper.assertTrue(clones.size() == 2, "two clones, found " + clones.size());
        float share = before / 3f;
        helper.assertTrue(Math.abs(data.getChakra() - share) < 0.5f, "you keep a third: " + data.getChakra());
        ShadowClone clone = clones.get(0);
        helper.assertTrue(Math.abs(clone.chakra() - share) < 0.5f, "each clone gets a third: " + clone.chakra());
        clone.tryUseChakra(10f);
        for (int i = 0; i < 40; i++) {
            clone.aiStep();
        }
        helper.assertTrue(clone.chakra() <= share - 9.9f, "a clone's chakra doesn't come back on its own: " + clone.chakra());
        float mine = data.getChakra();
        clone.dispel();
        helper.assertTrue(data.getChakra() >= mine + share - 10.5f, "what it didn't spend comes home: " + data.getChakra());
        clones.forEach(ShadowClone::dispel);
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = BATCH + "_memories")
    public static void memoriesReturnAndTireTheMind(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper, plain());
        ShinobiData data = data(player);
        cast(player, "universal/shadow_clone");
        List<ShadowClone> clones = clones(helper, player);
        helper.assertFalse(clones.isEmpty(), "clones appear");
        ShadowClone clone = clones.get(0);
        clone.memories().add(TrainingSystem.Stat.TAIJUTSU, 40f);
        clone.onJutsuCast(Noirm.id("universal/shadow_clone"));
        float taiBefore = data.training().xp(TrainingSystem.Stat.TAIJUTSU) + data.getTaijutsu() * 1000;
        clone.dispel();
        float taiAfter = data.training().xp(TrainingSystem.Stat.TAIJUTSU) + data.getTaijutsu() * 1000;
        helper.assertTrue(taiAfter > taiBefore, "the clone's taijutsu practice comes back to you");
        helper.assertTrue(CloneTraining.fatigue(player) > CloneTraining.FATIGUE_PER_CLONE, "and tires your mind: " + CloneTraining.fatigue(player));
        // Training alongside you: every idle clone banks a share of what you learn.
        ShadowClone other = clones.get(1);
        float banked = other.memories().xp(TrainingSystem.Stat.SPEED);
        TrainingSystem.grant(player, TrainingSystem.Stat.SPEED, 10f);
        helper.assertTrue(other.memories().xp(TrainingSystem.Stat.SPEED) >= banked + 10f * CloneTraining.CLONE_TRAIN_SHARE - 0.01f,
                "an idle clone trains alongside you");
        // Fatigue: tired minds learn less and regen slower; an exhausted one can't make clones.
        CloneTraining.setFatigue(player, CloneTraining.EXHAUSTED_AT + 1);
        helper.assertTrue(CloneTraining.regenMultiplier(data) == CloneTraining.EXHAUSTED_REGEN, "exhausted: slow regen");
        helper.assertTrue(CloneTraining.memoryFactor(player) == CloneTraining.EXHAUSTED_MEMORY, "and memories sink in less");
        clones.forEach(ShadowClone::dispel);
        CloneTraining.setFatigue(player, CloneTraining.NO_CLONES_AT + 1);
        data.setChakra(data.getMaxChakra());
        cast(player, "universal/shadow_clone");
        helper.assertTrue(clones(helper, player).isEmpty(), "too tired for another clone");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = BATCH + "_combos")
    public static void clonesFeintSwapAndShapeTheRasengan(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper, plain());
        cast(player, "universal/shadow_clone");
        List<ShadowClone> clones = clones(helper, player);
        helper.assertTrue(clones.size() >= 2, "clones appear");
        helper.assertTrue(ShadowClone.comboPower(player, Noirm.id("universal/rasengan")) == ShadowClone.RASENGAN_CLONE_BONUS,
                "a clone beside you helps shape the Rasengan");
        helper.assertTrue(ShadowClone.comboPower(player, Noirm.id("katon/great_fireball")) == 1f, "only the Rasengan");
        // Clone Swap: you trade places.
        ShadowClone swap = clones.get(0);
        Vec3 mine = player.position();
        Vec3 theirs = swap.position();
        cast(player, "universal/clone_swap");
        helper.assertTrue(player.position().distanceTo(mine) > 0.5 || swap.position().distanceTo(theirs) > 0.5, "someone moved");
        // A feint: an enemy that strikes a clone down is left wide open.
        SparringNinja enemy = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 2.5f);
        enemy.joinClan(TestClans.PLAIN);
        ShadowClone bait = clones.get(1);
        bait.hurt(bait.damageSources().mobAttack(enemy), ShadowClone.SOLID_HIT + 2f);
        helper.assertTrue(bait.isRemoved(), "the clone pops");
        helper.assertTrue(CombatSystem.isStaggered(enemy), "and the attacker reels from the feint");
        clones.forEach(ShadowClone::dispel);
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void bigPoolsMakeACrowd(GameTestHelper helper) {
        Player plain = player(helper, plain());
        Player uzumaki = player(helper, ClanData.canon("uzumaki").orElseThrow(IllegalStateException::new));
        helper.assertTrue(CanonTechniques.maxClones(plain, 24) == ShadowClone.MAX_PER_OWNER, "a normal pool: no crowd");
        helper.assertTrue(CanonTechniques.maxClones(uzumaki, 24) == ShadowClone.MULTI_MAX, "an Uzumaki: " + CanonTechniques.maxClones(uzumaki, 24));
        helper.assertTrue(CanonTechniques.maxClones(uzumaki, 2) == ShadowClone.MAX_PER_OWNER + ShadowClone.bonusClones(uzumaki),
                "the plain jutsu still has its usual cap");
        helper.assertTrue(JutsuData.get(Noirm.id("universal/multi_shadow_clone")) != null, "the Multi Shadow Clone Jutsu exists");
        helper.assertTrue(JutsuData.get(Noirm.id("universal/clone_swap")) != null, "Clone Swap exists");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void weightsSlowYouButTeachMore(GameTestHelper helper) {
        Player player = player(helper, plain());
        helper.assertTrue(WeightedTraining.xpMultiplier(player, TrainingSystem.Stat.SPEED) == 1f, "no weights: normal XP");
        player.getInventory().add(new ItemStack(NoirmItems.TRAINING_WEIGHTS.get()));
        helper.assertTrue(WeightedTraining.xpMultiplier(player, TrainingSystem.Stat.SPEED) == WeightedTraining.XP_MULTIPLIER, "weights: more speed XP");
        helper.assertTrue(WeightedTraining.xpMultiplier(player, TrainingSystem.Stat.RESERVES) == 1f, "but not chakra XP");
        double before = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        player.tickCount = 10;
        WeightedTraining.tick(player, data(player));
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < before, "and slow you down");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH + "_drills")
    public static void leafFocusAndTreeClimbingTrainControl(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper, plain());
        ShinobiData data = data(player);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LEAVES));
        player.setShiftKeyDown(true);
        float before = data.training().xp(Supplementary.Stat.CONTROL) + data.getControl() * 1000;
        for (int i = 0; i <= ChakraDrills.LEAF_INTERVAL + 1; i++) {
            ChakraDrills.tick(player, data);
        }
        helper.assertTrue(data.training().xp(Supplementary.Stat.CONTROL) + data.getControl() * 1000 > before, "a still mind with a leaf trains CTL");
        // Tree climbing: crouching into a trunk counts as a ladder.
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.OAK_LOG);
        Vec3 at = helper.absoluteVec(new Vec3(2.0 - player.getBbWidth() / 2, 1, 1.5));
        player.setPos(at.x, at.y, at.z);
        player.horizontalCollision = true;
        data.setChakra(data.getMaxChakra());
        helper.assertTrue(ChakraDrills.climbing(player), "crouching into a trunk with chakra: climbing");
        helper.assertTrue(player.onClimbable(), "the game treats it as a ladder");
        data.setChakra(0f);
        helper.assertFalse(ChakraDrills.climbing(player), "no chakra, no grip");
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    private static Clan plain() {
        return TestClans.PLAIN;
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static Player player(GameTestHelper helper, Clan clan) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z);
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, clan);
        data.talents().set(List.of());
        TrainingSystem.applyReserves(data);
        com.noirm.traits.PerkEvents.tick(player, data);
        CloneTraining.setFatigue(player, 0f);
        return player;
    }

    private static void cast(LivingEntity caster, String path) {
        ResourceLocation id = Noirm.id(path);
        var def = JutsuData.get(id);
        JutsuBehaviors.get(def.behavior()).cast(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), 1f));
    }

    private static List<ShadowClone> clones(GameTestHelper helper, LivingEntity owner) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(8);
        return helper.getLevel().getEntitiesOfClass(ShadowClone.class, area, c -> c.isOwnedBy(owner) && c.isAlive());
    }
}

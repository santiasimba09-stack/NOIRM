package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.biju.BijuSystem;
import com.noirm.biju.SealingScrollItem;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TailedBeast;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.registry.NoirmItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Tests for tailed beasts: size, subduing, sealing scrolls, born and NPC jinchūriki. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BijuGameTests {
    /** Beasts are big (a Nine-Tails shoves its neighbours) and swing wide: keep them away from other tests. */
    private static final String BEAST_BATCH = "noirm_beasts";

    private BijuGameTests() {}

    @GameTest(template = "empty", batch = BEAST_BATCH)
    public static void moreTailsMeansABiggerBeast(GameTestHelper helper) {
        floor(helper);
        TailedBeast one = helper.spawnWithNoFreeWill(NoirmEntities.TAILED_BEAST.get(), 1.5f, 1f, 1.5f);
        TailedBeast nine = helper.spawnWithNoFreeWill(NoirmEntities.TAILED_BEAST.get(), 1.5f, 1f, 1.5f);
        one.setTails(1);
        nine.setTails(9);
        helper.assertTrue(nine.getBbWidth() > one.getBbWidth() * 2, "the Nine-Tails should dwarf the One-Tails");
        helper.assertTrue(nine.getMaxHealth() == TailedBeast.healthFor(9) && nine.getHealth() == nine.getMaxHealth(), "health follows the tails");
        helper.assertTrue(nine.combatTaijutsu() > one.combatTaijutsu(), "more tails, stronger blows");
        cleanUp(helper);
    }

    @GameTest(template = "empty", batch = BEAST_BATCH)
    public static void beastsAreSubduedThenSealedOrKilled(GameTestHelper helper) {
        floor(helper);
        TailedBeast beast = helper.spawnWithNoFreeWill(NoirmEntities.TAILED_BEAST.get(), 1.5f, 1f, 1.5f);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        ninja.joinClan(TestClans.PLAIN);
        beast.setTails(2);
        for (int i = 0; i < 20 && !beast.isSubdued(); i++) {
            beast.invulnerableTime = 0;
            beast.hurt(beast.damageSources().mobAttack(ninja), 80f);
        }
        helper.assertTrue(beast.isSubdued(), "beaten down, the beast should be subdued");
        helper.assertTrue(beast.isAlive(), "beating it down doesn't kill it");
        for (int i = 0; i < 20 && beast.isAlive(); i++) {
            beast.invulnerableTime = 0;
            beast.hurt(beast.damageSources().mobAttack(ninja), 80f);
        }
        helper.assertFalse(beast.isAlive(), "but finishing blows on a subdued beast kill it");
        cleanUp(helper);
    }

    @GameTest(template = "empty", batch = BEAST_BATCH)
    public static void scrollsCaptureOnlySubduedBeasts(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        TailedBeast beast = helper.spawnWithNoFreeWill(NoirmEntities.TAILED_BEAST.get(), 1.5f, 1f, 1.5f);
        beast.setTails(3);
        ItemStack scroll = new ItemStack(NoirmItems.SEALING_SCROLL.get());
        helper.assertFalse(SealingScrollItem.capture(player, scroll, beast), "a fighting beast can't be sealed");
        helper.assertTrue(SealingScrollItem.tails(scroll) == 0, "the scroll stays empty");
        beast.subdue();
        helper.assertTrue(SealingScrollItem.capture(player, scroll, beast), "a subdued beast is sealed");
        helper.assertTrue(SealingScrollItem.tails(scroll) == 3, "the scroll now holds the Three-Tails");
        helper.assertTrue(beast.isRemoved(), "the beast is gone into the scroll");
        cleanUp(helper);
    }

    @GameTest(template = "empty")
    public static void scrollsSealIntoOthersAndYourself(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        ninja.joinClan(TestClans.PLAIN);

        ItemStack forNinja = new ItemStack(NoirmItems.SEALING_SCROLL.get());
        SealingScrollItem.setTails(forNinja, 4);
        helper.assertTrue(SealingScrollItem.sealInto(player, forNinja, ninja), "seal into a ninja");
        helper.assertTrue(ninja.tails() == 4, "the ninja is now the Four-Tails' jinchūriki");
        helper.assertTrue(ninja.knownJutsu().contains(BijuSystem.BEAST_BALL), "jinchūriki ninjas can throw a Tailed Beast Ball");
        helper.assertTrue(forNinja.isEmpty(), "the scroll is used up");

        ItemStack forMe = new ItemStack(NoirmItems.SEALING_SCROLL.get());
        SealingScrollItem.setTails(forMe, 7);
        helper.assertTrue(SealingScrollItem.sealInto(player, forMe, player), "seal into yourself");
        helper.assertTrue(data.biju().tails() == 7, "you are now the Seven-Tails' jinchūriki");
        helper.assertTrue(data.jutsu().knows(BijuSystem.BEAST_BALL), "and you learn the Tailed Beast Ball");

        ItemStack another = new ItemStack(NoirmItems.SEALING_SCROLL.get());
        SealingScrollItem.setTails(another, 2);
        helper.assertFalse(SealingScrollItem.sealInto(player, another, player), "one beast per host");
        cleanUp(helper);
    }

    @GameTest(template = "empty")
    public static void beastBallNeedsTheCloak(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        BijuSystem.seal(player, data, 9);
        var def = JutsuData.get(BijuSystem.BEAST_BALL);
        helper.assertTrue(def != null, "Tailed Beast Ball data should load");
        helper.assertTrue(JutsuCaster.checkRequirements(player, BijuSystem.BEAST_BALL, def) == JutsuCaster.Result.NEEDS_CLOAK, "not without the cloak");
        data.biju().setCloak(1, 100);
        helper.assertTrue(JutsuCaster.checkRequirements(player, BijuSystem.BEAST_BALL, def) == JutsuCaster.Result.OK, "fine in the cloak");
        cleanUp(helper);
    }

    @GameTest(template = "empty")
    public static void someAreBornJinchuriki(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        RandomSource random = RandomSource.create(1);
        helper.assertTrue(BijuSystem.maybeBornJinchuriki(player, data, 0.5f, random) == 0, "most people aren't");
        helper.assertFalse(data.biju().isJinchuriki(), "still no beast");
        int tails = BijuSystem.maybeBornJinchuriki(player, data, 0f, random);
        helper.assertTrue(tails >= 1 && tails <= 9 && data.biju().tails() == tails, "a lucky (or unlucky) few are");
        cleanUp(helper);
    }

    @GameTest(template = "empty", batch = BEAST_BATCH + "_freed")
    public static void aFallenJinchurikisBeastBreaksFree(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        ninja.joinClan(TestClans.PLAIN);
        ninja.sealBeast(5);
        ninja.kill();
        helper.runAfterDelay(2, () -> {
            AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(3);
            List<TailedBeast> beasts = helper.getLevel().getEntitiesOfClass(TailedBeast.class, area);
            helper.assertTrue(beasts.size() == 1, "one beast should break free, found " + beasts.size());
            helper.assertTrue(beasts.get(0).tails() == 5, "the Five-Tails");
            beasts.forEach(TailedBeast::discard);
            cleanUp(helper);
        });
    }

    @GameTest(template = "empty", batch = BEAST_BATCH + "_fight", timeoutTicks = 300)
    public static void beastFightsItsTarget(GameTestHelper helper) {
        floor(helper);
        TailedBeast beast = helper.spawn(NoirmEntities.TAILED_BEAST.get(), 1.5f, 1f, 0.5f);
        SparringNinja target = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 3.5f);
        target.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        target.joinClan(TestClans.PLAIN);
        beast.setTarget(target);
        float start = target.getHealth();
        helper.onEachTick(() -> {
            if (target.getHealth() < start || !target.isAlive()) {
                cleanUp(helper);
            }
        });
    }

    /**
     * Beasts can be bigger than the test area, so the framework doesn't clear them: remove them
     * (and any beast a test released) ourselves, then pass.
     */
    private static void cleanUp(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(12);
        helper.getLevel().getEntitiesOfClass(TailedBeast.class, area).forEach(TailedBeast::discard);
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }
}

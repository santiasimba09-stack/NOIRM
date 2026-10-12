package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.ModeSystem;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tests for jutsu power scaling, terrain destruction (and its healing) and modes. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DangerGameTests {
    private static final ResourceLocation FIREBALL = Noirm.id("katon/great_fireball");
    private static final ResourceLocation ARMOR = Noirm.id("raiton/lightning_armor");

    private DangerGameTests() {}

    @GameTest(template = "empty")
    public static void chakraAndMasteryMakeJutsuStronger(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        com.noirm.clan.ClanSystem.assign(player, data, TestClans.FIRE);
        float base = JutsuCaster.power(player, FIREBALL, JutsuData.get(FIREBALL));
        data.setReserves(30);
        float withChakra = JutsuCaster.power(player, FIREBALL, JutsuData.get(FIREBALL));
        helper.assertTrue(Math.abs(withChakra / base - 1.4f) < 0.01f, "reserves 30 = 1.4× power, got " + withChakra / base);
        data.jutsu().addXp(FIREBALL, 30); // mastery 2
        float mastered = JutsuCaster.power(player, FIREBALL, JutsuData.get(FIREBALL));
        helper.assertTrue(Math.abs(mastered / withChakra - 1.3f) < 0.01f, "mastery 2 = +30%, got " + mastered / withChakra);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_destruction", timeoutTicks = 200)
    public static void cratersHealThemselves(GameTestHelper helper) {
        var rules = helper.getLevel().getGameRules();
        rules.getRule(Destruction.TERRAIN_REGEN).set(1, helper.getLevel().getServer());
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
            }
        }
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.OBSIDIAN);
        Vec3 center = helper.absoluteVec(new Vec3(1.5, 1.5, 1.5));
        int broken = Destruction.crater(helper.getLevel(), center, 2.2f, 1f);
        helper.assertTrue(broken > 0, "the crater should break dirt");
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 1, 1));
        helper.assertBlockPresent(Blocks.CHEST, new BlockPos(2, 1, 2));
        helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(0, 1, 0));
        helper.runAfterDelay(100, () -> {
            helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 1, 1));
            rules.getRule(Destruction.TERRAIN_REGEN).set(60, helper.getLevel().getServer());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "noirm_destruction_off")
    public static void destructionCanBeTurnedOff(GameTestHelper helper) {
        var rules = helper.getLevel().getGameRules();
        rules.getRule(Destruction.TERRAIN_DAMAGE).set(false, helper.getLevel().getServer());
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.DIRT);
        int broken = Destruction.crater(helper.getLevel(), helper.absoluteVec(new Vec3(1.5, 1.5, 1.5)), 2f, 2f);
        rules.getRule(Destruction.TERRAIN_DAMAGE).set(true, helper.getLevel().getServer());
        helper.assertTrue(broken == 0, "nothing breaks with noirmTerrainDamage off");
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 1, 1));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lightningArmorIsAMode(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        com.noirm.clan.ClanSystem.assign(player, data, TestClans.FIRE); // fire + lightning
        data.jutsu().learn(ARMOR, false);
        data.jutsu().equip(0, ARMOR);
        float before = com.noirm.combat.Perception.perception(player);
        helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "switch it on");
        helper.assertTrue(ModeSystem.isActive(player, ARMOR), "the armor is on");
        float after = com.noirm.combat.Perception.perception(player);
        helper.assertTrue(after > before * 1.5f, "it sharpens perception (" + before + " -> " + after + ")");
        helper.assertTrue(ModeSystem.actionSpeed(player) > 1.2f, "and speeds up your moves");
        helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "the same key switches it off");
        helper.assertFalse(ModeSystem.isActive(player, ARMOR), "the armor is off");
        helper.succeed();
    }
}

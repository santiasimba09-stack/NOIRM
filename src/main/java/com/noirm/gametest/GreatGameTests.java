package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.GreatTechniques;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tests for the large-scale techniques: Kirin, waves, Chidori Current. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GreatGameTests {
    private GreatGameTests() {}

    @GameTest(template = "empty", batch = "noirm_kirin", timeoutTicks = 80)
    public static void kirinFallsFromTheSky(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        target.setInvulnerable(false);
        face(caster, target);
        run(caster, "raiton/kirin", 1f);
        helper.assertTrue(target.getHealth() == target.getMaxHealth(), "nothing yet: the storm gathers first");
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth() * 0.7f || !target.isAlive(), "then the bolt falls, health " + target.getHealth());
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_wave", timeoutTicks = 80)
    public static void wavesRollOverAndCarry(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 4.5f, TestClans.PLAIN);
        target.setInvulnerable(false);
        face(caster, target);
        run(caster, "suiton/great_waterfall", 1f);
        helper.assertTrue(GreatTechniques.pending() > 0, "the wave is rolling");
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the wave hits");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_nova")
    public static void chidoriCurrentShocksEveryoneAround(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        SparringNinja front = ninja(helper, 1.5f, 3.5f, TestClans.PLAIN);
        SparringNinja back = ninja(helper, 1.5f, 0f, TestClans.PLAIN);
        front.setInvulnerable(false);
        back.setInvulnerable(false);
        run(caster, "raiton/chidori_current", 1f);
        helper.assertTrue(com.noirm.combat.CombatSystem.isStaggered(front) && com.noirm.combat.CombatSystem.isStaggered(back), "both sides are stunned");
        clearAround(helper);
    }

    @GameTest(template = "empty")
    public static void newJutsuExist(GameTestHelper helper) {
        for (String path : java.util.List.of("raiton/kirin", "raiton/chidori_current", "raiton/chidori_senbon", "katon/great_fire_annihilation",
                "katon/fire_dragon", "suiton/great_waterfall", "doton/earth_dragon", "futon/pressure_damage")) {
            helper.assertTrue(JutsuData.get(Noirm.id(path)) != null, "missing " + path);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    private static void run(LivingEntity caster, String path, float charge) {
        ResourceLocation id = Noirm.id(path);
        var def = JutsuData.get(id);
        JutsuBehaviors.get(def.behavior()).cast(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), charge));
    }

    private static void channel(LivingEntity caster, String path, int tick) {
        ResourceLocation id = Noirm.id(path);
        var def = JutsuData.get(id);
        JutsuBehaviors.get(def.behavior()).channel(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), 1f), tick);
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        ninja.joinClan(clan);
        return ninja;
    }
}

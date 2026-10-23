package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.CanonTechniques;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Tests for the canon techniques with their own mechanics (docs/RESEARCH.md §1). */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CanonGameTests {
    private CanonGameTests() {}

    @GameTest(template = "empty", batch = "noirm_rasengan", timeoutTicks = 60)
    public static void rasenganGrindsThenBlasts(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        target.setInvulnerable(false);
        face(caster, target);
        float start = target.getHealth();
        run(caster, "universal/rasengan", 1f);
        helper.assertTrue(CanonTechniques.isGrinding(caster), "the Rasengan is grinding into the target");
        helper.runAfterDelay(12, () -> {
            helper.assertFalse(CanonTechniques.isGrinding(caster), "and then it bursts");
            helper.assertTrue(target.getHealth() < start - 8, "a Rasengan should hurt a lot, health " + target.getHealth());
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_dome", timeoutTicks = 40)
    public static void waterFormationWallBlocksHits(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        SparringNinja attacker = ninja(helper, 1.5f, 3.5f, TestClans.PLAIN);
        caster.setInvulnerable(false);
        run(caster, "suiton/water_formation_wall", 1f);
        helper.assertTrue(CanonTechniques.hasDome(caster), "the dome is up");
        float before = caster.getHealth();
        caster.hurt(caster.damageSources().mobAttack(attacker), 6f);
        helper.assertTrue(caster.getHealth() == before, "nothing gets through the wall");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_heal")
    public static void mysticalPalmHeals(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        caster.setInvulnerable(false);
        caster.setHealth(10f);
        for (int tick = 0; tick < 20; tick++) {
            channel(caster, "yang/mystical_palm", tick);
        }
        helper.assertTrue(caster.getHealth() > 11f, "a second of Mystical Palm heals, health " + caster.getHealth());
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_shadow_bind", timeoutTicks = 60)
    public static void shadowImitationCatchesTheTarget(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.GLOWSTONE); // shadows need light
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 3.5f, TestClans.PLAIN);
        face(caster, target);
        run(caster, "yin/shadow_imitation", 1f);
        int[] tick = {0};
        helper.onEachTick(() -> channel(caster, "yin/shadow_imitation", tick[0]++));
        helper.succeedWhen(() -> {
            helper.assertTrue(CanonTechniques.isBound(target), "the shadow reaches and holds the target");
            helper.assertTrue(com.noirm.combat.CombatSystem.isStaggered(target), "who can't act");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_clones", timeoutTicks = 40)
    public static void shadowClonesTakeHitsThenVanish(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        run(caster, "universal/shadow_clone", 1f);
        List<ShadowClone> clones = clones(helper, caster);
        helper.assertTrue(clones.size() == 2, "two clones appear, found " + clones.size());
        ShadowClone clone = clones.get(0);
        helper.assertTrue(clone.isOwnedBy(caster), "they belong to the caster");
        helper.assertTrue(clone.getMaxHealth() >= ShadowClone.MIN_HEALTH, "a clone has a real body");
        clone.hurt(clone.damageSources().generic(), 3f);
        helper.assertFalse(clone.isRemoved(), "it takes a hit like the real thing");
        clone.invulnerableTime = 0;
        clone.hurt(clone.damageSources().generic(), 500f);
        helper.assertTrue(clone.isRemoved(), "and a finishing blow dispels it");
        clones.forEach(ShadowClone::dispel);
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_clone_powers", timeoutTicks = 40)
    public static void clonesCarryTheirOwnersPowers(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.FIRE);
        caster.setSharinganStage(2);
        helper.assertTrue(JutsuCaster.npcCast(caster, Noirm.id("raiton/lightning_armor"), 1f), "the owner puts on Lightning Armor");
        run(caster, "universal/shadow_clone", 1f);
        List<ShadowClone> clones = clones(helper, caster);
        helper.assertFalse(clones.isEmpty(), "clones appear");
        ShadowClone clone = clones.get(0);
        helper.assertTrue(clone.activeSharinganStage() == 2, "with the owner's Sharingan");
        helper.assertTrue(com.noirm.jutsu.ModeSystem.isActive(clone, Noirm.id("raiton/lightning_armor")), "and their Lightning Armor");
        helper.assertTrue(clone.knownJutsu().contains(Noirm.id("katon/great_fireball")), "and their jutsu");
        helper.assertFalse(clone.knownJutsu().contains(Noirm.id("universal/shadow_clone")), "but clones don't make clones");
        clones.forEach(ShadowClone::dispel);
        com.noirm.jutsu.ModeSystem.end(caster);
        clearAround(helper);
    }

    @GameTest(template = "empty")
    public static void canonJutsuExist(GameTestHelper helper) {
        for (String path : List.of("katon/great_fireball", "katon/phoenix_flower", "suiton/water_dragon", "suiton/water_formation_wall",
                "doton/earth_wall", "futon/great_breakthrough", "raiton/chidori", "universal/rasengan", "universal/shadow_clone",
                "yin/shadow_imitation")) {
            helper.assertTrue(JutsuData.get(Noirm.id(path)) != null, "missing canon jutsu " + path);
        }
        helper.assertTrue(JutsuData.get(Noirm.id("universal/rasengan")).unlock().flatMap(u -> u.stat()).isPresent(),
                "the Rasengan is learned through chakra control (a stat), not a nature");
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

    private static List<ShadowClone> clones(GameTestHelper helper, LivingEntity owner) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(6);
        return helper.getLevel().getEntitiesOfClass(ShadowClone.class, area, c -> c.isOwnedBy(owner));
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        ninja.joinClan(clan);
        return ninja;
    }
}

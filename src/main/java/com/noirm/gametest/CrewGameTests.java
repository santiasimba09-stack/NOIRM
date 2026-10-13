package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.clan.Clan;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.player.ShinobiCapability;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tests for the crew (allies) and for jutsu never hitting their own caster. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrewGameTests {
    private CrewGameTests() {}

    /** The bug from playtest 3: a big Great Fireball burst on its own caster. */
    @GameTest(template = "empty", batch = "noirm_own_fireball", timeoutTicks = 40)
    public static void bigFireballLeavesTheCaster(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.FIRE);
        caster.setInvulnerable(false);
        caster.setYRot(0);
        caster.setYHeadRot(0);
        caster.setXRot(-20); // aim up, into open sky
        helper.assertTrue(JutsuCaster.npcCast(caster, Noirm.id("katon/great_fireball"), 1f), "cast the Great Fireball");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "the caster is unhurt, health " + caster.getHealth());
            AABB around = caster.getBoundingBox().inflate(20);
            var shots = helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, around);
            helper.assertTrue(!shots.isEmpty() && shots.get(0).distanceTo(caster) > 2.5, "and the fireball is flying away");
            shots.forEach(JutsuProjectile::discard);
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_crew")
    public static void beatenNinjasYieldAndJoin(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        SparringNinja ninja = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        ninja.setInvulnerable(false);
        ninja.setHealth(ninja.getMaxHealth() * 0.3f); // 12 of 40; a 3-point hit leaves 9, under a quarter
        ninja.hurt(ninja.damageSources().playerAttack(player), 3f);
        helper.assertTrue(ninja.isYielded(), "brought low, the ninja yields");
        helper.assertTrue(Crew.recruit(player, ninja), "and can be invited");
        helper.assertTrue(player.getUUID().equals(ninja.leaderId()), "they follow the player now");
        helper.assertTrue(ShinobiCapability.get(player).map(d -> d.crew().contains(ninja.getUUID())).orElse(false), "and are in the crew");
        helper.assertTrue(Crew.sameSide(player, ninja), "on the same side");
        float health = ninja.getHealth();
        ninja.hurt(ninja.damageSources().playerAttack(player), 5f);
        helper.assertTrue(ninja.getHealth() == health, "a stray hit from their leader does nothing");
        clearAround(helper);
    }

    /** Playtest 4: allies' jutsu hurt the player. Nothing from your own side may hurt you. */
    @GameTest(template = "empty", batch = "noirm_crew_jutsu")
    public static void crewJutsuSpareTheirSide(GameTestHelper helper) {
        floor(helper);
        java.util.UUID leader = java.util.UUID.randomUUID();
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        SparringNinja mate = ninja(helper, 0.5f, 1.5f, TestClans.PLAIN);
        SparringNinja enemy = ninja(helper, 2.5f, 1.5f, TestClans.PLAIN);
        caster.joinCrew(leader);
        mate.joinCrew(leader);
        mate.setInvulnerable(false);
        enemy.setInvulnerable(false);
        helper.assertTrue(Crew.sameSide(caster, mate), "two crew mates of one leader are on the same side");
        var id = Noirm.id("raiton/chidori_current");
        var def = com.noirm.jutsu.JutsuData.get(id);
        com.noirm.jutsu.JutsuBehaviors.get(def.behavior()).cast(new com.noirm.jutsu.JutsuContext(caster, id, def, 1f, 1f));
        helper.assertTrue(com.noirm.combat.CombatSystem.isStaggered(enemy) && enemy.getHealth() < enemy.getMaxHealth(), "the enemy is shocked");
        helper.assertFalse(com.noirm.combat.CombatSystem.isStaggered(mate), "the crew mate isn't stunned");
        helper.assertTrue(mate.getHealth() == mate.getMaxHealth(), "nor hurt");
        // The aftermath (burning ground, craters) spares the caster's side too.
        com.noirm.jutsu.FriendlyZones.mark(caster);
        float before = mate.getHealth();
        mate.hurt(mate.damageSources().inFire(), 4f);
        mate.hurt(mate.damageSources().fall(), 6f);
        helper.assertTrue(mate.getHealth() == before, "fire and falls near their own side's jutsu don't hurt");
        enemy.invulnerableTime = 0;
        float enemyBefore = enemy.getHealth();
        enemy.hurt(enemy.damageSources().inFire(), 4f);
        helper.assertTrue(enemy.getHealth() < enemyBefore, "but they still hurt the enemy");
        clearAround(helper);
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        return ninja;
    }

    private static void clearAround(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(14);
        helper.getLevel().getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof Player)).forEach(LivingEntity::discard);
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    @SuppressWarnings("unused")
    private static float yaw(LivingEntity from, LivingEntity to) {
        return (float) (Mth.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()) * Mth.RAD_TO_DEG) - 90f;
    }
}

package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

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

    @GameTest(template = "empty", batch = "noirm_crew_growth")
    public static void crewMatesGrowAndLearn(GameTestHelper helper) {
        SparringNinja mate = ninja(helper, 1.5f, 1.5f, TestClans.FIRE);
        mate.joinCrew(java.util.UUID.randomUUID());
        var taijutsu = com.noirm.training.TrainingSystem.Stat.TAIJUTSU;
        float before = mate.stat(taijutsu);
        com.noirm.ally.AllyTraining.grant(mate, taijutsu, 1000f);
        helper.assertTrue(mate.stat(taijutsu) > before + 3, "XP raises a crew mate's stats, got " + mate.stat(taijutsu));
        // Drills stop teaching at the cap; real fights don't.
        mate.setStat(taijutsu, 40f);
        mate.setXp(taijutsu, 0f);
        com.noirm.ally.AllyTraining.onHitLanded(mate, helper.spawnWithNoFreeWill(NoirmEntities.TRAINING_DUMMY.get(), 0.5f, 1f, 0.5f), false, false);
        helper.assertTrue(mate.xp(taijutsu) == 0f, "the dummy teaches nothing past the drill cap");
        // Orders.
        mate.setOrder(com.noirm.ally.Order.MEDITATE);
        helper.assertTrue(mate.order() == com.noirm.ally.Order.MEDITATE && mate.isWaiting(), "they take orders");
        // Teaching: their natures only, and stronger jutsu need more reserves.
        var fireball = com.noirm.jutsu.JutsuData.get(Noirm.id("katon/great_fireball"));
        var waterDragon = com.noirm.jutsu.JutsuData.get(Noirm.id("suiton/water_dragon"));
        helper.assertTrue(com.noirm.ally.AllyPackets.blockerFor(mate, waterDragon).equals("screen.noirm.ally.teach.nature"), "not their nature");
        mate.setStat(com.noirm.training.TrainingSystem.Stat.RESERVES, 10f);
        var kirin = com.noirm.jutsu.JutsuData.get(Noirm.id("raiton/kirin"));
        helper.assertTrue(com.noirm.ally.AllyPackets.blockerFor(mate, kirin).equals("screen.noirm.ally.teach.reserves"), "S rank needs reserves");
        helper.assertTrue(com.noirm.ally.AllyPackets.blockerFor(mate, fireball).isEmpty(), "but a fire user can learn the Great Fireball");
        mate.teach(Noirm.id("raiton/kirin"));
        helper.assertTrue(mate.knownJutsu().contains(Noirm.id("raiton/kirin")) && mate.taughtJutsu().contains(Noirm.id("raiton/kirin")), "taught jutsu are kept");
        clearAround(helper);
    }

    /** Clan spawn eggs carry {Clan, Tails:0}: exactly that clan, and no surprise beast. */
    @GameTest(template = "empty", batch = "noirm_clan_eggs", timeoutTicks = 30)
    public static void clanEggsSpawnThatClan(GameTestHelper helper) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        ninja.saveWithoutId(tag);
        tag.putString("Clan", "uchiha");
        tag.putInt("Tails", 0);
        ninja.load(tag);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue("uchiha".equals(ninja.clanId()), "an Uchiha egg makes an Uchiha, got " + ninja.clanId());
            helper.assertTrue(ninja.tails() == 0, "and never a jinchūriki");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_crew_mind")
    public static void crewMatesHaveAMind(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        SparringNinja mate = ninja(helper, 1.5f, 1.5f, TestClans.FIRE);
        mate.joinCrew(player.getUUID());
        ShinobiCapability.get(player).ifPresent(d -> d.crew().add(mate.getUUID()));
        // Loadout: up to four, only what they know.
        var fireball = Noirm.id("katon/great_fireball");
        helper.assertTrue(mate.toggleLoadout(fireball), "equip a jutsu they know");
        helper.assertFalse(mate.toggleLoadout(Noirm.id("suiton/water_dragon")), "not one they don't know");
        helper.assertTrue(mate.castableJutsu().size() == 1 && mate.castableJutsu().contains(fireball), "they cast what's equipped");
        // Mastery makes their jutsu stronger.
        var def = com.noirm.jutsu.JutsuData.get(fireball);
        float before = com.noirm.jutsu.JutsuCaster.power(mate, fireball, def);
        mate.addJutsuXp(fireball, 1000);
        helper.assertTrue(mate.jutsuMastery(fireball) > 0 && com.noirm.jutsu.JutsuCaster.power(mate, fireball, def) > before, "mastery adds power");
        // Respect: a strong leader is respected; enough bad moments and they leave.
        helper.assertTrue(com.noirm.ally.AllyMind.respectFromPower(player, mate) > 0, "respect has a baseline");
        mate.setRespect(8);
        com.noirm.ally.AllyMind.respect(mate, -10f, null);
        helper.assertTrue(mate.leaderId() == null, "with no respect left, they leave the crew");
        // Retreat when badly hurt (only crew mates).
        mate.joinCrew(player.getUUID());
        mate.setInvulnerable(false);
        mate.setHealth(1f);
        helper.assertTrue(com.noirm.ally.AllyMind.shouldRetreat(mate), "badly hurt, they back off");
        clearAround(helper);
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        return ninja;
    }
}

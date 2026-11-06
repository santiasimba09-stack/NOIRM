package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.clan.ClanSystem;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.SealLock;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.movement.MovementFeel;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Movement feel (blueprint section 3): heavier gravity with the jump made up for, landing
 * recovery by fall height (skipped by a dodge), and no melee while forming seals. The client's
 * slide to a stop can't be tested here (it lives on the client); it was measured in the real client.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementGameTests {
    private static final ResourceLocation PHOENIX = Noirm.id("katon/phoenix_flower");

    private MovementGameTests() {}

    // ------------------------------------------------------------ gravity

    /** Players get the heavier gravity, but not while flying; the game rule turns it off; NPCs keep vanilla gravity. */
    @GameTest(template = "empty", batch = "noirm_move_gravity")
    public static void heavierGravityComesAndGoes(GameTestHelper helper) {
        Player player = player(helper);
        MovementFeel.updateGravity(player);
        helper.assertTrue(MovementFeel.hasHeavyGravity(player), "a player on foot gets the heavier gravity");
        double gravity = player.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get());
        helper.assertTrue(Math.abs(gravity - 0.08 * MovementFeel.GRAVITY_MULTIPLIER) < 1e-6, "gravity is 0.08 × " + MovementFeel.GRAVITY_MULTIPLIER + ", got " + gravity);

        player.getAbilities().flying = true;
        MovementFeel.updateGravity(player);
        helper.assertTrue(!MovementFeel.hasHeavyGravity(player), "no heavier gravity while flying");
        player.getAbilities().flying = false;
        MovementFeel.updateGravity(player);
        helper.assertTrue(MovementFeel.hasHeavyGravity(player), "back on landing");

        var rule = helper.getLevel().getGameRules().getRule(MovementFeel.RULE);
        rule.set(false, helper.getLevel().getServer());
        try {
            MovementFeel.updateGravity(player);
            helper.assertTrue(!MovementFeel.hasHeavyGravity(player), "the game rule switches it off");
            helper.assertTrue(Math.abs(player.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get()) - 0.08) < 1e-6, "vanilla gravity again");
        } finally {
            rule.set(true, helper.getLevel().getServer());
        }

        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!MovementFeel.hasHeavyGravity(ninja), "ninjas keep vanilla gravity (their leaps are aimed with it)");
            ninja.discard();
            helper.succeed();
        });
    }

    /** A jump reaches the same height as before: the jump is scaled up for the heavier gravity. */
    @GameTest(template = "empty", batch = "noirm_move_jump")
    public static void jumpHeightStaysTheSame(GameTestHelper helper) {
        double vanilla = MovementFeel.peak(0.42, 0.08);
        double now = MovementFeel.peak(0.42 * MovementFeel.JUMP_SCALE, 0.08 * MovementFeel.GRAVITY_MULTIPLIER);
        helper.assertTrue(Math.abs(now - vanilla) < 0.01, "same peak: " + vanilla + " vs " + now);
        helper.assertTrue(MovementFeel.JUMP_SCALE > 1.0 && MovementFeel.JUMP_SCALE < 1.15, "a modest jump boost, got " + MovementFeel.JUMP_SCALE);

        Player player = player(helper);
        MovementFeel.updateGravity(player);
        player.setDeltaMovement(0, 0.42, 0);
        ForgeHooks.onLivingJump(player); // as the game does at the end of a jump
        double up = player.getDeltaMovement().y;
        helper.assertTrue(Math.abs(up - 0.42 * MovementFeel.JUMP_SCALE) < 1e-4, "the jump is scaled: " + up);
        helper.succeed();
    }

    // ------------------------------------------------------------ landing recovery

    /** 1 to 3 ticks of recovery by fall height; none for small drops. */
    @GameTest(template = "empty", batch = "noirm_move_landing")
    public static void landingRecoveryGrowsWithTheFall(GameTestHelper helper) {
        helper.assertTrue(MovementFeel.recoveryTicksFor(1.25f) == 0, "a jump's fall costs nothing");
        helper.assertTrue(MovementFeel.recoveryTicksFor(2.9f) == 0, "a small drop costs nothing");
        helper.assertTrue(MovementFeel.recoveryTicksFor(3f) == 1, "3 blocks: 1 tick");
        helper.assertTrue(MovementFeel.recoveryTicksFor(6f) == 2, "6 blocks: 2 ticks");
        helper.assertTrue(MovementFeel.recoveryTicksFor(9f) == 3, "9 blocks: 3 ticks");
        helper.assertTrue(MovementFeel.recoveryTicksFor(40f) == 3, "never more than 3");

        Player player = player(helper);
        player.tickCount = 100;
        player.causeFallDamage(2f, 1f, player.damageSources().fall()); // the real landing path (LivingFallEvent)
        player.tickCount = 101;
        helper.assertTrue(!MovementFeel.recovering(player), "a 2-block drop: no recovery");

        player.tickCount = 200;
        player.causeFallDamage(7f, 1f, player.damageSources().fall());
        player.tickCount = 201;
        helper.assertTrue(MovementFeel.recovering(player), "7 blocks: recovering the tick after");
        player.tickCount = 202;
        helper.assertTrue(MovementFeel.recovering(player), "...and the next (2 ticks)");
        player.tickCount = 203;
        helper.assertTrue(!MovementFeel.recovering(player), "then free again");
        helper.succeed();
    }

    /** Dodging just before landing (the roll) skips the recovery; dodging during it ends it. */
    @GameTest(template = "empty", batch = "noirm_move_roll")
    public static void dodgeSkipsLandingRecovery(GameTestHelper helper) {
        Player player = player(helper);
        ShinobiCapability.get(player).ifPresent(d -> d.setStamina(100f));
        player.tickCount = 100;
        helper.assertTrue(CombatSystem.requestDodge(player, 0, 0), "the dodge goes off");
        player.tickCount = 105;
        helper.assertTrue(MovementFeel.landed(player, 10f) == 0, "landing out of a dodge: no recovery");

        player.tickCount = 300;
        helper.assertTrue(MovementFeel.landed(player, 10f) == 3, "a plain 10-block landing: 3 ticks");
        player.tickCount = 301;
        helper.assertTrue(MovementFeel.recovering(player), "recovering");
        MovementFeel.noteDodge(player);
        helper.assertTrue(!MovementFeel.recovering(player), "a dodge cuts the recovery short");
        helper.succeed();
    }

    // ------------------------------------------------------------ no melee while forming seals

    /** A player forming seals can't start a light, heavy or grab, nor punch; once the jutsu fires, they can. */
    @GameTest(template = "empty", batch = "noirm_move_seal_player")
    public static void noMeleeWhileFormingSeals(GameTestHelper helper) {
        Player player = player(helper);
        ShinobiData data = ShinobiCapability.get(player).orElseThrow();
        ClanSystem.assign(player, data, TestClans.FIRE);
        data.jutsu().learn(PHOENIX, false);
        data.jutsu().equip(0, PHOENIX);
        data.setChakra(100f);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1.5f, 1f, 2.5f);

        helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "the seals start");
        helper.assertTrue(SealLock.blocksMelee(player), "forming seals");
        helper.assertTrue(!CombatSystem.requestAttack(player, AttackInput.LIGHT), "no light attack mid-seal");
        helper.assertTrue(!CombatSystem.requestAttack(player, AttackInput.HEAVY), "no heavy attack mid-seal");
        helper.assertTrue(!CombatSystem.requestAttack(player, AttackInput.GRAB), "no grab mid-seal");
        player.attack(pig);
        helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "no plain punch mid-seal");

        int seals = JutsuCaster.sealPhaseTicks(PHOENIX, JutsuData.get(PHOENIX));
        for (int i = 0; i < seals; i++) {
            JutsuCaster.tick(player); // (test players aren't in the world: tick them by hand)
        }
        helper.assertTrue(JutsuCaster.casting(player) == null && !SealLock.blocksMelee(player), "the jutsu fired: hands free");
        discardProjectiles(helper);
        helper.assertTrue(CombatSystem.requestAttack(player, AttackInput.LIGHT), "a light attack works again");
        pig.discard();
        helper.succeed();
    }

    /** NPC ninjas follow the same rule. */
    @GameTest(template = "empty", batch = "noirm_move_seal_npc")
    public static void ninjasCantStrikeWhileFormingSeals(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.FIRE);
        ninja.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        helper.assertTrue(JutsuCaster.npcCast(ninja, PHOENIX, 1f), "the ninja starts its seals");
        helper.assertTrue(!CombatSystem.requestAttack(ninja, AttackInput.LIGHT), "no jab mid-seal");
        int seals = JutsuCaster.sealPhaseTicks(PHOENIX, JutsuData.get(PHOENIX));
        helper.runAfterDelay(seals + 3, () -> {
            discardProjectiles(helper);
            helper.assertTrue(JutsuCaster.casting(ninja) == null, "the jutsu went off");
            helper.assertTrue(CombatSystem.requestAttack(ninja, AttackInput.LIGHT), "and the ninja can jab again");
            ninja.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------ helpers

    private static Player player(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        return player;
    }

    private static void discardProjectiles(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1))).inflate(24);
        helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, area).forEach(JutsuProjectile::discard);
    }
}

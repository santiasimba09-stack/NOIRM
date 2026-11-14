package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.Nature;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuClash;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The automated casting engine (blueprint section 4): hand seals before tap jutsu, 80% movement
 * while weaving, the interruption matrix, spam fall-off and elemental clashes.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CastingEngineGameTests {
    private static final ResourceLocation PHOENIX = Noirm.id("katon/phoenix_flower");
    private static final ResourceLocation FIRE_DRAGON = Noirm.id("katon/fire_dragon");
    private static final ResourceLocation WATER_BULLET = Noirm.id("suiton/water_bullet");
    private static final ResourceLocation SUBSTITUTION = Noirm.id("universal/substitution");
    private static final ResourceLocation BODY_FLICKER = Noirm.id("universal/body_flicker");
    private static final ResourceLocation AIR_PALM = Noirm.id("futon/air_palm");

    private CastingEngineGameTests() {}

    // ------------------------------------------------------------ the seal phase

    /** Tap jutsu wait for their seals: 2 ticks a seal, capped at 12 (Phoenix Flower has 6). */
    @GameTest(template = "empty", batch = "noirm_cast_seals")
    public static void tapJutsuWaitsForItsSeals(GameTestHelper helper) {
        helper.assertTrue(JutsuCaster.sealPhaseTicks(WATER_BULLET, JutsuData.get(WATER_BULLET)) == 2, "1 seal = 2 ticks");
        helper.assertTrue(JutsuCaster.sealPhaseTicks(FIRE_DRAGON, JutsuData.get(FIRE_DRAGON)) == 8, "4 seals = 8 ticks");
        int seals = JutsuCaster.sealPhaseTicks(PHOENIX, JutsuData.get(PHOENIX));
        helper.assertTrue(seals == JutsuCaster.SEAL_PHASE_MAX_TICKS, "6 seals are capped at 12 ticks, got " + seals);

        Player player = firePlayer(helper, PHOENIX);
        ShinobiData data = data(player);
        helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "the seals start");
        for (int i = 0; i < seals - 1; i++) {
            JutsuCaster.tick(player); // (test players aren't in the world: tick them by hand)
        }
        helper.assertTrue(data.jutsu().cooldown(PHOENIX) == 0 && JutsuCaster.casting(player) != null, "not fired one tick early");
        helper.assertTrue(JutsuCaster.casting(player).sealing(), "still forming seals");
        JutsuCaster.tick(player);
        helper.assertTrue(data.jutsu().cooldown(PHOENIX) > 0, "fired once its seals are done");
        helper.assertTrue(JutsuCaster.casting(player) == null, "and the cast is over");
        discardProjectiles(helper);
        helper.succeed();
    }

    /** Reflex jutsu ("quick": true) and jutsu without seals go off at once. */
    @GameTest(template = "empty", batch = "noirm_cast_quick")
    public static void quickJutsuStayInstant(GameTestHelper helper) {
        helper.assertTrue(JutsuData.get(SUBSTITUTION).quick() && JutsuData.get(BODY_FLICKER).quick(), "Substitution and Body Flicker are quick");
        helper.assertTrue(JutsuCaster.sealPhaseTicks(SUBSTITUTION, JutsuData.get(SUBSTITUTION)) == 0, "Substitution has no seal phase");
        helper.assertTrue(JutsuCaster.sealPhaseTicks(BODY_FLICKER, JutsuData.get(BODY_FLICKER)) == 0, "Body Flicker has no seal phase");
        helper.assertTrue(JutsuCaster.sealPhaseTicks(AIR_PALM, JutsuData.get(AIR_PALM)) == 0, "no sequence, no seal phase");
        Player player = firePlayer(helper, SUBSTITUTION);
        helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "cast");
        helper.assertTrue(data(player).jutsu().cooldown(SUBSTITUTION) > 0, "it went off at once");
        helper.assertTrue(JutsuCaster.casting(player) == null && !JutsuCaster.isWeaving(player), "no seals to weave");
        helper.succeed();
    }

    /** 80% speed while weaving, and the slow goes when the seals finish. NPCs weave too. */
    @GameTest(template = "empty", batch = "noirm_cast_weave")
    public static void weavingSlowsAndEnds(GameTestHelper helper) {
        SparringNinja caster = ninja(helper, TestClans.FIRE);
        double before = caster.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(JutsuCaster.npcCast(caster, PHOENIX, 1f), "the ninja starts its seals");
        helper.assertTrue(JutsuCaster.isWeaving(caster), "weaving");
        double during = caster.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(Math.abs(during / before - JutsuCaster.WEAVING_SPEED) < 0.01, "80% speed, got " + during / before);
        helper.runAfterDelay(JutsuCaster.SEAL_PHASE_MAX_TICKS + 2, () -> {
            helper.assertFalse(JutsuCaster.isWeaving(caster), "the slow ends with the seals");
            helper.assertFalse(JutsuCaster.npcReady(caster, PHOENIX), "and the jutsu went off (its cooldown runs)");
            discardProjectiles(helper);
            clearAround(helper);
        });
    }

    /** Dying mid-seal (or logging out) can never leave the slow behind. */
    @GameTest(template = "empty", batch = "noirm_cast_weave_death")
    public static void weavingSlowEndsOnDeath(GameTestHelper helper) {
        SparringNinja caster = ninja(helper, TestClans.FIRE);
        helper.assertTrue(JutsuCaster.npcCast(caster, FIRE_DRAGON, 1f), "the ninja starts its seals");
        helper.assertTrue(JutsuCaster.isWeaving(caster), "weaving");
        caster.die(helper.getLevel().damageSources().generic());
        helper.assertFalse(JutsuCaster.isWeaving(caster), "the slow is gone");
        helper.assertTrue(JutsuCaster.casting(caster) == null, "and so is the cast");
        clearAround(helper);
    }

    // ------------------------------------------------------------ interruption

    /** A light hit pauses the seals for a moment; then they carry on. */
    @GameTest(template = "empty", batch = "noirm_cast_light")
    public static void lightHitPausesTheSeals(GameTestHelper helper) {
        Player player = firePlayer(helper, PHOENIX);
        ShinobiData data = data(player);
        JutsuCaster.onKey(player, 0, true);
        JutsuCaster.onHurt(player, player.getMaxHealth() * 0.05f);
        int seals = JutsuCaster.sealPhaseTicks(PHOENIX, JutsuData.get(PHOENIX));
        for (int i = 0; i < seals; i++) {
            JutsuCaster.tick(player);
        }
        helper.assertTrue(data.jutsu().cooldown(PHOENIX) == 0 && JutsuCaster.casting(player) != null, "held up by the pause");
        for (int i = 0; i < JutsuCaster.LIGHT_PAUSE_TICKS; i++) {
            JutsuCaster.tick(player);
        }
        helper.assertTrue(data.jutsu().cooldown(PHOENIX) > 0, "then it fires, " + JutsuCaster.LIGHT_PAUSE_TICKS + " ticks late");
        helper.assertFalse(JutsuCaster.isWeaving(player), "and the slow is gone");
        discardProjectiles(helper);
        helper.succeed();
    }

    /** A medium hit slows you hard for a second; the seals carry on. */
    @GameTest(template = "empty", batch = "noirm_cast_medium")
    public static void mediumHitSlowsButTheSealsContinue(GameTestHelper helper) {
        Player player = firePlayer(helper, PHOENIX);
        ShinobiData data = data(player);
        JutsuCaster.onKey(player, 0, true);
        JutsuCaster.onHurt(player, player.getMaxHealth() * 0.12f);
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowed hard");
        helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() == JutsuCaster.MEDIUM_SLOW_TICKS, "for a second");
        for (int i = 0; i < JutsuCaster.sealPhaseTicks(PHOENIX, JutsuData.get(PHOENIX)); i++) {
            JutsuCaster.tick(player);
        }
        helper.assertTrue(data.jutsu().cooldown(PHOENIX) > 0, "the jutsu still fires on time");
        discardProjectiles(helper);
        helper.succeed();
    }

    /** A heavy hit breaks the seals: it fizzles, 30% of the cost is lost, no cooldown. */
    @GameTest(template = "empty", batch = "noirm_cast_heavy")
    public static void heavyHitBreaksTheSeals(GameTestHelper helper) {
        Player player = firePlayer(helper, PHOENIX);
        ShinobiData data = data(player);
        data.setChakra(80f);
        float start = data.getChakra();
        float cost = JutsuCaster.cost(player, PHOENIX, JutsuData.get(PHOENIX));
        JutsuCaster.onKey(player, 0, true);
        assertNear(helper, start - cost, data.getChakra(), "the cost is paid as the seals start");
        JutsuCaster.tick(player);
        // A real hit, through the damage events.
        player.hurt(helper.getLevel().damageSources().generic(), player.getMaxHealth() * 0.5f);
        helper.assertTrue(JutsuCaster.casting(player) == null, "the seals broke");
        helper.assertFalse(JutsuCaster.isWeaving(player), "the slow is gone");
        assertNear(helper, start - JutsuCaster.FIZZLE_BACKLASH * cost, data.getChakra(), "only the backlash is lost");
        helper.assertTrue(data.jutsu().cooldown(PHOENIX) == 0, "no cooldown: try again");
        for (int i = 0; i < 20; i++) {
            JutsuCaster.tick(player);
        }
        helper.assertTrue(data.jutsu().cooldown(PHOENIX) == 0, "and it never fires");
        helper.succeed();
    }

    /** NPCs can be punished the same way: a stagger mid-seal breaks theirs. */
    @GameTest(template = "empty", batch = "noirm_cast_npc_break")
    public static void staggerBreaksAnNpcsSeals(GameTestHelper helper) {
        SparringNinja caster = ninja(helper, TestClans.FIRE);
        helper.assertTrue(JutsuCaster.npcCast(caster, PHOENIX, 1f), "the ninja starts its seals");
        com.noirm.combat.CombatSystem.stun(caster, 20);
        helper.runAfterDelay(JutsuCaster.SEAL_PHASE_MAX_TICKS + 2, () -> {
            helper.assertTrue(JutsuCaster.casting(caster) == null && !JutsuCaster.isWeaving(caster), "the seals broke");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, new AABB(caster.blockPosition()).inflate(16)).isEmpty(),
                    "nothing was thrown");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ spam fall-off

    /** Recasting a minor projectile jutsu quickly: -20% a time down to 40%, reset after a pause. */
    @GameTest(template = "empty", batch = "noirm_cast_spam", timeoutTicks = 200)
    public static void spammingMinorJutsuFallsOff(GameTestHelper helper) {
        SparringNinja caster = ninja(helper, TestClans.EVERY_NATURE);
        JutsuDefinition bullet = JutsuData.get(WATER_BULLET);
        float[] expected = {1f, 0.8f, 0.6f, 0.4f, 0.4f};
        for (float want : expected) {
            assertNear(helper, want, JutsuCaster.spamFactor(caster, WATER_BULLET, bullet), "water bullet spammed");
        }
        JutsuDefinition dragon = JutsuData.get(FIRE_DRAGON);
        for (int i = 0; i < 3; i++) {
            assertNear(helper, 1f, JutsuCaster.spamFactor(caster, FIRE_DRAGON, dragon), "a B-rank jutsu never falls off");
        }
        int window = JutsuCaster.cooldown(caster, WATER_BULLET, bullet) + JutsuCaster.SPAM_WINDOW_TICKS;
        helper.runAfterDelay(window + 2, () -> {
            assertNear(helper, 1f, JutsuCaster.spamFactor(caster, WATER_BULLET, bullet), "a pause resets it");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ elemental clashes

    /** Water beats fire: at equal damage, the water bullet flies on (weakened) and the fire is put out. */
    @GameTest(template = "empty", batch = "noirm_clash_advantage")
    public static void clashAdvantageWins(GameTestHelper helper) {
        JutsuProjectile water = shot(helper, Nature.WATER, 6f, 0.5, 1);
        JutsuProjectile fire = shot(helper, Nature.FIRE, 6f, 5.5, -1);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(water.clashOutcome() == JutsuClash.Outcome.WON && !water.isRemoved(), "water wins: " + water.clashOutcome());
            helper.assertTrue(fire.clashOutcome() == JutsuClash.Outcome.LOST && fire.isRemoved(), "fire is put out: " + fire.clashOutcome());
            // 6 x 1.5 = 9 against 6: it keeps (9 - 6) / 9 of its strength.
            assertNear(helper, 6f * (1f / 3f), water.damage(), "and flies on weakened");
            discardProjectiles(helper);
            helper.succeed();
        });
    }

    /** Raw power can still beat the wheel: a fire twice as strong burns through water. */
    @GameTest(template = "empty", batch = "noirm_clash_power")
    public static void clashPowerBeatsTheWheel(GameTestHelper helper) {
        JutsuProjectile water = shot(helper, Nature.WATER, 6f, 0.5, 1);
        JutsuProjectile fire = shot(helper, Nature.FIRE, 12f, 5.5, -1);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(fire.clashOutcome() == JutsuClash.Outcome.WON, "the strong fire wins: " + fire.clashOutcome());
            helper.assertTrue(water.isRemoved(), "the water is gone");
            discardProjectiles(helper);
            helper.succeed();
        });
    }

    /** Near-equal power with no advantage: both burst in a cross-element explosion. */
    @GameTest(template = "empty", batch = "noirm_clash_burst")
    public static void clashEqualPowerBursts(GameTestHelper helper) {
        JutsuProjectile fire = shot(helper, Nature.FIRE, 6f, 0.5, 1);
        JutsuProjectile earth = shot(helper, Nature.EARTH, 6.4f, 5.5, -1);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(fire.clashOutcome() == JutsuClash.Outcome.BURST && earth.clashOutcome() == JutsuClash.Outcome.BURST,
                    "both burst: " + fire.clashOutcome() + ", " + earth.clashOutcome());
            helper.assertTrue(fire.isRemoved() && earth.isRemoved(), "and are gone");
            discardProjectiles(helper);
            helper.succeed();
        });
    }

    /** The same nature: the stronger one wins. */
    @GameTest(template = "empty", batch = "noirm_clash_same")
    public static void clashSameNatureStrongerWins(GameTestHelper helper) {
        JutsuProjectile strong = shot(helper, Nature.FIRE, 10f, 0.5, 1);
        JutsuProjectile weak = shot(helper, Nature.FIRE, 5f, 5.5, -1);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(strong.clashOutcome() == JutsuClash.Outcome.WON && weak.isRemoved(), "the stronger fire wins");
            assertNear(helper, 5f, strong.damage(), "keeping half its strength");
            discardProjectiles(helper);
            helper.succeed();
        });
    }

    /** One side's jutsu never clash with each other. */
    @GameTest(template = "empty", batch = "noirm_clash_friendly")
    public static void sameSideNeverClashes(GameTestHelper helper) {
        SparringNinja owner = ninja(helper, TestClans.EVERY_NATURE);
        JutsuProjectile water = shot(helper, Nature.WATER, 6f, 0.5, 1);
        JutsuProjectile fire = shot(helper, Nature.FIRE, 6f, 5.5, -1);
        water.setOwner(owner);
        fire.setOwner(owner);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(water.clashOutcome() == JutsuClash.Outcome.NONE && fire.clashOutcome() == JutsuClash.Outcome.NONE, "they pass through");
            helper.assertTrue(!water.isRemoved() && !fire.isRemoved(), "both still fly");
            discardProjectiles(helper);
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ helpers

    /** A projectile flying along z toward the other one, 3 blocks up, no owner (owners differ). */
    private static JutsuProjectile shot(GameTestHelper helper, Nature nature, float damage, double z, int direction) {
        JutsuProjectile shot = NoirmEntities.JUTSU_PROJECTILE.get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 3, z));
        shot.moveTo(at.x, at.y, at.z, 0, 0);
        shot.configure(nature, 0.5f, damage, 0.5f, 0, 0, 0);
        shot.setDeltaMovement(0, 0, 0.3 * direction);
        helper.getLevel().addFreshEntity(shot);
        return shot;
    }

    private static Player firePlayer(GameTestHelper helper, ResourceLocation jutsu) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.FIRE);
        data.jutsu().learn(jutsu, false);
        data.jutsu().equip(0, jutsu);
        data.setChakra(100f);
        return player;
    }

    private static SparringNinja ninja(GameTestHelper helper, Clan clan) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        ninja.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        return ninja;
    }

    private static void discardProjectiles(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1))).inflate(24);
        helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, area).forEach(JutsuProjectile::discard);
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow();
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }
}

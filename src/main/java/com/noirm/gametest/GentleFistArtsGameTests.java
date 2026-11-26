package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanSystem;
import com.noirm.combat.CombatSystem;
import com.noirm.dojutsu.Byakugan;
import com.noirm.dojutsu.GentleFistArts;
import com.noirm.dojutsu.XRayScanner;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Wave "Hyūga": the Gentle Fist's own arts (targeted tenketsu, organ strikes, chakra burst, reopening points, palm
 * parry) and the Byakugan lag fix (the x-ray scanned a slice per tick).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GentleFistArtsGameTests {
    private static final String BATCH = "noirm_gentle_fist_arts";

    private GentleFistArtsGameTests() {}

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        for (int bx = 0; bx < 3; bx++) {
            for (int bz = 0; bz < 3; bz++) {
                helper.setBlock(new BlockPos(bx, 0, bz), Blocks.STONE);
            }
        }
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        return ninja;
    }

    /** Legs slow, arms weaken, the chest closes more points; effects stack up to their cap. */
    @GameTest(template = "empty", batch = BATCH)
    public static void tenketsuRegions(GameTestHelper helper) {
        GentleFistArts.Tuning t = GentleFistArts.tuning();
        helper.assertTrue(GentleFistArts.regionAt(0.1f) == GentleFistArts.Region.LEGS, "low is the legs");
        helper.assertTrue(GentleFistArts.regionAt(0.55f) == GentleFistArts.Region.CHEST, "the middle is the chest");
        helper.assertTrue(GentleFistArts.regionAt(0.9f) == GentleFistArts.Region.ARMS, "high is the arms");
        helper.assertTrue(GentleFistArts.pointsFor(GentleFistArts.Region.CHEST) > GentleFistArts.pointsFor(GentleFistArts.Region.LEGS),
                "the chest closes more points");
        SparringNinja hyuga = ninja(helper, 0.5f, 0.5f, TestClans.BYAKUGAN);
        SparringNinja target = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        for (int i = 0; i < 6; i++) {
            GentleFistArts.strikeRegion(hyuga, target, GentleFistArts.Region.LEGS);
        }
        var slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slow != null && slow.getAmplifier() == t.legsMaxLevel(), "leg strikes stack Slowness to the cap");
        GentleFistArts.strikeRegion(hyuga, target, GentleFistArts.Region.ARMS);
        helper.assertTrue(target.hasEffect(MobEffects.WEAKNESS), "arm strikes weaken");
        hyuga.discard();
        target.discard();
        helper.succeed();
    }

    /** A heavy hit leaves the organs bleeding: damage keeps coming for a few seconds. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 120)
    public static void organStrikeBleeds(GameTestHelper helper) {
        SparringNinja hyuga = ninja(helper, 0.5f, 0.5f, TestClans.BYAKUGAN);
        SparringNinja target = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        float start = target.getHealth();
        GentleFistArts.organStrike(hyuga, target, 8f);
        helper.assertTrue(GentleFistArts.bleeding(target), "bleeding inside");
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(target.getHealth() < start - 1f, "the bleed hurt: " + start + " -> " + target.getHealth());
            hyuga.discard();
            target.discard();
            helper.succeed();
        });
    }

    /** Pushing chakra into someone forming hand seals breaks the jutsu. */
    @GameTest(template = "empty", batch = BATCH)
    public static void chakraBurstBreaksSeals(GameTestHelper helper) {
        Player caster = helper.makeMockPlayer();
        caster.getAbilities().instabuild = false;
        ShinobiData data = ShinobiCapability.get(caster).orElseThrow();
        ClanSystem.assign(caster, data, TestClans.FIRE);
        ResourceLocation fireball = Noirm.id("katon/great_fireball");
        data.jutsu().learn(fireball, false);
        data.jutsu().equip(0, fireball);
        data.setChakra(data.getMaxChakra());
        helper.assertTrue(JutsuCaster.onKey(caster, 0, true) == JutsuCaster.Result.OK, "the fireball starts");
        JutsuCaster.Casting casting = JutsuCaster.casting(caster);
        helper.assertTrue(casting != null && casting.sealing(), "forming hand seals");
        SparringNinja hyuga = ninja(helper, 0.5f, 0.5f, TestClans.BYAKUGAN);
        helper.assertTrue(GentleFistArts.chakraBurst(hyuga, caster, false), "a Gentle Fist hit bursts the flow");
        helper.assertTrue(JutsuCaster.casting(caster) == null, "and the jutsu is gone");
        helper.assertFalse(GentleFistArts.chakraBurst(hyuga, caster, false), "nothing left to break");
        hyuga.discard();
        helper.succeed();
    }

    /** A Byakugan bearer forces their own closed points open, and a seal on them wears off faster. */
    @GameTest(template = "empty", batch = BATCH)
    public static void byakuganReopensOwnPoints(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 0.5f, 0.5f, TestClans.PLAIN);
        SparringNinja hyuga = ninja(helper, 2.5f, 2.5f, TestClans.BYAKUGAN);
        helper.assertTrue(GentleFistArts.canReopen(hyuga), "the eye sees its own network");
        Byakugan.closePoints(attacker, hyuga, 10, 0f);
        Byakugan.reopen(hyuga, GentleFistArts.tuning().reopenPerSecond(), 1f);
        helper.assertTrue(Byakugan.closedPoints(hyuga) == 10 - GentleFistArts.tuning().reopenPerSecond(), "points reopen");
        Byakugan.closePoints(attacker, hyuga, Byakugan.SEAL_POINTS, 0f);
        int left = Byakugan.sealTicksLeft(hyuga);
        helper.assertTrue(left > 0, "sealed");
        Byakugan.reopen(hyuga, 0, 2f);
        helper.assertTrue(Byakugan.sealTicksLeft(hyuga) < left, "the seal wears off faster");
        attacker.discard();
        hyuga.discard();
        helper.succeed();
    }

    /** Guard raised with the eye open: an arrow is turned back at its shooter. */
    @GameTest(template = "empty", batch = BATCH)
    public static void palmParryTurnsArrowsBack(GameTestHelper helper) {
        SparringNinja hyuga = ninja(helper, 1.5f, 0.5f, TestClans.BYAKUGAN);
        SparringNinja archer = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        TestKit.face(hyuga, archer);
        CombatSystem.setGuard(hyuga, true);
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setOwner(archer);
        arrow.setPos(hyuga.getX(), hyuga.getY() + 1.2, hyuga.getZ() + 0.8);
        arrow.setDeltaMovement(new Vec3(0, 0, -1.5));
        helper.assertTrue(GentleFistArts.palmParries(hyuga, arrow), "the palm parries it");
        GentleFistArts.turnBack(hyuga, arrow);
        helper.assertTrue(arrow.getDeltaMovement().z > 0, "it flies back toward the archer");
        helper.assertTrue(arrow.getOwner() == hyuga, "now the Hyūga's");
        CombatSystem.setGuard(hyuga, false);
        SparringNinja plain = ninja(helper, 0.5f, 1.5f, TestClans.PLAIN);
        CombatSystem.setGuard(plain, true);
        helper.assertFalse(GentleFistArts.palmParries(plain, arrow), "no eye, no palm parry");
        arrow.discard();
        hyuga.discard();
        archer.discard();
        plain.discard();
        helper.succeed();
    }

    /** The lag fix: the x-ray never looks at more than its per-tick budget, and finishes a pass in a few ticks. */
    @GameTest(template = "empty", batch = BATCH)
    public static void xrayScanIsSpreadOverTicks(GameTestHelper helper) {
        XRayScanner scanner = new XRayScanner();
        BlockPos eye = helper.absolutePos(new BlockPos(1, 1, 1));
        long t0 = System.nanoTime();
        int ticks = 0;
        int most = 0;
        long time = helper.getLevel().getGameTime();
        do {
            most = Math.max(most, scanner.step(helper.getLevel(), eye, Byakugan.MAX_XRAY, time + ticks));
            ticks++;
        } while (scanner.scanning() && ticks < 200);
        long micros = (System.nanoTime() - t0) / 1000;
        Noirm.LOGGER.info("Byakugan x-ray: widest pass (r={}) took {} ticks, at most {} blocks per tick, {} µs in all",
                Byakugan.MAX_XRAY, ticks, most, micros);
        helper.assertTrue(most <= XRayScanner.BLOCKS_PER_STEP, "within the per-tick budget");
        helper.assertTrue(ticks > 1 && ticks < 60, "spread over a few ticks: " + ticks);
        helper.assertTrue(scanner.finds().size() <= XRayScanner.MAX_RESULTS, "capped finds");
        helper.succeed();
    }
}

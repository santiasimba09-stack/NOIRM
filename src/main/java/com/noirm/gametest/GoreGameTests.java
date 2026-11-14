package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.gore.BloodSplats;
import com.noirm.gore.Gore;
import com.noirm.gore.SeveredLimb;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Gore (blueprint §7): marks by damage type that fade, severed limbs, the cap and the switch. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoreGameTests {
    private GoreGameTests() {}

    private static SparringNinja ninja(GameTestHelper helper) {
        TestKit.floor(helper);
        SparringNinja ninja = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.setNoAi(true);
        return ninja;
    }

    private static Player swordsman(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        return player;
    }

    private static void hurt(SparringNinja ninja, net.minecraft.world.damagesource.DamageSource source, float amount) {
        ninja.invulnerableTime = 0;
        ninja.hurt(source, amount);
    }

    private static List<SeveredLimb> limbs(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(12);
        return helper.getLevel().getEntitiesOfClass(SeveredLimb.class, area, e -> true);
    }

    /** Removes what a test left or threw around: limbs, bodies, drops. */
    private static void tidy(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(12);
        helper.getLevel().getEntitiesOfClass(Entity.class, area, e -> e instanceof SeveredLimb || e instanceof SparringNinja
                || e instanceof ItemEntity || e instanceof ExperienceOrb).forEach(Entity::discard);
    }

    /** Fire blackens, blades cut, falls dirty, fists bruise; healing makes the marks fade. */
    @GameTest(template = "empty", batch = "noirm_gore_marks", timeoutTicks = 100)
    public static void marksBuildUpByDamageTypeAndFade(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper);
        var sources = helper.getLevel().damageSources();
        hurt(ninja, sources.inFire(), 4f);
        helper.assertTrue(Gore.mark(ninja, Gore.Mark.SOOT) > 0.03f, "fire leaves soot");
        helper.assertTrue(Gore.mark(ninja, Gore.Mark.CUT) == 0f && Gore.mark(ninja, Gore.Mark.BRUISE) == 0f, "and nothing else");
        hurt(ninja, sources.playerAttack(swordsman(helper)), 4f);
        helper.assertTrue(Gore.mark(ninja, Gore.Mark.CUT) > 0.03f, "a sword cuts");
        hurt(ninja, sources.fall(), 3f);
        helper.assertTrue(Gore.mark(ninja, Gore.Mark.DIRT) > 0.03f, "a fall dirties");
        SparringNinja brawler = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        brawler.setNoAi(true);
        brawler.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // bare fists
        hurt(ninja, sources.mobAttack(brawler), 4f);
        helper.assertTrue(Gore.mark(ninja, Gore.Mark.BRUISE) > 0.03f, "fists bruise");
        float cut = Gore.mark(ninja, Gore.Mark.CUT);
        float soot = Gore.mark(ninja, Gore.Mark.SOOT);
        ninja.setHealth(ninja.getMaxHealth()); // healed: the marks fade with it
        helper.runAfterDelay(Gore.FADE_INTERVAL * 2 + 2, () -> {
            helper.assertTrue(Gore.mark(ninja, Gore.Mark.CUT) < cut * 0.9f, "cuts fade as health comes back");
            helper.assertTrue(Gore.mark(ninja, Gore.Mark.SOOT) < soot * 0.9f, "soot too");
            helper.assertTrue(ninja.getHealth() == ninja.getMaxHealth(), "visual only: marks don't hurt");
            tidy(helper);
            helper.succeed();
        });
    }

    /** A killing sword blow cuts a limb off; it lies there 15 s, burns away, and is gone. */
    @GameTest(template = "empty", batch = "noirm_gore_limb", timeoutTicks = 400)
    public static void lethalBladeSeversALimbThatDissolves(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper);
        hurt(ninja, helper.getLevel().damageSources().playerAttack(swordsman(helper)), 1000f);
        helper.assertTrue(ninja.isDeadOrDying(), "the blow kills");
        helper.assertTrue(Gore.severed(ninja) != 0, "the dying body has lost a part");
        helper.assertTrue(limbs(helper).size() == 1, "one limb flies off, got " + limbs(helper).size());
        helper.assertTrue(SeveredLimb.LIFE_TICKS >= 300, "limbs lie there at least 15 s");
        helper.runAfterDelay(SeveredLimb.LIFE_TICKS - 10, () -> {
            helper.assertTrue(limbs(helper).size() == 1, "still there before 15 s");
            helper.assertTrue(limbs(helper).get(0).onGround(), "it has settled on the ground");
        });
        helper.runAfterDelay(SeveredLimb.LIFE_TICKS + SeveredLimb.DISSOLVE_TICKS + 5, () -> {
            helper.assertTrue(limbs(helper).isEmpty(), "burnt to ash after 15 s and the flame");
            tidy(helper);
            helper.succeed();
        });
    }

    /** A light blunt finishing hit doesn't sever (only blades, crits, blasts and heavy hits). */
    @GameTest(template = "empty", batch = "noirm_gore_light")
    public static void lightBluntKillKeepsLimbs(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper);
        ninja.setHealth(1f);
        hurt(ninja, helper.getLevel().damageSources().generic(), 2f);
        helper.assertTrue(ninja.isDeadOrDying(), "the hit kills");
        helper.assertTrue(limbs(helper).isEmpty(), "but cuts nothing off");
        tidy(helper);
        helper.succeed();
    }

    /** {@code noirmGore false}: no marks, no limbs. */
    @GameTest(template = "empty", batch = "noirm_gore_off")
    public static void goreRuleTurnsItOff(GameTestHelper helper) {
        var rules = helper.getLevel().getGameRules();
        rules.getRule(Gore.GORE).set(false, helper.getLevel().getServer());
        try {
            SparringNinja ninja = ninja(helper);
            hurt(ninja, helper.getLevel().damageSources().inFire(), 4f);
            helper.assertTrue(Gore.mark(ninja, Gore.Mark.SOOT) == 0f, "no soot with gore off");
            long sent = Gore.bloodSent();
            hurt(ninja, helper.getLevel().damageSources().playerAttack(swordsman(helper)), 5f);
            helper.assertTrue(Gore.mark(ninja, Gore.Mark.BLOOD) == 0f, "no blood with gore off");
            hurt(ninja, helper.getLevel().damageSources().playerAttack(swordsman(helper)), 1000f);
            helper.assertTrue(limbs(helper).isEmpty(), "no limb with gore off");
            helper.assertTrue(Gore.bloodSent() == sent, "and not a drop of blood sent");
        } finally {
            rules.getRule(Gore.GORE).set(true, helper.getLevel().getServer());
        }
        tidy(helper);
        helper.succeed();
    }

    /** Never more than {@link SeveredLimb#MAX_LIMBS} at once: the oldest go first. */
    @GameTest(template = "empty", batch = "noirm_gore_cap")
    public static void limbCapHolds(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper);
        SeveredLimb first = null;
        for (int i = 0; i < SeveredLimb.MAX_LIMBS + 4; i++) {
            SeveredLimb limb = Gore.sever(helper.getLevel(), ninja, SeveredLimb.Part.byId(i % 5), null);
            if (first == null) {
                first = limb;
            }
        }
        helper.assertTrue(SeveredLimb.liveCount() <= SeveredLimb.MAX_LIMBS, "the cap holds, got " + SeveredLimb.liveCount());
        helper.assertTrue(limbs(helper).size() <= SeveredLimb.MAX_LIMBS, "in the world too");
        helper.assertTrue(first != null && first.isRemoved(), "the oldest went first");
        tidy(helper);
        helper.succeed();
    }

    /** Blades bleed: blood builds on the body, fresh and wet, drops fly, and it dries with time. */
    @GameTest(template = "empty", batch = "noirm_gore_blood", timeoutTicks = 100)
    public static void bloodBuildsAndDries(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper);
        long sent = Gore.bloodSent();
        Player swordsman = swordsman(helper);
        hurt(ninja, helper.getLevel().damageSources().playerAttack(swordsman), 3f);
        float first = Gore.mark(ninja, Gore.Mark.BLOOD);
        helper.assertTrue(first > 0.1f, "a sword cut bleeds, got " + first);
        helper.assertTrue(Gore.freshness(ninja) == 1f, "the blood is fresh");
        helper.assertTrue(Gore.bloodSent() > sent, "drops fly from the cut");
        hurt(ninja, helper.getLevel().damageSources().playerAttack(swordsman), 3f);
        helper.assertTrue(Gore.mark(ninja, Gore.Mark.BLOOD) > first, "more cuts, more blood");
        helper.assertTrue(Gore.mark(ninja, Gore.Mark.BLOOD) > Gore.mark(ninja, Gore.Mark.BRUISE), "blades bleed more than they bruise");
        float blood = Gore.mark(ninja, Gore.Mark.BLOOD);
        float health = ninja.getHealth();
        helper.runAfterDelay(Gore.FADE_INTERVAL * 4 + 2, () -> {
            float fresh = Gore.freshness(ninja);
            helper.assertTrue(fresh < 1f && fresh > 0.5f, "it starts to dry, got " + fresh);
            helper.assertTrue(Gore.mark(ninja, Gore.Mark.BLOOD) > blood * 0.9f, "but stays on the body (slow fade)");
            helper.assertTrue(ninja.getHealth() >= health, "visual only: no bleeding damage");
            tidy(helper);
            helper.succeed();
        });
    }

    /** Someone badly hurt who has bled leaves a trail of drops as they move. */
    @GameTest(template = "empty", batch = "noirm_gore_trail", timeoutTicks = 100)
    public static void badlyHurtLeaveATrail(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper);
        hurt(ninja, helper.getLevel().damageSources().playerAttack(swordsman(helper)), 2f);
        ninja.setHealth(ninja.getMaxHealth() * 0.2f);
        long sent = Gore.bloodSent();
        double x = ninja.getX(), y = ninja.getY(), z = ninja.getZ();
        float health = ninja.getHealth();
        for (int i = 1; i <= Gore.BLEED_INTERVAL * 6; i++) {
            int step = i;
            helper.runAfterDelay(i, () -> ninja.teleportTo(x + (step % 2 == 0 ? 0.6 : -0.6), y, z)); // pacing back and forth
        }
        helper.runAfterDelay(Gore.BLEED_INTERVAL * 6 + 1, () -> {
            helper.assertTrue(Gore.bloodSent() - sent >= 4, "a trail of drops, got " + (Gore.bloodSent() - sent));
            helper.assertTrue(ninja.getHealth() >= health, "visual only: the trail costs no health");
            tidy(helper);
            helper.succeed();
        });
    }

    /** Heavy blunt killing blows sever most of the time now (85%). */
    @GameTest(template = "empty", batch = "noirm_gore_blunt", timeoutTicks = 60)
    public static void heavyBluntKillsSeverOften(GameTestHelper helper) {
        TestKit.floor(helper);
        SparringNinja brawler = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        brawler.setNoAi(true);
        brawler.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // bare fists
        int kills = 24, severed = 0;
        for (int i = 0; i < kills; i++) {
            SparringNinja ninja = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
            ninja.setNoAi(true);
            hurt(ninja, helper.getLevel().damageSources().mobAttack(brawler), 1000f);
            helper.assertTrue(ninja.isDeadOrDying(), "the blow kills");
            if (Gore.severed(ninja) != 0) {
                severed++;
            }
            ninja.discard();
        }
        helper.assertTrue(Gore.SEVER_CHANCE_HEAVY >= 0.8f, "heavy blunt kills sever at 80%+");
        helper.assertTrue(severed >= kills / 2, "most heavy blunt kills sever, got " + severed + "/" + kills);
        helper.assertTrue(SeveredLimb.liveCount() <= SeveredLimb.MAX_LIMBS, "and the limb cap holds");
        tidy(helper);
        helper.succeed();
    }

    /**
     * Blood on the ground (the client's store, tested here as plain data): stains sit on block
     * tops, merge when close, never hang over an edge, are capped, dry, and fade away.
     */
    @GameTest(template = "empty", batch = "noirm_gore_splats")
    public static void groundSplatsAreCappedAndFade(GameTestHelper helper) {
        TestKit.floor(helper);
        var level = helper.getLevel();
        var random = net.minecraft.util.RandomSource.create(7);
        BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 1));
        double cx = floor.getX() + 0.5, cy = floor.getY(), cz = floor.getZ() + 0.5;
        BloodSplats splats = new BloodSplats(16);
        int first = splats.add(level, cx, cy + 0.02, cz, 0.12f, false, random);
        helper.assertTrue(first >= 0, "a drop on the floor leaves a splat");
        long now = level.getGameTime();
        helper.assertTrue(Math.abs(splats.y(first) - cy) < 0.03, "lying on the block top, got " + splats.y(first) + " vs " + cy);
        int again = splats.add(level, cx + 0.05, cy + 0.02, cz, 0.12f, false, random);
        helper.assertTrue(again == first && splats.size(first) > 0.12f, "a drop on a fresh splat makes it bigger");
        helper.assertTrue(splats.add(level, cx, cy + 5, cz, 0.1f, false, random) == -1, "nothing in mid-air");
        for (int i = 0; i < 100; i++) {
            splats.add(level, cx + (i % 10) * 0.25 - 1.1, cy + 0.02, cz + (i / 10) * 0.25 - 1.1, 0.08f, false, random);
        }
        helper.assertTrue(splats.count(now) <= 16, "the cap holds, got " + splats.count(now));
        for (int i = 0; i < splats.capacity(); i++) {
            if (splats.used(i)) {
                helper.assertTrue(splats.size(i) <= BloodSplats.MAX_RADIUS, "no stain too big");
            }
        }
        BloodSplats pools = new BloodSplats(4);
        int pool = pools.add(level, cx, cy + 0.2, cz, 5f, true, random);
        helper.assertTrue(pool >= 0 && pools.size(pool) <= 1.5f + 0.01f,
                "a huge pool is cut to its 3x3 floor, got " + (pool < 0 ? -1 : pools.size(pool)));
        helper.assertTrue(pools.dryness(pool, now) == 0f && pools.dryness(pool, now + BloodSplats.DRY_TICKS) == 1f, "fresh, then dried");
        helper.assertTrue(pools.alpha(pool, now + BloodSplats.POOL_LIFE + 1, 0f) == 0f
                && pools.count(now + BloodSplats.POOL_LIFE + 1) == 0, "and gone after its life");
        helper.succeed();
    }
}

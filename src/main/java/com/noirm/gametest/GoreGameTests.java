package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
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

    /** A killing sword blow cuts a limb off; it lies there 5 s, burns away, and is gone. */
    @GameTest(template = "empty", batch = "noirm_gore_limb", timeoutTicks = 220)
    public static void lethalBladeSeversALimbThatDissolves(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper);
        hurt(ninja, helper.getLevel().damageSources().playerAttack(swordsman(helper)), 1000f);
        helper.assertTrue(ninja.isDeadOrDying(), "the blow kills");
        helper.assertTrue(Gore.severed(ninja) != 0, "the dying body has lost a part");
        helper.assertTrue(limbs(helper).size() == 1, "one limb flies off, got " + limbs(helper).size());
        helper.runAfterDelay(SeveredLimb.LIFE_TICKS - 10, () -> {
            helper.assertTrue(limbs(helper).size() == 1, "still there before 5 s");
            helper.assertTrue(limbs(helper).get(0).onGround(), "it has settled on the ground");
        });
        helper.runAfterDelay(SeveredLimb.LIFE_TICKS + SeveredLimb.DISSOLVE_TICKS + 5, () -> {
            helper.assertTrue(limbs(helper).isEmpty(), "burnt to ash after 5 s and the flame");
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
            hurt(ninja, helper.getLevel().damageSources().playerAttack(swordsman(helper)), 1000f);
            helper.assertTrue(limbs(helper).isEmpty(), "no limb with gore off");
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
}

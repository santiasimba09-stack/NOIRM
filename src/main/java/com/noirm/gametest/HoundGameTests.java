package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.clan.ClanData;
import com.noirm.clan.ClanSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.hound.HoundData;
import com.noirm.hound.HoundEvents;
import com.noirm.hound.HoundGrowth;
import com.noirm.hound.HoundLook;
import com.noirm.hound.HoundTraits;
import com.noirm.hound.HoundOrder;
import com.noirm.hound.HoundRegistry;
import com.noirm.hound.HoundSmell;
import com.noirm.hound.HoundStage;
import com.noirm.hound.HoundTechniques;
import com.noirm.hound.Hounds;
import com.noirm.hound.NinjaHound;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.traits.PerkEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** The Inuzuka ninja hound (docs/INUZUKA.md): who gets one, growth, size, orders, smell, Fang Over Fang, knockouts, saving, NPC hounds. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HoundGameTests {
    private static final ResourceLocation FANG = Noirm.id("clan/fang_over_fang");

    private HoundGameTests() {}

    /** A fake player of {@code clan} on a stone floor in the middle of the arena, with no hound yet. */
    private static FakePlayer owner(GameTestHelper helper, String name, String clan) {
        floor(helper);
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        player.setOnGround(true);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ShinobiData data = data(player);
        data.hound().clear();
        data.talents().setHoundGiven(false);
        if (clan != null) {
            ClanSystem.assign(player, data, ClanData.canon(clan).orElseThrow(() -> new IllegalStateException("no clan " + clan)));
        }
        return player;
    }

    private static ShinobiData data(FakePlayer player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static NinjaHound puppy(GameTestHelper helper, FakePlayer player) {
        NinjaHound hound = Hounds.grantPuppy(player, HoundLook.DEFAULT, false);
        helper.assertTrue(hound != null, "a puppy was placed");
        // A plain, known nature so the numbers in the tests don't depend on a roll.
        hound.setTraits(java.util.List.of("loyal"));
        data(player).hound().setTraits(hound.traits());
        return hound;
    }

    /** Takes the hound (and anything else the test made) away, then succeeds. */
    private static void done(GameTestHelper helper, FakePlayer player) {
        if (player.isPassenger()) {
            player.stopRiding();
        }
        player.ejectPassengers();
        data(player).hound().clear();
        clearAround(helper);
    }

    // ================================================================ who gets one

    @GameTest(template = "empty", batch = "noirm_hound_spawn")
    public static void inuzukaStartWithAPuppyHatakeWithAContract(GameTestHelper helper) {
        FakePlayer player = owner(helper, "noirm_hound_inuzuka", "inuzuka");
        NinjaHound hound = PerkEvents.giveHound(player, data(player));
        helper.assertTrue(hound != null && hound.isAlive(), "an Inuzuka gets a hound");
        helper.assertTrue(hound.stage() == HoundStage.PUPPY, "a puppy");
        helper.assertTrue(hound.isOwnedBy(player) && player.equals(hound.getOwner()), "bound to its owner");
        helper.assertTrue(hound.distanceTo(player) < 3, "right beside them");
        helper.assertTrue(data(player).hound().has() && hound.getUUID().equals(data(player).hound().entityId()), "kept in the owner's record");
        helper.assertTrue(Crew.sameSide(player, hound), "on the owner's side");
        helper.assertTrue(data(player).jutsu().knows(Hounds.DYNAMIC_MARKING), "and the pair know Dynamic Marking");
        data(player).hound().clear();
        hound.discard();

        FakePlayer hatake = owner(helper, "noirm_hound_hatake", "hatake");
        helper.assertTrue(PerkEvents.giveHound(hatake, data(hatake)) == null, "a Hatake gets no puppy at once…");
        ItemStack contract = ItemStack.EMPTY;
        for (int i = 0; i < hatake.getInventory().getContainerSize(); i++) {
            if (hatake.getInventory().getItem(i).is(HoundRegistry.NINKEN_CONTRACT.get())) {
                contract = hatake.getInventory().getItem(i);
            }
        }
        helper.assertTrue(!contract.isEmpty(), "…but a summoning contract");
        hatake.setItemInHand(InteractionHand.MAIN_HAND, contract);
        hatake.getAbilities().instabuild = true;
        contract.use(helper.getLevel(), hatake, InteractionHand.MAIN_HAND);
        HoundData hd = data(hatake).hound();
        helper.assertTrue(hd.has() && hd.byContract() && Hounds.find(hatake) != null, "signing it calls a contract puppy");
        helper.assertTrue(Hounds.recall(hatake) && !hd.isOut() && Hounds.find(hatake) == null, "it can be sent home");
        helper.assertTrue(Hounds.recall(hatake) && hd.isOut() && Hounds.find(hatake) != null, "and summoned back");
        hatake.getAbilities().instabuild = false;
        done(helper, hatake);
    }

    // ================================================================ growth

    @GameTest(template = "empty", batch = "noirm_hound_growth")
    public static void growsLevelByLevelAsYouDo(GameTestHelper helper) {
        FakePlayer player = owner(helper, "noirm_hound_grow", "inuzuka");
        ShinobiData data = data(player);
        data.setSpeed(10f);
        data.setTaijutsu(10f);
        NinjaHound hound = puppy(helper, player);
        hound.setXp(HoundGrowth.xpFor(60));
        hound.setBond(100f);
        helper.assertTrue(!Hounds.checkGrowth(hound, player) && hound.houndLevel() == 1,
                "XP and bond aren't enough: it grows as you do (your power is low)");
        float power = HoundGrowth.powerFor(21);
        data.setSpeed(power);
        data.setTaijutsu(power);
        helper.assertTrue(Hounds.checkGrowth(hound, player), "once you're stronger it grows");
        helper.assertTrue(hound.houndLevel() == 21, "exactly as far as your power allows: level " + hound.houndLevel());
        helper.assertTrue(data.hound().level() == 21, "the owner's record follows");
        helper.assertTrue(hound.stage() == HoundStage.GREAT, "past the great-hound milestone (level 20)");
        helper.assertTrue(data.jutsu().knows(Hounds.MAN_BEAST_CLONE) && data.jutsu().knows(Hounds.TWO_HEADED_WOLF),
                "the milestones on the way taught the Inuzuka techniques");
        // No hard cap on getting stronger: a strong enough owner takes it past any fixed stage.
        data.setSpeed(HoundGrowth.powerFor(60));
        data.setTaijutsu(HoundGrowth.powerFor(60));
        Hounds.checkGrowth(hound, player);
        helper.assertTrue(hound.houndLevel() == 60, "and on: level " + hound.houndLevel());
        // Bond grows with feeding and petting; XP with fights.
        NinjaHound young = puppy(helper, player);
        float bond = young.bond();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF, 4));
        young.setHealth(young.getMaxHealth() * 0.5f);
        helper.assertTrue(Hounds.feed(young, player, player.getMainHandItem()), "it eats meat from your hand");
        helper.assertTrue(young.bond() > bond, "and the bond grows");
        bond = young.bond();
        Hounds.pet(young, player);
        helper.assertTrue(young.bond() > bond, "petting grows it too");
        Zombie prey = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2.5f, 1f, 2.5f);
        float xp = young.xp();
        young.doHurtTarget(prey);
        helper.assertTrue(young.xp() > xp, "a bite earns hound XP");
        done(helper, player);
    }

    /** The curve itself: smooth, strictly rising, size bounded, strength forever (more slowly). */
    @GameTest(template = "empty", batch = "noirm_hound_curve")
    public static void growthIsContinuousAndBounded(GameTestHelper helper) {
        for (int lv = 2; lv <= 40; lv++) {
            helper.assertTrue(HoundGrowth.shoulder(lv) > HoundGrowth.shoulder(lv - 1), "size rises every level to 40: " + lv);
            helper.assertTrue(HoundGrowth.width(lv, 1f) > HoundGrowth.width(lv - 1, 1f) && HoundGrowth.height(lv, 1f) > HoundGrowth.height(lv - 1, 1f),
                    "the body grows every level to 40: " + lv);
        }
        for (int lv = 2; lv <= 300; lv++) {
            helper.assertTrue(HoundGrowth.health(lv) > HoundGrowth.health(lv - 1) && HoundGrowth.bite(lv) > HoundGrowth.bite(lv - 1)
                    && HoundGrowth.smell(lv) > HoundGrowth.smell(lv - 1), "strength keeps rising: " + lv);
            helper.assertTrue(HoundGrowth.xpFor(lv) > HoundGrowth.xpFor(lv - 1) && HoundGrowth.powerFor(lv) > HoundGrowth.powerFor(lv - 1),
                    "and each level asks for more: " + lv);
            helper.assertTrue(HoundGrowth.shoulder(lv) <= HoundGrowth.maxShoulder() && HoundGrowth.speed(lv) < 0.37,
                    "size and speed level off: " + lv);
        }
        helper.assertTrue(HoundGrowth.health(40) - HoundGrowth.health(20) < HoundGrowth.health(20) - HoundGrowth.health(1),
                "with diminishing returns");
        helper.assertTrue(HoundGrowth.adulthood(1) == 0f && HoundGrowth.adulthood(HoundGrowth.ADULT_LEVEL) == 1f
                && HoundGrowth.legGrowth(6) > HoundGrowth.headGrowth(6), "puppy proportions blend into adult ones, legs first");
        // Milestones.
        helper.assertTrue(HoundGrowth.canBeCarried(1, 1f) && HoundGrowth.canBeCarried(5, 1f) && !HoundGrowth.canBeCarried(6, 1f),
                "small enough to carry up to level 5");
        helper.assertTrue(!HoundGrowth.canSpin(5) && HoundGrowth.canSpin(6), "Fang Over Fang together from level 6");
        helper.assertTrue(!HoundGrowth.canGuard(11) && HoundGrowth.canGuard(12), "guards alone from level 12");
        helper.assertTrue(!HoundGrowth.canBeRidden(19, 1f) && HoundGrowth.canBeRidden(20, 1f), "ridden from level 20");
        helper.assertTrue(HoundGrowth.height(20, 1f) > EntityType.HORSE.getHeight() && HoundGrowth.width(20, 1f) >= EntityType.HORSE.getWidth() - 0.05f,
                "at level 20 it's as big as a horse");
        helper.assertTrue(HoundGrowth.height(1, 1f) < 0.6f, "a puppy fits on your head");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_hound_size")
    public static void sizeAndStrengthFollowTheLevel(GameTestHelper helper) {
        FakePlayer player = owner(helper, "noirm_hound_size", null);
        NinjaHound hound = puppy(helper, player);
        float w = 0, hgt = 0, health = 0, bite = 0;
        for (int lv = 1; lv <= 40; lv++) {
            hound.setHoundLevel(lv);
            helper.assertTrue(hound.getBbWidth() > w && hound.getBbHeight() > hgt, "level " + lv + " is bigger than the one before");
            helper.assertTrue(hound.getMaxHealth() > health, "level " + lv + " has more health");
            double b = hound.getAttributeValue(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(b > bite, "level " + lv + " bites harder");
            w = hound.getBbWidth();
            hgt = hound.getBbHeight();
            health = hound.getMaxHealth();
            bite = (float) b;
        }
        hound.setHoundLevel(1);
        helper.assertTrue(hound.canBeCarried() && !hound.canBeRidden(), "a puppy rides on you");
        hound.setHoundLevel(20);
        helper.assertTrue(!hound.canBeCarried() && hound.canBeRidden(), "a level-20 hound carries you");
        done(helper, player);
    }

    // ================================================================ traits and looks

    @GameTest(template = "empty", batch = "noirm_hound_traits")
    public static void traitsRollAndMatter(GameTestHelper helper) {
        helper.assertTrue(HoundTraits.all().size() >= 10, "the trait list loaded: " + HoundTraits.all().size());
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 300; i++) {
            java.util.List<String> rolled = HoundTraits.roll(random);
            helper.assertTrue(rolled.size() >= 2 && rolled.size() <= 3, "2-3 traits: " + rolled);
            long weak = rolled.stream().filter(t -> HoundTraits.get(t).weakness()).count();
            helper.assertTrue(weak <= 1, "at most one weakness: " + rolled);
            helper.assertTrue(!(rolled.contains("brave") && rolled.contains("timid")) && !(rolled.contains("big_boned") && rolled.contains("runt")),
                    "no opposites together: " + rolled);
            seen.addAll(rolled);
        }
        helper.assertTrue(seen.size() == HoundTraits.all().size(), "every trait turns up: " + seen);
        java.util.List<String> again1 = HoundTraits.roll(net.minecraft.util.RandomSource.create(7));
        java.util.List<String> again2 = HoundTraits.roll(net.minecraft.util.RandomSource.create(7));
        helper.assertTrue(again1.equals(again2), "the same seed rolls the same traits");

        FakePlayer player = owner(helper, "noirm_hound_traits", null);
        NinjaHound hound = puppy(helper, player);
        hound.setHoundLevel(10);
        hound.setTraits(java.util.List.of());
        double smell = hound.smellRange();
        float width = hound.getBbWidth();
        float health = hound.getMaxHealth();
        hound.setTraits(java.util.List.of("keen_nose", "big_boned"));
        helper.assertTrue(hound.smellRange() > smell, "Keen Nose smells farther");
        helper.assertTrue(hound.getBbWidth() > width && hound.getMaxHealth() > health, "Big-Boned is bigger and tougher");
        hound.setTraits(java.util.List.of("runt"));
        helper.assertTrue(hound.getBbWidth() < width, "a Runt stays small");
        hound.setTraits(java.util.List.of("loyal"));
        float b0 = hound.bond();
        Hounds.addBond(hound, 1f);
        helper.assertTrue(hound.bond() - b0 > 1.2f, "a Loyal hound bonds faster");
        hound.setTraits(java.util.List.of("stubborn"));
        int ignored = 0;
        for (int i = 0; i < 80; i++) {
            if (Hounds.ignores(hound, player)) {
                ignored++;
            }
        }
        helper.assertTrue(ignored > 2 && ignored < 40, "a Stubborn hound sometimes ignores you: " + ignored + "/80");
        done(helper, player);
    }

    @GameTest(template = "empty", batch = "noirm_hound_looks")
    public static void looksAreVariedAndStable(GameTestHelper helper) {
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(3);
        java.util.Set<HoundLook.Breed> breeds = java.util.EnumSet.noneOf(HoundLook.Breed.class);
        java.util.Set<HoundLook.Pattern> patterns = java.util.EnumSet.noneOf(HoundLook.Pattern.class);
        java.util.Set<HoundLook.Ears> ears = java.util.EnumSet.noneOf(HoundLook.Ears.class);
        java.util.Set<HoundLook.Tail> tails = java.util.EnumSet.noneOf(HoundLook.Tail.class);
        java.util.Set<Integer> colours = new java.util.HashSet<>();
        for (int i = 0; i < 400; i++) {
            HoundLook look = HoundLook.roll(random);
            helper.assertTrue(HoundLook.unpack(look.pack()).equals(look), "a look survives packing: " + look);
            breeds.add(look.breed());
            patterns.add(look.pattern());
            ears.add(look.ears());
            tails.add(look.tail());
            colours.add(look.base());
        }
        helper.assertTrue(breeds.size() == HoundLook.Breed.values().length && patterns.size() == HoundLook.Pattern.values().length
                && ears.size() == 3 && tails.size() == 3, "every breed, pattern, ear and tail turns up");
        helper.assertTrue(colours.size() >= 12, "many colours: " + colours.size());
        helper.assertTrue(HoundLook.roll(net.minecraft.util.RandomSource.create(9)).equals(HoundLook.roll(net.minecraft.util.RandomSource.create(9))),
                "deterministic per seed");
        helper.succeed();
    }

    // ================================================================ orders

    @GameTest(template = "empty", batch = "noirm_hound_orders")
    public static void ordersCarryAndRide(GameTestHelper helper) {
        FakePlayer player = owner(helper, "noirm_hound_orders", null);
        NinjaHound hound = puppy(helper, player);
        helper.assertTrue(Hounds.command(player, "sit") && hound.order() == HoundOrder.SIT && hound.isOrderedToSit(), "sit");
        helper.assertTrue(Hounds.command(player, "guard") && hound.order() == HoundOrder.SIT, "a puppy is too young to guard: it sits");
        helper.assertTrue(Hounds.command(player, "follow") && hound.order() == HoundOrder.FOLLOW && !hound.isOrderedToSit(), "follow");
        helper.assertTrue(Hounds.command(player, "carry") && hound.isCarried() && hound.carry() == NinjaHound.Carry.HEAD
                && hound.getVehicle() == player, "the puppy hops onto your head");
        helper.assertTrue(Hounds.command(player, "jacket") && hound.carry() == NinjaHound.Carry.JACKET, "or into your jacket");
        helper.assertTrue(!hound.hurt(hound.damageSources().generic(), 5f), "tucked in, it takes no blows");
        helper.assertTrue(Hounds.command(player, "jacket") && !hound.isCarried(), "and comes out again");
        helper.assertTrue(!Hounds.command(player, "ride"), "you can't ride a puppy");
        hound.setStage(HoundStage.GROWN);
        helper.assertTrue(!Hounds.command(player, "carry"), "a grown hound can't be carried");
        helper.assertTrue(Hounds.command(player, "guard") && hound.order() == HoundOrder.GUARD && hound.guardPost() != null, "a grown hound guards");
        hound.setStage(HoundStage.GREAT);
        helper.assertTrue(Hounds.command(player, "ride") && player.getVehicle() == hound, "you ride the great hound");
        helper.assertTrue(hound.getControllingPassenger() == player, "and steer it");
        player.stopRiding();
        Zombie enemy = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2.5f, 1f, 2.5f);
        hound.setTarget(enemy);
        helper.assertTrue(Hounds.command(player, "come") && hound.getTarget() == null && hound.order() == HoundOrder.FOLLOW, "come drops the fight");
        done(helper, player);
    }

    // ================================================================ smell

    @GameTest(template = "empty", batch = "noirm_hound_smell")
    public static void smellMarksAHiddenEnemy(GameTestHelper helper) {
        FakePlayer player = owner(helper, "noirm_hound_smell", null);
        NinjaHound hound = puppy(helper, player);
        Zombie hidden = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 0.5f, 1f, 2.5f);
        hidden.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false));
        hidden.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false));
        Zombie seen = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2.5f, 1f, 0.5f);
        seen.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false));
        helper.assertTrue(HoundSmell.hidden(hidden) && !HoundSmell.hidden(seen), "one hides from the eyes, one doesn't");
        int found = HoundSmell.sniff(hound, player);
        helper.assertTrue(found == 1, "the hound smells exactly the hidden one: " + found);
        helper.assertTrue(hidden.hasEffect(MobEffects.GLOWING), "and marks it with a glow");
        helper.assertTrue(!seen.hasEffect(MobEffects.GLOWING), "the visible one isn't marked");
        helper.assertTrue(HoundSmell.findScent(hound, player) != null, "it can pick up a scent to track");
        helper.assertTrue(Hounds.command(player, "track") && hound.tracking() != null, "and follows it on your order");
        done(helper, player);
    }

    // ================================================================ Fang Over Fang

    @GameTest(template = "empty", batch = "noirm_hound_fang", timeoutTicks = 80)
    public static void fangOverFangHitsWithBoth(GameTestHelper helper) {
        openFloor(helper, -2, -2, 6, 12, 4);
        FakePlayer player = owner(helper, "noirm_hound_fang", "inuzuka");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        NinjaHound hound = puppy(helper, player);
        hound.setStage(HoundStage.GROWN);
        Vec3 side = helper.absoluteVec(new Vec3(3.5, 1, 0.5));
        hound.moveTo(side.x, side.y, side.z, 0f, 0f);
        SparringNinja target = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 2.0f);
        target.setInvulnerable(false);
        face(player, target);
        float before = target.getHealth();
        HoundTechniques.fangOverFang(new JutsuContext(player, FANG, JutsuData.get(FANG), 1f, 1f));
        helper.assertTrue(HoundTechniques.drilling(player), "you spin into a drill");
        helper.assertTrue(hound.spinTicks() > 0, "and your hound spins beside you");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(HoundTechniques.hitsBy(player) > 0, "your drill hit");
            helper.assertTrue(HoundTechniques.hitsBy(hound) > 0, "the hound's drill hit");
            helper.assertTrue(target.getHealth() < before, "the target took both");
            data(player).hound().clear();
            clearStrays(helper, 20);
            helper.succeed();
        });
    }

    // ================================================================ knockout

    @GameTest(template = "empty", batch = "noirm_hound_down", timeoutTicks = 120)
    public static void knockedOutNotKilled(GameTestHelper helper) {
        FakePlayer player = owner(helper, "noirm_hound_down", null);
        NinjaHound hound = puppy(helper, player);
        hound.hurt(hound.damageSources().generic(), 1000f);
        helper.assertTrue(hound.isAlive() && hound.isDown(), "a killing blow only knocks it out");
        helper.assertTrue(!hound.hurt(hound.damageSources().generic(), 5f), "it isn't hit again while down");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
        helper.assertTrue(Hounds.feed(hound, player, player.getMainHandItem()) && !hound.isDown(), "a treat wakes it at once");
        helper.assertTrue(hound.getHealth() >= hound.getMaxHealth() * NinjaHound.REVIVE_HEALTH - 0.01f, "with half its health");
        hound.invulnerableTime = 0; // (past the vanilla hurt cooldown)
        hound.hurt(hound.damageSources().generic(), 1000f);
        helper.assertTrue(hound.isDown(), "down again");
        hound.setDownTicks(10);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!hound.isDown() && hound.isAlive(), "and it gets up by itself after its rest");
            // Only the void or /kill end the body: then it comes back from your record after a rest.
            hound.kill();
            HoundData hd = data(player).hound();
            helper.assertTrue(hd.has() && hd.downUntil() > helper.getLevel().getGameTime() && hd.entityId() == null, "lost, it rests and stays yours");
            hd.setDownUntil(0);
            Hounds.tickOwner(player, data(player));
            NinjaHound back = Hounds.find(player);
            helper.assertTrue(back != null && back.isAlive() && back.stage() == HoundStage.PUPPY, "and comes back to you");
            done(helper, player);
        });
    }

    // ================================================================ saving

    @GameTest(template = "empty", batch = "noirm_hound_save")
    public static void savedWithItsOwner(GameTestHelper helper) {
        FakePlayer player = owner(helper, "noirm_hound_save", null);
        NinjaHound hound = puppy(helper, player);
        hound.setStage(HoundStage.GROWN);
        hound.setBond(55f);
        hound.setXp(500f);
        HoundLook look = new HoundLook(HoundLook.Breed.SPITZ, HoundLook.Ears.FLOPPY, HoundLook.Tail.CURLED, HoundLook.Pattern.SADDLE, 3, 14, 5, 2);
        hound.setLook(look);
        String name = hound.getCustomName().getString();
        Hounds.snapshot(hound);
        // The entity's own save.
        CompoundTag tag = new CompoundTag();
        hound.saveWithoutId(tag);
        NinjaHound copy = HoundRegistry.NINJA_HOUND.get().create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.stage() == HoundStage.GROWN && copy.look().equals(look) && copy.traits().equals(hound.traits()) && Math.abs(copy.bond() - 55f) < 0.01f
                && Math.abs(copy.xp() - 500f) < 0.01f, "the hound's save keeps stage, coat, bond and XP");
        helper.assertTrue(player.getUUID().equals(copy.getOwnerUUID()), "and its owner");
        // The owner's save: log out, lose the entity, log back in.
        CompoundTag saved = data(player).save();
        hound.discard();
        data(player).hound().clear();
        data(player).load(saved);
        HoundData hd = data(player).hound();
        helper.assertTrue(hd.has() && hd.stage() == HoundStage.GROWN && hd.look().equals(look) && name.equals(hd.name()), "the owner's record survives a save");
        Hounds.tickOwner(player, data(player));
        NinjaHound back = Hounds.find(player);
        helper.assertTrue(back != null && back != hound && back.stage() == HoundStage.GROWN && back.look().equals(look) && back.traits().equals(java.util.List.of("loyal"))
                && Math.abs(back.bond() - 55f) < 0.01f && name.equals(back.getCustomName().getString()), "the same hound comes back beside you");
        // Far away (or in another world) and following: it comes to you like a vanilla pet.
        Vec3 far = back.position().add(80, 0, 0);
        back.teleportTo(far.x, far.y, far.z);
        Hounds.tickOwner(player, data(player));
        NinjaHound near = Hounds.find(player);
        helper.assertTrue(near != null && near.distanceTo(player) < 4, "left far behind, it's back at your side");
        done(helper, player);
    }

    // ================================================================ NPC Inuzuka

    @GameTest(template = "empty", batch = "noirm_hound_npc")
    public static void inuzukaNinjasBringTheirHounds(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(ClanData.canon("inuzuka").orElseThrow());
        ninja.getPersistentData().putBoolean(HoundEvents.NPC_TEST_OK, true);
        NinjaHound hound = HoundEvents.npcHound(ninja);
        helper.assertTrue(hound != null && hound.isAlive(), "an Inuzuka ninja brings a hound");
        helper.assertTrue(ninja.getUUID().equals(hound.getOwnerUUID()) && hound.getOwner() == ninja, "its own");
        helper.assertTrue(Crew.sameSide(ninja, hound), "fighting on its side");
        helper.assertTrue(hound.stage().ordinal() >= HoundStage.YOUNG.ordinal(), "grown enough to fight: " + hound.stage());
        helper.assertTrue(Hounds.houndOf(ninja) == hound, "and the ninja knows its hound (for Fang Over Fang)");
        helper.assertTrue(HoundEvents.npcHound(ninja) == null, "only one");
        SparringNinja other = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        other.joinClan(ClanData.canon("uchiha").orElseThrow());
        other.getPersistentData().putBoolean(HoundEvents.NPC_TEST_OK, true);
        helper.assertTrue(HoundEvents.npcHound(other) == null, "an Uchiha brings none");
        clearAround(helper);
    }
}

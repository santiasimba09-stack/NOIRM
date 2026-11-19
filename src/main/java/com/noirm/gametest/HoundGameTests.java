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
        NinjaHound hound = Hounds.grantPuppy(player, 0, false);
        helper.assertTrue(hound != null, "a puppy was placed");
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
    public static void growsThroughTheStagesAsYouDo(GameTestHelper helper) {
        FakePlayer player = owner(helper, "noirm_hound_grow", null);
        ShinobiData data = data(player);
        data.setSpeed(10f);
        data.setTaijutsu(10f);
        NinjaHound hound = puppy(helper, player);
        hound.setXp(HoundStage.GIANT.xpNeeded);
        hound.setBond(100f);
        helper.assertTrue(!Hounds.checkGrowth(hound, player), "XP and bond aren't enough: it grows as you do (your power is low)");
        helper.assertTrue(hound.stage() == HoundStage.PUPPY, "still a puppy");
        HoundStage last = HoundStage.PUPPY;
        for (HoundStage next : new HoundStage[]{HoundStage.YOUNG, HoundStage.GROWN, HoundStage.GREAT, HoundStage.GIANT}) {
            data.setSpeed(next.ownerPowerNeeded);
            data.setTaijutsu(next.ownerPowerNeeded);
            helper.assertTrue(Hounds.checkGrowth(hound, player), "grows into " + next + " once you're strong enough");
            helper.assertTrue(hound.stage() == next, "one stage at a time: " + hound.stage());
            helper.assertTrue(!Hounds.checkGrowth(hound, player), "and no further than you allow");
            helper.assertTrue(data.hound().stage() == next, "the owner's record follows");
            last = next;
        }
        helper.assertTrue(last == HoundStage.GIANT && hound.stage().next() == null, "the giant is the top");
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

    @GameTest(template = "empty", batch = "noirm_hound_size")
    public static void sizeAndStrengthScaleWithTheStage(GameTestHelper helper) {
        FakePlayer player = owner(helper, "noirm_hound_size", null);
        NinjaHound hound = puppy(helper, player);
        float w = 0, hgt = 0, health = 0, bite = 0;
        for (HoundStage s : HoundStage.values()) {
            hound.setStage(s);
            helper.assertTrue(hound.getBbWidth() > w && hound.getBbHeight() > hgt, s + " is bigger than the stage before");
            helper.assertTrue(hound.getMaxHealth() > health, s + " has more health");
            double b = hound.getAttributeValue(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(b > bite, s + " bites harder");
            w = hound.getBbWidth();
            hgt = hound.getBbHeight();
            health = hound.getMaxHealth();
            bite = (float) b;
        }
        helper.assertTrue(HoundStage.PUPPY.canBeCarried() && !HoundStage.YOUNG.canBeCarried(), "only a puppy rides on you");
        helper.assertTrue(HoundStage.GREAT.height > EntityType.HORSE.getHeight() && HoundStage.GREAT.width >= EntityType.HORSE.getWidth(),
                "the great hound is bigger than a horse");
        helper.assertTrue(HoundStage.GREAT.canBeRidden() && !HoundStage.GROWN.canBeRidden(), "and only it (and the giant) can be ridden");
        helper.assertTrue(HoundStage.PUPPY.height < 0.6f, "a puppy fits on your head");
        done(helper, player);
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
        SparringNinja target = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 4.5f);
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
        hound.setCoat(2);
        String name = hound.getCustomName().getString();
        Hounds.snapshot(hound);
        // The entity's own save.
        CompoundTag tag = new CompoundTag();
        hound.saveWithoutId(tag);
        NinjaHound copy = HoundRegistry.NINJA_HOUND.get().create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.stage() == HoundStage.GROWN && copy.coat() == 2 && Math.abs(copy.bond() - 55f) < 0.01f
                && Math.abs(copy.xp() - 500f) < 0.01f, "the hound's save keeps stage, coat, bond and XP");
        helper.assertTrue(player.getUUID().equals(copy.getOwnerUUID()), "and its owner");
        // The owner's save: log out, lose the entity, log back in.
        CompoundTag saved = data(player).save();
        hound.discard();
        data(player).hound().clear();
        data(player).load(saved);
        HoundData hd = data(player).hound();
        helper.assertTrue(hd.has() && hd.stage() == HoundStage.GROWN && hd.coat() == 2 && name.equals(hd.name()), "the owner's record survives a save");
        Hounds.tickOwner(player, data(player));
        NinjaHound back = Hounds.find(player);
        helper.assertTrue(back != null && back != hound && back.stage() == HoundStage.GROWN && back.coat() == 2
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

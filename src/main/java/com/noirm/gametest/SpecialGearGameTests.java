package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.combat.CombatData;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.gear.ClanOutfits;
import com.noirm.gear.GearItem;
import com.noirm.gear.GearPerk;
import com.noirm.gear.GearPerks;
import com.noirm.gear.NoirmGear;
import com.noirm.player.ShinobiCapability;
import com.noirm.registry.NoirmEntities;
import com.noirm.weapon.Moveset;
import com.noirm.weapon.NinjaArsenal;
import com.noirm.weapon.SpecialWeaponItem;
import com.noirm.weapon.SpecialWeapons;
import com.noirm.weapon.WeaponMoves;
import com.noirm.world.village.VillagePalette;
import com.noirm.world.village.VillageResidents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The second gear set (perks, who wears what) and the special weapons' effects. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpecialGearGameTests {
    private SpecialGearGameTests() {}

    /** Every new piece is light gear with a recipe; masks hide names, cloaks speed sprints, wraps hit harder, tabi soften falls. */
    @GameTest(template = "empty", batch = "noirm_gear_perks")
    public static void gearPerksWork(GameTestHelper helper) {
        helper.assertTrue(NoirmGear.ALL.size() >= 55, "at least 55 gear pieces, got " + NoirmGear.ALL.size());
        int heaviest = 0;
        for (RegistryObject<Item> piece : NoirmGear.ALL) {
            GearItem gear = (GearItem) piece.get();
            if (gear.getMaterial() != com.noirm.gear.GearMaterial.SENJU) {
                heaviest = Math.max(heaviest, gear.getDefense());
            }
        }
        helper.assertTrue(heaviest <= 6, "ninja gear stays light (best non-Senju piece " + heaviest + " ≤ iron's 6)");
        // Masks hide the name tag; a forehead protector doesn't.
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(TestClans.PLAIN);
        helper.assertFalse(GearPerks.hidesName(ninja), "bare face shows the name");
        ninja.setItemSlot(EquipmentSlot.HEAD, new ItemStack(NoirmGear.MASK_WOLF.get()));
        helper.assertTrue(GearPerks.hidesName(ninja), "a wolf mask hides the name");
        ninja.setItemSlot(EquipmentSlot.HEAD, new ItemStack(NoirmGear.ANBU_MASK.get()));
        helper.assertTrue(GearPerks.hidesName(ninja), "the ANBU mask hides the name");
        ninja.setItemSlot(EquipmentSlot.HEAD, new ItemStack(NoirmGear.FOREHEAD_PROTECTOR_SLASHED.get()));
        helper.assertFalse(GearPerks.hidesName(ninja), "a forehead protector doesn't");
        // Cloak: faster only while sprinting.
        ninja.setItemSlot(EquipmentSlot.CHEST, new ItemStack(NoirmGear.ROGUE_CLOAK.get()));
        double walk = ninja.getAttributeValue(Attributes.MOVEMENT_SPEED);
        ninja.setSprinting(true);
        GearPerks.updateSwift(ninja);
        helper.assertTrue(GearPerks.hasSwiftBonus(ninja), "sprinting in a cloak gets the bonus");
        double sprint = ninja.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(sprint > walk * 1.1, "…and it's faster: " + walk + " -> " + sprint);
        ninja.setSprinting(false);
        GearPerks.updateSwift(ninja);
        helper.assertFalse(GearPerks.hasSwiftBonus(ninja), "and loses it when it stops");
        ninja.discard();
        // Hand wraps add attack damage while worn.
        var mods = NoirmGear.HAND_WRAPS.get().getAttributeModifiers(EquipmentSlot.CHEST, new ItemStack(NoirmGear.HAND_WRAPS.get()));
        double fist = mods.get(Attributes.ATTACK_DAMAGE).stream().mapToDouble(m -> m.getAmount()).sum();
        helper.assertTrue(Math.abs(fist - GearPerks.HAND_WRAPS_DAMAGE) < 1e-4, "hand wraps: +" + GearPerks.HAND_WRAPS_DAMAGE + " damage, got " + fist);
        helper.assertTrue(NoirmGear.HAND_WRAPS.get().getAttributeModifiers(EquipmentSlot.MAINHAND, new ItemStack(NoirmGear.HAND_WRAPS.get()))
                .get(Attributes.ATTACK_DAMAGE).isEmpty(), "only when worn");
        // Tabi: soft landings.
        Player player = helper.makeMockPlayer();
        float[] bare = net.minecraftforge.common.ForgeHooks.onLivingFall(player, 10f, 1f);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(NoirmGear.TABI.get()));
        float[] soft = net.minecraftforge.common.ForgeHooks.onLivingFall(player, 10f, 1f);
        helper.assertTrue(bare != null && soft != null && soft[0] <= bare[0] - GearPerks.SOFT_STEP_BLOCKS + 0.01f,
                "tabi take " + GearPerks.SOFT_STEP_BLOCKS + " blocks off a fall: " + (bare == null ? "?" : bare[0]) + " -> " + (soft == null ? "?" : soft[0]));
        helper.assertTrue(((GearItem) NoirmGear.WARAJI.get()).perk() == GearPerk.SOFT_STEP, "straw sandals too");
        helper.succeed();
    }

    /** Guards wear their village's vest, the Kage its hat, patrols are often ANBU; rogues wear cloaks; rogue swordsmen sometimes carry legends. */
    @GameTest(template = "empty", batch = "noirm_gear_roles")
    public static void villageRolesAndRoguesDress(GameTestHelper helper) {
        RandomSource r = RandomSource.create(11);
        Set<Item> vests = new HashSet<>();
        Set<Item> hats = new HashSet<>();
        for (VillagePalette palette : VillagePalette.values()) {
            Map<EquipmentSlot, Item> guard = ClanOutfits.residentOutfit("GUARD", palette, r);
            Item chest = guard.get(EquipmentSlot.CHEST);
            helper.assertTrue(chest == ClanOutfits.villageVest(palette) || chest == NoirmGear.JONIN_VEST.get(),
                    palette + " guards wear their vest, got " + chest);
            helper.assertTrue(guard.containsKey(EquipmentSlot.HEAD) && guard.containsKey(EquipmentSlot.FEET), palette + " guards are fully dressed");
            vests.add(ClanOutfits.villageVest(palette));
            Map<EquipmentSlot, Item> kage = ClanOutfits.residentOutfit("KAGE", palette, r);
            helper.assertTrue(kage.get(EquipmentSlot.CHEST) == NoirmGear.KAGE_ROBE.get(), "the Kage wears the robe");
            hats.add(kage.get(EquipmentSlot.HEAD));
        }
        helper.assertTrue(vests.size() == VillagePalette.values().length && hats.size() == VillagePalette.values().length,
                "each village look has its own vest and Kage hat");
        helper.assertTrue(ClanOutfits.residentOutfit("TRAINEE", VillagePalette.LEAF, r).isEmpty(), "other residents keep their clan gear");
        int anbu = 0;
        Set<Item> masks = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            Map<EquipmentSlot, Item> patrol = ClanOutfits.residentOutfit("PATROL", VillagePalette.MIST, r);
            if (patrol.get(EquipmentSlot.CHEST) == NoirmGear.ANBU_ARMOR.get()) {
                anbu++;
                masks.add(patrol.get(EquipmentSlot.HEAD));
            }
        }
        helper.assertTrue(anbu > 40 && anbu < 120 && masks.size() >= 4, "patrols are often ANBU (" + anbu + "/200) in several masks (" + masks.size() + ")");
        int rogueCloaks = 0;
        for (int i = 0; i < 100; i++) {
            Item chest = ClanOutfits.rogue(ClanOutfits.roll("some_clan", r), r).get(EquipmentSlot.CHEST);
            if (chest == NoirmGear.ROGUE_CLOAK.get()) {
                rogueCloaks++;
            } else {
                helper.assertTrue(chest == NoirmGear.TRAVEL_CLOAK.get(), "rogues wear cloaks, got " + chest);
            }
        }
        helper.assertTrue(rogueCloaks > 40, "mostly the rogue cloak: " + rogueCloaks);
        // Legendary blades: only rogues, and rarely.
        int legends = 0;
        for (int i = 0; i < 6000; i++) {
            Item resident = NinjaArsenal.mainWeapon(false, r);
            helper.assertFalse(resident != null && SpecialWeapons.LEGENDARY.stream().anyMatch(l -> l.get() == resident), "residents never carry legends");
            Item rogue = NinjaArsenal.mainWeapon(true, r);
            if (rogue != null && SpecialWeapons.LEGENDARY.stream().anyMatch(l -> l.get() == rogue)) {
                legends++;
            }
        }
        helper.assertTrue(legends > 20 && legends < 200, "about 1 rogue in 85 carries a legend: " + legends + "/6000");
        // A village's Kage spawns dressed in its look.
        SparringNinja kage = VillageResidents.kage(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), 100, 100,
                VillagePalette.MIST.kageTitle, VillagePalette.MIST);
        helper.assertTrue(kage != null && kage.getItemBySlot(EquipmentSlot.HEAD).is(NoirmGear.KAGE_HAT_TEAL.get())
                && kage.getItemBySlot(EquipmentSlot.CHEST).is(NoirmGear.KAGE_ROBE.get()), "the Mizukage wears the teal hat and robe");
        helper.assertTrue(kage.gearRolled(), "and won't be re-dressed by their clan");
        kage.discard();
        helper.succeed();
    }

    /** Recipes, movesets and reach of the special weapons. */
    @GameTest(template = "empty", batch = "noirm_gear_roles")
    public static void specialWeaponsBasics(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(SpecialWeapons.ALL.size() == 6 && SpecialWeapons.LEGENDARY.size() == 4, "six special weapons, four legendary");
        for (var weapon : SpecialWeapons.ALL) {
            boolean legendary = SpecialWeapons.LEGENDARY.contains(weapon);
            helper.assertTrue(recipes.byKey(weapon.getId()).isPresent() != legendary,
                    weapon.getId() + (legendary ? " is drop-only" : " has a recipe"));
            helper.assertTrue(WeaponMoves.movesetFor(new ItemStack(weapon.get())) != null, weapon.getId() + " fights with a moveset");
        }
        helper.assertTrue(WeaponMoves.movesetFor(new ItemStack(SpecialWeapons.THUNDER_FANGS.get())) == Moveset.KUNAI, "fangs: kunai moveset");
        helper.assertTrue(WeaponMoves.movesetFor(new ItemStack(SpecialWeapons.KUSARIGAMA.get())) == Moveset.STAFF, "kusarigama: staff moveset");
        double reach = SpecialWeapons.KUSARIGAMA.get().getAttributeModifiers(EquipmentSlot.MAINHAND, new ItemStack(SpecialWeapons.KUSARIGAMA.get()))
                .get(ForgeMod.ENTITY_REACH.get()).stream().mapToDouble(m -> m.getAmount()).sum();
        helper.assertTrue(reach >= 2, "the chain reaches 2 blocks further: " + reach);
        double fast = SpecialWeapons.REEDCUTTER.get().getAttributeModifiers(EquipmentSlot.MAINHAND, new ItemStack(SpecialWeapons.REEDCUTTER.get()))
                .get(Attributes.ATTACK_SPEED).stream().mapToDouble(m -> m.getAmount()).sum();
        double slow = SpecialWeapons.CRESCENT_CLEAVER.get().getAttributeModifiers(EquipmentSlot.MAINHAND, new ItemStack(SpecialWeapons.CRESCENT_CLEAVER.get()))
                .get(Attributes.ATTACK_SPEED).stream().mapToDouble(m -> m.getAmount()).sum();
        helper.assertTrue(fast > slow + 1, "the reedcutter swings far faster than the cleaver");
        helper.assertTrue(new ItemStack(SpecialWeapons.GALE_GUNBAI.get()).canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK),
                "the gunbai guards like a shield");
        helper.succeed();
    }

    /** The Crescent Cleaver heals its wielder and mends itself. */
    @GameTest(template = "empty", batch = "noirm_special_cleaver")
    public static void cleaverHealsWielder(GameTestHelper helper) {
        floor(helper);
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.5f);
        target.setInvulnerable(false);
        ItemStack cleaver = new ItemStack(SpecialWeapons.CRESCENT_CLEAVER.get());
        cleaver.setDamageValue(100);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
        attacker.setHealth(10f);
        target.hurt(attacker.damageSources().mobAttack(attacker), 8f);
        float healed = attacker.getHealth() - 10f;
        helper.assertTrue(healed > 1f && healed <= SpecialWeapons.LIFESTEAL_MAX, "healed 20% of 8: " + healed);
        helper.assertTrue(attacker.getMainHandItem().getDamageValue() == 100 - SpecialWeapons.LIFESTEAL_MEND, "the blade mends itself");
        clearAround(helper);
    }

    /** The Sharkskin Greatsword eats the target's chakra and feeds the wielder. */
    @GameTest(template = "empty", batch = "noirm_special_drain")
    public static void sharkskinDrainsChakra(GameTestHelper helper) {
        Player wielder = helper.makeMockPlayer();
        Player victim = helper.makeMockPlayer();
        wielder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SpecialWeapons.SHARKSKIN_GREATSWORD.get()));
        var w = ShinobiCapability.get(wielder).orElseThrow(IllegalStateException::new);
        var v = ShinobiCapability.get(victim).orElseThrow(IllegalStateException::new);
        w.setChakra(20f);
        v.setChakra(v.getMaxChakra());
        float before = v.getChakra();
        victim.hurt(victim.damageSources().playerAttack(wielder), 3f);
        helper.assertTrue(Math.abs(before - v.getChakra() - SpecialWeapons.DRAIN_CHAKRA) < 0.01f, "the victim lost 8 chakra: " + before + " -> " + v.getChakra());
        helper.assertTrue(Math.abs(w.getChakra() - 28f) < 0.01f, "the wielder gained it: " + w.getChakra());
        helper.succeed();
    }

    /** Thunder Fangs stun what they cut. */
    @GameTest(template = "empty", batch = "noirm_special_thunder")
    public static void thunderFangsStun(GameTestHelper helper) {
        floor(helper);
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.5f);
        target.setInvulnerable(false);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SpecialWeapons.THUNDER_FANGS.get()));
        helper.assertFalse(CombatSystem.isStaggered(target), "not stunned yet");
        target.hurt(attacker.damageSources().mobAttack(attacker), 3f);
        helper.assertTrue(CombatSystem.isStaggered(target), "lightning numbs the target");
        clearAround(helper);
    }

    /** The gunbai blows enemies back, and its guard turns arrows around. */
    @GameTest(template = "empty", batch = "noirm_special_gale", timeoutTicks = 60)
    public static void gunbaiBlowsBackAndReflects(GameTestHelper helper) {
        floor(helper);
        SparringNinja defender = ninja(helper, 1.5f, 0.5f);
        SparringNinja shooter = ninja(helper, 1.5f, 2.5f);
        shooter.setInvulnerable(false);
        defender.setInvulnerable(false);
        face(defender, shooter);
        face(shooter, defender);
        defender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SpecialWeapons.GALE_GUNBAI.get()));
        shooter.hurt(defender.damageSources().mobAttack(defender), 2f);
        Vec3 push = shooter.getDeltaMovement();
        helper.assertTrue(push.z > 0.7, "the fan blows the target back hard: " + push);
        shooter.setDeltaMovement(Vec3.ZERO);
        shooter.setPos(helper.absoluteVec(new Vec3(1.5, 1, 2.5)));
        shooter.setHealth(shooter.getMaxHealth());
        // Guard, and an arrow comes in from the front.
        defender.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(SpecialWeaponItem.guardsWithFan(defender), "the fan is raised");
        Arrow arrow = new Arrow(helper.getLevel(), shooter);
        arrow.setPos(helper.absoluteVec(new Vec3(1.5, 1.9, 1.7)));
        arrow.shoot(0, 0, -1, 1.0f, 0f);
        helper.getLevel().addFreshEntity(arrow);
        float health = defender.getHealth();
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(defender.getHealth() >= health, "the guard took no damage");
            helper.assertTrue(arrow.getOwner() == defender, "the arrow now belongs to the defender");
            helper.assertTrue(arrow.isRemoved() || arrow.getZ() > helper.absoluteVec(new Vec3(0, 0, 1.0)).z,
                    "and flies back past where it was turned: " + arrow.position());
            helper.getLevel().getEntitiesOfClass(Arrow.class, new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(16)).forEach(Arrow::discard);
            clearAround(helper);
        });
    }

    /** The kusarigama drags a distant target in. */
    @GameTest(template = "empty", batch = "noirm_special_chain")
    public static void kusarigamaPullsIn(GameTestHelper helper) {
        floor(helper);
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 4.5f);
        target.setInvulnerable(false);
        face(attacker, target);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SpecialWeapons.KUSARIGAMA.get()));
        target.hurt(attacker.damageSources().mobAttack(attacker), 2f);
        Vec3 motion = target.getDeltaMovement();
        helper.assertTrue(motion.z < -0.3, "the chain drags the target towards the wielder: " + motion);
        clearAround(helper);
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        return ninja;
    }
}

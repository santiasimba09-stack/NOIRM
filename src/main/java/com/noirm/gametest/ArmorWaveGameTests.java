package com.noirm.gametest;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.gear.ClanOutfits;
import com.noirm.gear.GearItem;
import com.noirm.gear.GearMaterial;
import com.noirm.gear.GearPerk;
import com.noirm.gear.GearPerks;
import com.noirm.gear.NoirmGear;
import com.noirm.registry.NoirmEntities;
import com.noirm.world.village.VillagePalette;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The armor wave (round 16): new pieces, their perks, kits and who wears them. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmorWaveGameTests {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ArmorWaveGameTests() {}

    /** Every piece has armour and a recipe; samurai plate is iron-strong and slows; mesh is light but tough. */
    @GameTest(template = "empty", batch = "noirm_armor_wave")
    public static void armorWavePiecesAndStats(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        Set<ArmorItem.Type> types = new HashSet<>();
        for (RegistryObject<Item> piece : NoirmGear.armorWave()) {
            GearItem gear = (GearItem) piece.get();
            helper.assertTrue(gear.getDefense() > 0, piece.getId() + " gives armour");
            helper.assertTrue(recipes.byKey(piece.getId()).isPresent(), "a recipe for " + piece.getId());
            types.add(gear.getType());
        }
        helper.assertTrue(types.size() == 4, "the wave covers head, chest, legs and feet");
        for (RegistryObject<Item> piece : NoirmGear.samuraiSet()) {
            GearItem gear = (GearItem) piece.get();
            helper.assertTrue(gear.getMaterial() == GearMaterial.SAMURAI && gear.perk() == GearPerk.HEAVY, piece.getId() + " is heavy plate");
        }
        GearItem cuirass = (GearItem) NoirmGear.SAMURAI_CUIRASS.get();
        GearItem mesh = (GearItem) NoirmGear.MESH_SHIRT.get();
        GearItem shirt = (GearItem) NoirmGear.TRAINING_SHIRT.get();
        helper.assertTrue(cuirass.getDefense() == 6 && cuirass.getToughness() > mesh.getToughness(), "the cuirass is iron-strong and tough");
        helper.assertTrue(mesh.getDefense() == shirt.getDefense() && mesh.getToughness() > shirt.getToughness(),
                "mesh: a shirt's armour plus toughness");
        helper.succeed();
    }

    /** HEAVY slows, SHADOW hides you from monsters, MENDING heals, the hood and half mask hide your name. */
    @GameTest(template = "empty", batch = "noirm_armor_wave_perks")
    public static void armorWavePerksWork(GameTestHelper helper) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(TestClans.PLAIN);
        double bare = ninja.getAttributeValue(Attributes.MOVEMENT_SPEED);
        var set = NoirmGear.samuraiSet();
        for (int i = 0; i < 4; i++) {
            ItemStack stack = new ItemStack(set.get(i).get());
            ninja.setItemSlot(ARMOR[i], stack);
            // What vanilla does on the entity's next tick when its equipment changes.
            ninja.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(ARMOR[i]));
        }
        double heavy = ninja.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double expected = bare * (1 - 4 * GearPerks.HEAVY_SLOW);
        helper.assertTrue(heavy < bare * 0.95 && Math.abs(heavy - expected) < bare * 0.03,
                "a full samurai set slows about " + (int) (400 * GearPerks.HEAVY_SLOW) + "%: " + bare + " -> " + heavy);
        helper.assertTrue(ninja.getArmorValue() >= 16, "and armours a lot: " + ninja.getArmorValue());
        for (EquipmentSlot slot : ARMOR) {
            ninja.getAttributes().removeAttributeModifiers(ninja.getItemBySlot(slot).getAttributeModifiers(slot));
            ninja.setItemSlot(slot, ItemStack.EMPTY);
        }
        helper.assertTrue(Math.abs(ninja.getAttributeValue(Attributes.MOVEMENT_SPEED) - bare) < 1e-6, "back to full speed undressed");

        // Assassin's black: monsters notice you from less far.
        Zombie zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, 0.5f, 1f, 0.5f);
        double seen = ForgeHooks.getEntityVisibilityMultiplier(ninja, zombie, 1.0);
        ninja.setItemSlot(EquipmentSlot.CHEST, new ItemStack(NoirmGear.ASSASSIN_SUIT.get()));
        ninja.setItemSlot(EquipmentSlot.LEGS, new ItemStack(NoirmGear.ASSASSIN_PANTS.get()));
        double hidden = ForgeHooks.getEntityVisibilityMultiplier(ninja, zombie, 1.0);
        helper.assertTrue(Math.abs(hidden - seen * GearPerks.SHADOW_VISIBILITY * GearPerks.SHADOW_VISIBILITY) < 1e-6,
                "the assassin suit and trousers hide you: " + seen + " -> " + hidden);
        zombie.discard();

        // Masks: the hood and the half mask hide the name tag.
        ninja.setItemSlot(EquipmentSlot.HEAD, new ItemStack(NoirmGear.TATTERED_HOOD.get()));
        helper.assertTrue(GearPerks.hidesName(ninja), "the hood hides the name");
        ninja.setItemSlot(EquipmentSlot.HEAD, new ItemStack(NoirmGear.HALF_MASK.get()));
        helper.assertTrue(GearPerks.hidesName(ninja), "the half mask hides the name");
        ninja.setItemSlot(EquipmentSlot.HEAD, new ItemStack(NoirmGear.FOREHEAD_PROTECTOR_MIST.get()));
        helper.assertFalse(GearPerks.hidesName(ninja), "a village forehead protector doesn't");

        // The medic coat closes wounds.
        ninja.setHealth(ninja.getMaxHealth() - 6f);
        float hurt = ninja.getHealth();
        helper.assertFalse(GearPerks.mend(ninja), "no mending without the coat");
        ninja.setItemSlot(EquipmentSlot.CHEST, new ItemStack(NoirmGear.MEDIC_COAT.get()));
        helper.assertTrue(GearPerks.mend(ninja) && ninja.getHealth() > hurt, "the medic coat heals: " + hurt + " -> " + ninja.getHealth());
        ninja.setHealth(ninja.getMaxHealth());
        helper.assertFalse(GearPerks.mend(ninja), "nothing to mend at full health");

        // Chest bandages hit a little harder; the tabi boots soften falls.
        double fist = NoirmGear.SARASHI_WRAPS.get().getAttributeModifiers(EquipmentSlot.CHEST, new ItemStack(NoirmGear.SARASHI_WRAPS.get()))
                .get(Attributes.ATTACK_DAMAGE).stream().mapToDouble(m -> m.getAmount()).sum();
        helper.assertTrue(Math.abs(fist - GearPerks.SARASHI_DAMAGE) < 1e-4, "bandages: +" + fist + " damage");
        helper.assertTrue(((GearItem) NoirmGear.JIKA_TABI.get()).perk() == GearPerk.SOFT_STEP
                && ((GearItem) NoirmGear.WRAPPED_SANDALS.get()).perk() == GearPerk.SOFT_STEP, "boots and sandals soften falls");
        helper.assertTrue(((GearItem) NoirmGear.TATTERED_CLOAK.get()).perk() == GearPerk.SWIFT, "the tattered cloak is swift");
        ninja.discard();
        helper.succeed();
    }

    /** Ninjas in the world wear the wave: guards their village's forehead protector, rogues hoods and assassin black, ronin samurai plate. */
    @GameTest(template = "empty", batch = "noirm_armor_wave")
    public static void ninjasWearTheArmorWave(GameTestHelper helper) {
        RandomSource r = RandomSource.create(16);
        for (VillagePalette palette : new VillagePalette[]{VillagePalette.MIST, VillagePalette.CLOUD, VillagePalette.STONE, VillagePalette.SAND}) {
            Item band = ClanOutfits.villageHeadband(palette);
            helper.assertTrue(band != null, palette + " has its own forehead protector");
            int wearing = 0;
            for (int i = 0; i < 50; i++) {
                if (ClanOutfits.residentOutfit("GUARD", palette, r).get(EquipmentSlot.HEAD) == band) {
                    wearing++;
                }
            }
            helper.assertTrue(wearing > 25, palette + " guards wear it: " + wearing + "/50");
        }
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            seen.addAll(ClanOutfits.rogue(ClanOutfits.roll("some_clan", r), r).values());
            seen.addAll(ClanOutfits.roll("some_clan", r).values());
            seen.addAll(ClanOutfits.roll("uchiha", r).values());
            seen.addAll(ClanOutfits.roll("hyuga", r).values());
        }
        for (RegistryObject<Item> piece : new RegistryObject[]{NoirmGear.TATTERED_CLOAK, NoirmGear.TATTERED_HOOD, NoirmGear.HALF_MASK,
                NoirmGear.ASSASSIN_SUIT, NoirmGear.ASSASSIN_PANTS, NoirmGear.MESH_SHIRT, NoirmGear.TRAINING_SHIRT, NoirmGear.HIGH_COLLAR_JACKET,
                NoirmGear.SARASHI_WRAPS, NoirmGear.BANDOLIER, NoirmGear.MEDIC_COAT, NoirmGear.BANDAGE_LEGGINGS, NoirmGear.JIKA_TABI,
                NoirmGear.WRAPPED_SANDALS, NoirmGear.BATTLE_KIMONO, NoirmGear.BATTLE_HAKAMA, NoirmGear.FORMAL_ROBE, NoirmGear.FORMAL_ROBE_HEM}) {
            helper.assertTrue(seen.contains(piece.get()), "someone wears " + piece.getId());
        }
        Set<Item> samurai = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Map<EquipmentSlot, Item> look = ClanOutfits.samurai(r);
            helper.assertTrue(look.get(EquipmentSlot.CHEST) == NoirmGear.SAMURAI_CUIRASS.get()
                    || look.get(EquipmentSlot.CHEST) == NoirmGear.SODE_PAULDRONS.get(), "samurai wear plate on the chest");
            samurai.addAll(look.values());
        }
        for (RegistryObject<Item> piece : NoirmGear.samuraiSet()) {
            helper.assertTrue(samurai.contains(piece.get()), "samurai wear " + piece.getId());
        }
        helper.succeed();
    }

    /** {@code /noirm kit samurai} (and the other new kits) dress you. */
    @GameTest(template = "empty", batch = "noirm_armor_wave_kits")
    public static void armorWaveKitsDressYou(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_kit_test"));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        CommandSourceStack source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        Object[][] kits = {
                {"samurai", NoirmGear.KABUTO, NoirmGear.SAMURAI_CUIRASS, NoirmGear.KUSAZURI, NoirmGear.SAMURAI_GREAVES},
                {"assassin", NoirmGear.HALF_MASK, NoirmGear.ASSASSIN_SUIT, NoirmGear.ASSASSIN_PANTS, NoirmGear.JIKA_TABI},
                {"shinobi_basics", NoirmGear.FOREHEAD_PROTECTOR_MIST, NoirmGear.MESH_SHIRT, NoirmGear.BANDAGE_LEGGINGS, NoirmGear.JIKA_TABI},
                {"ceremonial", null, NoirmGear.FORMAL_ROBE, NoirmGear.FORMAL_ROBE_HEM, NoirmGear.TABI},
                {"medic", NoirmGear.FOREHEAD_PROTECTOR_CLOUD, NoirmGear.MEDIC_COAT, NoirmGear.SHINOBI_PANTS, NoirmGear.SHINOBI_SANDALS},
                {"village_bands", NoirmGear.FOREHEAD_PROTECTOR_CLOUD, null, null, null},
        };
        for (Object[] kit : kits) {
            for (EquipmentSlot slot : ARMOR) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            try {
                helper.getLevel().getServer().getCommands().getDispatcher().execute("noirm kit " + kit[0], source);
            } catch (Exception e) {
                throw new IllegalStateException("/noirm kit " + kit[0] + " failed: " + e.getMessage(), e);
            }
            for (int i = 0; i < 4; i++) {
                if (kit[i + 1] == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Item want = ((RegistryObject<Item>) kit[i + 1]).get();
                helper.assertTrue(player.getItemBySlot(ARMOR[i]).is(want),
                        "kit " + kit[0] + " puts on " + want + ", got " + player.getItemBySlot(ARMOR[i]));
            }
        }
        helper.assertTrue(player.getInventory().countItem(NoirmGear.SODE_PAULDRONS.get()) == 1, "spares go in the bag");
        player.discard();
        helper.succeed();
    }
}

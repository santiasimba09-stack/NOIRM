package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.entity.NinjaLook;
import com.noirm.entity.SparringNinja;
import com.noirm.gear.ClanOutfits;
import com.noirm.gear.GearItem;
import com.noirm.gear.NoirmGear;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.Set;

/** Wearable ninja gear, clan gear on ninjas, and ninja looks. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GearGameTests {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private GearGameTests() {}

    @GameTest(template = "empty", batch = "noirm_gear")
    public static void gearGivesArmor(GameTestHelper helper) {
        helper.assertTrue(NoirmGear.ALL.size() >= 16, "expected at least 16 gear pieces, got " + NoirmGear.ALL.size());
        for (RegistryObject<Item> piece : NoirmGear.ALL) {
            Item item = piece.get();
            helper.assertTrue(item instanceof GearItem, piece.getId() + " is wearable gear");
            ArmorItem armor = (ArmorItem) item;
            helper.assertTrue(armor.getDefense() > 0, piece.getId() + " gives armor points");
        }
        int senju = ((ArmorItem) NoirmGear.SENJU_CHESTPLATE.get()).getDefense();
        int flak = ((ArmorItem) NoirmGear.FLAK_VEST.get()).getDefense();
        int robe = ((ArmorItem) NoirmGear.HYUGA_ROBE.get()).getDefense();
        helper.assertTrue(senju > flak && flak > robe, "Senju armor > flak vest > robe: " + senju + ", " + flak + ", " + robe);
        helper.assertTrue(senju >= 7, "Senju armor is at least iron-strong (" + senju + ")");
        var recipes = helper.getLevel().getRecipeManager();
        for (RegistryObject<Item> piece : NoirmGear.ALL) {
            helper.assertTrue(recipes.byKey(piece.getId()).isPresent(), "a recipe for " + piece.getId());
        }
        helper.succeed();
    }

    /** Senju ninjas often spawn in their red battle armor (and gear rarely drops). */
    @GameTest(template = "empty", batch = "noirm_gear")
    public static void senjuNinjasWearClanGear(GameTestHelper helper) {
        Clan senju = ClanData.canon("senju").orElseThrow(() -> new IllegalStateException("senju clan missing"));
        int dressed = 0;
        int redArmor = 0;
        for (int i = 0; i < 12; i++) {
            SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
            ninja.joinClan(senju);
            boolean any = false;
            for (EquipmentSlot slot : ARMOR) {
                if (!ninja.getItemBySlot(slot).isEmpty()) {
                    any = true;
                }
            }
            if (any) {
                dressed++;
            }
            if (ninja.getItemBySlot(EquipmentSlot.CHEST).is(NoirmGear.SENJU_CHESTPLATE.get())) {
                redArmor++;
            }
            helper.assertTrue(NinjaLook.folder(ninja.outfit()).equals("senju"), "a Senju look: " + ninja.outfit());
            // Rejoining (e.g. after loading) never re-dresses them.
            ninja.setItemSlot(EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            ninja.setItemSlot(EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
            ninja.setItemSlot(EquipmentSlot.LEGS, net.minecraft.world.item.ItemStack.EMPTY);
            ninja.setItemSlot(EquipmentSlot.FEET, net.minecraft.world.item.ItemStack.EMPTY);
            ninja.joinClan(senju);
            helper.assertTrue(ninja.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "gear is handed out once");
            ninja.discard();
        }
        helper.assertTrue(dressed >= 1 && redArmor >= 1, "some Senju wear gear (" + dressed + "/12), some the red armor (" + redArmor + ")");
        // Over many rolls every clan gets its own pieces, and some ninjas wear nothing.
        var random = net.minecraft.util.RandomSource.create(42);
        Set<Item> seen = new HashSet<>();
        int bare = 0;
        for (String clan : new String[]{"senju", "uchiha", "hyuga", "uzumaki", "some_generated_clan"}) {
            for (int i = 0; i < 200; i++) {
                var outfit = ClanOutfits.roll(clan, random);
                seen.addAll(outfit.values());
                if (outfit.isEmpty()) {
                    bare++;
                }
            }
        }
        helper.assertTrue(seen.contains(NoirmGear.UCHIHA_TUNIC.get()) && seen.contains(NoirmGear.HYUGA_ROBE.get())
                && seen.contains(NoirmGear.UZUMAKI_COAT.get()) && seen.contains(NoirmGear.FLAK_VEST.get()), "each clan has its gear");
        helper.assertTrue(bare > 0, "some ninjas wear nothing");
        helper.succeed();
    }

    /** A seed is a look: the same key always decodes the same; seeds give many different looks. */
    @GameTest(template = "empty", batch = "noirm_gear")
    public static void looksAreDeterministicAndVaried(GameTestHelper helper) {
        for (String folder : NinjaLook.FOLDERS) {
            Set<String> distinct = new HashSet<>();
            for (long seed = 1; seed <= 200; seed++) {
                String key = NinjaLook.key(folder, seed * 0x9E3779B97L);
                var a = NinjaLook.decode(key);
                var b = NinjaLook.decode(key);
                helper.assertTrue(a.equals(b), "same key, same look: " + key);
                helper.assertTrue(!a.get("hair_style").isEmpty() && !a.get("skin").isEmpty(), "every field is filled: " + a);
                distinct.add(a.values().toString());
            }
            helper.assertTrue(distinct.size() >= 50, folder + " has at least 50 distinct looks, got " + distinct.size());
        }
        helper.assertTrue(NinjaLook.decode(NinjaLook.key("hyuga", 7)).get("eyes").equals("pale"), "Hyūga have pale eyes");
        // Same answers as the Python twin (tools/skingen/looks.py) that draws the previews.
        String[][] python = {
                {"uchiha:1234", "[#e8bc98, duck, #1b1c22, narrow, #2a1f24, soft, turtleneck, #1c2233, long, #d9d4c8, 0, none, none, #1f2638]"},
                {"generic:1a2b0000abcdef", "[#c98e66, spiky, #2f3f6a, round, #6a4a2a, thin, mesh, #2a2e3a, shorts, #33302a, 0, whiskers, head_bandage, #1f2638]"},
        };
        for (String[] expected : python) {
            String got = NinjaLook.decode(expected[0]).values().values().toString();
            helper.assertTrue(got.equals(expected[1]), expected[0] + " decodes like the Python tools: " + got);
        }
        // Old saves ("uchiha/3") keep their clan and get a fixed look.
        String old = NinjaLook.normalize("uchiha/3");
        helper.assertTrue(NinjaLook.folder(old).equals("uchiha") && old.equals(NinjaLook.normalize("uchiha/3")), "old outfit mapped: " + old);
        helper.assertTrue(NinjaLook.normalize(old).equals(old), "a valid key stays as it is");
        // The ally screen steps through looks of the same clan.
        String next = NinjaLook.cycle(old, 1);
        helper.assertTrue(!next.equals(old) && NinjaLook.cycle(next, -1).equals(old) && NinjaLook.folder(next).equals("uchiha"),
                "cycling looks: " + old + " -> " + next);
        // A generated clan's members share a family.
        var random = net.minecraft.util.RandomSource.create(5);
        String a = NinjaLook.random("ember_clan", random);
        String b = NinjaLook.random("ember_clan", random);
        helper.assertTrue(NinjaLook.fits(a, "ember_clan") && NinjaLook.fits(b, "ember_clan") && !a.equals(b), "family looks: " + a + ", " + b);
        helper.succeed();
    }

    /** The worn textures are HD (4x: 256x128 for the 64x32 armour layout), one per gear piece. */
    public static final int GEAR_TEXTURE_SCALE = 4;

    @GameTest(template = "empty", batch = "noirm_gear")
    public static void gearTexturesAreHd(GameTestHelper helper) {
        int w = 64 * GEAR_TEXTURE_SCALE;
        int h = 32 * GEAR_TEXTURE_SCALE;
        // The armor wave (round 16) is part of the check: 28 more pieces, all HD.
        helper.assertTrue(NoirmGear.armorWave().size() >= 28 && NoirmGear.ALL.containsAll(NoirmGear.armorWave()),
                "the armor wave's pieces are registered: " + NoirmGear.armorWave().size());
        for (RegistryObject<Item> piece : NoirmGear.ALL) {
            GearItem gear = (GearItem) piece.get();
            int layer = gear.getType() == ArmorItem.Type.LEGGINGS ? 2 : 1;
            String path = "/assets/" + Noirm.MOD_ID + "/textures/models/armor/" + gear.texture() + "_layer_" + layer + ".png";
            try (java.io.InputStream in = GearGameTests.class.getResourceAsStream(path)) {
                helper.assertTrue(in != null, piece.getId() + ": missing worn texture " + path);
                byte[] head = in.readNBytes(24);
                java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(head);
                helper.assertTrue(head.length == 24 && buf.getInt(12) == 0x49484452, path + " is a PNG");
                int tw = buf.getInt(16);
                int th = buf.getInt(20);
                helper.assertTrue(tw == w && th == h, path + " is " + tw + "x" + th + ", expected " + w + "x" + h);
            } catch (java.io.IOException e) {
                helper.fail(path + ": " + e);
            }
        }
        helper.succeed();
    }
}

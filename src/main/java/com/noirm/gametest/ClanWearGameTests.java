package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.entity.SparringNinja;
import com.noirm.gear.CapeItem;
import com.noirm.gear.ClanOutfits;
import com.noirm.gear.ClothSim;
import com.noirm.gear.Drapes;
import com.noirm.gear.GearItem;
import com.noirm.gear.GearPerks;
import com.noirm.gear.MaskItem;
import com.noirm.gear.NoirmGear;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** v0.3 clan wear: the pieces, their recipes, the 3D drapes' data, the cloth simulation, masks, capes and who wears what. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClanWearGameTests {
    private ClanWearGameTests() {}

    private static TransientCraftingContainer grid() {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        return new TransientCraftingContainer(menu, 3, 3);
    }

    /** Every piece registers, gives armour, has a recipe, and its own grid crafts it (no recipe clashes). */
    @GameTest(template = "empty", batch = "noirm_clan_wear")
    public static void clanWearItemsAndRecipes(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        helper.assertTrue(NoirmGear.clanWear().size() >= 50, "the wave has its pieces: " + NoirmGear.clanWear().size());
        for (RegistryObject<Item> piece : NoirmGear.clanWear()) {
            helper.assertTrue(piece.get() instanceof GearItem gear && gear.getDefense() > 0, piece.getId() + " is gear with armour");
            var recipe = recipes.byKey(piece.getId());
            helper.assertTrue(recipe.isPresent() && recipe.get() instanceof ShapedRecipe, "a shaped recipe for " + piece.getId());
            ShapedRecipe shaped = (ShapedRecipe) recipe.get();
            TransientCraftingContainer grid = grid();
            for (int y = 0; y < shaped.getHeight(); y++) {
                for (int x = 0; x < shaped.getWidth(); x++) {
                    Ingredient ing = shaped.getIngredients().get(y * shaped.getWidth() + x);
                    ItemStack[] options = ing.getItems();
                    grid.setItem(y * 3 + x, options.length == 0 ? ItemStack.EMPTY : options[0].copy());
                }
            }
            Optional<CraftingRecipe> found = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level);
            helper.assertTrue(found.isPresent() && found.get().assemble(grid, level.registryAccess()).is(piece.get()),
                    piece.getId() + "'s grid crafts it, got " + found.map(r -> r.getId().toString()).orElse("nothing"));
        }
        helper.assertTrue(recipes.byKey(Noirm.id("cape_sewing")).isPresent(), "the cape-sewing recipe loads");
        helper.succeed();
    }

    /** drapes.json reads cleanly: known items and kinds, capes on every cape, skirts on the gowns. */
    @GameTest(template = "empty", batch = "noirm_clan_wear")
    public static void drapeDataReads(GameTestHelper helper) {
        helper.assertTrue(Drapes.problems().isEmpty(), "drapes.json problems: " + Drapes.problems());
        for (RegistryObject<Item> cape : NoirmGear.capes()) {
            helper.assertTrue(cape.get() instanceof CapeItem, cape.getId() + " is a cape");
            helper.assertTrue(Drapes.of(cape.get()).stream().anyMatch(p -> p.kind() == Drapes.Kind.CAPE), cape.getId() + " hangs a 3D cape");
        }
        for (RegistryObject<Item> hem : new RegistryObject[]{NoirmGear.HYUGA_GOWN_HEM, NoirmGear.KAGE_ROBE_HEM, NoirmGear.PRIESTESS_GOWN_HEM,
                NoirmGear.KIMONO_INDIGO_HEM}) {
            helper.assertTrue(Drapes.of(hem.get()).stream().anyMatch(p -> p.kind() == Drapes.Kind.SKIRT), hem.getId() + " flows as a skirt");
        }
        helper.assertTrue(Drapes.of(NoirmGear.CLOUD_CLOAK.get()).size() >= 2, "the cloud cloak has a collar and a long skirt");
        helper.assertTrue(Drapes.of(NoirmGear.KASA_HAT.get()).get(0).kind() == Drapes.Kind.HAT, "the straw hat is a 3D hat");
        helper.succeed();
    }

    /** The cloth simulation stays finite and in range with wild input, and running and falling lift the cape. */
    @GameTest(template = "empty", batch = "noirm_clan_wear")
    public static void clothSimIsStable(GameTestHelper helper) {
        ClothSim still = new ClothSim(), run = new ClothSim(), fall = new ClothSim(), wild = new ClothSim();
        RandomSource r = RandomSource.create(7);
        for (int i = 0; i < 200; i++) {
            still.step(0, 0, 0, 0, 0.2f);
            run.step(0.3f, 0, 0, 0, 0.2f);
            fall.step(0, -0.8f, 0, 0, 0.2f);
            float big = (r.nextFloat() - 0.5f) * 1e6f;
            wild.step(i % 7 == 0 ? Float.NaN : big, i % 5 == 0 ? Float.POSITIVE_INFINITY : -big, big, big, i % 3 - 1);
            for (int row = 0; row < ClothSim.ROWS; row++) {
                float a = wild.pitch(row, 0.5f);
                helper.assertTrue(Float.isFinite(a) && a >= ClothSim.MIN_ANGLE && a <= ClothSim.MAX_ANGLE, "row " + row + " stays in range: " + a);
            }
            helper.assertTrue(Float.isFinite(wild.roll(0.5f)) && Float.isFinite(wild.lift(1f)), "roll and lift stay finite");
        }
        int last = ClothSim.ROWS - 1;
        helper.assertTrue(still.pitch(last, 1) < 0.3f, "standing still the cape hangs: " + still.pitch(last, 1));
        helper.assertTrue(run.pitch(last, 1) > 0.7f, "running lifts it behind: " + run.pitch(last, 1));
        helper.assertTrue(fall.pitch(last, 1) > 0.9f, "falling blows it up: " + fall.pitch(last, 1));
        helper.assertTrue(run.pitch(last, 1) > run.pitch(0, 1) - 0.05f, "the hem lifts at least as much as the top");
        helper.succeed();
    }

    /** A cape sewn onto a chest piece keeps both (and its sprint perk); unpicking gives the cape back. */
    @GameTest(template = "empty", batch = "noirm_clan_wear")
    public static void capesSewOnAndOff(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        TransientCraftingContainer grid = grid();
        ItemStack cape = new ItemStack(NoirmGear.UCHIHA_CAPE.get());
        grid.setItem(0, new ItemStack(NoirmGear.UCHIHA_COLLAR_SHIRT.get()));
        grid.setItem(4, cape);
        var sewing = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level);
        helper.assertTrue(sewing.isPresent(), "a chest piece and a cape make a recipe");
        ItemStack sewn = sewing.get().assemble(grid, level.registryAccess());
        helper.assertTrue(sewn.is(NoirmGear.UCHIHA_COLLAR_SHIRT.get()) && Drapes.sewnCape(sewn).is(NoirmGear.UCHIHA_CAPE.get()), "the shirt carries the cape");
        TransientCraftingContainer back = grid();
        back.setItem(3, sewn);
        var unpick = recipes.getRecipeFor(RecipeType.CRAFTING, back, level);
        helper.assertTrue(unpick.isPresent(), "a sewn piece alone unpicks");
        ItemStack plain = unpick.get().assemble(back, level.registryAccess());
        helper.assertTrue(plain.is(NoirmGear.UCHIHA_COLLAR_SHIRT.get()) && Drapes.sewnCape(plain).isEmpty(), "the shirt comes back plain");
        helper.assertTrue(unpick.get().getRemainingItems(back).get(3).is(NoirmGear.UCHIHA_CAPE.get()), "and the cape stays in the grid");
        TransientCraftingContainer two = grid();
        two.setItem(0, new ItemStack(NoirmGear.CAPE.get()));
        two.setItem(1, new ItemStack(NoirmGear.KAGE_CAPE.get()));
        helper.assertFalse(recipes.getRecipeFor(RecipeType.CRAFTING, two, level).isPresent(), "a cape can't be sewn onto a cape");

        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(TestClans.PLAIN);
        ninja.setItemSlot(EquipmentSlot.CHEST, sewn);
        helper.assertTrue(GearPerks.wears(ninja, com.noirm.gear.GearPerk.SWIFT), "a sewn cape keeps its sprint perk");
        ninja.discard();
        helper.succeed();
    }

    /** A mask over the face hides the name and makes you harder to notice; pushed aside it does neither. */
    @GameTest(template = "empty", batch = "noirm_clan_wear")
    public static void masksPushAside(GameTestHelper helper) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(TestClans.PLAIN);
        var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, 0.5f, 1f, 0.5f);
        double bare = ForgeHooks.getEntityVisibilityMultiplier(ninja, zombie, 1.0);
        ItemStack mask = new ItemStack(NoirmGear.MASK_FOX.get());
        ninja.setItemSlot(EquipmentSlot.HEAD, mask);
        helper.assertTrue(GearPerks.hidesName(ninja), "the fox mask hides the name");
        double masked = ForgeHooks.getEntityVisibilityMultiplier(ninja, zombie, 1.0);
        helper.assertTrue(Math.abs(masked - bare * GearPerks.MASK_VISIBILITY) < 1e-6, "and is harder to notice: " + bare + " -> " + masked);
        MaskItem.setAside(ninja.getItemBySlot(EquipmentSlot.HEAD), true);
        helper.assertTrue(MaskItem.isAside(ninja.getItemBySlot(EquipmentSlot.HEAD)), "the mask is pushed aside");
        helper.assertFalse(GearPerks.hidesName(ninja), "pushed aside it shows the face");
        helper.assertTrue(Math.abs(ForgeHooks.getEntityVisibilityMultiplier(ninja, zombie, 1.0) - bare) < 1e-6, "and gives no stealth");
        helper.assertTrue(NoirmGear.animalMasks().size() >= 9, "nine animal masks for the ANBU");
        zombie.discard();
        ninja.discard();
        helper.succeed();
    }

    /** Each canon clan has its own recognisable piece; rogues sometimes wear the cloud cloak; ANBU wear the uniform. */
    @GameTest(template = "empty", batch = "noirm_clan_wear")
    public static void clansWearTheirClothes(GameTestHelper helper) {
        RandomSource r = RandomSource.create(11);
        Map<String, Item> own = Map.ofEntries(
                Map.entry("uchiha", NoirmGear.UCHIHA_COLLAR_SHIRT.get()), Map.entry("senju", NoirmGear.SENJU_PLATE_ARMOR.get()),
                Map.entry("uzumaki", NoirmGear.UZUMAKI_VEST.get()), Map.entry("aburame", NoirmGear.ABURAME_COAT.get()),
                Map.entry("hyuga", NoirmGear.HYUGA_GOWN.get()), Map.entry("nara", NoirmGear.NARA_VEST.get()),
                Map.entry("akimichi", NoirmGear.AKIMICHI_ARMOR.get()), Map.entry("inuzuka", NoirmGear.INUZUKA_FUR_COAT.get()),
                Map.entry("kaguya", NoirmGear.KAGUYA_ROBE.get()), Map.entry("hoshigaki", NoirmGear.HOSHIGAKI_CLOAK.get()),
                Map.entry("yamanaka", NoirmGear.YAMANAKA_WRAP.get()), Map.entry("might", NoirmGear.MIGHT_LEG_WARMERS.get()),
                Map.entry("yuki", NoirmGear.HUNTER_MASK.get()), Map.entry("hozuki", NoirmGear.HUNTER_MASK.get()),
                Map.entry("kazekage", NoirmGear.SAND_GOURD.get()), Map.entry("shimura", NoirmGear.ROOT_ROBE.get()),
                Map.entry("chinoike", NoirmGear.CHINOIKE_ROBE.get()), Map.entry("yotsuki", NoirmGear.YOTSUKI_HARNESS.get()),
                Map.entry("hatake", NoirmGear.CLOTH_MASK.get()), Map.entry("kurogane", NoirmGear.KABUTO.get()),
                Map.entry("kurama", NoirmGear.KIMONO_PLUM.get()), Map.entry("fuma", NoirmGear.HOODED_CLOAK.get()),
                Map.entry("iburi", NoirmGear.TRAVEL_HOOD.get()), Map.entry("kamizuru", NoirmGear.HOODED_CLOAK.get()),
                Map.entry("jugo", NoirmGear.HAORI.get()), Map.entry("sarutobi", NoirmGear.HEAD_WRAP_PROTECTOR.get()));
        for (var e : own.entrySet()) {
            helper.assertTrue(ClanData.canon(e.getKey()).isPresent(), "canon clan " + e.getKey());
            Set<Item> seen = new HashSet<>();
            for (int i = 0; i < 300; i++) {
                seen.addAll(ClanOutfits.roll(e.getKey(), r).values());
            }
            helper.assertTrue(seen.contains(e.getValue()), e.getKey() + " wear their " + e.getValue());
        }
        int clouds = 0, uniforms = 0;
        for (int i = 0; i < 400; i++) {
            if (ClanOutfits.rogue(ClanOutfits.roll("some_clan", r), r).get(EquipmentSlot.CHEST) == NoirmGear.CLOUD_CLOAK.get()) {
                clouds++;
            }
            if (ClanOutfits.residentOutfit("PATROL", com.noirm.world.village.VillagePalette.LEAF, r).get(EquipmentSlot.CHEST) == NoirmGear.ANBU_UNIFORM.get()) {
                uniforms++;
            }
        }
        helper.assertTrue(clouds > 10 && clouds < 100, "some rogues wear the cloud cloak: " + clouds + "/400");
        helper.assertTrue(uniforms > 30, "ANBU patrols wear the new uniform: " + uniforms + "/400");
        helper.succeed();
    }

    /** Aburame ninjas spawn dressed in their coats (the NPC path, not just the dice). */
    @GameTest(template = "empty", batch = "noirm_clan_wear_npcs")
    public static void aburameNinjasWearCoats(GameTestHelper helper) {
        Clan aburame = ClanData.canon("aburame").orElseThrow(() -> new IllegalStateException("aburame clan missing"));
        int coats = 0;
        for (int i = 0; i < 10; i++) {
            SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
            ninja.joinClan(aburame);
            Item chest = ninja.getItemBySlot(EquipmentSlot.CHEST).getItem();
            if (chest == NoirmGear.ABURAME_COAT.get() || chest == NoirmGear.ABURAME_FIELD_COAT.get()) {
                coats++;
            }
            ninja.discard();
        }
        helper.assertTrue(coats >= 3, "Aburame wear their long coats: " + coats + "/10");
        helper.succeed();
    }
}

package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.economy.Ryo;
import com.noirm.economy.Shop;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import com.noirm.entity.VillageMerchant;
import com.noirm.mission.MissionTemplate;
import com.noirm.mission.PlayerRank;
import com.noirm.pills.Apothecary;
import com.noirm.pills.ApothecaryMenu;
import com.noirm.pills.Grade;
import com.noirm.pills.HerbBlock;
import com.noirm.pills.MedicalScrollItem;
import com.noirm.pills.NpcPills;
import com.noirm.pills.Pharmacology;
import com.noirm.pills.PillData;
import com.noirm.pills.PillDefinition;
import com.noirm.pills.PillEffects;
import com.noirm.pills.PillItems;
import com.noirm.pills.PillRegistry;
import com.noirm.pills.PillShop;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.registry.NoirmItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

import static com.noirm.gametest.TestKit.floor;

/**
 * Foods and pills ({@code com.noirm.pills}, {@code docs/PILLS.md}): the data loads, every pill works
 * and crashes, toxicity weakens pills, pharmacology changes the odds, a failed brew, the apothecary's
 * flow, herbs generate and grow, ninjas use pills, shop stock, advancements, saving, Tsubaki's lessons.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillGameTests {
    private PillGameTests() {}

    static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    static ResourceLocation id(RegistryObject<Item> item) {
        return ForgeRegistries.ITEMS.getKey(item.get());
    }

    /** Ticks the player's pills {@code n} times. */
    static void tick(Player player, ShinobiData data, int n) {
        for (int i = 0; i < n; i++) {
            player.tickCount++;
            PillEffects.tick(player, data);
        }
    }

    // ---------------------------------------------------------------- the data

    @GameTest(template = "empty", batch = "noirm_pills_data")
    public static void everyPillAndFoodDefinitionLoads(GameTestHelper helper) {
        for (RegistryObject<Item> pill : PillItems.pills()) {
            PillDefinition def = PillRegistry.pill(pill.get());
            helper.assertTrue(def != null, "a definition for " + id(pill));
            helper.assertTrue(def.recipe().isPresent(), "a recipe for " + id(pill));
            helper.assertTrue(def.cooldown() > 0 && def.toxicity() > 0, "a cooldown and toxicity for " + id(pill));
            helper.assertTrue(def.effects().size() + (def.instant().cure() ? 1 : 0) > 0, "it does something: " + id(pill));
        }
        for (RegistryObject<Item> food : PillItems.foods()) {
            helper.assertTrue(food.get().isEdible(), "edible: " + id(food));
            helper.assertTrue(PillRegistry.food(food.get()) != null, "a food definition for " + id(food));
        }
        helper.assertTrue(PillRegistry.food(NoirmItems.RAMEN.get()) != null, "the stand ramen got its bonus");
        List<PillDefinition> all = new java.util.ArrayList<>(PillRegistry.pills().values());
        all.addAll(PillRegistry.foods().values());
        helper.assertTrue(PillRegistry.pills().size() >= 8 && PillRegistry.foods().size() >= 10, "8 pills and 10+ foods");
        for (PillDefinition def : all) {
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(def.item()), "its item exists: " + def.item());
            for (var list : List.of(def.effects(), def.crash())) {
                for (PillDefinition.EffectSpec spec : list) {
                    helper.assertTrue(spec.kind() != null, "a known effect type: " + spec.type() + " in " + def.item());
                    spec.effect().ifPresent(e -> helper.assertTrue(ForgeRegistries.MOB_EFFECTS.containsKey(e), "a real status effect: " + e));
                }
            }
            def.recipe().ifPresent(r -> r.ingredients().forEach(i ->
                    helper.assertTrue(ForgeRegistries.ITEMS.containsKey(i.item()), "a real ingredient: " + i.item())));
            def.recipe().ifPresent(r -> helper.assertTrue(r.ingredients().size() <= ApothecaryMenu.INPUTS, "fits the mortar: " + def.item()));
        }
        // Crafting and cooking recipes for the foods and the apothecary.
        var recipes = helper.getLevel().getRecipeManager();
        for (String r : List.of("apothecary", "onigiri", "plum_onigiri", "ramen", "fish_ramen", "beef_ramen", "dango", "curry_rice",
                "field_ration", "nutrient_bar", "steamed_rice_from_smelting", "steamed_rice_from_campfire", "grilled_fish_from_smoking")) {
            helper.assertTrue(recipes.byKey(Noirm.id(r)).isPresent(), "recipe " + r);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- pills: effects, crash, tolerance

    @GameTest(template = "empty", batch = "noirm_pills_effects")
    public static void everyPillWorksThenCrashes(GameTestHelper helper) {
        for (RegistryObject<Item> pill : PillItems.pills()) {
            Player player = helper.makeMockPlayer();
            ShinobiData data = data(player);
            ResourceLocation id = id(pill);
            PillDefinition def = PillRegistry.pill(id);
            data.setChakra(0f);
            data.setStamina(0f);
            player.setHealth(4f);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            float speed = data.getSpeed();
            helper.assertTrue(PillEffects.take(player, data, id, Grade.STANDARD) == PillEffects.Result.TAKEN, "swallowed " + id);
            if (def.instant().chakra() > 0) {
                helper.assertTrue(data.getChakra() > 0f, id + " gives chakra");
            }
            if (def.instant().stamina() > 0) {
                helper.assertTrue(data.getStamina() > 0f, id + " gives stamina");
            }
            if (def.instant().cure()) {
                helper.assertFalse(player.hasEffect(MobEffects.POISON), id + " cures poison");
            }
            helper.assertTrue(data.pills().toxicity() > 0f, id + " adds toxicity");
            helper.assertTrue(data.pills().cooldown(id) > 0, id + " starts its cooldown");
            helper.assertTrue(PillEffects.take(player, data, id, Grade.STANDARD) == PillEffects.Result.COOLDOWN, id + ": not again so soon");
            if (def.effects().stream().anyMatch(e -> "stamina_regen".equals(e.type()))) {
                helper.assertTrue(PillEffects.staminaRegenMultiplier(data) > 1f, id + " speeds stamina");
            }
            if (def.effects().stream().anyMatch(e -> "perception".equals(e.type()))) {
                helper.assertTrue(PillEffects.perceptionMultiplier(player) > 1f, id + " sharpens perception");
            }
            if (def.effects().stream().anyMatch(e -> "speed".equals(e.type()))) {
                helper.assertTrue(data.getSpeed() > speed, id + " lends speed");
            }
            if (def.effects().stream().anyMatch(e -> "health_regen".equals(e.type()))) {
                float before = player.getHealth();
                tick(player, data, 40);
                helper.assertTrue(player.getHealth() > before, id + " heals over time");
            }
            // Run the effects out: the crash (if any) starts, then everything ends and borrowed stats go back.
            tick(player, data, def.effectTicks() + 2);
            if (!def.crash().isEmpty()) {
                helper.assertTrue(data.pills().boosts().stream().anyMatch(b -> b.crash && b.active()), id + " crashes after");
                if (def.crash().stream().anyMatch(e -> "effect".equals(e.type()))) {
                    helper.assertTrue(player.getActiveEffects().stream().anyMatch(e -> !e.getEffect().isBeneficial()), id + ": a status effect in the crash");
                }
            }
            tick(player, data, 20 * 260);
            helper.assertTrue(data.pills().boosts().isEmpty(), id + ": everything wears off");
            helper.assertTrue(Math.abs(data.getSpeed() - speed) < 0.01f, id + ": speed back to " + speed + ", is " + data.getSpeed());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_pills_tolerance")
    public static void toxicityWeakensPillsAndSickens(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        PillData pills = data.pills();
        float clean = PillEffects.potency(pills, Grade.STANDARD);
        ResourceLocation soldier = id(PillItems.SOLDIER_PILL);
        PillEffects.take(player, data, soldier, Grade.STANDARD);
        float after = PillEffects.potency(pills, Grade.STANDARD);
        helper.assertTrue(after < clean, "a second pill on top works less: " + after + " < " + clean);
        helper.assertTrue(PillEffects.potency(pills, Grade.PERFECT) > PillEffects.potency(pills, Grade.CRUDE), "perfect beats crude");
        // Chakra: the second chakra pill gives less than the first.
        ResourceLocation chakra = id(PillItems.CHAKRA_PILL);
        Player fresh = helper.makeMockPlayer();
        ShinobiData freshData = data(fresh);
        freshData.setChakra(0f);
        PillEffects.take(fresh, freshData, chakra, Grade.STANDARD);
        float first = freshData.getChakra();
        data.setChakra(0f);
        data.pills().setToxicity(80f);
        PillEffects.take(player, data, chakra, Grade.STANDARD);
        helper.assertTrue(data.getChakra() < first, "a full body gets less from a pill: " + data.getChakra() + " < " + first);
        helper.assertTrue(player.hasEffect(MobEffects.CONFUSION) && player.hasEffect(MobEffects.WEAKNESS), "too many pills: sick");
        pills.setToxicity(94f);
        pills.setCooldown(id(PillItems.STAMINA_PILL), 0);
        PillEffects.take(player, data, id(PillItems.STAMINA_PILL), Grade.STANDARD);
        helper.assertTrue(player.hasEffect(MobEffects.POISON), "an overdose poisons");
        // It wears off.
        float tox = pills.toxicity();
        tick(player, data, 200);
        helper.assertTrue(pills.toxicity() < tox, "toxicity wears off");
        // A crude pill adds more toxicity than a perfect one.
        Player a = helper.makeMockPlayer(), b = helper.makeMockPlayer();
        PillEffects.take(a, data(a), soldier, Grade.CRUDE);
        PillEffects.take(b, data(b), soldier, Grade.PERFECT);
        helper.assertTrue(data(a).pills().toxicity() > data(b).pills().toxicity(), "crude pills are dirtier");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_pills_foods")
    public static void foodsGiveShortBonusesAndRefresh(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ResourceLocation ramen = ForgeRegistries.ITEMS.getKey(NoirmItems.RAMEN.get());
        PillEffects.eat(player, data, ramen, PillRegistry.food(NoirmItems.RAMEN.get()));
        helper.assertTrue(PillEffects.chakraRegenMultiplier(data) > 1.2f, "ramen: faster chakra");
        float regen = data.chakraRegenMultiplier();
        helper.assertTrue(regen > 1f, "and ShinobiData uses it: " + regen);
        PillEffects.eat(player, data, ramen, PillRegistry.food(NoirmItems.RAMEN.get()));
        helper.assertTrue(data.pills().boosts().size() == 1, "eating it again refreshes, never stacks");
        ResourceLocation beef = id(PillItems.BEEF_RAMEN);
        PillEffects.eat(player, data, beef, PillRegistry.food(PillItems.BEEF_RAMEN.get()));
        helper.assertTrue(PillEffects.trainingXpMultiplier(data) > 1f, "beef ramen: training sticks");
        helper.assertTrue(data.pills().toxicity() == 0f, "food is never toxic");
        tick(player, data, 20 * 200);
        helper.assertTrue(data.pills().boosts().isEmpty(), "and it wears off");
        helper.succeed();
    }

    // ---------------------------------------------------------------- pharmacology and the apothecary

    /** Always-high random numbers: every success roll fails, every grade roll is the best. */
    static RandomSource high() {
        return new LegacyRandomSource(1L) {
            @Override
            public float nextFloat() {
                return 0.999f;
            }
        };
    }

    static RandomSource low() {
        return new LegacyRandomSource(1L) {
            @Override
            public float nextFloat() {
                return 0.001f;
            }
        };
    }

    @GameTest(template = "empty", batch = "noirm_pills_skill")
    public static void pharmacologyRaisesSuccessGradeAndYield(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        PillDefinition.Recipe blood = PillRegistry.pill(id(PillItems.BLOOD_PILL)).recipe().get();
        data.pills().setPharmacology(1f);
        float lowSuccess = Apothecary.successChance(data, blood);
        float[] lowOdds = Apothecary.gradeOdds(data, blood);
        data.pills().setPharmacology(50f);
        float highSuccess = Apothecary.successChance(data, blood);
        float[] highOdds = Apothecary.gradeOdds(data, blood);
        helper.assertTrue(highSuccess > lowSuccess, "more skill, more success: " + lowSuccess + " → " + highSuccess);
        helper.assertTrue(lowOdds[0] > 0.3f && lowOdds[3] == 0f, "a beginner brews crude pills, never perfect");
        helper.assertTrue(highOdds[3] > 0.4f && highOdds[0] == 0f, "a master brews perfect pills, never crude");
        float sum = 0f;
        for (float o : highOdds) {
            sum += o;
        }
        helper.assertTrue(Math.abs(sum - 1f) < 0.01f, "the odds add up");
        helper.assertTrue(Apothecary.yieldOf(data, blood) > blood.yield(), "and more pills per brew");
        // A real roll at low and high skill (the best grade roll each time).
        data.pills().setPharmacology(1f);
        Apothecary.Outcome beginner = Apothecary.roll(player, data, id(PillItems.BLOOD_PILL), blood, low());
        data.pills().setPharmacology(60f);
        Apothecary.Outcome master = Apothecary.roll(player, data, id(PillItems.BLOOD_PILL), blood, low());
        helper.assertTrue(beginner.success() && master.success(), "both succeed on a low roll");
        helper.assertTrue(master.grade().ordinal() >= beginner.grade().ordinal(), "the master's pill is at least as good");
        // XP and levels.
        data.pills().setPharmacology(1f);
        data.pills().setPharmacologyXp(0f);
        Pharmacology.grant(player, data, 100f);
        helper.assertTrue(data.pills().pharmacology() > 1f, "brewing XP raises pharmacology: " + data.pills().pharmacology());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_pills_fail")
    public static void aFailedBrewLeavesARuinedPill(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        data.pills().setPharmacology(1f);
        PillDefinition.Recipe blood = PillRegistry.pill(id(PillItems.BLOOD_PILL)).recipe().get();
        float xpBefore = data.pills().pharmacologyXp();
        Apothecary.Outcome out = Apothecary.roll(player, data, id(PillItems.BLOOD_PILL), blood, high());
        helper.assertFalse(out.success(), "a high roll fails");
        ItemStack made = Apothecary.result(out);
        helper.assertTrue(made.is(PillItems.RUINED_PILL.get()) && made.getCount() == 1, "one ruined pill");
        helper.assertTrue(data.pills().pharmacologyXp() > xpBefore || data.pills().pharmacology() > 1f, "a failure still teaches a little");
        helper.assertTrue(data.pills().brewed() == 0, "nothing brewed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_pills_flow")
    public static void theApothecaryFillsGrindsAndRefuses(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, PillItems.APOTHECARY.get());
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 2.5)));
        ApothecaryMenu menu = new ApothecaryMenu(0, player.getInventory(), helper.absolutePos(at));
        helper.assertTrue(menu.stillValid(player), "usable next to the block");
        // Stamina pills: 2 ironmillet + 1 sugar.
        player.getInventory().add(new ItemStack(PillItems.HERB_ITEMS.get("ironmillet").get(), 5));
        player.getInventory().add(new ItemStack(Items.SUGAR, 3));
        ResourceLocation stamina = id(PillItems.STAMINA_PILL);
        menu.select(player, data, stamina);
        helper.assertTrue(Apothecary.matches(menu.mortar(), ApothecaryMenu.INPUTS, PillRegistry.pill(stamina).recipe().get()), "filled from the bag");
        data.pills().setPharmacology(60f); // never fails
        helper.assertTrue(menu.brew(player, data), "ground");
        ItemStack out = menu.mortar().getItem(ApothecaryMenu.OUTPUT);
        helper.assertTrue(out.is(PillItems.STAMINA_PILL.get()) && out.getCount() >= 3, "stamina pills out: " + out);
        helper.assertTrue(Grade.of(out).ordinal() >= Grade.REFINED.ordinal(), "a skilled hand: refined or better, got " + Grade.of(out));
        helper.assertTrue(menu.mortar().getItem(0).isEmpty() && menu.mortar().getItem(1).isEmpty(), "ingredients used up");
        helper.assertTrue(data.pills().brewed() == 1, "counted");
        // An unknown recipe is refused; a known one with too little skill is refused.
        ResourceLocation three = id(PillItems.THREE_SEAL_PILL);
        player.getInventory().add(new ItemStack(PillItems.HERB_ITEMS.get("bloodleaf").get(), 2));
        player.getInventory().add(new ItemStack(PillItems.HERB_ITEMS.get("ironmillet").get(), 2));
        player.getInventory().add(new ItemStack(PillItems.HERB_ITEMS.get("ashthorn").get(), 2));
        player.getInventory().add(new ItemStack(Items.GOLD_NUGGET, 3));
        menu.select(player, data, three);
        int brewed = data.pills().brewed();
        helper.assertFalse(menu.brew(player, data), "the three-seal pill isn't known yet");
        data.pills().learn(three);
        data.pills().setPharmacology(10f);
        helper.assertFalse(menu.brew(player, data), "known, but pharmacology 10 is too low");
        helper.assertTrue(data.pills().brewed() == brewed, "nothing brewed");
        // A refused brew uses nothing up (closing the screen hands the mortar back; vanilla does that for real players only).
        int nuggets = 0;
        for (int i = 0; i < ApothecaryMenu.INPUTS; i++) {
            ItemStack s = menu.mortar().getItem(i);
            nuggets += s.is(Items.GOLD_NUGGET) ? s.getCount() : 0;
        }
        helper.assertTrue(nuggets == 3, "a refused brew uses nothing: " + nuggets + " nuggets still in the mortar");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_pills_unlocks")
    public static void scrollsAndTsubakiTeachRecipes(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ResourceLocation focus = id(PillItems.FOCUS_PILL);
        PillDefinition.Recipe recipe = PillRegistry.pill(focus).recipe().get();
        helper.assertTrue(Apothecary.state(data, focus, recipe) == Apothecary.State.UNKNOWN, "the focus pill starts unknown");
        helper.assertTrue(Apothecary.state(data, id(PillItems.SOLDIER_PILL), PillRegistry.pill(id(PillItems.SOLDIER_PILL)).recipe().get())
                == Apothecary.State.READY, "the soldier pill is a starter");
        // Tsubaki's Pill Lore.
        var tsubaki = com.noirm.master.MasterData.get(com.noirm.master.MasterData.MEDIC);
        helper.assertTrue(tsubaki != null && tsubaki.lesson("pill_lore").isPresent() && tsubaki.lesson("high_grade_pills").isPresent()
                && tsubaki.lesson("pill_supply").isPresent(), "Tsubaki has the pill lessons");
        helper.assertTrue(com.noirm.master.MasterPerks.known(tsubaki.lesson("pill_lore").get())
                && com.noirm.master.MasterPerks.known(tsubaki.lesson("pill_supply").get()), "perk and service are real");
        data.masters().learn(com.noirm.master.MasterData.MEDIC.toString(), "pill_lore");
        com.noirm.pills.PillLore.granted(player, data, "pill_lore");
        helper.assertTrue(Apothecary.knows(data, focus, recipe), "Pill Lore teaches the focus pill");
        helper.assertTrue(data.pills().pharmacology() >= PillData.START_PHARMACOLOGY + com.noirm.pills.PillLore.LORE_LEVELS, "and pharmacology");
        helper.assertTrue(player.getInventory().countItem(PillItems.APOTHECARY_ITEM.get()) == 1, "and an apothecary");
        // A medical scroll teaches something new each time, until there's nothing left.
        int learned = 0;
        for (int i = 0; i < 12; i++) {
            if (MedicalScrollItem.read(player, data, RandomSource.create(i)) != null) {
                learned++;
            }
        }
        helper.assertTrue(learned > 0, "scrolls teach");
        for (var e : PillRegistry.recipes()) {
            helper.assertTrue(Apothecary.knows(data, e.getKey(), e.getValue().recipe().get()), "all known in the end: " + e.getKey());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- herbs

    @GameTest(template = "empty", batch = "noirm_pills_herbs")
    public static void herbsGenerateAndGrow(GameTestHelper helper) {
        var level = helper.getLevel();
        var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var modifiers = level.registryAccess().registryOrThrow(ForgeRegistries.Keys.BIOME_MODIFIERS);
        List<String> crops = List.of("rice", "moonroot", "bloodleaf", "ironmillet", "mistcap", "ashthorn");
        for (int i = 0; i < crops.size(); i++) {
            String name = crops.get(i);
            ResourceLocation patch = Noirm.id(name + "_patch");
            helper.assertTrue(placed.containsKey(patch), "placed feature " + patch);
            helper.assertTrue(modifiers.containsKey(patch), "biome modifier " + patch);
            // The patch's own block placement, at one spot on grass: a ripe wild herb appears.
            var config = configured.get(patch);
            helper.assertTrue(config != null && config.config() instanceof RandomPatchConfiguration, "a random patch");
            RandomPatchConfiguration rp = (RandomPatchConfiguration) config.config();
            int x = i % 3, z = i / 3;
            BlockPos ground = new BlockPos(x, 1, z);
            helper.setBlock(ground, name.equals("ashthorn") ? Blocks.RED_SAND : Blocks.GRASS_BLOCK);
            helper.setBlock(ground.above(), Blocks.AIR);
            BlockPos abs = helper.absolutePos(ground.above());
            rp.feature().value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), abs);
            BlockState grown = level.getBlockState(abs);
            helper.assertTrue(grown.getBlock() instanceof HerbBlock && grown.getValue(HerbBlock.AGE) == HerbBlock.MAX_AGE,
                    name + " generates ripe on its ground, got " + grown);
            // Farmed: a seedling on farmland grows to ripe.
            HerbBlock crop = (HerbBlock) PillItems.CROPS.get(name).get();
            helper.setBlock(ground, Blocks.FARMLAND);
            helper.setBlock(ground.above(), crop.defaultBlockState());
            for (int k = 0; k < 6; k++) {
                crop.growCrops(level, abs, level.getBlockState(abs));
            }
            BlockState ripe = level.getBlockState(abs);
            helper.assertTrue(crop.isMaxAge(ripe), name + " grows ripe");
            List<ItemStack> drops = Block.getDrops(ripe, level, abs, null);
            Item seed = name.equals("rice") ? PillItems.RICE.get() : PillItems.SEEDS.get(name).get();
            Item herb = name.equals("rice") ? PillItems.RICE.get() : PillItems.HERB_ITEMS.get(name).get();
            helper.assertTrue(drops.stream().anyMatch(s -> s.is(herb)) && drops.stream().anyMatch(s -> s.is(seed)), name + " drops its herb and seeds");
            helper.setBlock(ground.above(), Blocks.AIR);
            helper.setBlock(ground, Blocks.STONE);
        }
        // Not on stone.
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.assertFalse(PillItems.CROPS.get("moonroot").get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(1, 2, 1))),
                "herbs don't take root on stone");
        // Every herb's biome tag names real biomes.
        for (String name : crops) {
            var tag = net.minecraft.tags.TagKey.create(Registries.BIOME, Noirm.id("has_herb/" + name));
            var biomes = level.registryAccess().registryOrThrow(Registries.BIOME).getTag(tag);
            helper.assertTrue(biomes.isPresent() && biomes.get().size() > 0, name + " grows somewhere");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- ninjas

    @GameTest(template = "empty", batch = "noirm_pills_npc", timeoutTicks = 200)
    public static void aHurtNinjaSwallowsABloodPill(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        TrainingDummy dummy = helper.spawn(NoirmEntities.TRAINING_DUMMY.get(), 1.5f, 1f, 0.5f);
        NpcPills.setPouch(ninja, NpcPills.Kind.BLOOD, 1);
        NpcPills.setPouch(ninja, NpcPills.Kind.SOLDIER, 1);
        ninja.setHealth(ninja.getMaxHealth() * 0.2f);
        ninja.setTarget(dummy);
        helper.succeedWhen(() -> {
            ninja.setTarget(dummy);
            helper.assertTrue(NpcPills.pouch(ninja, NpcPills.Kind.BLOOD) == 0, "the blood pill is swallowed");
            helper.assertTrue(ninja.hasEffect(MobEffects.REGENERATION), "and it heals");
            helper.assertTrue(NpcPills.used(ninja) == 1, "only one at a time");
            // A soldier pill when chakra runs dry: chakra back, then a crash.
            ninja.setChakra(0f);
            NpcPills.use(ninja, NpcPills.Kind.SOLDIER);
            helper.assertTrue(ninja.chakra() > ninja.maxChakra() * 0.5f && ninja.hasEffect(MobEffects.DAMAGE_BOOST), "a soldier pill's burst");
            ninja.discard();
            dummy.discard();
        });
    }

    // ---------------------------------------------------------------- where pills come from

    @GameTest(template = "empty", batch = "noirm_pills_shop")
    public static void shopsAndMissionsCarryFoodsAndPills(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        BlockPos home = helper.absolutePos(new BlockPos(1, 1, 1));
        VillageMerchant store = NoirmEntities.VILLAGE_MERCHANT.get().create(helper.getLevel());
        store.setup(VillageMerchant.Role.MERCHANT, VillageMerchant.SHOP_GENERAL, home, home, 4);
        List<Shop.Row> rows = Shop.rows(store, player);
        Shop.Row soldier = rows.stream().filter(r -> !r.sell() && r.stack().is(PillItems.SOLDIER_PILL.get())).findFirst().orElse(null);
        helper.assertTrue(soldier != null && soldier.price() > 0 && soldier.need() == PlayerRank.GENIN, "the general store sells soldier pills to genin");
        helper.assertTrue(rows.stream().anyMatch(r -> !r.sell() && r.stack().is(PillItems.APOTHECARY_ITEM.get())), "and the apothecary");
        Ryo.give(player, 1000);
        com.noirm.mission.CareerData career = data(player).career();
        career.setRank(PlayerRank.GENIN);
        int index = rows.indexOf(soldier);
        helper.assertTrue(Shop.trade(player, store, index), "bought");
        helper.assertTrue(player.getInventory().countItem(PillItems.SOLDIER_PILL.get()) == 1, "a soldier pill in the bag");
        VillageMerchant ramen = NoirmEntities.VILLAGE_MERCHANT.get().create(helper.getLevel());
        ramen.setup(VillageMerchant.Role.MERCHANT, VillageMerchant.SHOP_RAMEN, home, home, 4);
        helper.assertTrue(Shop.rows(ramen, player).stream().anyMatch(r -> !r.sell() && r.stack().is(PillItems.FISH_RAMEN.get())), "ramen stand: fish ramen");
        VillageMerchant florist = NoirmEntities.VILLAGE_MERCHANT.get().create(helper.getLevel());
        florist.setup(VillageMerchant.Role.MERCHANT, VillageMerchant.SHOP_FLOWERS, home, home, 4);
        helper.assertTrue(Shop.rows(florist, player).stream().anyMatch(r -> !r.sell() && r.stack().is(PillItems.SEEDS.get("moonroot").get())), "florist: seeds");
        // Missions: S always pays refined pills; D never pays pills.
        helper.assertTrue(PillShop.rewards(MissionTemplate.Letter.D, RandomSource.create(1)).isEmpty(), "no pills for D-rank");
        List<ItemStack> s = PillShop.rewards(MissionTemplate.Letter.S, RandomSource.create(1));
        helper.assertTrue(s.stream().anyMatch(st -> Grade.of(st) == Grade.REFINED), "S-rank pays refined pills");
        helper.succeed();
    }

    // ---------------------------------------------------------------- advancements

    @GameTest(template = "empty", batch = "noirm_pills_adv")
    public static void medicineAdvancementsFire(GameTestHelper helper) {
        ServerPlayer player = ProgressGameTests.player(helper);
        try {
            ShinobiData data = data(player);
            ProgressGameTests.assertDone(helper, player, "medicine/first_food", false);
            PillEffects.eat(player, data, Noirm.id("onigiri"), PillRegistry.food(PillItems.ONIGIRI.get()));
            ProgressGameTests.assertDone(helper, player, "medicine/first_food", true);
            PillEffects.take(player, data, id(PillItems.STAMINA_PILL), Grade.STANDARD);
            ProgressGameTests.assertDone(helper, player, "medicine/first_pill", true);
            data.pills().setPharmacology(80f);
            PillDefinition.Recipe blood = PillRegistry.pill(id(PillItems.BLOOD_PILL)).recipe().get();
            Apothecary.roll(player, data, id(PillItems.BLOOD_PILL), blood, low());
            ProgressGameTests.assertDone(helper, player, "medicine/brew", true);
            ProgressGameTests.assertDone(helper, player, "medicine/refined", true);
            ProgressGameTests.assertDone(helper, player, "medicine/perfect", true);
            com.noirm.progress.PillTrigger.pharmacology(player, 25);
            ProgressGameTests.assertDone(helper, player, "medicine/herbalist", true);
            data.pills().setCooldown(id(PillItems.THREE_SEAL_PILL), 0);
            PillEffects.take(player, data, id(PillItems.THREE_SEAL_PILL), Grade.STANDARD);
            ProgressGameTests.assertDone(helper, player, "medicine/three_seals", false);
            tick(player, data, 20 * 200);
            ProgressGameTests.assertDone(helper, player, "medicine/three_seals", true);
            data.pills().setToxicity(96f);
            data.pills().setCooldown(id(PillItems.STAMINA_PILL), 0);
            PillEffects.take(player, data, id(PillItems.STAMINA_PILL), Grade.STANDARD);
            ProgressGameTests.assertDone(helper, player, "medicine/overdose", true);
        } finally {
            ProgressGameTests.leave(helper, player);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- saving

    @GameTest(template = "empty", batch = "noirm_pills_save")
    public static void pharmacologyAndPillsSaveAndLoad(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        data.pills().setPharmacology(27f);
        data.pills().setPharmacologyXp(12f);
        data.pills().learn(id(PillItems.FOCUS_PILL));
        float speed = data.getSpeed();
        PillEffects.take(player, data, id(PillItems.THREE_SEAL_PILL), Grade.PERFECT);
        float boosted = data.getSpeed();
        helper.assertTrue(boosted > speed, "boosted");
        ShinobiData copy = new ShinobiData();
        copy.load(data.save());
        PillData p = copy.pills();
        helper.assertTrue(p.pharmacology() == 27f && p.pharmacologyXp() == 12f, "pharmacology and its XP saved");
        helper.assertTrue(p.learned().contains(id(PillItems.FOCUS_PILL)), "learned recipes saved");
        helper.assertTrue(Math.abs(p.toxicity() - data.pills().toxicity()) < 0.01f && p.toxicity() > 0f, "toxicity saved");
        helper.assertTrue(p.cooldown(id(PillItems.THREE_SEAL_PILL)) > 0, "cooldowns saved");
        helper.assertTrue(p.boosts().size() == data.pills().boosts().size(), "running effects saved");
        helper.assertTrue(copy.getSpeed() == boosted, "the boosted stat saved as is");
        // The loaded copy still takes the borrowed points back when the pill wears off.
        Player other = helper.makeMockPlayer();
        ShinobiData otherData = data(other);
        otherData.copyFrom(copy);
        tick(other, otherData, 20 * 200);
        helper.assertTrue(Math.abs(otherData.getSpeed() - speed) < 0.01f, "after loading, the stat comes back to " + speed + ": " + otherData.getSpeed());
        // Dying ends everything.
        PillEffects.clearAll(player, data);
        helper.assertTrue(data.pills().boosts().isEmpty() && Math.abs(data.getSpeed() - speed) < 0.01f, "clearing gives the points back");
        helper.succeed();
    }
}

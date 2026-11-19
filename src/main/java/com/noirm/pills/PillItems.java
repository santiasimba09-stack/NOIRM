package com.noirm.pills;

import com.noirm.Noirm;
import com.noirm.player.ShinobiCapability;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every food, herb, pill and the apothecary (blocks, items, the screen's menu). Hunger and
 * saturation of the foods are the numbers right here; what they add on top (a short stamina or
 * chakra boost...) is their JSON in {@code data/noirm/foods/}, and pills are {@code data/noirm/pills/}.
 * Our own icons and textures: {@code tools/pillgen/gen_pills.py}.
 */
public final class PillItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Noirm.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Noirm.MOD_ID);

    // ---------------------------------------------------------------- foods: hunger, saturation (× hunger × 2 = points)
    //                                         nutrition  saturation
    public static final int RICE_N = 4;      public static final float RICE_S = 0.5f;
    public static final int ONIGIRI_N = 6;   public static final float ONIGIRI_S = 0.7f;
    public static final int PLUM_N = 7;      public static final float PLUM_S = 0.8f;
    public static final int FISH_RAMEN_N = 10; public static final float FISH_RAMEN_S = 0.9f;
    public static final int BEEF_RAMEN_N = 11; public static final float BEEF_RAMEN_S = 0.9f;
    public static final int DANGO_N = 4;     public static final float DANGO_S = 0.5f;
    public static final int CURRY_N = 12;    public static final float CURRY_S = 1.0f;
    public static final int GRILLED_FISH_N = 7; public static final float GRILLED_FISH_S = 0.7f;
    public static final int RATION_N = 5;    public static final float RATION_S = 1.0f;
    public static final int BAR_N = 4;       public static final float BAR_S = 0.8f;
    public static final int UMEBOSHI_N = 1;  public static final float UMEBOSHI_S = 0.3f;

    // ---------------------------------------------------------------- the herbs (and rice): crop, seeds, harvest
    public static final List<String> HERBS = List.of("moonroot", "bloodleaf", "ironmillet", "mistcap", "ashthorn");
    /** Crop block per herb (and {@code rice}), by name. */
    public static final Map<String, RegistryObject<Block>> CROPS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Item>> SEEDS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Item>> HERB_ITEMS = new LinkedHashMap<>();

    public static final RegistryObject<Item> RICE;

    static {
        RegistryObject<Block> riceCrop = crop("rice", () -> PillItems.RICE.get(), MapColor.COLOR_YELLOW);
        RICE = ITEMS.register("rice", () -> new ItemNameBlockItem(riceCrop.get(), new Item.Properties()));
        for (String herb : HERBS) {
            String seedName = herb + "_seeds";
            RegistryObject<Block> block = crop(herb, () -> SEEDS.get(herb).get(),
                    switch (herb) {
                        case "bloodleaf" -> MapColor.COLOR_RED;
                        case "moonroot" -> MapColor.COLOR_LIGHT_BLUE;
                        case "mistcap" -> MapColor.COLOR_LIGHT_GRAY;
                        case "ashthorn" -> MapColor.COLOR_ORANGE;
                        default -> MapColor.PLANT;
                    });
            SEEDS.put(herb, ITEMS.register(seedName, () -> new ItemNameBlockItem(block.get(), new Item.Properties())));
            HERB_ITEMS.put(herb, ITEMS.register(herb, () -> new Item(new Item.Properties())));
        }
    }

    private static RegistryObject<Block> crop(String name, Supplier<? extends Item> seed, MapColor color) {
        RegistryObject<Block> block = BLOCKS.register(name + "_crop", () -> new HerbBlock(seed, BlockBehaviour.Properties.of()
                .mapColor(color).noCollission().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)));
        CROPS.put(name, block);
        return block;
    }

    // ---------------------------------------------------------------- foods
    public static final RegistryObject<Item> STEAMED_RICE = food("steamed_rice", RICE_N, RICE_S, false, false);
    public static final RegistryObject<Item> ONIGIRI = food("onigiri", ONIGIRI_N, ONIGIRI_S, false, false);
    public static final RegistryObject<Item> UMEBOSHI = food("umeboshi", UMEBOSHI_N, UMEBOSHI_S, true, false);
    public static final RegistryObject<Item> PLUM_ONIGIRI = food("plum_onigiri", PLUM_N, PLUM_S, false, false);
    public static final RegistryObject<Item> FISH_RAMEN = food("fish_ramen", FISH_RAMEN_N, FISH_RAMEN_S, false, true);
    public static final RegistryObject<Item> BEEF_RAMEN = food("beef_ramen", BEEF_RAMEN_N, BEEF_RAMEN_S, false, true);
    public static final RegistryObject<Item> DANGO = food("dango", DANGO_N, DANGO_S, true, false);
    public static final RegistryObject<Item> CURRY_RICE = food("curry_rice", CURRY_N, CURRY_S, false, true);
    public static final RegistryObject<Item> RAW_FISH_SKEWER = ITEMS.register("raw_fish_skewer", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GRILLED_FISH = food("grilled_fish", GRILLED_FISH_N, GRILLED_FISH_S, false, false);
    public static final RegistryObject<Item> FIELD_RATION = food("field_ration", RATION_N, RATION_S, true, false);
    public static final RegistryObject<Item> NUTRIENT_BAR = food("nutrient_bar", BAR_N, BAR_S, true, false);

    private static RegistryObject<Item> food(String name, int nutrition, float saturation, boolean fast, boolean bowl) {
        return ITEMS.register(name, () -> {
            FoodProperties.Builder food = new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation);
            if (fast) {
                food.fast();
            }
            Item.Properties props = new Item.Properties().food(food.build());
            return bowl ? new BowlFoodItem(props.stacksTo(16)) : new Item(props);
        });
    }

    // ---------------------------------------------------------------- pills
    public static final RegistryObject<Item> STAMINA_PILL = pill("stamina_pill", Rarity.COMMON);
    public static final RegistryObject<Item> SOLDIER_PILL = pill("soldier_pill", Rarity.COMMON);
    public static final RegistryObject<Item> CHAKRA_PILL = pill("chakra_pill", Rarity.COMMON);
    public static final RegistryObject<Item> BLOOD_PILL = pill("blood_pill", Rarity.COMMON);
    public static final RegistryObject<Item> ANTIDOTE_PILL = pill("antidote_pill", Rarity.COMMON);
    public static final RegistryObject<Item> FOCUS_PILL = pill("focus_pill", Rarity.UNCOMMON);
    public static final RegistryObject<Item> IRON_BONE_PILL = pill("iron_bone_pill", Rarity.RARE);
    public static final RegistryObject<Item> THREE_SEAL_PILL = pill("three_seal_pill", Rarity.EPIC);
    public static final RegistryObject<Item> RUINED_PILL = ITEMS.register("ruined_pill", () -> new RuinedPillItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MEDICAL_SCROLL = ITEMS.register("medical_scroll",
            () -> new MedicalScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    private static RegistryObject<Item> pill(String name, Rarity rarity) {
        return ITEMS.register(name, () -> new PillItem(new Item.Properties().stacksTo(16).rarity(rarity)));
    }

    // ---------------------------------------------------------------- the apothecary
    public static final RegistryObject<Block> APOTHECARY = BLOCKS.register("apothecary", () -> new ApothecaryBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(2.0f, 6.0f).sound(SoundType.STONE).noOcclusion()));
    public static final RegistryObject<Item> APOTHECARY_ITEM = ITEMS.register("apothecary", () -> new BlockItem(APOTHECARY.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<ApothecaryMenu>> APOTHECARY_MENU = MENUS.register("apothecary",
            () -> IForgeMenuType.create(ApothecaryMenu::fromNetwork));

    private PillItems() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(PillItems::creativeTabs);
        modBus.addListener(PillItems::setup);
    }

    /** Every pill item (in a stable order). */
    public static List<RegistryObject<Item>> pills() {
        return List.of(STAMINA_PILL, SOLDIER_PILL, CHAKRA_PILL, BLOOD_PILL, ANTIDOTE_PILL, FOCUS_PILL, IRON_BONE_PILL, THREE_SEAL_PILL);
    }

    /** Every food item NOIRM adds here (the stand ramen lives in {@code NoirmItems}). */
    public static List<RegistryObject<Item>> foods() {
        return List.of(STEAMED_RICE, ONIGIRI, PLUM_ONIGIRI, FISH_RAMEN, BEEF_RAMEN, DANGO, CURRY_RICE, GRILLED_FISH, FIELD_RATION,
                NUTRIENT_BAR, UMEBOSHI);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ComposterBlock.COMPOSTABLES.put(RICE.get(), 0.3f);
            SEEDS.values().forEach(s -> ComposterBlock.COMPOSTABLES.put(s.get(), 0.3f));
            HERB_ITEMS.values().forEach(h -> ComposterBlock.COMPOSTABLES.put(h.get(), 0.5f));
        });
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            foods().forEach(event::accept);
            pills().forEach(event::accept);
            for (RegistryObject<Item> pill : List.of(SOLDIER_PILL, BLOOD_PILL, THREE_SEAL_PILL)) {
                event.accept(Grade.PERFECT.apply(new net.minecraft.world.item.ItemStack(pill.get())));
            }
            event.accept(RUINED_PILL);
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(RICE);
            SEEDS.values().forEach(event::accept);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            HERB_ITEMS.values().forEach(event::accept);
            event.accept(RAW_FISH_SKEWER);
            event.accept(MEDICAL_SCROLL);
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(APOTHECARY_ITEM);
        }
    }

    /** Foods: when one is eaten, its NOIRM effects (hunger and saturation are vanilla's). */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
    public static final class FoodEvents {
        private FoodEvents() {}

        @SubscribeEvent
        public static void foodEaten(LivingEntityUseItemEvent.Finish event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && !player.level().isClientSide()) {
                PillDefinition food = PillRegistry.food(event.getItem().getItem());
                if (food != null) {
                    var id = ForgeRegistries.ITEMS.getKey(event.getItem().getItem());
                    ShinobiCapability.get(player).ifPresent(data -> PillEffects.eat(player, data, id, food));
                }
            }
        }
    }
}

package com.noirm.aburame;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Everything the Aburame add to the registries (docs/ABURAME.md): the swarm, the insect clone, the
 * Giant Insect, wild insects; the hive, the breeding jar and the pitcher plant; jars, the gourd, the
 * case, the net, gloves, eggs, the queen, the salad; and the Drained, Exhausted and Cell Rot states.
 */
public final class AburameRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Noirm.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Noirm.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Noirm.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Noirm.MOD_ID);

    // ---------------------------------------------------------------- entities

    /** One swarm of insects: the size here is a placeholder, it grows with its count. Never saved (it flies home). */
    public static final RegistryObject<EntityType<InsectSwarm>> INSECT_SWARM = ENTITIES.register("insect_swarm",
            () -> EntityType.Builder.<InsectSwarm>of(InsectSwarm::new, MobCategory.MISC)
                    .sized(1.5f, 1.5f)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(Noirm.id("insect_swarm").toString()));

    public static final RegistryObject<EntityType<InsectClone>> INSECT_CLONE = ENTITIES.register("insect_clone",
            () -> EntityType.Builder.<InsectClone>of(InsectClone::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .noSave()
                    .clientTrackingRange(10)
                    .build(Noirm.id("insect_clone").toString()));

    public static final RegistryObject<EntityType<GiantInsect>> GIANT_INSECT = ENTITIES.register("giant_insect",
            () -> EntityType.Builder.<GiantInsect>of(GiantInsect::new, MobCategory.MISC)
                    .sized(2.6f, 1.4f)
                    .clientTrackingRange(10)
                    .build(Noirm.id("giant_insect").toString()));

    public static final RegistryObject<EntityType<WildInsect>> WILD_INSECT = ENTITIES.register("wild_insect",
            () -> EntityType.Builder.<WildInsect>of(WildInsect::new, MobCategory.AMBIENT)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(6)
                    .build(Noirm.id("wild_insect").toString()));

    // ---------------------------------------------------------------- blocks

    public static final RegistryObject<Block> INSECT_HIVE = BLOCKS.register("insect_hive",
            () -> new InsectHiveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(1.5f)
                    .sound(SoundType.DECORATED_POT).noOcclusion()));
    public static final RegistryObject<Block> BREEDING_JAR = BLOCKS.register("breeding_jar",
            () -> new BreedingJarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.8f)
                    .sound(SoundType.GLASS).noOcclusion()));
    public static final RegistryObject<Block> PITCHER_PLANT = BLOCKS.register("pitcher_plant",
            () -> new PitcherPlantBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).instabreak()
                    .sound(SoundType.GRASS).noCollission().noOcclusion()));

    public static final RegistryObject<BlockEntityType<InsectHiveBlockEntity>> HIVE_ENTITY = BLOCK_ENTITIES.register("insect_hive",
            () -> BlockEntityType.Builder.of(InsectHiveBlockEntity::new, INSECT_HIVE.get()).build(null));
    public static final RegistryObject<BlockEntityType<BreedingJarBlockEntity>> JAR_ENTITY = BLOCK_ENTITIES.register("breeding_jar",
            () -> BlockEntityType.Builder.of(BreedingJarBlockEntity::new, BREEDING_JAR.get()).build(null));

    // ---------------------------------------------------------------- items

    public static final RegistryObject<Item> INSECT_HIVE_ITEM = ITEMS.register("insect_hive",
            () -> new BlockItem(INSECT_HIVE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BREEDING_JAR_ITEM = ITEMS.register("breeding_jar",
            () -> new BlockItem(BREEDING_JAR.get(), new Item.Properties()));
    public static final RegistryObject<Item> PITCHER_PLANT_ITEM = ITEMS.register("pitcher_plant",
            () -> new BlockItem(PITCHER_PLANT.get(), new Item.Properties()));
    /** A belt jar: 5,000 insects, worn from anywhere in your inventory (two count). */
    public static final RegistryObject<Item> INSECT_JAR = ITEMS.register("insect_jar",
            () -> new InsectJarItem(new Item.Properties().stacksTo(1), ColonyGrowth.JAR_CAPACITY, false));
    /** The big gourd: 25,000 (one counts). Not an armour piece: it works from the inventory. */
    public static final RegistryObject<Item> INSECT_GOURD = ITEMS.register("insect_gourd",
            () -> new InsectJarItem(new Item.Properties().stacksTo(1), ColonyGrowth.GOURD_CAPACITY, true));
    public static final RegistryObject<Item> INSECT_CASE = ITEMS.register("insect_case",
            () -> new InsectCaseItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BUG_NET = ITEMS.register("bug_net",
            () -> new BugNetItem(new Item.Properties().stacksTo(1).durability(128)));
    public static final RegistryObject<Item> ABURAME_GLOVES = ITEMS.register("aburame_gloves",
            () -> new AburameGlovesItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> KIDAICHU_EGG = ITEMS.register("kidaichu_egg",
            () -> new AburameItems.KidaichuEgg(new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> QUEEN_COCOON = ITEMS.register("queen_cocoon",
            () -> new AburameItems.QueenCocoon(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BIKOCHU_EGG = ITEMS.register("bikochu_egg",
            () -> new AburameItems.BikochuEgg(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BIKOCHU = ITEMS.register("bikochu",
            () -> new AburameItems.Bikochu(new Item.Properties().stacksTo(1)));
    /** Shino's favourite: wild-grass salad (+10 Vigour for an Aburame). */
    public static final RegistryObject<Item> WILD_GRASS_SALAD = ITEMS.register("wild_grass_salad",
            () -> new AburameItems.Salad(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(0.5f).build())));

    // ---------------------------------------------------------------- states

    /** Drained (0 chakra): slowness III, no sprinting, no jutsu, no dodging. */
    public static final RegistryObject<MobEffect> DRAINED = EFFECTS.register("drained",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x2B2B30) {}.addAttributeModifier(
                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "3f6a1c52-7a2b-4c1e-9d0e-11aa22bb3301", -0.45,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** Exhausted (after Drained, until 20% chakra): slowness I and 30% less damage. */
    public static final RegistryObject<MobEffect> EXHAUSTED = EFFECTS.register("exhausted",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x5A5068) {}.addAttributeModifier(
                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "3f6a1c52-7a2b-4c1e-9d0e-11aa22bb3302", -0.15,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** Cell Rot (rinkaichū): 1 damage a second per stack (amplifier + 1), it spreads by touch. */
    public static final RegistryObject<MobEffect> CELL_ROT = EFFECTS.register("cell_rot",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6A2A7A) {});

    private AburameRegistry() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(AburameRegistry::attributes);
        modBus.addListener(AburameRegistry::creativeTabs);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(INSECT_CLONE.get(), SparringNinja.createAttributes().build());
        event.put(GIANT_INSECT.get(), GiantInsect.createAttributes().build());
        event.put(WILD_INSECT.get(), WildInsect.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(INSECT_JAR);
            event.accept(INSECT_GOURD);
            event.accept(INSECT_CASE);
            event.accept(BUG_NET);
            event.accept(ABURAME_GLOVES);
            event.accept(KIDAICHU_EGG);
            event.accept(QUEEN_COCOON);
            event.accept(BIKOCHU_EGG);
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(INSECT_HIVE_ITEM);
            event.accept(BREEDING_JAR_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(PITCHER_PLANT_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(WILD_GRASS_SALAD);
        }
    }
}

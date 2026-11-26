package com.noirm.registry;

import com.noirm.Noirm;
import com.noirm.biju.SealingScrollItem;
import com.noirm.training.TrainingDummyItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every item NOIRM adds. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NoirmItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    public static final RegistryObject<Item> SPARRING_NINJA_SPAWN_EGG = ITEMS.register("sparring_ninja_spawn_egg",
            () -> new ForgeSpawnEggItem(NoirmEntities.SPARRING_NINJA, 0x2B2B33, 0xC23B22, new Item.Properties()));

    public static final RegistryObject<Item> TRAINING_DUMMY = ITEMS.register("training_dummy",
            () -> new TrainingDummyItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> SEALING_SCROLL = ITEMS.register("sealing_scroll",
            () -> new SealingScrollItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> TAILED_BEAST_SPAWN_EGG = ITEMS.register("tailed_beast_spawn_egg",
            () -> new ForgeSpawnEggItem(NoirmEntities.TAILED_BEAST, 0xD9661E, 0x3B1D0E, new Item.Properties()));

    /** One spawn egg per tailed beast (1–9 tails), colored after each beast. */
    private static final int[][] BEAST_EGG_COLORS = {
            {0xC9A86A, 0x5A3D1E}, {0x3A6FD8, 0x101C3A}, {0x7A8C78, 0xC0402A}, {0xB8402A, 0x5C2A1A}, {0xE8E2D4, 0x7A5A3A},
            {0xD8E8D0, 0x7AA0A0}, {0x6FB04A, 0x2A4A6A}, {0x7A4A6A, 0xE0D8C8}, {0xE07A20, 0x5A1A0A}};
    public static final java.util.List<RegistryObject<Item>> BEAST_SPAWN_EGGS = java.util.stream.IntStream.rangeClosed(1, 9)
            .mapToObj(t -> ITEMS.register("tailed_beast_" + t + "_spawn_egg", () -> (Item) new com.noirm.biju.BeastEggItem(t,
                    BEAST_EGG_COLORS[t - 1][0], BEAST_EGG_COLORS[t - 1][1], new Item.Properties())))
            .toList();

    /** Training weights: slower, but body-stat XP ×1.5 while carried (training/WeightedTraining). */
    public static final RegistryObject<Item> TRAINING_WEIGHTS = ITEMS.register("training_weights",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** A bowl of ramen from a village stand: a filling meal (gives the bowl back). */
    public static final RegistryObject<Item> RAMEN = ITEMS.register("ramen",
            () -> new net.minecraft.world.item.BowlFoodItem(new Item.Properties().stacksTo(16)
                    .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(10).saturationMod(0.9f).build())));

    public static final RegistryObject<Item> VILLAGE_MERCHANT_SPAWN_EGG = ITEMS.register("village_merchant_spawn_egg",
            () -> new ForgeSpawnEggItem(NoirmEntities.VILLAGE_MERCHANT, 0xE8D3A9, 0xD9661E, new Item.Properties()));

    /** Eyes taken from a fallen Uchiha, for transplants (see dojutsu/MangekyoTransplant). */
    public static final RegistryObject<Item> SHARINGAN_EYE = ITEMS.register("sharingan_eye",
            () -> new com.noirm.dojutsu.SharinganEyeItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> MANGEKYO_EYES = ITEMS.register("mangekyo_eyes",
            () -> new com.noirm.dojutsu.SharinganEyeItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    /** A master (Might Guy unless the egg's {@code EntityTag} names another, e.g. {@code {Master:"noirm:might_guy"}}). */
    public static final RegistryObject<Item> MASTER_SPAWN_EGG = ITEMS.register("master_spawn_egg",
            () -> new ForgeSpawnEggItem(NoirmEntities.MASTER, 0x3E7A2E, 0xE07A30, new Item.Properties()));

    private NoirmItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(SPARRING_NINJA_SPAWN_EGG);
            // One egg per canon clan: exactly that clan, never a surprise jinchūriki.
            for (String clan : java.util.List.of("uchiha", "senju", "hyuga", "uzumaki")) {
                net.minecraft.world.item.ItemStack egg = new net.minecraft.world.item.ItemStack(SPARRING_NINJA_SPAWN_EGG.get());
                net.minecraft.nbt.CompoundTag entity = new net.minecraft.nbt.CompoundTag();
                entity.putString("Clan", clan);
                entity.putInt("Tails", 0);
                egg.getOrCreateTag().put("EntityTag", entity);
                egg.setHoverName(net.minecraft.network.chat.Component.translatable("item.noirm.clan_ninja_spawn_egg",
                        net.minecraft.network.chat.Component.translatable("clan.noirm." + clan)).withStyle(s -> s.withItalic(false)));
                event.accept(egg);
            }
            event.accept(TAILED_BEAST_SPAWN_EGG);
            BEAST_SPAWN_EGGS.forEach(egg -> event.accept(egg.get().getDefaultInstance()));
            event.accept(VILLAGE_MERCHANT_SPAWN_EGG);
            event.accept(MASTER_SPAWN_EGG);
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(RAMEN);
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(TRAINING_DUMMY);
            event.accept(TRAINING_WEIGHTS);
            event.accept(SEALING_SCROLL);
            event.accept(com.noirm.dojutsu.MangekyoTransplant.EyeInfo.sharingan(3, "").toStack());
        }
    }
}

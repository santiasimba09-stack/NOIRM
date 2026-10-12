package com.noirm.registry;

import com.noirm.Noirm;
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

    private NoirmItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(SPARRING_NINJA_SPAWN_EGG);
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(TRAINING_DUMMY);
        }
    }
}

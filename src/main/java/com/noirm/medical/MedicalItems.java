package com.noirm.medical;

import com.noirm.Noirm;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The medical ninja's items: the antidote. */
public final class MedicalItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    /** Cures poison (and wither, nausea, hunger); only works if a medic-trained hand mixed it. */
    public static final RegistryObject<Item> ANTIDOTE = ITEMS.register("antidote",
            () -> new AntidoteItem(new Item.Properties().stacksTo(16)));

    private MedicalItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(MedicalItems::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ANTIDOTE);
        }
    }
}

package com.noirm.guide;

import com.noirm.Noirm;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The new-player guide's item: the Shinobi Handbook. */
public final class GuideItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);

    public static final RegistryObject<Item> HANDBOOK = ITEMS.register("shinobi_handbook",
            () -> new HandbookItem(new Item.Properties().stacksTo(1)));

    private GuideItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(GuideItems::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HANDBOOK);
        }
    }
}

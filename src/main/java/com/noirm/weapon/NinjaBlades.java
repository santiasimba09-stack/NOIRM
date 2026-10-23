package com.noirm.weapon;

import com.noirm.Noirm;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The v0.13 blades (see {@code docs/WEAPONS.md}, "More blades"): seven weapons with clearly
 * different feels, each with its own moveset. Numbers: {@code data/noirm/weapons/stats.json}
 * ({@link BladeStats}); effects: {@link BladeEvents}; icons: {@code tools/weapongen/gen_weapons.py}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NinjaBlades {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);
    /** Every blade, in creative-tab order. */
    public static final List<RegistryObject<NinjaBladeItem>> ALL = new ArrayList<>();

    /** Tantō: the ANBU's short blade. Very fast; stabs in the back cut deep. */
    public static final RegistryObject<NinjaBladeItem> TANTO = blade("tanto", Rarity.COMMON);
    /** Katana: a full-length two-hander, slower but strong; lightning chakra flows into it. */
    public static final RegistryObject<NinjaBladeItem> KATANA = blade("katana", Rarity.UNCOMMON);
    /** Nodachi: a huge field sword. Slow, wide sweeps, long reach, every hit shoves. */
    public static final RegistryObject<NinjaBladeItem> NODACHI = blade("nodachi", Rarity.UNCOMMON);
    /** Tonfa (a pair): fast blunt blows from both arms that crush guards. */
    public static final RegistryObject<NinjaBladeItem> TONFA = blade("tonfa", Rarity.COMMON);
    /** Yari: a straight spear with the longest reach of any hand weapon. */
    public static final RegistryObject<NinjaBladeItem> YARI = blade("yari", Rarity.COMMON);
    /** Tekko-kagi: hand claws. Fast rakes that make the target bleed; wind chakra flows into them. */
    public static final RegistryObject<NinjaBladeItem> TEKKO_KAGI = blade("tekko_kagi", Rarity.UNCOMMON);
    /** Tessen: an iron war fan. Quick strikes, a shoving gust, swats thrown things away. */
    public static final RegistryObject<NinjaBladeItem> TESSEN = blade("tessen", Rarity.COMMON);

    private NinjaBlades() {}

    private static RegistryObject<NinjaBladeItem> blade(String id, Rarity rarity) {
        RegistryObject<NinjaBladeItem> item = ITEMS.register(id, () -> new NinjaBladeItem(id, new Item.Properties().rarity(rarity)));
        ALL.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void addBladesToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            ALL.forEach(item -> event.accept(item.get()));
        }
    }
}

package com.noirm.gear;

import com.noirm.Noirm;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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
 * Every piece of wearable ninja gear. Players craft and wear them; ninjas spawn wearing their
 * clan's ({@link ClanOutfits}). Recipes live in {@code data/noirm/recipes/}, names in the lang files.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NoirmGear {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);
    /** In creative-tab order. */
    public static final List<RegistryObject<Item>> ALL = new ArrayList<>();

    // Forehead protectors (helmet)
    public static final RegistryObject<Item> FOREHEAD_PROTECTOR_NAVY = piece("forehead_protector_navy", GearMaterial.FOREHEAD_PROTECTOR, ArmorItem.Type.HELMET, "forehead_protector_navy");
    public static final RegistryObject<Item> FOREHEAD_PROTECTOR_BLACK = piece("forehead_protector_black", GearMaterial.FOREHEAD_PROTECTOR, ArmorItem.Type.HELMET, "forehead_protector_black");
    public static final RegistryObject<Item> FOREHEAD_PROTECTOR_RED = piece("forehead_protector_red", GearMaterial.FOREHEAD_PROTECTOR, ArmorItem.Type.HELMET, "forehead_protector_red");
    // Senju battle armour (no helmet: they fight bare-headed or with a forehead protector)
    public static final RegistryObject<Item> SENJU_CHESTPLATE = piece("senju_chestplate", GearMaterial.SENJU, ArmorItem.Type.CHESTPLATE, "senju");
    public static final RegistryObject<Item> SENJU_LEGGINGS = piece("senju_leggings", GearMaterial.SENJU, ArmorItem.Type.LEGGINGS, "senju");
    public static final RegistryObject<Item> SENJU_BOOTS = piece("senju_boots", GearMaterial.SENJU, ArmorItem.Type.BOOTS, "senju");
    // Uchiha
    public static final RegistryObject<Item> UCHIHA_TUNIC = piece("uchiha_tunic", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "uchiha");
    public static final RegistryObject<Item> UCHIHA_PANTS = piece("uchiha_pants", GearMaterial.PADDED, ArmorItem.Type.LEGGINGS, "uchiha");
    // Hyuga
    public static final RegistryObject<Item> HYUGA_ROBE = piece("hyuga_robe", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "hyuga");
    public static final RegistryObject<Item> HYUGA_PANTS = piece("hyuga_pants", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "hyuga");
    // Uzumaki
    public static final RegistryObject<Item> UZUMAKI_COAT = piece("uzumaki_coat", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "uzumaki");
    // Village forces
    public static final RegistryObject<Item> FLAK_VEST = piece("flak_vest", GearMaterial.FLAK, ArmorItem.Type.CHESTPLATE, "flak");
    public static final RegistryObject<Item> ANBU_MASK = piece("anbu_mask", GearMaterial.ANBU, ArmorItem.Type.HELMET, "anbu_mask");
    public static final RegistryObject<Item> ANBU_ARMOR = piece("anbu_armor", GearMaterial.ANBU, ArmorItem.Type.CHESTPLATE, "anbu");
    // Everyday
    public static final RegistryObject<Item> SHINOBI_PANTS = piece("shinobi_pants", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "shinobi");
    public static final RegistryObject<Item> SHINOBI_SANDALS = piece("shinobi_sandals", GearMaterial.CLOTH, ArmorItem.Type.BOOTS, "shinobi");

    private NoirmGear() {}

    private static RegistryObject<Item> piece(String id, GearMaterial material, ArmorItem.Type type, String texture) {
        RegistryObject<Item> item = ITEMS.register(id, () -> new GearItem(material, type, texture, new Item.Properties()));
        ALL.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void addGearToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            ALL.forEach(event::accept);
        }
    }
}

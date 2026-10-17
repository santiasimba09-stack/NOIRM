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
    public static final RegistryObject<Item> ANBU_MASK = piece("anbu_mask", GearMaterial.ANBU, ArmorItem.Type.HELMET, "anbu_mask", GearPerk.HIDE_NAME, 0f);
    public static final RegistryObject<Item> ANBU_ARMOR = piece("anbu_armor", GearMaterial.ANBU, ArmorItem.Type.CHESTPLATE, "anbu");
    // Everyday
    public static final RegistryObject<Item> SHINOBI_PANTS = piece("shinobi_pants", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "shinobi");
    public static final RegistryObject<Item> SHINOBI_SANDALS = piece("shinobi_sandals", GearMaterial.CLOTH, ArmorItem.Type.BOOTS, "shinobi");

    // ---- The second set (textures: tools/gear/more_gear.py) ----
    // Masks: hide your name tag.
    public static final RegistryObject<Item> MASK_WOLF = mask("mask_wolf", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> MASK_HAWK = mask("mask_hawk", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> MASK_BOAR = mask("mask_boar", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> MASK_CRANE = mask("mask_crane", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> ONI_MASK = mask("oni_mask", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> CLOTH_MASK = mask("cloth_mask", GearMaterial.CLOTH);
    // Headgear
    public static final RegistryObject<Item> FOREHEAD_PROTECTOR_SLASHED = piece("forehead_protector_slashed", GearMaterial.FOREHEAD_PROTECTOR, ArmorItem.Type.HELMET, "forehead_protector_slashed");
    public static final RegistryObject<Item> HEAD_WRAP_PROTECTOR = piece("head_wrap_protector", GearMaterial.FOREHEAD_PROTECTOR, ArmorItem.Type.HELMET, "head_wrap_protector");
    public static final RegistryObject<Item> STRAW_HAT = piece("straw_hat", GearMaterial.CLOTH, ArmorItem.Type.HELMET, "straw_hat");
    /** Kage hats, one colour per village look: Leaf, Sand, Snow, Mist, Cloud, Stone. */
    public static final RegistryObject<Item> KAGE_HAT_RED = piece("kage_hat_red", GearMaterial.ROBE, ArmorItem.Type.HELMET, "kage_hat_red");
    public static final RegistryObject<Item> KAGE_HAT_ORANGE = piece("kage_hat_orange", GearMaterial.ROBE, ArmorItem.Type.HELMET, "kage_hat_orange");
    public static final RegistryObject<Item> KAGE_HAT_BLUE = piece("kage_hat_blue", GearMaterial.ROBE, ArmorItem.Type.HELMET, "kage_hat_blue");
    public static final RegistryObject<Item> KAGE_HAT_TEAL = piece("kage_hat_teal", GearMaterial.ROBE, ArmorItem.Type.HELMET, "kage_hat_teal");
    public static final RegistryObject<Item> KAGE_HAT_GOLD = piece("kage_hat_gold", GearMaterial.ROBE, ArmorItem.Type.HELMET, "kage_hat_gold");
    public static final RegistryObject<Item> KAGE_HAT_BROWN = piece("kage_hat_brown", GearMaterial.ROBE, ArmorItem.Type.HELMET, "kage_hat_brown");
    // Kimonos (top + hem)
    public static final RegistryObject<Item> KIMONO_INDIGO = piece("kimono_indigo", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "kimono_indigo");
    public static final RegistryObject<Item> KIMONO_INDIGO_HEM = piece("kimono_indigo_hem", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "kimono_indigo");
    public static final RegistryObject<Item> KIMONO_CRIMSON = piece("kimono_crimson", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "kimono_crimson");
    public static final RegistryObject<Item> KIMONO_CRIMSON_HEM = piece("kimono_crimson_hem", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "kimono_crimson");
    public static final RegistryObject<Item> KIMONO_SAGE = piece("kimono_sage", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "kimono_sage");
    public static final RegistryObject<Item> KIMONO_SAGE_HEM = piece("kimono_sage_hem", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "kimono_sage");
    public static final RegistryObject<Item> KIMONO_PLUM = piece("kimono_plum", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "kimono_plum");
    public static final RegistryObject<Item> KIMONO_PLUM_HEM = piece("kimono_plum_hem", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "kimono_plum");
    public static final RegistryObject<Item> HAKAMA = piece("hakama", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "hakama");
    public static final RegistryObject<Item> HAORI = piece("haori", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "haori");
    // Vests: the jonin vest, and one per village look (the Leaf wears the green flak vest)
    public static final RegistryObject<Item> JONIN_VEST = piece("jonin_vest", GearMaterial.JONIN, ArmorItem.Type.CHESTPLATE, "jonin_vest");
    public static final RegistryObject<Item> VEST_SAND = piece("vest_sand", GearMaterial.FLAK, ArmorItem.Type.CHESTPLATE, "vest_sand");
    public static final RegistryObject<Item> VEST_SNOW = piece("vest_snow", GearMaterial.FLAK, ArmorItem.Type.CHESTPLATE, "vest_snow");
    public static final RegistryObject<Item> VEST_MIST = piece("vest_mist", GearMaterial.FLAK, ArmorItem.Type.CHESTPLATE, "vest_mist");
    public static final RegistryObject<Item> VEST_CLOUD = piece("vest_cloud", GearMaterial.FLAK, ArmorItem.Type.CHESTPLATE, "vest_cloud");
    public static final RegistryObject<Item> VEST_STONE = piece("vest_stone", GearMaterial.FLAK, ArmorItem.Type.CHESTPLATE, "vest_stone");
    // Cloaks and robes
    public static final RegistryObject<Item> TRAVEL_CLOAK = piece("travel_cloak", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "travel_cloak", GearPerk.SWIFT, 0f);
    public static final RegistryObject<Item> ROGUE_CLOAK = piece("rogue_cloak", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "rogue_cloak", GearPerk.SWIFT, 0f);
    public static final RegistryObject<Item> KAGE_ROBE = piece("kage_robe", GearMaterial.ROBE, ArmorItem.Type.CHESTPLATE, "kage_robe");
    // Arms
    public static final RegistryObject<Item> ARM_GUARDS = piece("arm_guards", GearMaterial.STEEL_GUARD, ArmorItem.Type.CHESTPLATE, "arm_guards", GearPerk.FISTS, GearPerks.ARM_GUARDS_DAMAGE);
    public static final RegistryObject<Item> HAND_WRAPS = piece("hand_wraps", GearMaterial.WRAPS, ArmorItem.Type.CHESTPLATE, "hand_wraps", GearPerk.FISTS, GearPerks.HAND_WRAPS_DAMAGE);
    // Feet
    public static final RegistryObject<Item> TABI = piece("tabi", GearMaterial.CLOTH, ArmorItem.Type.BOOTS, "tabi", GearPerk.SOFT_STEP, 0f);
    public static final RegistryObject<Item> WARAJI = piece("waraji", GearMaterial.WRAPS, ArmorItem.Type.BOOTS, "waraji", GearPerk.SOFT_STEP, 0f);
    public static final RegistryObject<Item> SHIN_GUARDS = piece("shin_guards", GearMaterial.STEEL_GUARD, ArmorItem.Type.BOOTS, "shin_guards");

    /** The animal masks ANBU-style patrols wear (the first one is the original ANBU mask). */
    public static List<RegistryObject<Item>> animalMasks() {
        return List.of(ANBU_MASK, MASK_WOLF, MASK_HAWK, MASK_BOAR, MASK_CRANE);
    }

    private NoirmGear() {}

    private static RegistryObject<Item> piece(String id, GearMaterial material, ArmorItem.Type type, String texture) {
        return piece(id, material, type, texture, GearPerk.NONE, 0f);
    }

    private static RegistryObject<Item> piece(String id, GearMaterial material, ArmorItem.Type type, String texture, GearPerk perk, float power) {
        RegistryObject<Item> item = ITEMS.register(id, () -> new GearItem(material, type, texture, perk, power, new Item.Properties()));
        ALL.add(item);
        return item;
    }

    private static RegistryObject<Item> mask(String id, GearMaterial material) {
        return piece(id, material, ArmorItem.Type.HELMET, id, GearPerk.HIDE_NAME, 0f);
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

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
    public static final RegistryObject<Item> ANBU_MASK = animalMask("anbu_mask", GearMaterial.ANBU);
    public static final RegistryObject<Item> ANBU_ARMOR = piece("anbu_armor", GearMaterial.ANBU, ArmorItem.Type.CHESTPLATE, "anbu");
    // Everyday
    public static final RegistryObject<Item> SHINOBI_PANTS = piece("shinobi_pants", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "shinobi");
    public static final RegistryObject<Item> SHINOBI_SANDALS = piece("shinobi_sandals", GearMaterial.CLOTH, ArmorItem.Type.BOOTS, "shinobi");

    // ---- The second set (textures: tools/gear/more_gear.py) ----
    // Masks: hide your name tag.
    public static final RegistryObject<Item> MASK_WOLF = animalMask("mask_wolf", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> MASK_HAWK = animalMask("mask_hawk", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> MASK_BOAR = animalMask("mask_boar", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> MASK_CRANE = animalMask("mask_crane", GearMaterial.PORCELAIN);
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

    // ---- The third set, "armor wave" (round 16; textures: tools/gear/armor_wave.py) ----
    // Shinobi basics
    public static final RegistryObject<Item> MESH_SHIRT = piece("mesh_shirt", GearMaterial.MESH, ArmorItem.Type.CHESTPLATE, "mesh_shirt");
    public static final RegistryObject<Item> TRAINING_SHIRT = piece("training_shirt", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "training_shirt");
    public static final RegistryObject<Item> HIGH_COLLAR_JACKET = piece("high_collar_jacket", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "high_collar_jacket");
    public static final RegistryObject<Item> SARASHI_WRAPS = piece("sarashi_wraps", GearMaterial.WRAPS, ArmorItem.Type.CHESTPLATE, "sarashi_wraps", GearPerk.FISTS, GearPerks.SARASHI_DAMAGE);
    public static final RegistryObject<Item> BANDAGE_LEGGINGS = piece("bandage_leggings", GearMaterial.WRAPS, ArmorItem.Type.LEGGINGS, "bandage_leggings");
    public static final RegistryObject<Item> JIKA_TABI = piece("jika_tabi", GearMaterial.CLOTH, ArmorItem.Type.BOOTS, "jika_tabi", GearPerk.SOFT_STEP, 0f);
    public static final RegistryObject<Item> JIKA_TABI_ASH = piece("jika_tabi_ash", GearMaterial.CLOTH, ArmorItem.Type.BOOTS, "jika_tabi_ash", GearPerk.SOFT_STEP, 0f);
    public static final RegistryObject<Item> WRAPPED_SANDALS = piece("wrapped_sandals", GearMaterial.WRAPS, ArmorItem.Type.BOOTS, "wrapped_sandals", GearPerk.SOFT_STEP, 0f);
    // Heavy armour (samurai-style): tough, but every piece slows you a little
    public static final RegistryObject<Item> SAMURAI_CUIRASS = heavy("samurai_cuirass", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> SODE_PAULDRONS = piece("sode_pauldrons", GearMaterial.SAMURAI, ArmorItem.Type.CHESTPLATE, "sode_pauldrons", GearPerk.HEAVY, GearPerks.HEAVY_SLOW * 0.5f);
    public static final RegistryObject<Item> KABUTO = heavy("kabuto", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> KUSAZURI = heavy("kusazuri", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> SAMURAI_GREAVES = heavy("samurai_greaves", ArmorItem.Type.BOOTS);
    // Rogues and assassins
    public static final RegistryObject<Item> TATTERED_HOOD = mask("tattered_hood", GearMaterial.CLOTH);
    public static final RegistryObject<Item> TATTERED_CLOAK = piece("tattered_cloak", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "tattered_cloak", GearPerk.SWIFT, 0f);
    public static final RegistryObject<Item> HALF_MASK = mask("half_mask", GearMaterial.STEEL_GUARD);
    public static final RegistryObject<Item> ASSASSIN_SUIT = piece("assassin_suit", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "assassin_suit", GearPerk.SHADOW, 0f);
    public static final RegistryObject<Item> ASSASSIN_PANTS = piece("assassin_pants", GearMaterial.PADDED, ArmorItem.Type.LEGGINGS, "assassin_pants", GearPerk.SHADOW, 0f);
    public static final RegistryObject<Item> BANDOLIER = piece("bandolier", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "bandolier");
    // Village forehead protectors (our own marks): Mist, Cloud, Stone, Sand looks
    public static final RegistryObject<Item> FOREHEAD_PROTECTOR_MIST = piece("forehead_protector_mist", GearMaterial.FOREHEAD_PROTECTOR, ArmorItem.Type.HELMET, "forehead_protector_mist");
    public static final RegistryObject<Item> FOREHEAD_PROTECTOR_CLOUD = piece("forehead_protector_cloud", GearMaterial.FOREHEAD_PROTECTOR, ArmorItem.Type.HELMET, "forehead_protector_cloud");
    public static final RegistryObject<Item> FOREHEAD_PROTECTOR_STONE = piece("forehead_protector_stone", GearMaterial.FOREHEAD_PROTECTOR, ArmorItem.Type.HELMET, "forehead_protector_stone");
    public static final RegistryObject<Item> FOREHEAD_PROTECTOR_SAND = piece("forehead_protector_sand", GearMaterial.FOREHEAD_PROTECTOR, ArmorItem.Type.HELMET, "forehead_protector_sand");
    // Medic
    public static final RegistryObject<Item> MEDIC_COAT = piece("medic_coat", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "medic_coat", GearPerk.MENDING, 0f);
    // Ceremonial
    public static final RegistryObject<Item> FORMAL_ROBE = piece("formal_robe", GearMaterial.ROBE, ArmorItem.Type.CHESTPLATE, "formal_robe");
    public static final RegistryObject<Item> FORMAL_ROBE_HEM = piece("formal_robe_hem", GearMaterial.ROBE, ArmorItem.Type.LEGGINGS, "formal_robe");
    public static final RegistryObject<Item> BATTLE_KIMONO = piece("battle_kimono", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "battle_kimono");
    public static final RegistryObject<Item> BATTLE_HAKAMA = piece("battle_hakama", GearMaterial.PADDED, ArmorItem.Type.LEGGINGS, "battle_kimono");

    // ---- v0.3 clan wear (textures, drapes, icons, recipes: tools/gear/clan_wear.py; 3D cloth: Drapes) ----
    // Uchiha: the high-collar shirt with the fan crest and arm warmers; the warring-states armour
    public static final RegistryObject<Item> UCHIHA_COLLAR_SHIRT = piece("uchiha_collar_shirt", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "uchiha_collar_shirt");
    public static final RegistryObject<Item> UCHIHA_WAR_ARMOR = piece("uchiha_war_armor", GearMaterial.SENJU, ArmorItem.Type.CHESTPLATE, "uchiha_war_armor");
    public static final RegistryObject<Item> UCHIHA_WAR_TASSETS = piece("uchiha_war_tassets", GearMaterial.SENJU, ArmorItem.Type.LEGGINGS, "uchiha_war_armor");
    // Uzumaki: the swirl vest, the Uzushio robe
    public static final RegistryObject<Item> UZUMAKI_VEST = piece("uzumaki_vest", GearMaterial.FLAK, ArmorItem.Type.CHESTPLATE, "uzumaki_vest");
    public static final RegistryObject<Item> UZUSHIO_ROBE = piece("uzushio_robe", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "uzushio_robe");
    public static final RegistryObject<Item> UZUSHIO_ROBE_HEM = piece("uzushio_robe_hem", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "uzushio_robe");
    // Aburame: long high-collared coats, the hood with dark glasses, the glasses
    public static final RegistryObject<Item> ABURAME_COAT = piece("aburame_coat", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "aburame_coat");
    public static final RegistryObject<Item> ABURAME_FIELD_COAT = piece("aburame_field_coat", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "aburame_field_coat");
    public static final RegistryObject<Item> ABURAME_HOOD = mask("aburame_hood", GearMaterial.CLOTH);
    public static final RegistryObject<Item> ABURAME_GLASSES = piece("aburame_glasses", GearMaterial.CLOTH, ArmorItem.Type.HELMET, "aburame_glasses");
    // Senju: red-plated warring-states armour (chest and shoulder plates, thigh plates) and a lighter robe
    public static final RegistryObject<Item> SENJU_PLATE_ARMOR = piece("senju_plate_armor", GearMaterial.SENJU, ArmorItem.Type.CHESTPLATE, "senju_plate_armor");
    public static final RegistryObject<Item> SENJU_THIGH_PLATES = piece("senju_thigh_plates", GearMaterial.SENJU, ArmorItem.Type.LEGGINGS, "senju_plate_armor");
    public static final RegistryObject<Item> SENJU_ROBE = piece("senju_robe", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "senju_robe");
    // The other clans: one recognisable piece each
    public static final RegistryObject<Item> HYUGA_GOWN = piece("hyuga_gown", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "hyuga_gown");
    public static final RegistryObject<Item> HYUGA_GOWN_HEM = piece("hyuga_gown_hem", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "hyuga_gown");
    public static final RegistryObject<Item> NARA_VEST = piece("nara_vest", GearMaterial.JONIN, ArmorItem.Type.CHESTPLATE, "nara_vest");
    public static final RegistryObject<Item> AKIMICHI_ARMOR = piece("akimichi_armor", GearMaterial.JONIN, ArmorItem.Type.CHESTPLATE, "akimichi_armor");
    public static final RegistryObject<Item> INUZUKA_FUR_COAT = piece("inuzuka_fur_coat", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "inuzuka_fur_coat");
    public static final RegistryObject<Item> KAGUYA_ROBE = piece("kaguya_robe", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "kaguya_robe");
    public static final RegistryObject<Item> KAGUYA_ROBE_HEM = piece("kaguya_robe_hem", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "kaguya_robe");
    public static final RegistryObject<Item> HOSHIGAKI_CLOAK = piece("hoshigaki_cloak", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "hoshigaki_cloak", GearPerk.SWIFT, 0f);
    public static final RegistryObject<Item> YAMANAKA_WRAP = piece("yamanaka_wrap", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "yamanaka_wrap");
    public static final RegistryObject<Item> MIGHT_JUMPSUIT = piece("might_jumpsuit", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "might_jumpsuit");
    public static final RegistryObject<Item> MIGHT_JUMPSUIT_LEGS = piece("might_jumpsuit_legs", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "might_jumpsuit");
    public static final RegistryObject<Item> MIGHT_LEG_WARMERS = piece("might_leg_warmers", GearMaterial.CLOTH, ArmorItem.Type.BOOTS, "might_leg_warmers");
    public static final RegistryObject<Item> HUNTER_MASK = animalMask("hunter_mask", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> SAND_GOURD = piece("sand_gourd", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "sand_gourd");
    public static final RegistryObject<Item> ROOT_ROBE = piece("root_robe", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "root_robe");
    public static final RegistryObject<Item> CHINOIKE_ROBE = piece("chinoike_robe", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "chinoike_robe");
    public static final RegistryObject<Item> YOTSUKI_HARNESS = piece("yotsuki_harness", GearMaterial.FLAK, ArmorItem.Type.CHESTPLATE, "yotsuki_harness");
    // Capes: worn alone or sewn onto a chest piece (CapeSewingRecipe)
    public static final RegistryObject<Item> CAPE = cape("cape", true);
    public static final RegistryObject<Item> KAGE_CAPE = cape("kage_cape", false);
    public static final RegistryObject<Item> TRAVEL_CAPE = cape("travel_cape", false);
    public static final RegistryObject<Item> ROGUE_CAPE = cape("rogue_cape", false);
    public static final RegistryObject<Item> UCHIHA_CAPE = cape("uchiha_cape", false);
    public static final RegistryObject<Item> SENJU_CAPE = cape("senju_cape", false);
    public static final RegistryObject<Item> UZUMAKI_CAPE = cape("uzumaki_cape", false);
    // The cloud cloak (long, black, high collar, red clouds) and the straw travel hat with strips; a hooded travel cloak
    public static final RegistryObject<Item> CLOUD_CLOAK = piece("cloud_cloak", GearMaterial.PADDED, ArmorItem.Type.CHESTPLATE, "cloud_cloak", GearPerk.SWIFT, 0f);
    public static final RegistryObject<Item> KASA_HAT = piece("kasa_hat", GearMaterial.CLOTH, ArmorItem.Type.HELMET, "kasa_hat");
    public static final RegistryObject<Item> HOODED_CLOAK = piece("hooded_cloak", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "hooded_cloak", GearPerk.SWIFT, 0f);
    public static final RegistryObject<Item> TRAVEL_HOOD = mask("travel_hood", GearMaterial.CLOTH);
    // ANBU: four more animal masks, the uniform (grey vest, arm guards, the spiral mark) and trousers
    public static final RegistryObject<Item> MASK_CAT = animalMask("mask_cat", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> MASK_FOX = animalMask("mask_fox", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> MASK_DOG = animalMask("mask_dog", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> MASK_BIRD = animalMask("mask_bird", GearMaterial.PORCELAIN);
    public static final RegistryObject<Item> ANBU_UNIFORM = piece("anbu_uniform", GearMaterial.ANBU, ArmorItem.Type.CHESTPLATE, "anbu_uniform");
    public static final RegistryObject<Item> ANBU_PANTS = piece("anbu_pants", GearMaterial.ANBU, ArmorItem.Type.LEGGINGS, "anbu_uniform");
    // Gowns and robes that flow over the legs
    public static final RegistryObject<Item> KAGE_ROBE_HEM = piece("kage_robe_hem", GearMaterial.ROBE, ArmorItem.Type.LEGGINGS, "kage_robe_hem");
    public static final RegistryObject<Item> PRIESTESS_GOWN = piece("priestess_gown", GearMaterial.ROBE, ArmorItem.Type.CHESTPLATE, "priestess_gown");
    public static final RegistryObject<Item> PRIESTESS_GOWN_HEM = piece("priestess_gown_hem", GearMaterial.ROBE, ArmorItem.Type.LEGGINGS, "priestess_gown");
    public static final RegistryObject<Item> MEDIC_GOWN = piece("medic_gown", GearMaterial.CLOTH, ArmorItem.Type.CHESTPLATE, "medic_gown", GearPerk.MENDING, 0f);
    public static final RegistryObject<Item> MEDIC_GOWN_HEM = piece("medic_gown_hem", GearMaterial.CLOTH, ArmorItem.Type.LEGGINGS, "medic_gown");

    /** The cape-sewing recipe (a chest piece + a cape). */
    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Noirm.MOD_ID);
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<CapeSewingRecipe>> CAPE_SEWING =
            SERIALIZERS.register("cape_sewing", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(CapeSewingRecipe::new));

    /** The v0.3 clan wear, in creative-tab order. */
    public static List<RegistryObject<Item>> clanWear() {
        return ALL.subList(ALL.indexOf(UCHIHA_COLLAR_SHIRT), ALL.indexOf(MEDIC_GOWN_HEM) + 1);
    }

    /** Every cape (they can be sewn onto chest pieces). */
    public static List<RegistryObject<Item>> capes() {
        return List.of(CAPE, KAGE_CAPE, TRAVEL_CAPE, ROGUE_CAPE, UCHIHA_CAPE, SENJU_CAPE, UZUMAKI_CAPE);
    }

    /** The armor wave's pieces (round 16), in creative-tab order. */
    public static List<RegistryObject<Item>> armorWave() {
        return ALL.subList(ALL.indexOf(MESH_SHIRT), ALL.indexOf(BATTLE_HAKAMA) + 1);
    }

    /** The samurai-style set (helmet, chest, legs, feet). */
    public static List<RegistryObject<Item>> samuraiSet() {
        return List.of(KABUTO, SAMURAI_CUIRASS, KUSAZURI, SAMURAI_GREAVES);
    }

    /** The animal masks ANBU-style patrols wear (the first one is the original ANBU mask). */
    public static List<RegistryObject<Item>> animalMasks() {
        return List.of(ANBU_MASK, MASK_WOLF, MASK_HAWK, MASK_BOAR, MASK_CRANE, MASK_CAT, MASK_FOX, MASK_DOG, MASK_BIRD);
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

    /** A face mask that can be worn pushed aside ({@link MaskItem}). */
    private static RegistryObject<Item> animalMask(String id, GearMaterial material) {
        RegistryObject<Item> item = ITEMS.register(id, () -> new MaskItem(material, id, new Item.Properties()));
        ALL.add(item);
        return item;
    }

    private static RegistryObject<Item> cape(String id, boolean dyeable) {
        RegistryObject<Item> item = ITEMS.register(id, () -> dyeable ? new CapeItem.Dyeable(GearMaterial.CLOTH, id, new Item.Properties())
                : new CapeItem(GearMaterial.CLOTH, id, new Item.Properties()));
        ALL.add(item);
        return item;
    }

    private static RegistryObject<Item> heavy(String id, ArmorItem.Type type) {
        return piece(id, GearMaterial.SAMURAI, type, id, GearPerk.HEAVY, GearPerks.HEAVY_SLOW);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        SERIALIZERS.register(modBus);
    }

    @SubscribeEvent
    public static void addGearToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            ALL.forEach(event::accept);
        }
    }
}

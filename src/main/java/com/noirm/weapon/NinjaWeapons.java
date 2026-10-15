package com.noirm.weapon;

import com.noirm.Noirm;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
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
 * The ninja tools and weapons (see {@code docs/WEAPONS.md}): their items, the thrown-weapon
 * entity, and their place in the Combat creative tab. Recipes live in {@code data/noirm/recipes/},
 * names in the lang files, textures come from {@code tools/weapongen/gen_weapons.py}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NinjaWeapons {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Noirm.MOD_ID);
    /** In creative-tab order. */
    public static final List<RegistryObject<? extends Item>> ALL = new ArrayList<>();

    // Throwables (melee: kunai only).
    public static final RegistryObject<ThrowingWeaponItem> KUNAI = item("kunai",
            () -> new ThrowingWeaponItem(new Item.Properties().stacksTo(16), ThrowStats.KUNAI, 3f, -2.0f, Moveset.KUNAI, false));
    public static final RegistryObject<ThrowingWeaponItem> SHURIKEN = item("shuriken",
            () -> new ThrowingWeaponItem(new Item.Properties().stacksTo(32), ThrowStats.SHURIKEN, 0f, 0f, null, true));
    public static final RegistryObject<ThrowingWeaponItem> FUMA_SHURIKEN = item("fuma_shuriken",
            () -> new ThrowingWeaponItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), ThrowStats.FUMA_SHURIKEN, 0f, 0f, null, false));
    public static final RegistryObject<ThrowingWeaponItem> SENBON = item("senbon",
            () -> new ThrowingWeaponItem(new Item.Properties().stacksTo(64), ThrowStats.SENBON, 0f, 0f, null, false));
    public static final RegistryObject<ThrowingWeaponItem> EXPLOSIVE_KUNAI = item("explosive_kunai",
            () -> new ThrowingWeaponItem(new Item.Properties().stacksTo(16), ThrowStats.EXPLOSIVE_KUNAI, 0f, 0f, null, false));
    public static final RegistryObject<ExplosiveTagItem> EXPLOSIVE_TAG = item("explosive_tag",
            () -> new ExplosiveTagItem(new Item.Properties().stacksTo(16)));
    // Melee weapons.
    /** Bō staff: 5 damage, 1.4 swings a second, +1 block reach. */
    public static final RegistryObject<StaffItem> BO_STAFF = item("bo_staff",
            () -> new StaffItem(new Item.Properties().durability(300), 4f, -2.6f));
    /** Ninjatō: 6 damage, 1.8 swings a second. */
    public static final RegistryObject<SwordItem> NINJATO = item("ninjato",
            () -> new SwordItem(WeaponTiers.NINJATO, 3, -2.2f, new Item.Properties()));
    /** Chakra blades: 5 damage, 1.9 swings a second, plus the wielder's nature. */
    public static final RegistryObject<ChakraBladeItem> CHAKRA_BLADE = item("chakra_blade",
            () -> new ChakraBladeItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<EntityType<ThrownWeapon>> THROWN_WEAPON = ENTITIES.register("thrown_weapon",
            () -> EntityType.Builder.<ThrownWeapon>of(ThrownWeapon::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build(Noirm.id("thrown_weapon").toString()));

    private NinjaWeapons() {}

    private static <T extends Item> RegistryObject<T> item(String id, java.util.function.Supplier<T> factory) {
        RegistryObject<T> item = ITEMS.register(id, factory);
        ALL.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
    }

    @SubscribeEvent
    public static void addWeaponsToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            ALL.forEach(item -> event.accept(item.get()));
        }
    }
}

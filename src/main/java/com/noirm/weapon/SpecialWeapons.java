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
 * Special weapons (see {@code docs/WEAPONS.md}, "Special weapons"): four legendary blades that
 * only rogue swordsmen carry (and rarely drop), plus the war fan and the kusarigama, which smiths
 * can make. Their effects live in {@link SpecialWeaponEvents}; all the numbers are here.
 *
 * <p>Damage below is the tooltip number (1 + tier bonus + the item's own); speed is swings a second.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpecialWeapons {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Noirm.MOD_ID);
    /** Every special weapon, in creative-tab order. */
    public static final List<RegistryObject<SpecialWeaponItem>> ALL = new ArrayList<>();
    /** Just the legendary blades (drop only). */
    public static final List<RegistryObject<SpecialWeaponItem>> LEGENDARY = new ArrayList<>();

    // ---- effect numbers ----
    /** Crescent Cleaver: heals this share of the damage dealt… */
    public static final float LIFESTEAL_SHARE = 0.2f;
    /** …but at most this much per hit. */
    public static final float LIFESTEAL_MAX = 3f;
    /** …and the blade mends this much durability per hit. */
    public static final int LIFESTEAL_MEND = 2;
    /** Sharkskin Greatsword: chakra eaten per hit (from players; from ninjas, posture instead). */
    public static final float DRAIN_CHAKRA = 8f;
    /** Posture an NPC target loses to the sharkskin. */
    public static final float DRAIN_POSTURE = 10f;
    /** Health a ninja wielding the sharkskin regains per hit (ninjas have no chakra pool). */
    public static final float DRAIN_NPC_HEAL = 1f;
    /** Thunder Fangs: stun per hit, and its chakra price for players. */
    public static final int THUNDER_STUN = 6;
    public static final float THUNDER_CHAKRA = 2f;
    /** Gale Gunbai: how hard a hit blows the target back (horizontal, then up). */
    public static final double GALE_PUSH = 1.1;
    public static final double GALE_LIFT = 0.25;
    /** Speed a reflected projectile flies back at (× its own). */
    public static final double REFLECT_SPEED = 1.0;
    /** Kusarigama: pull strength, and the distance beyond which it pulls. */
    public static final double CHAIN_PULL = 0.55;
    public static final double CHAIN_MIN_DISTANCE = 2.5;

    /** 10 damage, 0.9 swings/s. Heals the wielder on hit. */
    public static final RegistryObject<SpecialWeaponItem> CRESCENT_CLEAVER = legendary("crescent_cleaver", 6, -3.1f, Moveset.SWORD, 0.5, SpecialWeaponItem.Power.LIFESTEAL);
    /** 9 damage, 1 swing/s. Eats chakra. */
    public static final RegistryObject<SpecialWeaponItem> SHARKSKIN_GREATSWORD = legendary("sharkskin_greatsword", 5, -3.0f, Moveset.SWORD, 0.5, SpecialWeaponItem.Power.CHAKRA_DRAIN);
    /** 7 damage, 1.8 swings/s, kunai moveset. Lightning stuns. */
    public static final RegistryObject<SpecialWeaponItem> THUNDER_FANGS = legendary("thunder_fangs", 3, -2.2f, Moveset.KUNAI, 0, SpecialWeaponItem.Power.THUNDER);
    /** 6 damage, 2.6 swings/s, +0.5 reach. Fast. */
    public static final RegistryObject<SpecialWeaponItem> REEDCUTTER = legendary("reedcutter", 2, -1.4f, Moveset.SWORD, 0.5, SpecialWeaponItem.Power.SWIFT);
    /** 5 damage, 1.2 swings/s, staff moveset. Blows back, guards like a shield, reflects projectiles. */
    public static final RegistryObject<SpecialWeaponItem> GALE_GUNBAI = item("gale_gunbai",
            () -> new SpecialWeaponItem(WeaponTiers.NINJATO, 2, -2.8f, Moveset.STAFF, 0.5, SpecialWeaponItem.Power.GALE,
                    new Item.Properties().rarity(Rarity.UNCOMMON)));
    /** 5 damage, 1.4 swings/s, staff moveset, +2 reach. Drags the target in. */
    public static final RegistryObject<SpecialWeaponItem> KUSARIGAMA = item("kusarigama",
            () -> new SpecialWeaponItem(WeaponTiers.NINJATO, 2, -2.6f, Moveset.STAFF, 2.0, SpecialWeaponItem.Power.CHAIN,
                    new Item.Properties().rarity(Rarity.UNCOMMON)));

    private SpecialWeapons() {}

    private static RegistryObject<SpecialWeaponItem> legendary(String id, int damage, float speed, Moveset moveset, double reach,
                                                              SpecialWeaponItem.Power power) {
        RegistryObject<SpecialWeaponItem> item = item(id, () -> new SpecialWeaponItem(WeaponTiers.LEGENDARY, damage, speed, moveset, reach, power,
                new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
        LEGENDARY.add(item);
        return item;
    }

    private static RegistryObject<SpecialWeaponItem> item(String id, java.util.function.Supplier<SpecialWeaponItem> factory) {
        RegistryObject<SpecialWeaponItem> item = ITEMS.register(id, factory);
        ALL.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            ALL.forEach(item -> event.accept(item.get()));
        }
    }
}

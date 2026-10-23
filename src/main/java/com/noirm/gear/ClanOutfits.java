package com.noirm.gear;

import com.noirm.clan.Clan;
import com.noirm.entity.SparringNinja;
import com.noirm.world.village.VillagePalette;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Dresses a ninja in its clan's gear when it joins the clan: Senju often in red battle armour,
 * Uchiha in navy tunics, Hyuga in robes, Uzumaki in red coats, everyone else in flak vests and
 * forehead protectors (rarely ANBU gear). Pieces are mixed and some ninjas wear nothing.
 * Wandering ninjas are sometimes rogues ({@link #rogue}); village guards, patrols and the Kage
 * wear their village's look ({@link #dressResident}).
 * It happens once per ninja; ninjas already wearing something (a crew mate you dressed) keep it.
 * Gear rarely drops ({@link #DROP_CHANCE}), so farming ninjas is not the way to get it.
 */
public final class ClanOutfits {
    public static final float DROP_CHANCE = 0.05f;
    /** Chance a ninja wears no gear at all. */
    public static final float BARE_CHANCE = 0.12f;
    /** Game-test clans ({@code TestClans}) stay bare so armour never changes test numbers. */
    public static final String TEST_PREFIX = "test_";
    /** Chance a wandering (non-village) ninja dresses as a rogue. */
    public static final float ROGUE_CHANCE = 0.2f;
    /** Chance a village patrol is ANBU-style (animal mask and plated vest). */
    public static final float ANBU_PATROL_CHANCE = 0.4f;

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ClanOutfits() {}

    /** Hands out the clan's gear, once per ninja (server side). */
    public static void equip(SparringNinja ninja, Clan clan, RandomSource random) {
        if (ninja.gearRolled()) {
            return;
        }
        ninja.setGearRolled(true);
        if (clan.id().startsWith(TEST_PREFIX)) {
            return;
        }
        com.noirm.weapon.NinjaArsenal.arm(ninja, random); // weapons and throwables (hands only)
        for (EquipmentSlot slot : ARMOR) {
            if (!ninja.getItemBySlot(slot).isEmpty()) {
                return; // already dressed
            }
        }
        Map<EquipmentSlot, Item> outfit = roll(clan.id(), random);
        // Wandering ninjas (not village residents) are often rogues; legendary swordsmen always are.
        boolean legendary = ninja.getMainHandItem().getItem() instanceof com.noirm.weapon.SpecialWeaponItem
                || com.noirm.weapon.NinjaArsenal.isRogueBlade(ninja.getMainHandItem().getItem());
        if (!ninja.isResident() && (legendary || random.nextFloat() < ROGUE_CHANCE)) {
            outfit = rogue(outfit, random);
        }
        wear(ninja, outfit);
    }

    private static void wear(SparringNinja ninja, Map<EquipmentSlot, Item> outfit) {
        outfit.forEach((slot, item) -> {
            ninja.setItemSlot(slot, new ItemStack(item));
            ninja.setDropChance(slot, DROP_CHANCE);
        });
    }

    /**
     * A rogue's look over their clan outfit: a cloak (usually the rogue cloak), something to hide
     * their face or their village (a slashed forehead protector, a straw hat, a mask), and boots.
     */
    public static Map<EquipmentSlot, Item> rogue(Map<EquipmentSlot, Item> clanOutfit, RandomSource r) {
        Map<EquipmentSlot, Item> out = new EnumMap<>(EquipmentSlot.class);
        out.putAll(clanOutfit);
        out.put(EquipmentSlot.CHEST, r.nextFloat() < 0.65f ? NoirmGear.ROGUE_CLOAK.get() : NoirmGear.TRAVEL_CLOAK.get());
        float head = r.nextFloat();
        Item hat = head < 0.4f ? NoirmGear.FOREHEAD_PROTECTOR_SLASHED.get()
                : head < 0.65f ? NoirmGear.STRAW_HAT.get()
                : head < 0.8f ? NoirmGear.ONI_MASK.get()
                : head < 0.9f ? NoirmGear.CLOTH_MASK.get()
                : null;
        if (hat != null) {
            out.put(EquipmentSlot.HEAD, hat);
        }
        if (!out.containsKey(EquipmentSlot.LEGS) && r.nextFloat() < 0.5f) {
            out.put(EquipmentSlot.LEGS, r.nextFloat() < 0.5f ? NoirmGear.HAKAMA.get() : NoirmGear.SHINOBI_PANTS.get());
        }
        if (!out.containsKey(EquipmentSlot.FEET) || r.nextFloat() < 0.4f) {
            out.put(EquipmentSlot.FEET, r.nextFloat() < 0.5f ? NoirmGear.WARAJI.get() : NoirmGear.TABI.get());
        }
        return out;
    }

    /**
     * Dresses a village's guards, patrols and Kage in their village's look (server side, when the
     * village spawns them). Guards wear the village vest and a forehead protector, patrols are
     * often ANBU-style (animal mask, plated vest), the Kage wears the robe and the village's hat.
     * Marks the ninja dressed, so joining a clan later doesn't re-dress them.
     */
    public static void dressResident(SparringNinja ninja, String role, VillagePalette palette, RandomSource r) {
        Map<EquipmentSlot, Item> outfit = residentOutfit(role, palette, r);
        if (outfit.isEmpty()) {
            return;
        }
        ninja.setGearRolled(true);
        wear(ninja, outfit);
        if (outfit.get(EquipmentSlot.CHEST) == NoirmGear.ANBU_ARMOR.get()) {
            com.noirm.weapon.NinjaArsenal.armAnbu(ninja, r); // ANBU carry the short tantō
        }
    }

    /** What a village resident of {@code role} ("GUARD", "PATROL", "KAGE") wears; empty for other roles. */
    public static Map<EquipmentSlot, Item> residentOutfit(String role, VillagePalette palette, RandomSource r) {
        Map<EquipmentSlot, Item> out = new EnumMap<>(EquipmentSlot.class);
        switch (role) {
            case "KAGE" -> {
                out.put(EquipmentSlot.HEAD, kageHat(palette));
                out.put(EquipmentSlot.CHEST, NoirmGear.KAGE_ROBE.get());
                out.put(EquipmentSlot.LEGS, NoirmGear.SHINOBI_PANTS.get());
                out.put(EquipmentSlot.FEET, NoirmGear.SHINOBI_SANDALS.get());
            }
            case "PATROL" -> {
                if (r.nextFloat() < ANBU_PATROL_CHANCE) {
                    var masks = NoirmGear.animalMasks();
                    out.put(EquipmentSlot.HEAD, masks.get(r.nextInt(masks.size())).get());
                    out.put(EquipmentSlot.CHEST, NoirmGear.ANBU_ARMOR.get());
                    out.put(EquipmentSlot.LEGS, NoirmGear.SHINOBI_PANTS.get());
                    out.put(EquipmentSlot.FEET, NoirmGear.TABI.get());
                } else {
                    guard(out, palette, r);
                }
            }
            case "GUARD" -> guard(out, palette, r);
            default -> { }
        }
        return out;
    }

    private static void guard(Map<EquipmentSlot, Item> out, VillagePalette palette, RandomSource r) {
        out.put(EquipmentSlot.CHEST, r.nextFloat() < 0.15f ? NoirmGear.JONIN_VEST.get() : villageVest(palette));
        out.put(EquipmentSlot.HEAD, r.nextFloat() < 0.2f ? NoirmGear.HEAD_WRAP_PROTECTOR.get() : headband(r, 2, 1, 1));
        out.put(EquipmentSlot.LEGS, NoirmGear.SHINOBI_PANTS.get());
        out.put(EquipmentSlot.FEET, r.nextFloat() < 0.25f ? NoirmGear.SHIN_GUARDS.get() : NoirmGear.SHINOBI_SANDALS.get());
    }

    /** The vest a village look's forces wear (the Leaf look: the green flak vest). */
    public static Item villageVest(VillagePalette palette) {
        return switch (palette) {
            case SAND -> NoirmGear.VEST_SAND.get();
            case SNOW -> NoirmGear.VEST_SNOW.get();
            case MIST -> NoirmGear.VEST_MIST.get();
            case CLOUD -> NoirmGear.VEST_CLOUD.get();
            case STONE -> NoirmGear.VEST_STONE.get();
            default -> NoirmGear.FLAK_VEST.get();
        };
    }

    /** The Kage hat in a village look's colour. */
    public static Item kageHat(VillagePalette palette) {
        return switch (palette) {
            case SAND -> NoirmGear.KAGE_HAT_ORANGE.get();
            case SNOW -> NoirmGear.KAGE_HAT_BLUE.get();
            case MIST -> NoirmGear.KAGE_HAT_TEAL.get();
            case CLOUD -> NoirmGear.KAGE_HAT_GOLD.get();
            case STONE -> NoirmGear.KAGE_HAT_BROWN.get();
            default -> NoirmGear.KAGE_HAT_RED.get();
        };
    }

    /** One random outfit for a member of this clan (may be empty). */
    public static Map<EquipmentSlot, Item> roll(String clanId, RandomSource r) {
        Map<EquipmentSlot, Item> out = new EnumMap<>(EquipmentSlot.class);
        if (r.nextFloat() < BARE_CHANCE) {
            return out;
        }
        switch (clanId) {
            case "senju" -> {
                if (r.nextFloat() < 0.6f) {
                    out.put(EquipmentSlot.CHEST, NoirmGear.SENJU_CHESTPLATE.get());
                    if (r.nextFloat() < 0.6f) {
                        out.put(EquipmentSlot.LEGS, NoirmGear.SENJU_LEGGINGS.get());
                    }
                    out.put(EquipmentSlot.FEET, r.nextFloat() < 0.6f ? NoirmGear.SENJU_BOOTS.get() : NoirmGear.SHINOBI_SANDALS.get());
                } else if (r.nextFloat() < 0.4f) {
                    out.put(EquipmentSlot.CHEST, NoirmGear.FLAK_VEST.get());
                }
                if (r.nextFloat() < 0.4f) {
                    out.put(EquipmentSlot.HEAD, headband(r, 2, 1, 1));
                }
            }
            case "uchiha" -> {
                if (r.nextFloat() < 0.7f) {
                    out.put(EquipmentSlot.CHEST, NoirmGear.UCHIHA_TUNIC.get());
                }
                if (r.nextFloat() < 0.6f) {
                    out.put(EquipmentSlot.LEGS, NoirmGear.UCHIHA_PANTS.get());
                }
                if (r.nextFloat() < 0.35f) {
                    out.put(EquipmentSlot.HEAD, headband(r, 1, 1, 0));
                }
            }
            case "hyuga" -> {
                if (r.nextFloat() < 0.7f) {
                    out.put(EquipmentSlot.CHEST, NoirmGear.HYUGA_ROBE.get());
                }
                if (r.nextFloat() < 0.65f) {
                    out.put(EquipmentSlot.LEGS, NoirmGear.HYUGA_PANTS.get());
                }
                if (r.nextFloat() < 0.4f) {
                    out.put(EquipmentSlot.HEAD, NoirmGear.FOREHEAD_PROTECTOR_NAVY.get());
                }
            }
            case "uzumaki" -> {
                if (r.nextFloat() < 0.65f) {
                    out.put(EquipmentSlot.CHEST, NoirmGear.UZUMAKI_COAT.get());
                }
                if (r.nextFloat() < 0.5f) {
                    out.put(EquipmentSlot.LEGS, NoirmGear.SHINOBI_PANTS.get());
                }
                if (r.nextFloat() < 0.4f) {
                    out.put(EquipmentSlot.HEAD, headband(r, 1, 0, 1));
                }
            }
            default -> {
                if (r.nextFloat() < 0.05f) {
                    out.put(EquipmentSlot.HEAD, NoirmGear.ANBU_MASK.get());
                    out.put(EquipmentSlot.CHEST, NoirmGear.ANBU_ARMOR.get());
                } else {
                    float chest = r.nextFloat();
                    if (chest < 0.38f) {
                        out.put(EquipmentSlot.CHEST, NoirmGear.FLAK_VEST.get());
                    } else if (chest < 0.45f) {
                        out.put(EquipmentSlot.CHEST, NoirmGear.JONIN_VEST.get());
                    } else if (chest < 0.52f) {
                        out.put(EquipmentSlot.CHEST, NoirmGear.HAORI.get());
                    } else if (chest < 0.57f) {
                        out.put(EquipmentSlot.CHEST, NoirmGear.HAND_WRAPS.get());
                    } else if (chest < 0.60f) {
                        out.put(EquipmentSlot.CHEST, NoirmGear.ARM_GUARDS.get());
                    }
                    float head = r.nextFloat();
                    if (head < 0.45f) {
                        out.put(EquipmentSlot.HEAD, headband(r, 2, 1, 1));
                    } else if (head < 0.5f) {
                        out.put(EquipmentSlot.HEAD, NoirmGear.CLOTH_MASK.get());
                    }
                }
                float legs = r.nextFloat();
                if (legs < 0.45f) {
                    out.put(EquipmentSlot.LEGS, NoirmGear.SHINOBI_PANTS.get());
                } else if (legs < 0.53f) {
                    out.put(EquipmentSlot.LEGS, NoirmGear.HAKAMA.get());
                }
                if (r.nextFloat() < 0.12f) {
                    out.put(EquipmentSlot.FEET, NoirmGear.TABI.get());
                }
            }
        }
        if (!out.containsKey(EquipmentSlot.FEET) && r.nextFloat() < 0.5f) {
            out.put(EquipmentSlot.FEET, NoirmGear.SHINOBI_SANDALS.get());
        }
        return out;
    }

    /** A forehead protector, weighted navy : black : red. */
    private static Item headband(RandomSource r, int navy, int black, int red) {
        int roll = r.nextInt(navy + black + red);
        if (roll < navy) {
            return NoirmGear.FOREHEAD_PROTECTOR_NAVY.get();
        }
        return roll < navy + black ? NoirmGear.FOREHEAD_PROTECTOR_BLACK.get() : NoirmGear.FOREHEAD_PROTECTOR_RED.get();
    }
}

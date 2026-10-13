package com.noirm.gear;

import com.noirm.clan.Clan;
import com.noirm.entity.SparringNinja;
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
 * It happens once per ninja; ninjas already wearing something (a crew mate you dressed) keep it.
 * Gear rarely drops ({@link #DROP_CHANCE}), so farming ninjas is not the way to get it.
 */
public final class ClanOutfits {
    public static final float DROP_CHANCE = 0.05f;
    /** Chance a ninja wears no gear at all. */
    public static final float BARE_CHANCE = 0.12f;
    /** Game-test clans ({@code TestClans}) stay bare so armour never changes test numbers. */
    public static final String TEST_PREFIX = "test_";

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
        for (EquipmentSlot slot : ARMOR) {
            if (!ninja.getItemBySlot(slot).isEmpty()) {
                return; // already dressed
            }
        }
        roll(clan.id(), random).forEach((slot, item) -> {
            ninja.setItemSlot(slot, new ItemStack(item));
            ninja.setDropChance(slot, DROP_CHANCE);
        });
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
                    if (r.nextFloat() < 0.45f) {
                        out.put(EquipmentSlot.CHEST, NoirmGear.FLAK_VEST.get());
                    }
                    if (r.nextFloat() < 0.5f) {
                        out.put(EquipmentSlot.HEAD, headband(r, 2, 1, 1));
                    }
                }
                if (r.nextFloat() < 0.5f) {
                    out.put(EquipmentSlot.LEGS, NoirmGear.SHINOBI_PANTS.get());
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

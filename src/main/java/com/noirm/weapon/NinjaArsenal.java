package com.noirm.weapon;

import com.noirm.entity.SparringNinja;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What ninjas carry. Some hold a weapon (kunai, ninjatō, bō staff, rarely chakra blades) and
 * fight with its moveset; some keep throwables in the off hand (shuriken, kunai, senbon, rarely a
 * fūma shuriken) and throw them from range ({@code NinjaThrowGoal}). Village guards and patrols
 * always carry kunai and shuriken. Ninjas never run out of throws; what they drop is rare.
 */
public final class NinjaArsenal {
    public static final float DROP_CHANCE = 0.06f;
    /** Share of sword-carrying rogues whose sword is a legendary blade (about 1 rogue in 85). */
    public static final float LEGENDARY_CHANCE = 0.15f;
    /** Chance a legendary blade drops when its rogue swordsman falls. */
    public static final float LEGENDARY_DROP_CHANCE = 0.3f;

    private NinjaArsenal() {}

    /** A ninja's weapons when it first joins its clan (only if its hands are empty). */
    public static void arm(SparringNinja ninja, RandomSource r) {
        if (!ninja.getMainHandItem().isEmpty() || !ninja.getOffhandItem().isEmpty()) {
            return;
        }
        Item main = mainWeapon(!ninja.isResident(), r);
        if (main != null) {
            give(ninja, EquipmentSlot.MAINHAND, new ItemStack(main));
            if (main instanceof SpecialWeaponItem && SpecialWeapons.LEGENDARY.stream().anyMatch(l -> l.get() == main)) {
                ninja.setDropChance(EquipmentSlot.MAINHAND, LEGENDARY_DROP_CHANCE);
            }
        }
        if (r.nextFloat() < 0.4f) {
            float pick = r.nextFloat();
            Item off = pick < 0.55f ? NinjaWeapons.SHURIKEN.get()
                    : pick < 0.88f ? NinjaWeapons.KUNAI.get()
                    : pick < 0.97f ? NinjaWeapons.SENBON.get()
                    : NinjaWeapons.FUMA_SHURIKEN.get();
            give(ninja, EquipmentSlot.OFFHAND, new ItemStack(off, Math.min(off.getMaxStackSize(), 1 + r.nextInt(4))));
        }
    }

    /**
     * A ninja's main weapon, or null for fists. Some carry a kusarigama or a war fan; a rogue
     * (a wandering ninja, not a village resident) who would carry a sword sometimes carries one of
     * the legendary blades instead ({@link #LEGENDARY_CHANCE}).
     */
    public static Item mainWeapon(boolean rogue, RandomSource r) {
        float roll = r.nextFloat();
        if (roll < 0.22f) {
            return NinjaWeapons.KUNAI.get();
        }
        if (roll < 0.30f) {
            if (rogue && r.nextFloat() < LEGENDARY_CHANCE) {
                return SpecialWeapons.LEGENDARY.get(r.nextInt(SpecialWeapons.LEGENDARY.size())).get();
            }
            return NinjaWeapons.NINJATO.get();
        }
        if (roll < 0.36f) {
            return NinjaWeapons.BO_STAFF.get();
        }
        if (roll < 0.38f) {
            return NinjaWeapons.CHAKRA_BLADE.get();
        }
        if (roll < 0.395f) {
            return SpecialWeapons.KUSARIGAMA.get();
        }
        if (roll < 0.405f) {
            return SpecialWeapons.GALE_GUNBAI.get();
        }
        // The v0.13 blades (NinjaBlades): about 1 ninja in 10 carries one.
        if (roll < 0.435f) {
            return NinjaBlades.TANTO.get();
        }
        if (roll < 0.455f) {
            return NinjaBlades.KATANA.get();
        }
        if (roll < 0.475f) {
            return NinjaBlades.YARI.get();
        }
        if (roll < 0.49f) {
            return NinjaBlades.TONFA.get();
        }
        if (roll < 0.50f) {
            return NinjaBlades.TEKKO_KAGI.get();
        }
        if (roll < 0.508f) {
            return NinjaBlades.TESSEN.get();
        }
        if (roll < 0.52f && rogue) {
            return NinjaBlades.NODACHI.get(); // big field swords are a wanderer's weapon
        }
        return null;
    }

    /** Share of ANBU-style patrols that carry a tantō (the rest keep their kunai or ninjatō). */
    public static final float ANBU_TANTO_CHANCE = 0.65f;

    /** An ANBU-style patrol's blade: usually the short tantō, swapped in for a kunai or ninjatō. */
    public static void armAnbu(SparringNinja ninja, RandomSource r) {
        if (r.nextFloat() < ANBU_TANTO_CHANCE) {
            give(ninja, EquipmentSlot.MAINHAND, new ItemStack(NinjaBlades.TANTO.get()));
        }
    }

    /** Does a wandering ninja holding this look like a rogue swordsman (a legendary blade or a nodachi)? */
    public static boolean isRogueBlade(Item item) {
        return item instanceof SpecialWeaponItem && SpecialWeapons.LEGENDARY.stream().anyMatch(l -> l.get() == item)
                || item == NinjaBlades.NODACHI.get();
    }

    /** Guards and patrols: a kunai in hand and shuriken ready. */
    public static void armGuard(SparringNinja ninja, RandomSource r) {
        if (ninja.getMainHandItem().isEmpty()) {
            give(ninja, EquipmentSlot.MAINHAND, new ItemStack(r.nextFloat() < 0.25f ? NinjaWeapons.NINJATO.get() : NinjaWeapons.KUNAI.get()));
        }
        if (ninja.getOffhandItem().isEmpty()) {
            give(ninja, EquipmentSlot.OFFHAND, new ItemStack(NinjaWeapons.SHURIKEN.get(), 1 + r.nextInt(4)));
        }
    }

    private static void give(SparringNinja ninja, EquipmentSlot slot, ItemStack stack) {
        ninja.setItemSlot(slot, stack);
        ninja.setDropChance(slot, DROP_CHANCE);
    }

    /** The throwable a ninja would throw: its off hand's, else a throwable main-hand weapon (kunai). */
    public static ItemStack throwable(SparringNinja ninja) {
        if (WeaponMoves.throwsOnUse(ninja.getOffhandItem())) {
            return ninja.getOffhandItem();
        }
        if (WeaponMoves.throwsOnUse(ninja.getMainHandItem())) {
            return ninja.getMainHandItem();
        }
        return ItemStack.EMPTY;
    }
}

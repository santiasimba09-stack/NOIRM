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

    private NinjaArsenal() {}

    /** A ninja's weapons when it first joins its clan (only if its hands are empty). */
    public static void arm(SparringNinja ninja, RandomSource r) {
        if (!ninja.getMainHandItem().isEmpty() || !ninja.getOffhandItem().isEmpty()) {
            return;
        }
        float roll = r.nextFloat();
        Item main = roll < 0.22f ? NinjaWeapons.KUNAI.get()
                : roll < 0.30f ? NinjaWeapons.NINJATO.get()
                : roll < 0.36f ? NinjaWeapons.BO_STAFF.get()
                : roll < 0.38f ? NinjaWeapons.CHAKRA_BLADE.get()
                : null;
        if (main != null) {
            give(ninja, EquipmentSlot.MAINHAND, new ItemStack(main));
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

package com.noirm.weapon;

import com.noirm.combat.CombatData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/**
 * Which weapon moveset an item gives (common code: the server picks moves with it, the client
 * picks the stance with it).
 */
public final class WeaponMoves {
    private WeaponMoves() {}

    /** An item that brings its own moveset. */
    public interface Armed {
        Moveset moveset();
    }

    /** The moveset {@code stack} fights with, or null for none (fists, blocks, food…). */
    public static Moveset movesetFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof Armed armed) {
            return armed.moveset();
        }
        if (stack.getItem() instanceof SwordItem) {
            return Moveset.SWORD;
        }
        return null;
    }

    /**
     * The combat style the weapon in hand overrides the fighter's own with, or null. Only once
     * the style's JSON has loaded, so a missing file falls back to fists instead of no moves.
     */
    public static ResourceLocation styleFor(ItemStack stack) {
        Moveset moveset = movesetFor(stack);
        return moveset == null || CombatData.style(moveset.style()) == null ? null : moveset.style();
    }

    /** The weapon's stance animation ({@code noirm:stance_kunai}…), or null without one. */
    public static ResourceLocation stanceFor(ItemStack stack) {
        Moveset moveset = movesetFor(stack);
        return moveset == null ? null : moveset.stance();
    }

    /** True if right click in combat mode should be a heavy attack with this in hand. */
    public static boolean isMeleeWeapon(ItemStack stack) {
        return movesetFor(stack) != null;
    }

    /** True if using this item throws it (kunai, shuriken, fūma, senbon, explosive kunai). */
    public static boolean throwsOnUse(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ThrowingWeaponItem;
    }
}

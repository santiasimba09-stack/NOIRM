package com.noirm.client.anim;

import com.noirm.weapon.Moveset;
import com.noirm.weapon.WeaponMoves;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Which stance a held weapon puts a fighter in: the weapon's own moveset decides (kunai, staff, sword). */
public final class WeaponStances {
    private WeaponStances() {}

    /** The stance animation for this main-hand item, or null to use the style's own. */
    public static ResourceLocation stanceFor(ItemStack stack) {
        return WeaponMoves.stanceFor(stack);
    }

    /** "sword", "kunai", "staff", or null for fists and anything else. */
    public static String kind(ItemStack stack) {
        Moveset moveset = WeaponMoves.movesetFor(stack);
        return moveset == null ? null : moveset.name().toLowerCase(java.util.Locale.ROOT);
    }
}

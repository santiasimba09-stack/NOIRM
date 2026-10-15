package com.noirm.client.anim;

import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.registries.ForgeRegistries;

/** Which stance a held weapon puts a fighter in: a sword, a kunai or a staff each stand their own way. */
public final class WeaponStances {
    private WeaponStances() {}

    /** The stance animation for this main-hand item, or null to use the style's own. */
    public static ResourceLocation stanceFor(ItemStack stack) {
        String kind = kind(stack);
        return kind == null ? null : Noirm.id("stance_" + kind);
    }

    /** "sword", "kunai", "staff", or null for fists and anything else. */
    public static String kind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String path = id == null ? "" : id.getPath();
        if (path.contains("kunai")) {
            return "kunai";
        }
        if (path.contains("staff") || path.endsWith("_bo") || path.equals("bo")) {
            return "staff";
        }
        if (stack.getItem() instanceof SwordItem || path.contains("ninjato") || path.contains("katana") || path.contains("sword")) {
            return "sword";
        }
        return null;
    }
}

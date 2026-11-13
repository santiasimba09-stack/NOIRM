package com.noirm.pills;

import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What Tsubaki the medic teaches about pills (lessons in {@code data/noirm/masters/medic_tsubaki.json}):
 * <ul>
 *   <li><b>{@value #PILL_LORE}</b> (genin): +{@value #LORE_LEVELS} pharmacology, the recipes marked
 *       {@code "unlock": "perk:pill_lore"} (focus pill), an apothecary if you have none, and a few seeds.</li>
 *   <li><b>{@value #HIGH_GRADE_PILLS}</b> (jōnin): +{@value #HIGH_LEVELS} pharmacology and the high-grade
 *       recipes ({@code "perk:high_grade_pills"}: the iron-bone and three-seal pills).</li>
 *   <li><b>{@value #PILL_SUPPLY}</b> (a service, paid each time): {@value #SUPPLY_EACH} soldier and
 *       {@value #SUPPLY_EACH} blood pills.</li>
 * </ul>
 */
public final class PillLore {
    public static final String PILL_LORE = "pill_lore";
    public static final String HIGH_GRADE_PILLS = "high_grade_pills";
    public static final String PILL_SUPPLY = "pill_supply";
    public static final int LORE_LEVELS = 5;
    public static final int HIGH_LEVELS = 5;
    public static final int LORE_SEEDS = 4;
    public static final int SUPPLY_EACH = 2;

    private PillLore() {}

    /** A pill lesson was just learned (from {@code MasterPerks.granted}). */
    public static void granted(Player player, ShinobiData data, String perk) {
        if (PILL_LORE.equals(perk)) {
            Pharmacology.raise(player, data, LORE_LEVELS);
            if (!player.getInventory().contains(new ItemStack(PillItems.APOTHECARY_ITEM.get()))) {
                give(player, new ItemStack(PillItems.APOTHECARY_ITEM.get()));
            }
            give(player, new ItemStack(PillItems.SEEDS.get("moonroot").get(), LORE_SEEDS));
            give(player, new ItemStack(PillItems.SEEDS.get("bloodleaf").get(), LORE_SEEDS));
            give(player, new ItemStack(PillItems.SEEDS.get("mistcap").get(), LORE_SEEDS));
        } else if (HIGH_GRADE_PILLS.equals(perk)) {
            Pharmacology.raise(player, data, HIGH_LEVELS);
            give(player, new ItemStack(PillItems.SEEDS.get("ashthorn").get(), LORE_SEEDS));
        }
        data.pills().markDirty();
    }

    /** The medic's pill supply (a service). */
    public static boolean supply(Player player) {
        give(player, new ItemStack(PillItems.SOLDIER_PILL.get(), SUPPLY_EACH));
        give(player, new ItemStack(PillItems.BLOOD_PILL.get(), SUPPLY_EACH));
        player.sendSystemMessage(Component.translatable("message.noirm.pill.supply", SUPPLY_EACH).withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}

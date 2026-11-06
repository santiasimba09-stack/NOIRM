package com.noirm.economy;

import com.noirm.mission.CareerData;
import com.noirm.mission.CareerItems;
import com.noirm.player.ShinobiCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Ryo, the currency. Every player has a <b>wallet</b> (a balance saved with them, shown on the
 * HUD, in the inventory and in {@code /noirm me}); shops, mission rewards and bounties go through
 * it. To hand money to someone, or drop it in a chest, take it out as a {@link RyoPouchItem ryo
 * pouch} ({@code /noirm ryo withdraw}); a pouch picked up off the ground goes straight into the
 * wallet, and right-clicking one in your hand empties it in.
 *
 * <p>The small API other systems use (bounties, masters…): {@link #balance}, {@link #give},
 * {@link #take}. All of it is server-side; the client only gets a copy for the HUD.
 */
public final class Ryo {
    private Ryo() {}

    /** How much ryo {@code player} has (0 without data). */
    public static int balance(Player player) {
        return ShinobiCapability.get(player).map(d -> d.career().ryo()).orElse(0);
    }

    /** Adds {@code amount} ryo to the wallet (negative amounts do nothing). Returns the new balance. */
    public static int give(Player player, int amount) {
        return ShinobiCapability.get(player).map(d -> {
            CareerData career = d.career();
            if (amount > 0) {
                career.setRyo((int) Math.min(CareerData.MAX_RYO, (long) career.ryo() + amount));
            }
            return career.ryo();
        }).orElse(0);
    }

    /** Takes {@code amount} ryo if the player has that much. Returns whether it was taken (nothing is taken otherwise). */
    public static boolean take(Player player, int amount) {
        if (amount < 0) {
            return false;
        }
        return ShinobiCapability.get(player).map(d -> {
            CareerData career = d.career();
            if (career.ryo() < amount) {
                return false;
            }
            career.setRyo(career.ryo() - amount);
            return true;
        }).orElse(false);
    }

    /** Sets the balance (commands). */
    public static void set(Player player, int amount) {
        ShinobiCapability.get(player).ifPresent(d -> d.career().setRyo(amount));
    }

    /**
     * Takes {@code amount} out of the wallet as a ryo pouch in the inventory (dropped at the feet
     * if it's full). Returns false, and takes nothing, if the player has less.
     */
    public static boolean withdraw(Player player, int amount) {
        if (amount <= 0 || !take(player, amount)) {
            return false;
        }
        ItemStack pouch = RyoPouchItem.of(amount);
        if (!player.getInventory().add(pouch)) {
            player.drop(pouch, false);
        }
        return true;
    }

    /** "1,250" (the HUD and messages). */
    public static String format(int amount) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(amount);
    }

    /** "1,250 ryo" as a translatable text. */
    public static Component text(int amount) {
        return Component.translatable("ryo.noirm.amount", format(amount));
    }

    /** The pouch item (for checks). */
    public static boolean isPouch(ItemStack stack) {
        return stack.is(CareerItems.RYO_POUCH.get());
    }
}

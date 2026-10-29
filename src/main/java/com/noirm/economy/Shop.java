package com.noirm.economy;

import com.noirm.entity.VillageMerchant;
import com.noirm.gear.NoirmGear;
import com.noirm.mission.CareerItems;
import com.noirm.mission.PlayerRank;
import com.noirm.weapon.NinjaBlades;
import com.noirm.weapon.NinjaWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Village shops in ryo. A shopkeeper's stock still comes from {@link VillageMerchant}'s list
 * (written in emeralds, so it stays one list): every price is turned into ryo here at
 * {@link #RYO_PER_EMERALD} a piece, and what they buy from you at {@link #RYO_PER_EMERALD_SOLD}.
 * Our merchants don't take emeralds any more; they buy them (vanilla villagers still trade in them).
 *
 * <p>On top of that: better gear and blades for higher ranks ({@link #EXTRAS}), rank gates on
 * village gear ({@link #requiredRank}), and the weaponsmith and general store buy the loot you
 * carry (gear and weapons, gems).
 */
public final class Shop {
    /** What one emerald of the old price list costs in ryo. */
    public static final int RYO_PER_EMERALD = 40;
    /** What a merchant pays per emerald of their old "buy" list. */
    public static final int RYO_PER_EMERALD_SOLD = 15;
    /** Loot the weaponsmith and general store buy: gear, other weapons; a shop's own goods go for this share of their price. */
    public static final int LOOT_GEAR = 20, LOOT_OTHER = 15, RESELL_DIVISOR = 4;

    /** One line in a shop: buy {@code stack} for {@code price} ryo, or sell it for that. */
    public record Row(ItemStack stack, int price, boolean sell, PlayerRank need) {}

    /** The better stock a weaponsmith keeps for ranked ninjas (ryo price, rank). */
    private static final Map<Supplier<? extends Item>, Integer> EXTRAS = new LinkedHashMap<>();

    static {
        EXTRAS.put(NinjaWeapons.EXPLOSIVE_KUNAI, 120);
        EXTRAS.put(NinjaWeapons.FUMA_SHURIKEN, 200);
        EXTRAS.put(NinjaBlades.NODACHI, 480);
        EXTRAS.put(NoirmGear.JONIN_VEST, 600);
        EXTRAS.put(NinjaWeapons.CHAKRA_BLADE, 1500);
        EXTRAS.put(NoirmGear.ANBU_MASK, 400);
        EXTRAS.put(NoirmGear.ANBU_ARMOR, 900);
        EXTRAS.put(NoirmGear.KAGE_HAT_RED, 1500);
        EXTRAS.put(NoirmGear.KAGE_ROBE, 3000);
    }

    private Shop() {}

    /** The rank you need to buy {@code item}: village gear and the better blades are for ranked ninjas. */
    public static PlayerRank requiredRank(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        String path = id == null ? "" : id.getPath();
        if (path.startsWith("kage_")) {
            return PlayerRank.KAGE_CANDIDATE;
        }
        if (path.startsWith("anbu_")) {
            return PlayerRank.ANBU;
        }
        if (path.equals("jonin_vest") || path.equals("samurai_cuirass") || path.equals("chakra_blade")) {
            return PlayerRank.JONIN;
        }
        if (path.equals("flak_vest") || path.startsWith("vest_") || path.equals("katana") || path.equals("nodachi") || path.equals("tessen")) {
            return PlayerRank.CHUNIN;
        }
        if (path.startsWith("forehead_protector") || path.equals("sealing_scroll") || path.equals("explosive_kunai")
                || path.equals("fuma_shuriken")) {
            return PlayerRank.GENIN;
        }
        return PlayerRank.ACADEMY;
    }

    /** Everything {@code merchant} sells to and buys from {@code player}, in ryo. */
    public static List<Row> rows(VillageMerchant merchant, Player player) {
        List<Row> rows = new ArrayList<>();
        if (merchant.role() != VillageMerchant.Role.MERCHANT) {
            return rows;
        }
        List<Item> stocked = new ArrayList<>();
        for (MerchantOffer offer : merchant.getOffers()) {
            ItemStack cost = offer.getBaseCostA();
            ItemStack result = offer.getResult();
            if (cost.is(Items.EMERALD) && !result.isEmpty()) {
                rows.add(new Row(result.copy(), cost.getCount() * RYO_PER_EMERALD, false, requiredRank(result.getItem())));
                stocked.add(result.getItem());
            }
        }
        if (merchant.shop() == VillageMerchant.SHOP_WEAPONS) {
            EXTRAS.forEach((item, price) -> {
                if (!stocked.contains(item.get())) {
                    rows.add(new Row(new ItemStack(item.get()), price, false, requiredRank(item.get())));
                }
            });
        }
        if (merchant.shop() == VillageMerchant.SHOP_GENERAL || merchant.shop() == VillageMerchant.SHOP_WEAPONS) {
            // The bingo book: cheap, for anyone (dangerous rogues and their bounties).
            rows.add(new Row(new ItemStack(com.noirm.bounty.BountyItems.BINGO_BOOK.get()), com.noirm.bounty.BingoBook.BOOK_PRICE, false,
                    PlayerRank.ACADEMY));
        }
        // What they buy: their old list, emeralds, and (weapons and general goods) the loot you carry.
        for (MerchantOffer offer : merchant.getOffers()) {
            ItemStack cost = offer.getBaseCostA();
            if (offer.getResult().is(Items.EMERALD) && !cost.isEmpty()) {
                rows.add(new Row(cost.copy(), offer.getResult().getCount() * RYO_PER_EMERALD_SOLD, true, PlayerRank.ACADEMY));
            }
        }
        rows.add(new Row(new ItemStack(Items.EMERALD), 20, true, PlayerRank.ACADEMY));
        boolean loot = merchant.shop() == VillageMerchant.SHOP_WEAPONS || merchant.shop() == VillageMerchant.SHOP_GENERAL;
        if (loot) {
            rows.add(new Row(new ItemStack(Items.DIAMOND), 80, true, PlayerRank.ACADEMY));
            rows.add(new Row(new ItemStack(Items.GOLD_INGOT), 15, true, PlayerRank.ACADEMY));
            List<Item> listed = new ArrayList<>();
            rows.stream().filter(Row::sell).forEach(r -> listed.add(r.stack().getItem()));
            for (ItemStack carried : player.getInventory().items) {
                Item item = carried.getItem();
                if (carried.isEmpty() || listed.contains(item) || !isLoot(item)) {
                    continue;
                }
                listed.add(item);
                int price = item instanceof ArmorItem ? LOOT_GEAR : LOOT_OTHER;
                for (Row row : rows) {
                    if (!row.sell() && row.stack().is(item)) {
                        price = Math.max(5, row.price() / Math.max(1, row.stack().getCount()) / RESELL_DIVISOR);
                    }
                }
                rows.add(new Row(new ItemStack(item), price, true, PlayerRank.ACADEMY));
            }
        }
        return rows;
    }

    /** Gear and weapons from this mod (not food, eggs, money or mission items). */
    static boolean isLoot(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !id.getNamespace().equals(com.noirm.Noirm.MOD_ID) || item instanceof SpawnEggItem || item.isEdible()) {
            return false;
        }
        return item != CareerItems.RYO_POUCH.get() && item != CareerItems.MISSION_PARCEL.get() && item != CareerItems.MISSION_SCROLL.get()
                && item != CareerItems.MISSION_BOARD_ITEM.get() && item != com.noirm.bounty.BountyItems.BINGO_BOOK.get();
    }

    /** How many of {@code item} the player carries (main inventory). */
    public static int count(Player player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Buys or sells row {@code index}. Returns whether it went through (if not, the player is told why). */
    public static boolean trade(Player player, VillageMerchant merchant, int index) {
        List<Row> rows = rows(merchant, player);
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        Row row = rows.get(index);
        if (row.sell()) {
            int need = row.stack().getCount();
            if (count(player, row.stack().getItem()) < need) {
                return refuse(player, Component.translatable("shop.noirm.why.none", row.stack().getHoverName()));
            }
            int left = need;
            var items = player.getInventory().items;
            for (int i = 0; i < items.size() && left > 0; i++) {
                ItemStack stack = items.get(i);
                if (stack.is(row.stack().getItem())) {
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
            Ryo.give(player, row.price());
            player.displayClientMessage(Component.translatable("shop.noirm.sold", row.stack().getHoverName(), need, Ryo.format(row.price()))
                    .withStyle(ChatFormatting.GOLD), true);
        } else {
            if (!PlayerRank.atLeast(player, row.need())) {
                return refuse(player, Component.translatable("shop.noirm.why.rank", row.need().title()));
            }
            if (!Ryo.take(player, row.price())) {
                return refuse(player, Component.translatable("shop.noirm.why.ryo", Ryo.format(row.price()), Ryo.format(Ryo.balance(player))));
            }
            ItemStack bought = row.stack().copy();
            if (!player.getInventory().add(bought)) {
                player.drop(bought, false);
            }
            player.displayClientMessage(Component.translatable("shop.noirm.bought", row.stack().getHoverName(), row.stack().getCount(),
                    Ryo.format(row.price())).withStyle(ChatFormatting.GOLD), true);
        }
        merchant.playSound(SoundEvents.WANDERING_TRADER_YES, 0.8f, 1f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.5f, 1.5f);
        return true;
    }

    private static boolean refuse(Player player, Component why) {
        player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
        return false;
    }
}

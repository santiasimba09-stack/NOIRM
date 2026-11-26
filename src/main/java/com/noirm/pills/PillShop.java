package com.noirm.pills;

import com.noirm.economy.Shop;
import com.noirm.entity.VillageMerchant;
import com.noirm.mission.MissionTemplate;
import com.noirm.mission.PlayerRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Where foods and pills come from besides your own mortar: village shops (in ryo, some gated by rank)
 * and mission pay (B-rank and up, now and then). Prices are here, in ryo per row.
 */
public final class PillShop {
    /** One line of stock: {@code count} of an item for {@code price} ryo, from {@code rank}. */
    public record Stock(int shop, Supplier<? extends Item> item, int count, int price, PlayerRank rank) {}

    public static final List<Stock> STOCK = new ArrayList<>();

    static {
        // General store: the field medic's basics, the mortar itself, rice.
        add(VillageMerchant.SHOP_GENERAL, PillItems.STAMINA_PILL, 2, 60, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_GENERAL, PillItems.SOLDIER_PILL, 1, 90, PlayerRank.GENIN);
        add(VillageMerchant.SHOP_GENERAL, PillItems.BLOOD_PILL, 1, 80, PlayerRank.GENIN);
        add(VillageMerchant.SHOP_GENERAL, PillItems.ANTIDOTE_PILL, 2, 70, PlayerRank.GENIN);
        add(VillageMerchant.SHOP_GENERAL, PillItems.CHAKRA_PILL, 1, 120, PlayerRank.CHUNIN);
        add(VillageMerchant.SHOP_GENERAL, PillItems.APOTHECARY_ITEM, 1, 150, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_GENERAL, PillItems.RICE, 8, 20, PlayerRank.ACADEMY);
        // Ramen stand: the bowls and rice balls.
        add(VillageMerchant.SHOP_RAMEN, PillItems.FISH_RAMEN, 2, 50, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_RAMEN, PillItems.BEEF_RAMEN, 2, 70, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_RAMEN, PillItems.ONIGIRI, 4, 30, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_RAMEN, PillItems.CURRY_RICE, 2, 60, PlayerRank.ACADEMY);
        // Teahouse: sweets and light food.
        add(VillageMerchant.SHOP_TEAHOUSE, PillItems.DANGO, 4, 30, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_TEAHOUSE, PillItems.PLUM_ONIGIRI, 3, 40, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_TEAHOUSE, PillItems.NUTRIENT_BAR, 4, 40, PlayerRank.ACADEMY);
        // Weaponsmith: field rations for the road.
        add(VillageMerchant.SHOP_WEAPONS, PillItems.FIELD_RATION, 4, 40, PlayerRank.ACADEMY);
        // Florist: herb seeds (ashthorn is rare and dear).
        add(VillageMerchant.SHOP_FLOWERS, () -> PillItems.SEEDS.get("moonroot").get(), 4, 40, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_FLOWERS, () -> PillItems.SEEDS.get("bloodleaf").get(), 4, 40, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_FLOWERS, () -> PillItems.SEEDS.get("ironmillet").get(), 4, 40, PlayerRank.ACADEMY);
        add(VillageMerchant.SHOP_FLOWERS, () -> PillItems.SEEDS.get("mistcap").get(), 4, 60, PlayerRank.GENIN);
        add(VillageMerchant.SHOP_FLOWERS, () -> PillItems.SEEDS.get("ashthorn").get(), 2, 80, PlayerRank.CHUNIN);
        // Scroll seller: a medical scroll (one recipe you don't know).
        add(VillageMerchant.SHOP_SCROLLS, PillItems.MEDICAL_SCROLL, 1, 400, PlayerRank.CHUNIN);
    }

    // ---------------------------------------------------------------- mission pay (chances 0..1)
    public static final float B_PILL = 0.3f;
    public static final float A_REFINED = 0.5f, A_SCROLL = 0.1f;
    public static final int S_REFINED = 2;
    public static final float S_SCROLL = 0.25f, S_HIGH_GRADE = 0.1f;

    private PillShop() {}

    private static void add(int shop, Supplier<? extends Item> item, int count, int price, PlayerRank rank) {
        STOCK.add(new Stock(shop, item, count, price, rank));
    }

    /** Adds this shop's foods and pills to its rows ({@code Shop.rows}), unless it already stocks the item. */
    public static void addStock(int shop, List<Shop.Row> rows, List<Item> stocked) {
        for (Stock s : STOCK) {
            if (s.shop() == shop && !stocked.contains(s.item().get())) {
                rows.add(new Shop.Row(new ItemStack(s.item().get(), s.count()), s.price(), false, s.rank()));
            }
        }
    }

    /** A mission was reported: B-rank and up pay a pill now and then (A and S better ones, and scrolls). */
    public static void missionReward(Player player, MissionTemplate.Letter rank) {
        List<ItemStack> extra = rewards(rank, player.getRandom());
        for (ItemStack stack : extra) {
            player.sendSystemMessage(Component.translatable("message.noirm.pill.mission_reward", stack.getCount(), stack.getHoverName())
                    .withStyle(ChatFormatting.AQUA));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** What a mission of {@code rank} adds (public for tests). */
    public static List<ItemStack> rewards(MissionTemplate.Letter rank, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        Supplier<Item> basic = () -> (random.nextBoolean() ? PillItems.SOLDIER_PILL : PillItems.BLOOD_PILL).get();
        switch (rank) {
            case B -> {
                if (random.nextFloat() < B_PILL) {
                    out.add(new ItemStack(basic.get()));
                }
            }
            case A -> {
                if (random.nextFloat() < A_REFINED) {
                    out.add(Grade.REFINED.apply(new ItemStack(basic.get())));
                }
                if (random.nextFloat() < A_SCROLL) {
                    out.add(new ItemStack(PillItems.MEDICAL_SCROLL.get()));
                }
            }
            case S -> {
                out.add(Grade.REFINED.apply(new ItemStack(basic.get(), S_REFINED)));
                if (random.nextFloat() < S_SCROLL) {
                    out.add(new ItemStack(PillItems.MEDICAL_SCROLL.get()));
                }
                if (random.nextFloat() < S_HIGH_GRADE) {
                    out.add(new ItemStack((random.nextBoolean() ? PillItems.IRON_BONE_PILL : PillItems.THREE_SEAL_PILL).get()));
                }
            }
            default -> { }
        }
        return out;
    }
}

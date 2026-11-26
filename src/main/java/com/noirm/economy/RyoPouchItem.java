package com.noirm.economy;

import com.noirm.mission.CareerItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Ryo you can hold: a pouch with an amount inside (the {@code Ryo} tag). It drops, trades and
 * sits in chests like any item; right-click it to put the money in your wallet. Picked up off the
 * ground, it goes into the wallet by itself ({@code RyoEvents}).
 */
public class RyoPouchItem extends Item {
    public static final String AMOUNT_TAG = "Ryo";

    public RyoPouchItem(Properties properties) {
        super(properties);
    }

    /** A pouch holding {@code amount} ryo. */
    public static ItemStack of(int amount) {
        ItemStack stack = new ItemStack(CareerItems.RYO_POUCH.get());
        stack.getOrCreateTag().putInt(AMOUNT_TAG, Math.max(0, amount));
        return stack;
    }

    /** The ryo in one pouch (a stack counts each pouch). */
    public static int amount(ItemStack stack) {
        return stack.hasTag() ? Math.max(0, stack.getTag().getInt(AMOUNT_TAG)) : 0;
    }

    /** Empties a whole stack of pouches into {@code player}'s wallet. Returns the ryo added. */
    public static int deposit(Player player, ItemStack stack) {
        int total = (int) Math.min(Integer.MAX_VALUE, (long) amount(stack) * stack.getCount());
        Ryo.give(player, total);
        return total;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            int added = deposit(player, stack);
            player.displayClientMessage(Component.translatable("message.noirm.ryo.deposited", Ryo.format(added),
                    Ryo.format(Ryo.balance(player))).withStyle(ChatFormatting.GOLD), true);
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
        return InteractionResultHolder.sidedSuccess(ItemStack.EMPTY, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.noirm.ryo_pouch.amount", Ryo.format(amount(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noirm.ryo_pouch.tip").withStyle(ChatFormatting.GRAY));
    }
}

package com.noirm.guide;

import com.noirm.player.ShinobiCapability;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Shinobi Handbook: right-click to read it (the client opens {@code client.HandbookScreen}; the
 * server notes that you opened it, which ticks off the first tip). Given once after character
 * creation ({@link FirstSteps}); craftable (a book and an ink sac) and free at any missions board
 * (crouch and right-click it with an empty hand).
 */
public class HandbookItem extends Item {
    public HandbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.noirm.client.HandbookScreen::open);
        } else {
            opened(player);
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** The server's side of opening it: the first step is done. */
    public static void opened(Player player) {
        ShinobiCapability.get(player).ifPresent(data -> data.guide().setOpenedHandbook(true));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noirm.shinobi_handbook.tip").withStyle(ChatFormatting.GRAY));
    }
}

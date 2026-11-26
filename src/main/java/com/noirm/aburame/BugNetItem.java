package com.noirm.aburame;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** The Bug Net: use it on a wild insect to catch it into your Insect Case (anyone can collect). */
public class BugNetItem extends Item {
    public BugNetItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof WildInsect bug)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide()) {
            if (WildInsect.catchInto(player, bug)) {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("item.noirm.bug_net.desc").withStyle(ChatFormatting.GRAY));
    }
}

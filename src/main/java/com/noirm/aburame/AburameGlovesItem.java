package com.noirm.aburame;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Black Aburame gloves (docs/ABURAME.md, 2.3): they keep a rinkaichū host's poison in. Use them to put
 * them on or take them off; worn gloves anywhere in your inventory count. (Not clothing: the look of
 * the Aburame's outfit is the gear work's; this is the containment.)
 */
public class AburameGlovesItem extends Item {
    public static final String WORN = "Worn";

    public AburameGlovesItem(Properties properties) {
        super(properties);
    }

    public static boolean worn(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(WORN);
    }

    public static boolean wearing(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof AburameGlovesItem && worn(s)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            boolean now = !worn(stack);
            stack.getOrCreateTag().putBoolean(WORN, now);
            player.displayClientMessage(Component.translatable(now ? "message.noirm.colony.gloves_on" : "message.noirm.colony.gloves_off"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return worn(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable(worn(stack) ? "item.noirm.aburame_gloves.worn" : "item.noirm.aburame_gloves.off").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("item.noirm.aburame_gloves.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}

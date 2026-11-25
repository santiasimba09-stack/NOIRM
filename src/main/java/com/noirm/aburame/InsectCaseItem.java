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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

/**
 * The Insect Case (docs/ABURAME.md, 2.6): the collection book of the twelve wild insects. Carry it to
 * catch bugs (with the Bug Net or Insect Gathering); use it to see what you have and where the rest
 * live. A full case makes you an Entomologist and opens the bikōchū quest.
 */
public class InsectCaseItem extends Item {
    public InsectCaseItem(Properties properties) {
        super(properties);
    }

    public static boolean carried(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof InsectCaseItem) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.aburame.ClientColony.openCase());
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("item.noirm.insect_case.desc").withStyle(ChatFormatting.GRAY));
    }
}

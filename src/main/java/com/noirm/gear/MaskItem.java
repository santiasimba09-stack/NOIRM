package com.noirm.gear;

import com.noirm.Noirm;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A face mask (the animal masks, the hunter mask). Worn over the face it hides your name tag and
 * makes you a little harder to notice ({@link GearPerks#MASK_VISIBILITY}). <b>Sneak and use it</b>
 * (in your hand) to wear it <b>pushed aside</b>, on the side of the head: the face shows, and so
 * does your name. The look is stored on the item, so it stays when you put the mask on.
 */
public class MaskItem extends GearItem {
    public static final String ASIDE = "NoirmAside";

    public MaskItem(ArmorMaterial material, String texture, Properties properties) {
        super(material, Type.HELMET, texture, GearPerk.HIDE_NAME, 0f, properties);
    }

    public static boolean isAside(ItemStack stack) {
        return stack.getItem() instanceof MaskItem && stack.hasTag() && stack.getTag().getBoolean(ASIDE);
    }

    public static void setAside(ItemStack stack, boolean aside) {
        if (aside) {
            stack.getOrCreateTag().putBoolean(ASIDE, true);
        } else if (stack.hasTag()) {
            stack.getTag().remove(ASIDE);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            setAside(stack, !isAside(stack));
            player.displayClientMessage(Component.translatable(isAside(stack) ? "message.noirm.mask.aside" : "message.noirm.mask.face"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return super.use(level, player, hand);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        if (isAside(stack)) {
            return Noirm.MOD_ID + ":textures/models/armor/" + texture() + "_aside_layer_1.png";
        }
        return super.getArmorTexture(stack, entity, slot, type);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isAside(stack)) {
            tooltip.add(Component.translatable("tooltip.noirm.mask.aside").withStyle(ChatFormatting.GRAY));
        } else {
            super.appendHoverText(stack, level, tooltip, flag);
            tooltip.add(Component.translatable("tooltip.noirm.mask.stealth").withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.noirm.mask.toggle").withStyle(ChatFormatting.DARK_GRAY));
    }
}

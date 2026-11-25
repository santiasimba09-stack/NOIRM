package com.noirm.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A cape. Worn on its own (chest slot) it's the cape with its clasp and straps; it can also be
 * <b>sewn onto any chest piece</b> at a crafting table ({@link CapeSewingRecipe}), so you keep your
 * shirt, vest or armour and wear the cape over it. Capes are swift like cloaks (faster sprinting).
 * The cloth hangs and flaps in 3D ({@link Drapes}, drawn by the client).
 */
public class CapeItem extends GearItem {
    public CapeItem(ArmorMaterial material, String texture, Properties properties) {
        super(material, Type.CHESTPLATE, texture, GearPerk.SWIFT, 0f, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.noirm.cape.sew").withStyle(ChatFormatting.GRAY));
    }

    /** The plain cape: takes any dye (crafted with dyes, like leather armour), red when undyed. */
    public static class Dyeable extends CapeItem implements DyeableLeatherItem {
        public static final int DEFAULT_COLOR = 0x8E1C20;

        public Dyeable(ArmorMaterial material, String texture, Properties properties) {
            super(material, texture, properties);
        }

        @Override
        public int getColor(ItemStack stack) {
            var display = stack.getTagElement("display");
            return display != null && display.contains("color", 99) ? display.getInt("color") : DEFAULT_COLOR;
        }

        /** Vanilla draws dyed armour twice (tinted, then an "overlay" untinted): the clasp keeps its colours. */
        @Override
        public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
            return super.getArmorTexture(stack, entity, slot, null);
        }
    }
}

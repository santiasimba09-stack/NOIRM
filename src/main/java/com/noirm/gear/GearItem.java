package com.noirm.gear;

import com.noirm.Noirm;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/**
 * A piece of wearable ninja gear. Its stats come from its {@link GearMaterial}; its picture on
 * the body comes from {@code textures/models/armor/<texture>_layer_1.png} (helmet, chest, boots) or
 * {@code _layer_2.png} (leggings), drawn by {@code tools/gear/gen_gear.py}.
 */
public class GearItem extends ArmorItem {
    private final String texture;

    public GearItem(ArmorMaterial material, Type type, String texture, Properties properties) {
        super(material, type, properties);
        this.texture = texture;
    }

    public String texture() {
        return texture;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        return Noirm.MOD_ID + ":textures/models/armor/" + texture + "_layer_" + layer + ".png";
    }
}

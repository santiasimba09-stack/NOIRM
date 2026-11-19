package com.noirm.gear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.noirm.Noirm;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * A piece of wearable ninja gear. Its stats come from its {@link GearMaterial}; its picture on
 * the body comes from {@code textures/models/armor/<texture>_layer_1.png} (helmet, chest, boots) or
 * {@code _layer_2.png} (leggings), drawn by {@code tools/gear/gen_gear.py}. Some pieces carry a
 * small {@link GearPerk}.
 */
public class GearItem extends ArmorItem {
    /** One id per armour type for the FISTS perk's attack-damage bonus. */
    private static final UUID[] FIST_IDS = {
            UUID.fromString("7a1e4c2b-90d3-4f5e-8b61-2c9d0e3f4a51"), UUID.fromString("7a1e4c2b-90d3-4f5e-8b61-2c9d0e3f4a52"),
            UUID.fromString("7a1e4c2b-90d3-4f5e-8b61-2c9d0e3f4a53"), UUID.fromString("7a1e4c2b-90d3-4f5e-8b61-2c9d0e3f4a54")};

    /** One id per armour type for the HEAVY perk's slowness. */
    private static final UUID[] HEAVY_IDS = {
            UUID.fromString("3c5b9e1d-6a2f-4d70-b8c4-1e2f3a4b5c61"), UUID.fromString("3c5b9e1d-6a2f-4d70-b8c4-1e2f3a4b5c62"),
            UUID.fromString("3c5b9e1d-6a2f-4d70-b8c4-1e2f3a4b5c63"), UUID.fromString("3c5b9e1d-6a2f-4d70-b8c4-1e2f3a4b5c64")};

    private final String texture;
    private final GearPerk perk;
    private final float perkPower;
    private Multimap<Attribute, AttributeModifier> withPerk;

    public GearItem(ArmorMaterial material, Type type, String texture, Properties properties) {
        this(material, type, texture, GearPerk.NONE, 0f, properties);
    }

    /** {@code perkPower}: for FISTS, the attack damage it adds; for HEAVY, the share of speed it takes away. */
    public GearItem(ArmorMaterial material, Type type, String texture, GearPerk perk, float perkPower, Properties properties) {
        super(material, type, properties);
        this.texture = texture;
        this.perk = perk;
        this.perkPower = perkPower;
    }

    public String texture() {
        return texture;
    }

    public GearPerk perk() {
        return perk;
    }

    public float perkPower() {
        return perkPower;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        return Noirm.MOD_ID + ":textures/models/armor/" + texture + "_layer_" + layer + ".png";
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
        if ((perk != GearPerk.FISTS && perk != GearPerk.HEAVY) || slot != getEquipmentSlot() || perkPower <= 0) {
            return base;
        }
        if (withPerk == null) {
            var builder = ImmutableMultimap.<Attribute, AttributeModifier>builder().putAll(base);
            if (perk == GearPerk.FISTS) {
                builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(FIST_IDS[getType().ordinal()], "Gear fists",
                        perkPower, AttributeModifier.Operation.ADDITION));
            } else {
                builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(HEAVY_IDS[getType().ordinal()], "Heavy gear",
                        -perkPower, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            withPerk = builder.build();
        }
        return withPerk;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (perk != GearPerk.NONE) {
            tooltip.add(Component.translatable(perk.tooltipKey()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}

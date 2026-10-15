package com.noirm.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * The bō staff (rod): a long iron-capped pole. Slower than a sword but it reaches a block
 * further and swings wide; in combat mode it fights with the staff moveset (sweeps, thrusts,
 * an overhead smash and a spinning sweep).
 */
public class StaffItem extends Item implements WeaponMoves.Armed {
    private static final UUID REACH_ID = UUID.fromString("3c1f0f9e-5d2a-4b8e-9a61-7f4e2d9b0a17");
    public static final double EXTRA_REACH = 1.0;

    private final Multimap<Attribute, AttributeModifier> modifiers;

    public StaffItem(Properties properties, float damage, float speed) {
        super(properties);
        modifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage, AttributeModifier.Operation.ADDITION))
                .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", speed, AttributeModifier.Operation.ADDITION))
                .put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_ID, "Staff reach", EXTRA_REACH, AttributeModifier.Operation.ADDITION))
                .build();
    }

    @Override
    public Moveset moveset() {
        return Moveset.STAFF;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return (enchantment.category == EnchantmentCategory.WEAPON && enchantment != Enchantments.SWEEPING_EDGE)
                || enchantment.category == EnchantmentCategory.BREAKABLE;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(net.minecraft.world.item.Items.IRON_INGOT) || super.isValidRepairItem(stack, repair);
    }
}

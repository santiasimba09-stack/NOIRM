package com.noirm.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A ninja tool you throw with right click: kunai, shuriken, fūma shuriken, senbon, explosive
 * kunai. They stack, so they have no durability; a single one (not a stack) takes Sharpness,
 * Smite, Bane, Knockback and Fire Aspect, which also work when it's thrown. The kunai doubles as
 * a dagger (its own melee damage and the kunai moveset in combat mode).
 */
public class ThrowingWeaponItem extends Item implements WeaponMoves.Armed {
    private final ThrowStats stats;
    private final Moveset moveset;
    private final Multimap<Attribute, AttributeModifier> melee;
    private final boolean fan;

    /**
     * @param meleeDamage damage added to a punch (0 = not a melee weapon)
     * @param meleeSpeed  attack speed modifier (vanilla's is 4; -2 gives 2 swings a second)
     * @param moveset     its moveset in combat mode, or null
     * @param fan         sneak + throw throws three in a fan (shuriken)
     */
    public ThrowingWeaponItem(Properties properties, ThrowStats stats, float meleeDamage, float meleeSpeed, Moveset moveset, boolean fan) {
        super(properties);
        this.stats = stats;
        this.moveset = moveset;
        this.fan = fan;
        if (meleeDamage > 0) {
            this.melee = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                    .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", meleeDamage, AttributeModifier.Operation.ADDITION))
                    .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", meleeSpeed, AttributeModifier.Operation.ADDITION))
                    .build();
        } else {
            this.melee = ImmutableMultimap.of();
        }
    }

    public ThrowStats stats() {
        return stats;
    }

    @Override
    public Moveset moveset() {
        return moveset;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            boolean spread = fan && player.isShiftKeyDown();
            Throwing.playerThrow(player, hand, stack, this, spread);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? melee : super.getDefaultAttributeModifiers(slot);
    }

    // ---------------------------------------------------------------- enchanting

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return stack.getCount() == 1;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.SWEEPING_EDGE) {
            return false;
        }
        if (enchantment == Enchantments.MOB_LOOTING) {
            return !melee.isEmpty(); // a dagger can loot; a thrown star can't
        }
        return enchantment.category == EnchantmentCategory.WEAPON;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.noirm.weapon.throw", stats.damage()).withStyle(net.minecraft.ChatFormatting.GRAY));
        if (fan) {
            tooltip.add(Component.translatable("tooltip.noirm.weapon.fan").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        if (stats.fuse() > 0) {
            tooltip.add(Component.translatable("tooltip.noirm.weapon.explosive").withStyle(net.minecraft.ChatFormatting.RED));
        }
        if (stats.slowTicks() > 0) {
            tooltip.add(Component.translatable("tooltip.noirm.weapon.numbs").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        if (stats.pierce() > 0) {
            tooltip.add(Component.translatable("tooltip.noirm.weapon.pierce", stats.pierce() + 1).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}

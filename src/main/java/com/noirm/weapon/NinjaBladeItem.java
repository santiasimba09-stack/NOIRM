package com.noirm.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One of the v0.13 blades (tantō, katana, nodachi, tonfa, yari, tekko-kagi, tessen). Its numbers
 * come from {@link BladeStats} (data), what it does on hit from {@link BladeEvents}. It enchants
 * and mends like a sword (iron).
 */
public class NinjaBladeItem extends SwordItem implements WeaponMoves.Armed {
    private static final UUID REACH_ID = UUID.fromString("5e8c2a71-0d4b-4f6e-b3a9-6c1d7e2f8a40");
    /** Stack tag: set (server side) while the wielder's chakra flows into the blade; the model glows. */
    public static final String FLOW_TAG = "NoirmFlow";

    private final String id;
    private final BladeStats stats;
    private final Multimap<Attribute, AttributeModifier> modifiers;
    /** The modifiers while chakra flows into it, when the flow lengthens the reach (else the same). */
    private final Multimap<Attribute, AttributeModifier> flowModifiers;

    public NinjaBladeItem(String id, Properties properties) {
        this(id, BladeStats.get(id), properties);
    }

    private NinjaBladeItem(String id, BladeStats stats, Properties properties) {
        super(WeaponTiers.NINJATO, 0, 0f, properties.durability(stats.durability()));
        this.id = id;
        this.stats = stats;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", stats.damage() - 1.0, AttributeModifier.Operation.ADDITION))
                .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", stats.speed() - 4.0, AttributeModifier.Operation.ADDITION));
        ImmutableMultimap<Attribute, AttributeModifier> base = builder.build();
        if (stats.reach() > 0) {
            builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_ID, "Weapon reach", stats.reach(), AttributeModifier.Operation.ADDITION));
        }
        modifiers = builder.build();
        float flowReach = stats.flow() == null ? 0f : stats.flow().reach();
        if (flowReach > 0) {
            flowModifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder().putAll(base)
                    .put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_ID, "Weapon reach", stats.reach() + flowReach, AttributeModifier.Operation.ADDITION))
                    .build();
        } else {
            flowModifiers = modifiers;
        }
    }

    /** The blade's id, e.g. {@code katana}. */
    public String bladeId() {
        return id;
    }

    public BladeStats stats() {
        return stats;
    }

    @Override
    public Moveset moveset() {
        return stats.moveset();
    }

    /** Is the wielder's chakra flowing into this stack right now (it glows)? */
    public static boolean isFlowing(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(FLOW_TAG);
    }

    @Override
    public float getDamage() {
        return stats.damage() - 1f;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }

    /** While chakra flows into it (the stack's flow tag), a flow that lengthens the reach adds its blocks. */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND && isFlowing(stack) ? flowModifiers : super.getAttributeModifiers(slot, stack);
    }

    @Override
    public int getEnchantmentValue() {
        return stats.enchantability();
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // The flow tag coming and going shouldn't make the blade bob in the hand.
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stats.trait() != BladeStats.Trait.NONE) {
            tooltip.add(Component.translatable("tooltip.noirm.blade." + stats.trait().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GOLD));
        }
        if (stats.flow() != null) {
            tooltip.add(Component.translatable("tooltip.noirm.blade.flow." + id)
                    .withStyle(isFlowing(stack) ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
    }
}

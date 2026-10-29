package com.noirm.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import java.util.List;
import java.util.UUID;

/**
 * A special weapon: a blade with its own {@link Power} (see {@link SpecialWeaponEvents} for what
 * each does on hit or on guard), fighting with one of the weapon movesets, sometimes with extra
 * reach. The numbers live in {@link SpecialWeapons}.
 */
public class SpecialWeaponItem extends SwordItem implements WeaponMoves.Armed {
    /** What the weapon does beyond cutting. */
    public enum Power {
        /** Crescent Cleaver: heals its wielder a little on every hit, and mends itself. */
        LIFESTEAL,
        /** Sharkskin Greatsword: eats the target's chakra and feeds it to the wielder. */
        CHAKRA_DRAIN,
        /** Thunder Fangs: lightning numbs (stuns) what they cut, for a little chakra. */
        THUNDER,
        /** Reedcutter: just very fast (and a little longer than it looks). */
        SWIFT,
        /** Gale Gunbai: hits blow enemies back; guarding blocks like a shield and turns projectiles back. */
        GALE,
        /** Kusarigama: the chain reaches far and drags the target in. */
        CHAIN
    }

    private static final UUID REACH_ID = UUID.fromString("9b2d4f6a-7c1e-4d3b-8a5f-1e2c3d4b5a69");

    private final Moveset moveset;
    private final Power power;
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public SpecialWeaponItem(Tier tier, int damage, float speed, Moveset moveset, double reach, Power power, Properties properties) {
        super(tier, damage, speed, properties);
        this.moveset = moveset;
        this.power = power;
        @SuppressWarnings("deprecation")
        Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.<Attribute, AttributeModifier>builder().putAll(base);
        if (reach > 0) {
            builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_ID, "Weapon reach", reach, AttributeModifier.Operation.ADDITION));
        }
        modifiers = builder.build();
    }

    public Power power() {
        return power;
    }

    @Override
    public Moveset moveset() {
        return moveset;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return (power == Power.GALE && action == ToolActions.SHIELD_BLOCK) || super.canPerformAction(stack, action);
    }

    // The gunbai is raised like a shield (hold right click outside combat mode).

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return power == Power.GALE ? UseAnim.BLOCK : super.getUseAnimation(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return power == Power.GALE ? 72000 : super.getUseDuration(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (power == Power.GALE) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    /** Is {@code entity} guarding with a gunbai (raised with right click, or the combat guard)? */
    public static boolean guardsWithFan(LivingEntity entity) {
        if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof SpecialWeaponItem fan && fan.power == Power.GALE) {
            return true;
        }
        var state = com.noirm.combat.CombatStates.getIfPresent(entity);
        if (state == null || !state.isGuarding()) {
            return false;
        }
        for (ItemStack held : new ItemStack[] {entity.getMainHandItem(), entity.getOffhandItem()}) {
            if (held.getItem() instanceof SpecialWeaponItem fan && fan.power == Power.GALE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.noirm.special." + power.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GOLD));
    }
}

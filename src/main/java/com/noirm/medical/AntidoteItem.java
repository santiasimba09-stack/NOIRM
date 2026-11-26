package com.noirm.medical;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An antidote: drink it to wash out poison, wither, nausea and hunger, with a moment of
 * regeneration. Anyone can mix one at a crafting table, but only someone taught Poison Lore by the
 * medical ninja gets a working one; anyone else's comes out <b>botched</b> ({@value #BOTCHED} tag):
 * bitter water that only makes you queasy. See {@link MedicalEvents#crafted}.
 */
public class AntidoteItem extends Item {
    public static final String BOTCHED = "Botched";
    /** What a good antidote clears. */
    public static final List<MobEffect> CURES = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.CONFUSION, MobEffects.HUNGER);
    /** Regeneration after a good antidote (ticks). */
    public static final int REGEN_TICKS = 80;

    public AntidoteItem(Properties properties) {
        super(properties);
    }

    public static boolean botched(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(BOTCHED);
    }

    public static void botch(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(BOTCHED, true);
    }

    /** Drinks the antidote's effect (public so tests can call it). Returns whether it cured anything. */
    public static boolean apply(LivingEntity drinker, ItemStack stack) {
        if (botched(stack)) {
            drinker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            if (drinker instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.noirm.antidote.botched").withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }
        boolean cured = false;
        for (MobEffect effect : CURES) {
            cured |= drinker.removeEffect(effect);
        }
        drinker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 0));
        if (drinker instanceof Player player) {
            player.displayClientMessage(Component.translatable(cured ? "message.noirm.antidote.cured" : "message.noirm.antidote.clean")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return cured;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            apply(entity, stack);
            level.playSound(null, entity.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            stack.shrink(1);
            if (stack.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(botched(stack) ? "item.noirm.antidote.botched" : "item.noirm.antidote.tip")
                .withStyle(botched(stack) ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
    }
}

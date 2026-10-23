package com.noirm.dojutsu;

import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Sharingan eye (or a pair of Mangekyō eyes) taken from a fallen Uchiha. Hold use for three
 * seconds to transplant it into yourself (see {@link MangekyoTransplant}); a refused transplant
 * keeps the eye.
 */
public class SharinganEyeItem extends Item {
    public SharinganEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        MangekyoTransplant.EyeInfo eye = MangekyoTransplant.EyeInfo.of(stack);
        if (!level.isClientSide()) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            MangekyoTransplant.Outcome outcome = data == null ? MangekyoTransplant.Outcome.EMPTY : MangekyoTransplant.outcome(data, eye);
            if (!outcome.success()) {
                player.displayClientMessage(Component.translatable("message.noirm.transplant." + outcome.key()).withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
            player.displayClientMessage(Component.translatable("message.noirm.transplant.surgery"), true);
        } else if (eye == null) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return MangekyoTransplant.SURGERY_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data != null && MangekyoTransplant.transplant(player, data, MangekyoTransplant.EyeInfo.of(stack)).success()
                    && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        MangekyoTransplant.EyeInfo eye = MangekyoTransplant.EyeInfo.of(stack);
        return eye != null && eye.mangekyo();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MangekyoTransplant.EyeInfo eye = MangekyoTransplant.EyeInfo.of(stack);
        if (eye == null) {
            tooltip.add(Component.translatable("item.noirm.sharingan_eye.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (eye.donor() != null && !eye.donor().isEmpty()) {
            tooltip.add(Component.translatable("item.noirm.sharingan_eye.donor", eye.donor()).withStyle(ChatFormatting.GRAY));
        }
        if (eye.mangekyo()) {
            tooltip.add(Component.translatable("pattern.noirm.mangekyo." + eye.pattern()).withStyle(ChatFormatting.DARK_RED));
            for (MangekyoAbility ability : new MangekyoAbility[] {eye.left(), eye.right()}) {
                if (ability != null) {
                    tooltip.add(Component.literal(" · ").append(Component.translatable(Sharingan.jutsuKey(ability.jutsu()))).withStyle(ChatFormatting.RED));
                }
            }
            tooltip.add(Component.translatable("item.noirm.sharingan_eye.strain", Math.round(eye.strain())).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.noirm.sharingan_eye.tomoe", eye.stage()).withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("item.noirm.sharingan_eye.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

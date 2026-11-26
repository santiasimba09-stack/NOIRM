package com.noirm.pills;

import com.noirm.player.ShinobiCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A pill: swallowed in half a second (no hunger needed). What it does is its JSON
 * ({@code data/noirm/pills/<id>.json}, see {@link PillEffects}); its grade is on the stack
 * ({@link Grade}). While its cooldown runs it won't go down.
 */
public class PillItem extends Item {
    /** Ticks to swallow one. */
    public static final int USE_TICKS = 10;

    public PillItem(Properties properties) {
        super(properties);
    }

    /** The pill id ({@code noirm:soldier_pill}): the item's own id. */
    public ResourceLocation pillId() {
        return ForgeRegistries.ITEMS.getKey(this);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int cooldown = level.isClientSide() ? PillClientState.cooldown(pillId())
                : ShinobiCapability.get(player).map(d -> d.pills().cooldown(pillId())).orElse(0);
        if (cooldown > 0 && !player.getAbilities().instabuild) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.noirm.pill.cooldown", (cooldown + 19) / 20)
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return stack;
        }
        boolean taken = ShinobiCapability.get(player)
                .map(data -> PillEffects.take(player, data, pillId(), Grade.of(stack)) == PillEffects.Result.TAKEN).orElse(false);
        if (taken && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public Component getName(ItemStack stack) {
        Grade grade = Grade.of(stack);
        Component base = super.getName(stack);
        if (grade == Grade.STANDARD) {
            return base;
        }
        return Component.translatable("pill.noirm.graded", Component.translatable(grade.translationKey()), base).withStyle(grade.format);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return Grade.of(stack) == Grade.PERFECT;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Grade grade = Grade.of(stack);
        tooltip.add(Component.translatable("pill.noirm.grade_line", Component.translatable(grade.translationKey()).withStyle(grade.format))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".tip").withStyle(ChatFormatting.GRAY));
        if (level != null && level.isClientSide()) {
            int cd = PillClientState.cooldown(pillId());
            if (cd > 0) {
                tooltip.add(Component.translatable("pill.noirm.cooldown_line", (cd + 19) / 20).withStyle(ChatFormatting.RED));
            }
        }
    }
}

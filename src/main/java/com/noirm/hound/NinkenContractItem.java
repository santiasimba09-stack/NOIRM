package com.noirm.hound;

import com.noirm.player.ShinobiCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A ninken summoning contract (the Hatake's way to a hound, like Kakashi's pack). Right-click it:
 * <ul>
 *   <li>with no hound: you sign it in blood (a little health and chakra) and a puppy answers in a
 *   puff of smoke;</li>
 *   <li>with a hound: dismiss it home, or summon it back to your side (it costs some chakra).</li>
 * </ul>
 */
public class NinkenContractItem extends Item {
    /** Signing in blood: the health it takes. */
    public static final float SIGN_HEALTH = 4f;

    public NinkenContractItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        HoundData hd = Hounds.data(sp);
        if (hd == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (!hd.has()) {
            if (!player.getAbilities().instabuild) {
                player.hurt(player.damageSources().magic(), SIGN_HEALTH);
            }
            NinjaHound hound = Hounds.grantPuppy(sp, null, true);
            if (hound == null) {
                return InteractionResultHolder.fail(stack);
            }
            ShinobiCapability.get(sp).ifPresent(d -> d.talents().setHoundGiven(true));
        } else {
            Hounds.recall(sp);
        }
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("item.noirm.ninken_contract.desc").withStyle(ChatFormatting.GRAY));
    }
}

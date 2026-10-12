package com.noirm.biju;

import com.noirm.entity.SparringNinja;
import com.noirm.entity.TailedBeast;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A sealing scroll. How tailed beasts change hands:
 * <ul>
 *   <li><b>Empty</b>, used on a <b>subdued</b> tailed beast: the beast is captured in the scroll.</li>
 *   <li><b>Holding a beast</b>, used on a player or a sparring ninja: the beast is sealed into
 *       them (they become a jinchūriki). Used in the air: sealed into yourself.</li>
 *   <li><b>Holding a beast</b>, crouch + use on a block: the beast is released there, angry.</li>
 * </ul>
 */
public class SealingScrollItem extends Item {
    private static final String TAILS = "Tails";

    public SealingScrollItem(Properties properties) {
        super(properties);
    }

    public static int tails(ItemStack stack) {
        return stack.getTag() == null ? 0 : stack.getTag().getInt(TAILS);
    }

    public static void setTails(ItemStack stack, int tails) {
        if (tails <= 0) {
            stack.removeTagKey(TAILS);
        } else {
            stack.getOrCreateTag().putInt(TAILS, tails);
        }
    }

    // ---------------------------------------------------------------- uses

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (tails(stack) == 0 && target instanceof TailedBeast) {
            if (!player.level().isClientSide()) {
                capture(player, player.getItemInHand(hand), (TailedBeast) target);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }
        if (tails(stack) > 0 && (target instanceof Player || target instanceof SparringNinja)) {
            if (!player.level().isClientSide()) {
                sealInto(player, player.getItemInHand(hand), target);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (tails(stack) == 0) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide()) {
            sealInto(player, stack, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null || !player.isShiftKeyDown() || tails(stack) == 0) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel level) {
            release(level, context.getClickedPos().above(), stack, player);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    // ---------------------------------------------------------------- the actual sealing (public for tests)

    /** Captures a subdued beast into an empty scroll. Returns false if the beast still fights. */
    public static boolean capture(Player player, ItemStack scroll, TailedBeast beast) {
        if (!beast.isSubdued()) {
            player.displayClientMessage(Component.translatable("message.noirm.scroll.not_subdued", beast.getName()), true);
            return false;
        }
        int tails = beast.tails();
        if (scroll.getCount() > 1) {
            scroll.shrink(1);
            ItemStack filled = new ItemStack(scroll.getItem());
            setTails(filled, tails);
            if (!player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
        } else {
            setTails(scroll, tails);
        }
        effects(beast, ParticleTypes.REVERSE_PORTAL, SoundEvents.END_PORTAL_FRAME_FILL);
        beast.discard();
        player.sendSystemMessage(Component.translatable("message.noirm.scroll.captured", BijuSystem.beastName(tails)).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Seals the scroll's beast into {@code target} (a player, possibly the user, or a ninja). */
    public static boolean sealInto(Player user, ItemStack scroll, LivingEntity target) {
        int tails = tails(scroll);
        if (tails == 0) {
            return false;
        }
        if (target instanceof SparringNinja ninja) {
            if (ninja.tails() > 0) {
                user.displayClientMessage(Component.translatable("message.noirm.scroll.already", target.getDisplayName()), true);
                return false;
            }
            ninja.sealBeast(tails);
        } else if (target instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data == null || data.biju().isJinchuriki()) {
                user.displayClientMessage(Component.translatable("message.noirm.scroll.already", target.getDisplayName()), true);
                return false;
            }
            BijuSystem.seal(player, data, tails);
            player.sendSystemMessage(Component.translatable("message.noirm.scroll.sealed_you", BijuSystem.beastName(tails)).withStyle(ChatFormatting.GOLD));
        } else {
            return false;
        }
        if (user != target) {
            user.sendSystemMessage(Component.translatable("message.noirm.scroll.sealed_other", BijuSystem.beastName(tails), target.getDisplayName()));
        }
        effects(target, ParticleTypes.ENCHANT, SoundEvents.BEACON_POWER_SELECT);
        if (!user.getAbilities().instabuild) {
            scroll.shrink(1);
        } else {
            setTails(scroll, 0);
        }
        return true;
    }

    /** Lets the scroll's beast out at {@code pos}. It is not grateful. */
    public static TailedBeast release(ServerLevel level, BlockPos pos, ItemStack scroll, @Nullable Player user) {
        TailedBeast beast = NoirmEntities.TAILED_BEAST.get().create(level);
        if (beast == null) {
            return null;
        }
        beast.setTails(tails(scroll));
        beast.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        level.addFreshEntity(beast);
        if (user != null && !user.getAbilities().instabuild) {
            beast.setTarget(user);
        }
        setTails(scroll, 0);
        effects(beast, ParticleTypes.EXPLOSION, SoundEvents.RAVAGER_ROAR);
        return beast;
    }

    private static void effects(LivingEntity at, net.minecraft.core.particles.ParticleOptions particle, net.minecraft.sounds.SoundEvent sound) {
        if (at.level() instanceof ServerLevel level) {
            level.sendParticles(particle, at.getX(), at.getY() + at.getBbHeight() * 0.5, at.getZ(), 60,
                    at.getBbWidth() * 0.4, at.getBbHeight() * 0.4, at.getBbWidth() * 0.4, 0.3);
            level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, 1.5f, 0.8f);
        }
    }

    // ---------------------------------------------------------------- looks

    @Override
    public Component getName(ItemStack stack) {
        int tails = tails(stack);
        return tails == 0 ? super.getName(stack)
                : Component.translatable("item.noirm.sealing_scroll.holding", BijuSystem.beastName(tails));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tails(stack) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = tails(stack) == 0 ? "item.noirm.sealing_scroll.tip_empty" : "item.noirm.sealing_scroll.tip_full";
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}

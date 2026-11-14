package com.noirm.medical;

import com.noirm.dojutsu.MangekyoTransplant;
import com.noirm.dojutsu.SharinganEyeItem;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Who puts a new eye in. The rules of what an eye does are in {@link MangekyoTransplant}; this is
 * about the <b>surgery</b>:
 * <ul>
 *   <li><b>The medical ninja</b> (her "Eye transplant" service, for ryo) does it properly: no risk,
 *       only the usual moment of darkness while the eye settles.</li>
 *   <li><b>Yourself</b> (hold the eye's use, as before): it still works, but it's back-alley surgery.
 *       {@link #SELF_FAIL_CHANCE} of the time the eye is <b>ruined</b> (lost) and you're hurt and
 *       blind for a while; when it works you still take {@link #SELF_DAMAGE} and a long blindness.</li>
 * </ul>
 */
public final class EyeSurgery {
    /** Chance that operating on yourself ruins the eye. */
    public static final float SELF_FAIL_CHANCE = 0.35f;
    /** Damage operating on yourself does when it works (health points)... */
    public static final float SELF_DAMAGE = 4f;
    /** ...and when it fails. */
    public static final float SELF_FAIL_DAMAGE = 8f;
    /** Blindness after a self-surgery (ticks): 15 s when it works, 40 s when it fails. */
    public static final int SELF_BLIND_TICKS = 300;
    public static final int SELF_FAIL_BLIND_TICKS = 800;

    private EyeSurgery() {}

    /** The eye a medic would put in: the one in your main hand if it fits, else the first one in your bag that does. */
    public static ItemStack eyeToTransplant(Player player, ShinobiData data) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (fits(data, held)) {
            return held;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (fits(data, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean fits(ShinobiData data, ItemStack stack) {
        return stack.getItem() instanceof SharinganEyeItem
                && MangekyoTransplant.outcome(data, MangekyoTransplant.EyeInfo.of(stack)).success();
    }

    /** Why the medic can't operate now (null if she can). */
    public static Component blocker(Player player, ShinobiData data) {
        if (!eyeToTransplant(player, data).isEmpty()) {
            return null;
        }
        boolean anyEye = player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof SharinganEyeItem);
        return Component.translatable(anyEye ? "screen.noirm.master.why.eye_wont_take" : "screen.noirm.master.why.no_eye");
    }

    /** The medic's surgery: safe. Returns whether it was done. */
    public static boolean byMedic(Player player, ShinobiData data, Component medic) {
        ItemStack eye = eyeToTransplant(player, data);
        if (eye.isEmpty()) {
            return false;
        }
        MangekyoTransplant.Outcome outcome = MangekyoTransplant.transplant(player, data, MangekyoTransplant.EyeInfo.of(eye));
        if (!outcome.success()) {
            return false;
        }
        eye.shrink(1);
        player.sendSystemMessage(Component.translatable("message.noirm.surgery.medic", medic).withStyle(ChatFormatting.GREEN));
        return true;
    }

    /**
     * Operating on yourself with {@code stack} (the eye item's own use). {@code roll} (0..1) below
     * {@link #SELF_FAIL_CHANCE} ruins the eye. Returns whether the transplant happened. The eye is
     * used up either way (unless creative), except when it would be refused anyway.
     */
    public static boolean selfSurgery(Player player, ShinobiData data, ItemStack stack, float roll) {
        MangekyoTransplant.EyeInfo eye = MangekyoTransplant.EyeInfo.of(stack);
        if (!MangekyoTransplant.outcome(data, eye).success()) {
            MangekyoTransplant.transplant(player, data, eye); // only says why it's refused
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        if (roll < SELF_FAIL_CHANCE) {
            if (!creative) {
                stack.shrink(1);
            }
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().magic(), SELF_FAIL_DAMAGE);
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SELF_FAIL_BLIND_TICKS, 0));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);
            player.sendSystemMessage(Component.translatable("message.noirm.surgery.self_failed").withStyle(ChatFormatting.DARK_RED));
            return false;
        }
        MangekyoTransplant.transplant(player, data, eye);
        if (!creative) {
            stack.shrink(1);
        }
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().magic(), SELF_DAMAGE);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SELF_BLIND_TICKS, 0));
        player.sendSystemMessage(Component.translatable("message.noirm.surgery.self_done").withStyle(ChatFormatting.GRAY));
        return true;
    }
}

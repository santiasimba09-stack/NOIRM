package com.noirm.mixin;

import com.noirm.training.ChakraDrills;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tree climbing with chakra (ChakraDrills): a crouching player pushing into a trunk counts as being on a ladder, so
 * the game's own ladder movement carries them up (and holds them still when they stop). Runs on both sides, so the
 * client predicts it. Listed under both names with {@code remap = false}, like the other mixins.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {
    @Inject(method = {"onClimbable()Z", "m_6147_()Z"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void noirm$chakraClimb(CallbackInfoReturnable<Boolean> result) {
        if (ChakraDrills.climbing((LivingEntity) (Object) this)) {
            result.setReturnValue(true);
        }
    }
}

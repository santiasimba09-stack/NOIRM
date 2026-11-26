package com.noirm.mixin;

import com.noirm.combat.ChakraArmour;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Chakra durability: wear on a worn armour piece comes out of its chakra pool first
 * ({@link ChakraArmour}). Patches the start of {@code ItemStack.hurtAndBreak}: if the pool takes
 * some of the wear, the call is cancelled and the rest (if any) is applied again. Both method names
 * are listed with {@code remap = false}, like the other NOIRM mixins.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackWearMixin {
    @Inject(method = {"hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            "m_41622_(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private <T extends LivingEntity> void noirm$chakraPool(int amount, T wearer, Consumer<T> onBroken, CallbackInfo info) {
        ItemStack self = (ItemStack) (Object) this;
        int left = ChakraArmour.absorb(self, amount, wearer);
        if (left == amount) {
            return;
        }
        info.cancel();
        if (left > 0) {
            ChakraArmour.applyLeftover(self, left, wearer, onBroken);
        }
    }
}

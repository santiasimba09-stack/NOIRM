package com.noirm.mixin;

import com.noirm.combat.NinjaArmour;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ninja attacks mostly ignore vanilla armour (owner, 2026-11-12). This patches the two vanilla steps
 * that work out armour ({@code getDamageAfterArmorAbsorb}) and enchantments plus Resistance
 * ({@code getDamageAfterMagicAbsorb}): at the start it notes the amount, at the end it lets
 * {@link NinjaArmour} change the answer for ninja attacks. All the rules live there.
 *
 * <p>Like {@code BlockStateBaseMixin}, each method is listed under both its names (the development
 * name and the real game's {@code m_…} name) with {@code remap = false}, so no refmap is needed.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityArmourMixin {
    @Inject(method = {"getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F", "m_21161_(Lnet/minecraft/world/damagesource/DamageSource;F)F"}, at = @At("HEAD"), remap = false)
    private void noirm$armourIn(DamageSource source, float amount, CallbackInfoReturnable<Float> result) {
        NinjaArmour.armourIn(amount);
    }

    @Inject(method = {"getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F", "m_21161_(Lnet/minecraft/world/damagesource/DamageSource;F)F"}, at = @At("RETURN"), cancellable = true, remap = false)
    private void noirm$armourOut(DamageSource source, float amount, CallbackInfoReturnable<Float> result) {
        float vanilla = result.getReturnValueF();
        float ours = NinjaArmour.armourOut((LivingEntity) (Object) this, source, vanilla);
        if (ours != vanilla) {
            result.setReturnValue(ours);
        }
    }

    @Inject(method = {"getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F", "m_6515_(Lnet/minecraft/world/damagesource/DamageSource;F)F"}, at = @At("HEAD"), remap = false)
    private void noirm$magicIn(DamageSource source, float amount, CallbackInfoReturnable<Float> result) {
        NinjaArmour.magicIn(amount);
    }

    @Inject(method = {"getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F", "m_6515_(Lnet/minecraft/world/damagesource/DamageSource;F)F"}, at = @At("RETURN"), cancellable = true, remap = false)
    private void noirm$magicOut(DamageSource source, float amount, CallbackInfoReturnable<Float> result) {
        float vanilla = result.getReturnValueF();
        float ours = NinjaArmour.magicOut((LivingEntity) (Object) this, source, vanilla);
        if (ours != vanilla) {
            result.setReturnValue(ours);
        }
    }
}
